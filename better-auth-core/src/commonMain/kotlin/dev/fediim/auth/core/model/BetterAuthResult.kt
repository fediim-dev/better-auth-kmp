package dev.fediim.auth.core.model

/**
 * Outcome of a [dev.fediim.auth.core.BetterAuthClient] call; calls return it instead of throwing.
 */
public sealed interface BetterAuthResult<out T> {
  /** The server answered 2xx and the body decoded; [value] is the decoded body. */
  public data class Success<out T>(val value: T) : BetterAuthResult<T>

  /**
   * The server answered a non-2xx status other than 429, or answered 2xx with a body that did not
   * decode (then [error] is empty). [error] is empty when the body was not a JSON error object.
   */
  public data class Failure(val status: Int, val error: BetterAuthError) : BetterAuthResult<Nothing>

  /**
   * The server answered 429. [retryAfterSeconds] is the delta-seconds `Retry-After` (or
   * `X-Retry-After`) value and is null when the header is missing, negative or not a number.
   */
  public data class RateLimited(val retryAfterSeconds: Long?, val error: BetterAuthError) :
      BetterAuthResult<Nothing>

  /** No HTTP response arrived (connection, TLS, timeout or other transport exception). */
  public data class NetworkError(val cause: Throwable) : BetterAuthResult<Nothing>
}
