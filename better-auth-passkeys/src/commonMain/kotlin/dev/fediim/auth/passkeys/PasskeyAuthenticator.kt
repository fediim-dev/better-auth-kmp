package dev.fediim.auth.passkeys

import kotlin.coroutines.cancellation.CancellationException

/** Whether an authenticator can run a WebAuthn ceremony on this platform right now. */
public enum class PasskeyCapability {
  Supported,
  Unsupported,
}

/**
 * Failure of a local passkey ceremony. [code] is one of `cancelled`, `no_credential`,
 * `PASSKEY_UNSUPPORTED` or `PASSKEY_FAILED`; [BetterAuthPasskeyClient] reports it as `Failure(0,
 * code)`.
 */
public class PasskeyException(
    public val code: String,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The platform half of a passkey ceremony: it turns server options into the authenticator's answer.
 * Implementations throw [PasskeyException] with code `cancelled` (the user dismissed the prompt),
 * `no_credential` (nothing matches) or `PASSKEY_FAILED`, and let [CancellationException] pass.
 */
public interface PasskeyAuthenticator {
  /** [PasskeyCapability.Unsupported] makes the client fail with `PASSKEY_UNSUPPORTED` up front. */
  public val capability: PasskeyCapability

  /** Creates a credential for [options]. */
  public suspend fun create(options: PublicKeyCredentialCreationOptions): RegistrationResponse

  /** Signs the challenge in [options] with an existing credential. */
  public suspend fun get(options: PublicKeyCredentialRequestOptions): AuthenticationResponse
}
