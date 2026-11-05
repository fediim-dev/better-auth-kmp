package dev.fediim.auth.core.storage

/**
 * Persistent holder of the session bearer token with a logout epoch.
 *
 * Concurrency contract: operations are linearizable per instance. [clear] atomically sets the token
 * to null and increases the epoch by one. [writeTokenIfEpoch] writes and returns true only when the
 * current epoch equals `expectedEpoch`, atomically with respect to [clear]. The epoch never
 * decreases. Implementations must be safe for concurrent use.
 */
public interface AuthTokenStorage {
  /** Current epoch; 0 for a store that was never cleared. */
  public suspend fun epoch(): Long

  /** The stored token, or null when signed out. */
  public suspend fun token(): String?

  /**
   * Stores [token] and returns true if the epoch still equals [expectedEpoch]; else returns false.
   */
  public suspend fun writeTokenIfEpoch(expectedEpoch: Long, token: String): Boolean

  /** Atomically removes the token and increases the epoch, discarding every in-flight write. */
  public suspend fun clear()
}
