package dev.fediim.auth.passkeys

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CredentialManagerPasskeyAuthenticatorTest {
  private val creation =
      PublicKeyCredentialCreationOptions(
          challenge = "Y2hhbGxlbmdl",
          rp = RelyingParty("localhost", "App"),
          user = PublicKeyCredentialUser("dQ", "n", "d"),
          pubKeyCredParams = listOf(CredentialParameter("public-key", -7)),
      )
  private val request = PublicKeyCredentialRequestOptions(challenge = "YXV0aA", rpId = "localhost")
  private val registrationJson =
      """{"id":"c1","rawId":"c1","type":"public-key","response":{"clientDataJSON":"Y2Q",""" +
          """"attestationObject":"YW8","transports":["internal"]},"extra":1}"""
  private val assertionJson =
      """{"id":"c1","rawId":"c1","type":"public-key","response":{"clientDataJSON":"Y2Q",""" +
          """"authenticatorData":"YWQ","signature":"c2ln","userHandle":"dQ"}}"""

  @Test
  fun createAndGetPassJsonThroughTheLambdas() = runTest {
    var createInput = ""
    var getInput = ""
    val authenticator =
        CredentialManagerPasskeyAuthenticator(
            createJson = {
              createInput = it
              registrationJson
            },
            getJson = {
              getInput = it
              assertionJson
            },
        )
    val registered = authenticator.create(creation)
    assertTrue("Y2hhbGxlbmdl" in createInput)
    assertEquals("c1", registered.id)
    assertEquals(listOf("internal"), registered.response.transports)
    val asserted = authenticator.get(request)
    assertTrue("YXV0aA" in getInput)
    assertEquals("c2ln", asserted.response.signature)
  }

  @Test
  fun androidxExceptionsMapToPasskeyCodes() = runTest {
    fun authenticator(error: Exception) =
        CredentialManagerPasskeyAuthenticator(
            createJson = { throw error },
            getJson = { throw error },
        )

    assertEquals(
        "cancelled",
        assertFailsWith<PasskeyException> {
              authenticator(CreateCredentialCancellationException()).create(creation)
            }
            .code,
    )
    assertEquals(
        "cancelled",
        assertFailsWith<PasskeyException> {
              authenticator(GetCredentialCancellationException()).get(request)
            }
            .code,
    )
    assertEquals(
        "no_credential",
        assertFailsWith<PasskeyException> { authenticator(NoCredentialException()).get(request) }
            .code,
    )
    assertEquals(
        "PASSKEY_FAILED",
        assertFailsWith<PasskeyException> { authenticator(IllegalStateException()).get(request) }
            .code,
    )
  }
}
