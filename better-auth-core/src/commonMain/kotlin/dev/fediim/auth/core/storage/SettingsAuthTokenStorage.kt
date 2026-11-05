package dev.fediim.auth.core.storage

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AuthTokenStorage] persisted in a multiplatform-settings [Settings] store, under the fixed keys
 * `better_auth_token`, `better_auth_epoch` and `better_auth_active_org`. A mutex per instance
 * serializes every operation, so use exactly one instance per [Settings] store per process.
 *
 * The token is stored as plaintext in the platform's settings store (preferences, `UserDefaults`,
 * `localStorage`); supply a custom [AuthTokenStorage] backed by a keystore where that is not
 * acceptable. Use a dedicated [Settings] instance (named preferences file or suite) so app keys
 * cannot collide; on the web, any script running on the origin (e.g. via XSS) can read the token.
 */
public class SettingsAuthTokenStorage(private val settings: Settings) : AuthTokenStorage {
  private val mutex = Mutex()

  override suspend fun epoch(): Long = mutex.withLock { settings.getLong(EPOCH_KEY, 0L) }

  override suspend fun token(): String? = mutex.withLock { settings.getStringOrNull(TOKEN_KEY) }

  override suspend fun writeTokenIfEpoch(expectedEpoch: Long, token: String): Boolean =
      mutex.withLock {
        if (settings.getLong(EPOCH_KEY, 0L) != expectedEpoch) return@withLock false
        settings.putString(TOKEN_KEY, token)
        true
      }

  override suspend fun clear(): Unit = mutex.withLock {
    settings.remove(TOKEN_KEY)
    settings.remove(ACTIVE_ORG_KEY)
    settings.putLong(EPOCH_KEY, settings.getLong(EPOCH_KEY, 0L) + 1)
  }

  override suspend fun activeOrganizationId(): String? = mutex.withLock {
    settings.getStringOrNull(ACTIVE_ORG_KEY)
  }

  override suspend fun writeActiveOrganizationIdIfEpoch(
      expectedEpoch: Long,
      organizationId: String?,
  ): Boolean = mutex.withLock {
    if (settings.getLong(EPOCH_KEY, 0L) != expectedEpoch) return@withLock false
    if (organizationId == null) settings.remove(ACTIVE_ORG_KEY)
    else settings.putString(ACTIVE_ORG_KEY, organizationId)
    true
  }

  private companion object {
    const val ACTIVE_ORG_KEY = "better_auth_active_org"
    const val TOKEN_KEY = "better_auth_token"
    const val EPOCH_KEY = "better_auth_epoch"
  }
}
