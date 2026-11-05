package dev.fediim.auth.core.storage

/**
 * Persistent holder of the session bearer token with a logout epoch.
 *
 * Concurrency contract: operations are linearizable per instance. [clear] atomically sets the token
 * to null and increases the epoch by one. [writeTokenIfEpoch] writes and returns true only when the
 * current epoch equals `expectedEpoch`, atomically with respect to [clear]. The epoch never
 * decreases. Implementations must be safe for concurrent use.
 *
 * The active organization id is cached alongside the token. [clear] also removes it in the same
 * atomic step, and [writeActiveOrganizationIdIfEpoch] is fenced by the same epoch. The default
 * implementations of the two organization members persist nothing, so a custom storage must
 * override them to keep the id.
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

  /**
   * Atomically removes the token and the active organization id and increases the epoch, discarding
   * every in-flight write.
   */
  public suspend fun clear()

  /** The cached active organization id; null when none is cached or unsupported by the storage. */
  public suspend fun activeOrganizationId(): String? = null

  /**
   * Stores [organizationId] (removes it when null) and returns true if the epoch still equals
   * [expectedEpoch], atomically with respect to [clear]; else returns false. The default persists
   * nothing and returns false.
   */
  public suspend fun writeActiveOrganizationIdIfEpoch(
      expectedEpoch: Long,
      organizationId: String?,
  ): Boolean = false
}
