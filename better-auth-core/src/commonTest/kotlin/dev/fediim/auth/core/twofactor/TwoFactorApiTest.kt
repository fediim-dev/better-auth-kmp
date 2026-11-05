package dev.fediim.auth.core.twofactor

import dev.fediim.auth.core.model.AuthResponse
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class TwoFactorApiTest {
  private val uri = "otpauth://totp/Acme:a@b.com?secret=ABCDEFGH"

  private fun rig(): TwoFactorRig = TwoFactorRig { request ->
    when (request.url.encodedPath) {
      "/api/auth/two-factor/enable" ->
          json("""{"totpURI":"$uri","backupCodes":["code-one","code-two"]}""")
      "/api/auth/two-factor/get-totp-uri" -> json("""{"totpURI":"$uri"}""")
      "/api/auth/two-factor/generate-backup-codes" ->
          json("""{"status":true,"backupCodes":["new-one","new-two"]}""")
      else -> json("""{"status":true}""")
    }
  }

  @Test
  fun t4ManagementCallsSendBearerAndDecode() = runTest {
    val rig = rig()
    rig.preStore("tA")
    val api = rig.client.twoFactor

    val enrolled = api.enable("pw", "Acme")
    assertEquals(
        BetterAuthResult.Success(TotpEnrollment(uri, listOf("code-one", "code-two"))),
        enrolled,
    )
    assertEquals("""{"password":"pw","issuer":"Acme"}""", rig.bodyOf(0))
    api.enable(null)
    assertEquals("{}", rig.bodyOf(1))
    assertEquals(BetterAuthResult.Success(Unit), api.disable("pw"))
    assertEquals("""{"password":"pw"}""", rig.bodyOf(2))
    assertEquals(BetterAuthResult.Success(uri), api.getTotpUri("pw"))
    assertEquals(
        BetterAuthResult.Success(listOf("new-one", "new-two")),
        api.generateBackupCodes("pw"),
    )
    api.verifyBackupCode("code-one")
    assertEquals("/api/auth/two-factor/verify-backup-code", rig.requests.last().url.encodedPath)
    assertEquals("""{"code":"code-one"}""", rig.bodyOf(rig.requests.lastIndex))
    rig.requests.indices.forEach { assertEquals("Bearer tA", rig.authOf(it)) }
  }

  @Test
  fun t4ServerErrorsAndSecretsInToString() = runTest {
    val rig = TwoFactorRig {
      json("""{"code":"INVALID_PASSWORD","message":"no"}""", HttpStatusCode.BadRequest)
    }
    assertEquals(
        BetterAuthResult.Failure(400, BetterAuthError("INVALID_PASSWORD", "no")),
        rig.client.twoFactor.enable("bad"),
    )
    val text = TotpEnrollment(uri, listOf("code-one", "code-two")).toString()
    assertFalse("otpauth" in text)
    assertFalse("code-one" in text || "code-two" in text)
  }

  @Test
  fun t4SessionModeStoresOnlyTheHeaderToken() = runTest {
    val body = """{"token":"old","user":$USER_JSON}"""
    val rig = TwoFactorRig { json(body) }
    rig.preStore("tA")
    assertIs<BetterAuthResult.Success<*>>(rig.client.twoFactor.verifyTotp("123456"))
    assertEquals("tA", rig.storage.token())

    val rotating = TwoFactorRig { json(body, extra = listOf("set-auth-token" to "rot")) }
    rotating.preStore("tA")
    rotating.client.twoFactor.verifyTotp("123456")
    assertEquals("rot", rotating.storage.token())
  }

  @Test
  fun t5SignInResultMapsTheBody() {
    val json = Json { ignoreUnknownKeys = true }
    fun result(body: String) = json.decodeFromString(AuthResponse.serializer(), body).signInResult

    val user =
        json.decodeFromString(AuthResponse.serializer(), """{"token":"t","user":$USER_JSON}""")
    assertEquals(SignInResult.SignedIn(user), user.signInResult)
    assertIs<SignInResult.SignedIn>(result("""{"twoFactorRedirect":false}"""))
    assertEquals(
        SignInResult.TwoFactorRequired(emptyList()),
        result("""{"twoFactorRedirect":true}"""),
    )
    assertEquals(
        SignInResult.TwoFactorRequired(listOf("totp", "otp")),
        result(TWO_FACTOR_BODY.replace("\"totp\"", "\"totp\",\"otp\"")),
    )
  }
}
