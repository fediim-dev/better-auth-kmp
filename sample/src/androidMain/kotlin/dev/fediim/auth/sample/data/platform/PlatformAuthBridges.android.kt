package dev.fediim.auth.sample.data.platform

import android.app.Activity
import android.content.Context
import dev.fediim.auth.core.social.AndroidCustomTabsSocialAuthLauncher
import dev.fediim.auth.passkeys.AuthenticationResponse
import dev.fediim.auth.passkeys.CredentialManagerPasskeyAuthenticator
import dev.fediim.auth.passkeys.PasskeyAuthenticator
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.auth.passkeys.PasskeyException
import dev.fediim.auth.passkeys.PublicKeyCredentialCreationOptions
import dev.fediim.auth.passkeys.PublicKeyCredentialRequestOptions
import dev.fediim.auth.passkeys.RegistrationResponse

/** Holds Android context and activity hooks required for Custom Tabs and Credential Manager. */
public object AndroidPlatformBridgeHooks {
  public var context: Context? = null
  public var activityProvider: (() -> Activity)? = null
}

/** Creates Android platform authentication bridges using Custom Tabs and Credential Manager. */
public actual fun createLivePlatformAuthBridges(): PlatformAuthBridges {
  val ctx = AndroidPlatformBridgeHooks.context
  val act = AndroidPlatformBridgeHooks.activityProvider
  val socialLauncher =
      if (ctx != null) {
        AndroidCustomTabsSocialAuthLauncher(ctx, "better-auth-sample://auth/callback")
      } else {
        null
      }
  val passkeyAuth =
      if (act != null) {
        CredentialManagerPasskeyAuthenticator(act)
      } else {
        object : PasskeyAuthenticator {
          override val capability: PasskeyCapability = PasskeyCapability.Unsupported

          override suspend fun create(
              options: PublicKeyCredentialCreationOptions
          ): RegistrationResponse = throw PasskeyException("PASSKEY_UNSUPPORTED")

          override suspend fun get(
              options: PublicKeyCredentialRequestOptions
          ): AuthenticationResponse = throw PasskeyException("PASSKEY_UNSUPPORTED")
        }
      }
  return PlatformAuthBridges(socialLauncher, passkeyAuth)
}
