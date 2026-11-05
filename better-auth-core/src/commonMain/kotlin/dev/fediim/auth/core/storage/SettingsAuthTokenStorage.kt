package dev.fediim.auth.core.storage

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AuthTokenStorage] persisted in a multiplatform-settings [Settings] store, under the fixed keys
 * `better_auth_token` and `better_auth_epoch`. A mutex per instance serializes every operation, so
 * use exactly one instance per [Settings] store per process.
 *
 * The token is stored as plaintext in the platform's settings store (preferences, `UserDefaults`,
 * `localStorage`); supply a custom [AuthTokenStorage] backed by a keystore where that is not
 * acceptable.
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
    settings.putLong(EPOCH_KEY, settings.getLong(EPOCH_KEY, 0L) + 1)
  }

  private companion object {
    const val TOKEN_KEY = "better_auth_token"
    const val EPOCH_KEY = "better_auth_epoch"
  }
}
