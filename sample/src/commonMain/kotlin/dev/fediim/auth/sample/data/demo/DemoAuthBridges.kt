package dev.fediim.auth.sample.data.demo

import dev.fediim.auth.core.social.SocialAuthLauncher
import dev.fediim.auth.core.social.SocialCallback
import dev.fediim.auth.core.social.SocialFlow
import dev.fediim.auth.passkeys.AssertionResponse
import dev.fediim.auth.passkeys.AttestationResponse
import dev.fediim.auth.passkeys.AuthenticationResponse
import dev.fediim.auth.passkeys.PasskeyAuthenticator
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.auth.passkeys.PasskeyException
import dev.fediim.auth.passkeys.PublicKeyCredentialCreationOptions
import dev.fediim.auth.passkeys.PublicKeyCredentialRequestOptions
import dev.fediim.auth.passkeys.RegistrationResponse
import dev.fediim.auth.sample.domain.model.DemoFailureScenario

/** Simulated social authentication launcher resolving instant one-time tokens in offline demo. */
public class DemoSocialAuthLauncher(private val store: DemoServerStore) : SocialAuthLauncher {
  override suspend fun launch(
      state: String,
      serverOrigin: String,
      authorize: suspend (flow: SocialFlow, receiver: String) -> String,
  ): SocialCallback {
    val receiver = "$serverOrigin/api/auth/callback"
    authorize(SocialFlow.Redirect, receiver)
    val ott = store.createOneTimeToken(state)
    return SocialCallback.OneTimeToken(ott)
  }
}

/** Simulated passkey authenticator providing WebAuthn credentials in offline demo. */
public class DemoPasskeyAuthenticator(
    private val failureScenario: () -> DemoFailureScenario,
) : PasskeyAuthenticator {
  override val capability: PasskeyCapability
    get() = PasskeyCapability.Supported

  override suspend fun create(options: PublicKeyCredentialCreationOptions): RegistrationResponse {
    if (failureScenario() == DemoFailureScenario.PASSKEY_FAILED) {
      throw PasskeyException("PASSKEY_FAILED")
    }
    return RegistrationResponse(
        id = "demo-pk-cred",
        rawId = "demo-pk-rawId",
        response =
            AttestationResponse(
                clientDataJSON = "eyJjaGFsbGVuZ2UiOiJkZW1vIn0=",
                attestationObject = "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YVik",
            ),
    )
  }

  override suspend fun get(options: PublicKeyCredentialRequestOptions): AuthenticationResponse {
    if (failureScenario() == DemoFailureScenario.PASSKEY_FAILED) {
      throw PasskeyException("PASSKEY_FAILED")
    }
    return AuthenticationResponse(
        id = "demo-pk-cred",
        rawId = "demo-pk-rawId",
        response =
            AssertionResponse(
                clientDataJSON = "eyJjaGFsbGVuZ2UiOiJkZW1vIn0=",
                authenticatorData = "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MBAAAA",
                signature = "MEQCIF_demo_sig",
                userHandle = "user-carol",
            ),
    )
  }
}
