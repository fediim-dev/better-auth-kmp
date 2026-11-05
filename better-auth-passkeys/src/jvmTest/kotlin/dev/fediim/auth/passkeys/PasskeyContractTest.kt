package dev.fediim.auth.passkeys

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.authRequest
import dev.fediim.auth.core.contract.BetterAuthContractServerHarness
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SignUpEmailRequest
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpMethod
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Live passkey flows against the Node better-auth contract server (plan #6 T14, L11). No software
 * authenticator: the ceremony depth is the options fetch plus negative verifies.
 */
class PasskeyContractTest {
  private fun failure(result: BetterAuthResult<*>): BetterAuthResult.Failure =
      result as? BetterAuthResult.Failure ?: error("expected failure, got $result")

  @Test
  fun optionsChallengeAndNegativeVerifies() = runBlocking {
    BetterAuthContractServerHarness.start().use { server ->
      val client =
          BetterAuthClient(
              BetterAuthConfig(server.baseUrl),
              SettingsAuthTokenStorage(MapSettings()),
              OkHttp.create(),
          )
      try {
        val email = "passkey-${UUID.randomUUID()}@example.com"
        client.signUpEmail(SignUpEmailRequest("Passkey", email, UUID.randomUUID().toString()))

        val forged = { id: String ->
          RegistrationResponse(
              id,
              id,
              AttestationResponse(
                  Base64Url.encode("""{"type":"webauthn.create"}""".encodeToByteArray()),
                  Base64Url.encode(byteArrayOf(0xA0.toByte())),
              ),
          )
        }
        var seenChallengeBytes = 0
        val registering =
            BetterAuthPasskeyClient(
                client,
                FakeAuthenticator(
                    onCreate = {
                      seenChallengeBytes = Base64Url.decode(it.challenge).size
                      forged("fake-cred-1")
                    },
                    onGet = {
                      AuthenticationResponse(
                          "fake-cred-2",
                          "fake-cred-2",
                          AssertionResponse("Y2Q", "YWQ", "c2ln"),
                      )
                    },
                ),
            )

        val registered = failure(registering.registerPasskey("Laptop"))
        assertEquals(400, registered.status)
        assertEquals("FAILED_TO_VERIFY_REGISTRATION", registered.error.code)
        assertTrue(seenChallengeBytes >= 16, "challenge bytes: $seenChallengeBytes")

        // Control: the same body without the challenge cookie never reaches verification.
        val body = buildJsonObject {
          put("response", wireJson.encodeToJsonElement(forged("fake-cred-1")))
        }
            .toString()
        val withoutCookie =
            failure(
                client.authRequest(HttpMethod.Post, "passkey/verify-registration", body) { _, _ -> }
            )
        assertEquals(400, withoutCookie.status)
        assertEquals("CHALLENGE_NOT_FOUND", withoutCookie.error.code)

        val signedIn = failure(registering.signInWithPasskey())
        assertEquals(401, signedIn.status)
        assertEquals("PASSKEY_NOT_FOUND", signedIn.error.code)

        assertEquals(BetterAuthResult.Success(emptyList()), registering.listPasskeys())
        val deleted = failure(registering.deletePasskey("nope"))
        assertEquals(404, deleted.status)
        assertEquals("PASSKEY_NOT_FOUND", deleted.error.code)
      } finally {
        client.close()
      }
    }
  }
}
