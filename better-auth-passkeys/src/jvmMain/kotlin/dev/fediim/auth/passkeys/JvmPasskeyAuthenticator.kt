package dev.fediim.auth.passkeys

/**
 * Desktop JVM has no platform passkey API. Without a [hook] this authenticator is
 * [PasskeyCapability.Unsupported]; pass a [hook] (for example one backed by a security-key library
 * or a test double) to run ceremonies through it.
 */
public class JvmPasskeyAuthenticator(private val hook: PasskeyAuthenticator? = null) :
    PasskeyAuthenticator {
  override val capability: PasskeyCapability
    get() = hook?.capability ?: PasskeyCapability.Unsupported

  override suspend fun create(options: PublicKeyCredentialCreationOptions): RegistrationResponse =
      hook?.create(options) ?: throw PasskeyException("PASSKEY_UNSUPPORTED")

  override suspend fun get(options: PublicKeyCredentialRequestOptions): AuthenticationResponse =
      hook?.get(options) ?: throw PasskeyException("PASSKEY_UNSUPPORTED")
}
