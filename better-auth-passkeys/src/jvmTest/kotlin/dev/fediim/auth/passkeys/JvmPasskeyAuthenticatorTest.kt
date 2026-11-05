package dev.fediim.auth.passkeys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class JvmPasskeyAuthenticatorTest {
  private val creation =
      PublicKeyCredentialCreationOptions(
          challenge = "Y2g",
          rp = RelyingParty("localhost", "App"),
          user = PublicKeyCredentialUser("dQ", "n", "d"),
          pubKeyCredParams = listOf(CredentialParameter("public-key", -7)),
      )
  private val request = PublicKeyCredentialRequestOptions(challenge = "Y2g")

  @Test
  fun withoutAHookItIsUnsupported() = runBlocking {
    val authenticator = JvmPasskeyAuthenticator()
    assertEquals(PasskeyCapability.Unsupported, authenticator.capability)
    assertEquals(
        "PASSKEY_UNSUPPORTED",
        assertFailsWith<PasskeyException> { authenticator.create(creation) }.code,
    )
    assertEquals(
        "PASSKEY_UNSUPPORTED",
        assertFailsWith<PasskeyException> { authenticator.get(request) }.code,
    )
  }

  @Test
  fun withAHookItDelegates() = runBlocking {
    val registration =
        RegistrationResponse(
            "id",
            "id",
            AttestationResponse("Y2Q", "YW8"),
            clientExtensionResults = JsonObject(emptyMap()),
        )
    val assertion = AuthenticationResponse("id", "id", AssertionResponse("Y2Q", "YWQ", "c2ln"))
    val hook = FakeAuthenticator(onCreate = { registration }, onGet = { assertion })
    val authenticator = JvmPasskeyAuthenticator(hook)
    assertEquals(PasskeyCapability.Supported, authenticator.capability)
    assertEquals(registration, authenticator.create(creation))
    assertEquals(assertion, authenticator.get(request))
    assertEquals(
        PasskeyCapability.Unsupported,
        JvmPasskeyAuthenticator(FakeAuthenticator(PasskeyCapability.Unsupported)).capability,
    )
  }
}
