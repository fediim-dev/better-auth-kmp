package dev.fediim.auth.passkeys

import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class BetterAuthPasskeyClientTest {
  private val passkeyRow =
      """{"id":"p1","name":"Laptop","userId":"u1","credentialID":"cred-1","publicKey":"cGs",""" +
          """"counter":0,"deviceType":"multiDevice","backedUp":true,""" +
          """"transports":"internal,hybrid","createdAt":"2026-11-05T10:00:00.000Z","aaguid":"abc"}"""

  private fun registrationRig(): PasskeyRig {
    var optionsCalls = 0
    return PasskeyRig { request ->
      when (request.url.encodedPath) {
        REGISTER_OPTIONS_PATH ->
            json(
                registerOptionsJson,
                cookies =
                    if (optionsCalls++ == 0)
                        listOf(
                            "better-auth.better-auth-passkey=pk.sig; Path=/; HttpOnly",
                            "better-auth.session_token=sess.p; Path=/",
                        )
                    else emptyList(),
            )
        VERIFY_REGISTRATION_PATH -> json(passkeyRow)
        else -> json("[$passkeyRow]")
      }
    }
  }

  @Test
  fun t8RegisterReplaysTheChallengeCookieOnce() = runTest {
    val rig = registrationRig()
    rig.preStore("tA")
    val fake = FakeAuthenticator()
    val passkeys = BetterAuthPasskeyClient(rig.client, fake)

    val result = passkeys.registerPasskey("Laptop", "platform")

    assertEquals(
        "/api/auth/passkey/generate-register-options",
        rig.requests[0].url.encodedPath,
    )
    assertEquals("name=Laptop&authenticatorAttachment=platform", rig.requests[0].url.encodedQuery)
    assertEquals("Bearer tA", rig.requests[0].headers[HttpHeaders.Authorization])
    val options = fake.created.single()
    assertEquals("Y2hhbGxlbmdlLTE2Ynl0ZXM", options.challenge)
    assertEquals("dXNlci1pZA", options.user.id)
    assertEquals("ZXhjbHVkZWQ", options.excludeCredentials.single().id)
    assertEquals(JsonObject(mapOf("credProps" to JsonPrimitive(true))), options.extensions)
    assertEquals(
        """{"response":{"id":"cred-1","rawId":"cred-1","response":{"clientDataJSON":"Y2Q",""" +
            """"attestationObject":"YW8","transports":["internal"]},""" +
            """"authenticatorAttachment":"platform","clientExtensionResults":{},""" +
            """"type":"public-key"},"name":"Laptop"}""",
        rig.bodyOf(1),
    )
    assertEquals("better-auth.better-auth-passkey=pk.sig", rig.cookieOf(1))
    assertEquals("https://example.com", rig.requests[1].headers[HttpHeaders.Origin])
    assertNull(rig.requests[0].headers[HttpHeaders.Origin])
    assertEquals("p1", (result as BetterAuthResult.Success).value.id)
    assertEquals("internal,hybrid", result.value.transports)

    passkeys.registerPasskey()
    assertEquals(VERIFY_REGISTRATION_PATH, rig.requests[3].url.encodedPath)
    assertNull(rig.cookieOf(3))
    assertNull(rig.requests[3].headers[HttpHeaders.Origin])
    assertEquals("", rig.requests[2].url.encodedQuery)
  }

  @Test
  fun t9SignInStoresTheSessionTokenAndMapsLocalFailures() = runTest {
    val rig = PasskeyRig { request ->
      when (request.url.encodedPath) {
        AUTH_OPTIONS_PATH ->
            json(authOptionsJson, cookies = listOf("better-auth.better-auth-passkey=a.sig; Path=/"))
        else -> json(sessionJson, extra = listOf("set-auth-token" to "pk-tok"))
      }
    }
    val passkeys = BetterAuthPasskeyClient(rig.client, FakeAuthenticator())
    val result = passkeys.signInWithPasskey()
    assertEquals("u1", (result as BetterAuthResult.Success).value.user.id)
    assertEquals("pk-tok", rig.storage.token())
    assertEquals("better-auth.better-auth-passkey=a.sig", rig.cookieOf(1))
    assertTrue(rig.bodyOf(1).startsWith("""{"response":{"id":"cred-2","rawId":"cred-2""""))

    val unsupported = PasskeyRig { json("{}") }
    assertEquals(
        BetterAuthResult.Failure(0, BetterAuthError("PASSKEY_UNSUPPORTED")),
        BetterAuthPasskeyClient(
                unsupported.client,
                FakeAuthenticator(capability = PasskeyCapability.Unsupported),
            )
            .signInWithPasskey(),
    )
    assertEquals(0, unsupported.requests.size)

    val cancelled = PasskeyRig { json(authOptionsJson) }
    assertEquals(
        BetterAuthResult.Failure(0, BetterAuthError("cancelled")),
        BetterAuthPasskeyClient(
                cancelled.client,
                FakeAuthenticator(onGet = { throw PasskeyException("cancelled") }),
            )
            .signInWithPasskey(),
    )
    assertEquals(1, cancelled.requests.size)

    val broken = PasskeyRig { json(authOptionsJson) }
    assertEquals(
        BetterAuthResult.Failure(0, BetterAuthError("PASSKEY_FAILED")),
        BetterAuthPasskeyClient(
                broken.client,
                FakeAuthenticator(onGet = { throw IllegalStateException("boom") }),
            )
            .signInWithPasskey(),
    )
  }

  @Test
  fun t9OptionsFailureSkipsTheAuthenticatorAndCancellationPropagates() = runTest {
    val denied = PasskeyRig { json("""{"code":"UNAUTHORIZED"}""", HttpStatusCode.Unauthorized) }
    val fake = FakeAuthenticator()
    val result = BetterAuthPasskeyClient(denied.client, fake).registerPasskey()
    assertEquals(BetterAuthResult.Failure(401, BetterAuthError("UNAUTHORIZED")), result)
    assertTrue(fake.created.isEmpty())

    val rig = PasskeyRig { json(authOptionsJson) }
    val cancelling =
        BetterAuthPasskeyClient(
            rig.client,
            FakeAuthenticator(onGet = { throw CancellationException("stop") }),
        )
    assertFailsWith<CancellationException> { cancelling.signInWithPasskey() }
  }

  @Test
  fun t9ListAndDelete() = runTest {
    val rig = registrationRig()
    val passkeys = BetterAuthPasskeyClient(rig.client, FakeAuthenticator())
    val listed = (passkeys.listPasskeys() as BetterAuthResult.Success).value.single()
    assertEquals("internal,hybrid", listed.transports)
    assertEquals("abc", listed.aaguid)
    assertEquals(true, listed.backedUp)
    assertIs<BetterAuthResult.Success<Unit>>(passkeys.deletePasskey("p1"))
    assertEquals("""{"id":"p1"}""", rig.bodyOf(1))
    assertEquals("/api/auth/passkey/delete-passkey", rig.requests[1].url.encodedPath)
  }
}
