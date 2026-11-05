package dev.fediim.auth.core.social

/**
 * Platform bridge that sends the user through a provider's sign-in page and reports the outcome.
 *
 * Implementations release every resource they acquired (listeners, loopback ports, pending-flow
 * slots) in a `finally` before [launch] returns or throws.
 */
public interface SocialAuthLauncher {
  /**
   * Runs one sign-in. [state] is the client's single-use CSRF nonce and [serverOrigin] the
   * normalized origin of the auth server (`scheme://host[:port]`). The launcher builds the
   * `receiver` for its [SocialFlow] and calls [authorize] to obtain the URL to open; a launcher
   * that falls back to another flow (the web launcher when a popup is blocked) calls it again.
   *
   * Exceptions thrown by [authorize] propagate out of [launch] unchanged. A timeout or a user
   * cancellation is reported as [SocialCallback.Error], not thrown. Coroutine cancellation is
   * rethrown.
   */
  public suspend fun launch(
      state: String,
      serverOrigin: String,
      authorize: suspend (flow: SocialFlow, receiver: String) -> String,
  ): SocialCallback
}

/** Which receiver contract a launcher uses when it calls `authorize`. */
public enum class SocialFlow {
  /**
   * `receiver` is the callback URL including `state`; `authorize` returns the provider URL to open.
   */
  Redirect,

  /** `receiver` is the opener origin; `authorize` returns the server's `/oauth-popup/start` URL. */
  Popup,
}

/** Outcome of a social sign-in as seen by the launcher; passed to `completeSocialSignIn`. */
public sealed interface SocialCallback {
  /** A session token delivered by the popup flow. [toString] omits [token]. */
  public data class SessionToken(val token: String) : SocialCallback {
    override fun toString(): String = "SessionToken(token=<redacted>)"
  }

  /** A single-use token to exchange at `/one-time-token/verify`. [toString] omits [token]. */
  public data class OneTimeToken(val token: String) : SocialCallback {
    override fun toString(): String = "OneTimeToken(token=<redacted>)"
  }

  /** The flow failed or was abandoned; [code] is a provider/server code or a local code. */
  public data class Error(val code: String, val description: String? = null) : SocialCallback
}
