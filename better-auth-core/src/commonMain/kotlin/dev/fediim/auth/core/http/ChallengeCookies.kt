package dev.fediim.auth.core.http

import dev.fediim.auth.core.storage.AuthTokenStorage
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val TWO_FACTOR_COOKIE_SUFFIX = ".two_factor"
internal const val PASSKEY_COOKIE_SUFFIX = ".better-auth-passkey"

private val ALLOWED_SUFFIXES = setOf(TWO_FACTOR_COOKIE_SUFFIX, PASSKEY_COOKIE_SUFFIX)

/**
 * In-memory holder of the short-lived signed challenge cookies of the two-factor and passkey
 * ceremonies (CONSTRAINTS C5), one per kind, plus the pending-two-factor marker.
 *
 * Only the two allowlisted name suffixes are ever captured, so the session cookie can never end up
 * here. A capture or mark is stored only when [tokenStorage] is still in the epoch the request
 * started in, and [clearAll] takes the same lock after the epoch moved, so a response that raced
 * `signOut` cannot leave a challenge behind. Cookie values are never logged.
 */
internal class ChallengeCookies(private val tokenStorage: AuthTokenStorage) {
  private val mutex = Mutex()
  private val cookies = mutableMapOf<String, String>()
  private var twoFactorPending = false

  /** Bumped whenever a two-factor challenge starts or is dropped, so a stale verify can tell. */
  private var twoFactorGeneration = 0L

  /**
   * Stores the first `Set-Cookie` pair named `...<suffix>` found in [response] (also inside a
   * Darwin-folded header) as `name=value`. Returns whether one was stored.
   *
   * @throws IllegalArgumentException when [suffix] is not on the allowlist.
   */
  suspend fun capture(response: HttpResponse, suffix: String): Boolean {
    requireAllowed(suffix)
    val epoch = response.call.request.attributes.getOrNull(StartEpochKey) ?: return false
    val pair = findPair(response, suffix) ?: return false
    return mutex.withLock {
      if (tokenStorage.epoch() != epoch) return@withLock false
      cookies[suffix] = pair
      true
    }
  }

  /** The held cookie for [suffix] without removing it. */
  suspend fun peek(suffix: String): String? {
    requireAllowed(suffix)
    return mutex.withLock { cookies[suffix] }
  }

  /** Removes and returns the held cookie for [suffix]. */
  suspend fun take(suffix: String): String? {
    requireAllowed(suffix)
    return mutex.withLock { cookies.remove(suffix) }
  }

  /** Removes the cookie for [suffix] only if it still equals [value]; returns whether it did. */
  suspend fun takeIfEquals(suffix: String, value: String): Boolean {
    requireAllowed(suffix)
    return mutex.withLock {
      if (cookies[suffix] != value) return@withLock false
      cookies.remove(suffix)
      true
    }
  }

  /**
   * Starts a new pending two-factor challenge for the request behind [response] (epoch-fenced):
   * sets the marker and drops any older two-factor cookie. A fenced-out call changes nothing.
   */
  suspend fun markTwoFactorPending(response: HttpResponse) {
    val epoch = response.call.request.attributes.getOrNull(StartEpochKey) ?: return
    mutex.withLock {
      if (tokenStorage.epoch() != epoch) return@withLock
      cookies.remove(TWO_FACTOR_COOKIE_SUFFIX)
      twoFactorPending = true
      twoFactorGeneration++
    }
  }

  /** Forgets the pending two-factor marker and cookie. */
  suspend fun clearTwoFactor() {
    mutex.withLock {
      cookies.remove(TWO_FACTOR_COOKIE_SUFFIX)
      twoFactorPending = false
      twoFactorGeneration++
    }
  }

  /** The current two-factor generation and held cookie, read together. */
  suspend fun twoFactorChallenge(): Pair<Long, String?> = mutex.withLock {
    twoFactorGeneration to cookies[TWO_FACTOR_COOKIE_SUFFIX]
  }

  /**
   * Completes the two-factor challenge of [generation]: runs [store] and clears the pending marker
   * under the lock, but only if no newer challenge started and nothing was cleared since; otherwise
   * does nothing and returns false. The cookie is removed separately via [takeIfEquals].
   */
  suspend fun completeTwoFactorIf(generation: Long, store: suspend () -> Unit): Boolean =
      mutex.withLock {
        if (!twoFactorPending || twoFactorGeneration != generation) return@withLock false
        store()
        twoFactorPending = false
        true
      }

  suspend fun isTwoFactorPending(): Boolean = mutex.withLock { twoFactorPending }

  /** Forgets everything; `signOut` calls it right after the storage epoch advanced. */
  suspend fun clearAll() {
    mutex.withLock {
      cookies.clear()
      twoFactorPending = false
      twoFactorGeneration++
    }
  }

  private fun requireAllowed(suffix: String) {
    require(suffix in ALLOWED_SUFFIXES) { "not a challenge cookie suffix" }
  }

  private fun findPair(response: HttpResponse, suffix: String): String? {
    val pattern =
        Regex("(?:^|[;,]\\s*)((?:__Secure-)?[^=;,\\s]+${Regex.escape(suffix)})=([^;,\\s]+)")
    for (header in response.headers.getAll(HttpHeaders.SetCookie).orEmpty()) {
      val match = pattern.find(header) ?: continue
      return "${match.groupValues[1]}=${match.groupValues[2]}"
    }
    return null
  }
}
