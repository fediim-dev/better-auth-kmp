@file:OptIn(InternalBetterAuthApi::class)

package dev.fediim.auth.core.twofactor

import dev.fediim.auth.core.InternalBetterAuthApi
import dev.fediim.auth.core.authRequest
import dev.fediim.auth.core.captureChallengeCookie
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SignInEmailRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class TwoFactorChallengeTest {
  private val signIn = SignInEmailRequest("a@b.com", "pw")
  private val verified = """{"token":"body-tok","user":$USER_JSON}"""

  private fun twoFactorRig(
      cookies: List<String>,
      signInHeaders: List<Pair<String, String>> = emptyList(),
  ): TwoFactorRig {
    var verifyCalls = 0
    return TwoFactorRig { request ->
      when (request.url.encodedPath) {
        SIGN_IN_PATH -> json(TWO_FACTOR_BODY, cookies = cookies, extra = signInHeaders)
        VERIFY_TOTP_PATH ->
            json(
                verified,
                extra = if (verifyCalls++ == 0) listOf("set-auth-token" to "fin") else emptyList(),
            )
        else -> json("""{"status":true}""")
      }
    }
  }

  @Test
  fun t1ChallengeModeSendsTheChallengeCookieNotBearer() = runTest {
    val rig =
        twoFactorRig(
            listOf(
                "better-auth.two_factor=chal.sig; Path=/; HttpOnly",
                "better-auth.session_token=sess.v; Path=/",
                "better-auth.session_data=data.v; Path=/",
                "better-auth.dont_remember=dr.v; Path=/",
            ),
            signInHeaders = listOf("set-auth-token" to "stale"),
        )
    rig.preStore("tA")

    val response = (rig.client.signInEmail(signIn) as BetterAuthResult.Success).value
    assertEquals(SignInResult.TwoFactorRequired(listOf("totp")), response.signInResult)
    assertNull(response.token)
    assertEquals("tA", rig.storage.token())

    assertIs<BetterAuthResult.Success<*>>(rig.client.twoFactor.verifyTotp("123456"))
    assertEquals(VERIFY_TOTP_PATH, rig.requests[1].url.encodedPath)
    assertEquals("""{"code":"123456"}""", rig.bodyOf(1))
    assertEquals("better-auth.two_factor=chal.sig", rig.cookieOf(1))
    assertEquals("https://example.com", rig.requests[1].headers[HttpHeaders.Origin])
    assertNull(rig.requests[0].headers[HttpHeaders.Origin])
    assertNull(rig.authOf(1))
    assertEquals("fin", rig.storage.token())

    rig.client.twoFactor.verifyTotp("654321")
    assertEquals("Bearer fin", rig.authOf(2))
    assertNull(rig.cookieOf(2))
    assertNull(rig.requests[2].headers[HttpHeaders.Origin])
  }

  @Test
  fun t2CapturesOnlyAllowlistedChallengeCookies() = runTest {
    val captured =
        mapOf(
            "__Secure-better-auth.two_factor=s1.x; Path=/; Secure" to
                "__Secure-better-auth.two_factor=s1.x",
            "myapp.two_factor=s2.x; Path=/" to "myapp.two_factor=s2.x",
            "a=1; Path=/, better-auth.two_factor=s3.x; Path=/" to "better-auth.two_factor=s3.x",
        )
    for ((setCookie, expected) in captured) {
      val rig = twoFactorRig(listOf(setCookie))
      rig.client.signInEmail(signIn)
      rig.client.twoFactor.verifyTotp("111111")
      assertEquals(expected, rig.cookieOf(1))
    }

    for (evil in
        listOf(
            "better-auth.two_factor_evil=evil.v; Path=/",
            "two_factorX=evil.w; Path=/",
            "better-authXtwo_factor=evil.x; Path=/",
        )) {
      val rig = twoFactorRig(listOf(evil))
      rig.preStore("tA")
      assertFalse(captureOf(listOf(evil), ".two_factor"))
      rig.client.signInEmail(signIn)
      rig.client.twoFactor.verifyTotp("111111")
      assertNull(rig.cookieOf(rig.requests.lastIndex), evil)
      assertNull(rig.authOf(rig.requests.lastIndex), evil)
    }

    val control =
        twoFactorRig(
            listOf(
                "better-auth.two_factor_evil=evil.v; Path=/",
                "better-auth.session_token=sess2.v; Path=/",
                "better-auth.two_factor=good.v; Path=/",
            )
        )
    control.client.signInEmail(signIn)
    control.client.twoFactor.verifyTotp("111111")
    assertEquals("better-auth.two_factor=good.v", control.cookieOf(1))
  }

  @Test
  fun t2SuffixesOutsideTheAllowlistAreRejected() = runTest {
    val rig = twoFactorRig(listOf("better-auth.session_token=sess.v; Path=/"))
    var saved: HttpResponse? = null
    rig.client.authRequest(HttpMethod.Get, "get-session") { response, _ -> saved = response }
    for (suffix in listOf("_token", "")) {
      assertFailsWith<IllegalArgumentException> {
        rig.client.captureChallengeCookie(saved!!, suffix)
      }
    }
  }

  @Test
  fun t2BrowserWithoutVisibleCookieStillUsesChallengeMode() = runTest {
    val rig = twoFactorRig(emptyList())
    rig.preStore("tA")
    rig.client.signInEmail(signIn)
    rig.client.twoFactor.verifyTotp("111111")
    assertNull(rig.authOf(1))
    assertNull(rig.cookieOf(1))
  }

  @Test
  fun t3ChallengeLifetime() = runTest {
    val invalid = """{"code":"INVALID_CODE","message":"x"}"""
    val rig = TwoFactorRig { request ->
      when (request.url.encodedPath) {
        SIGN_IN_PATH ->
            json(TWO_FACTOR_BODY, cookies = listOf("better-auth.two_factor=c.s; Path=/"))
        VERIFY_TOTP_PATH -> json(invalid, HttpStatusCode.Unauthorized)
        else -> json("""{"status":true}""")
      }
    }
    rig.preStore("tA")
    rig.client.signInEmail(signIn)
    val first = rig.client.twoFactor.verifyTotp("000000")
    assertEquals(BetterAuthResult.Failure(401, BetterAuthError("INVALID_CODE", "x")), first)
    rig.client.twoFactor.verifyTotp("000000")
    assertEquals("better-auth.two_factor=c.s", rig.cookieOf(2))
    assertEquals("tA", rig.storage.token())
  }

  @Test
  fun t3LaterPlainSignInAndSignOutClearTheChallenge() = runTest {
    var twoFactorSignIn = true
    val rig = TwoFactorRig { request ->
      when (request.url.encodedPath) {
        SIGN_IN_PATH ->
            if (twoFactorSignIn)
                json(TWO_FACTOR_BODY, cookies = listOf("better-auth.two_factor=c.s; Path=/"))
            else json("""{"token":"new","user":$USER_JSON}""")
        else -> json("""{"token":"t","user":$USER_JSON}""")
      }
    }
    rig.client.signInEmail(signIn)
    twoFactorSignIn = false
    rig.client.signInEmail(signIn)
    rig.client.twoFactor.verifyTotp("000000")
    assertNull(rig.cookieOf(2))

    twoFactorSignIn = true
    rig.client.signInEmail(signIn)
    rig.client.signOut()
    rig.client.twoFactor.verifyTotp("000000")
    assertNull(rig.cookieOf(rig.requests.lastIndex))
    assertNull(rig.authOf(rig.requests.lastIndex))
  }

  @Test
  fun t3SignOutFencesAnInFlightTwoFactorSignIn() = runTest {
    val inFlight = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()
    val rig = TwoFactorRig { request ->
      if (request.url.encodedPath == SIGN_IN_PATH) {
        inFlight.complete(Unit)
        gate.await()
        json(TWO_FACTOR_BODY, cookies = listOf("better-auth.two_factor=c.s; Path=/"))
      } else json("""{"status":true}""")
    }
    rig.preStore("tA")
    val signingIn = launch { rig.client.signInEmail(signIn) }
    inFlight.await()
    rig.client.signOut()
    gate.complete(Unit)
    signingIn.join()
    rig.client.twoFactor.verifyTotp("000000")
    assertNull(rig.cookieOf(rig.requests.lastIndex))
    assertEquals(1L, rig.storage.epoch())
  }

  private suspend fun captureOf(cookies: List<String>, suffix: String): Boolean {
    val probe = TwoFactorRig { json("{}", cookies = cookies) }
    val result =
        probe.client.authRequest(HttpMethod.Get, "probe") { response, _ ->
          probe.client.captureChallengeCookie(response, suffix)
        }
    return (result as BetterAuthResult.Success).value
  }
}
