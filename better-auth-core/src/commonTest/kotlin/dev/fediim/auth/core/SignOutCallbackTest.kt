package dev.fediim.auth.core

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.storage.AuthTokenStorage
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class SignOutCallbackTest {
  private class Seen(val token: String?, val epoch: Long, val org: String?, val requests: Int)

  private val storage = SettingsAuthTokenStorage(MapSettings())
  private val requests = mutableListOf<HttpRequestData>()
  private val client =
      BetterAuthClient(
          BetterAuthConfig("https://example.com"),
          storage,
          MockEngine { request ->
            requests += request
            respond("{}")
          },
      )

  private suspend fun seed(token: String, org: String? = null) {
    storage.writeTokenIfEpoch(storage.epoch(), token)
    if (org != null) storage.writeActiveOrganizationIdIfEpoch(storage.epoch(), org)
  }

  @Test
  fun callbackRunsAfterTheLocalClearAndBeforeTheServerCall() =
      kotlinx.coroutines.test.runTest {
        seed("t0", "o1")
        var seen: Seen? = null
        val result = client.signOut {
          seen =
              Seen(storage.token(), storage.epoch(), storage.activeOrganizationId(), requests.size)
        }
        assertIs<BetterAuthResult.Success<Unit>>(result)
        val s = seen!!
        assertNull(s.token)
        assertEquals(1, s.epoch)
        assertNull(s.org)
        assertEquals(0, s.requests)
        assertEquals("Bearer t0", requests.single().headers[HttpHeaders.Authorization])
      }

  @Test
  fun callbackRunsOnceEvenWithoutAToken() =
      kotlinx.coroutines.test.runTest {
        var calls = 0
        client.signOut { calls++ }
        assertEquals(1, calls)
        assertEquals(0, requests.size)
      }

  @Test
  fun callbackFailureStillRevokesThenRethrows() =
      kotlinx.coroutines.test.runTest {
        seed("t9")
        val thrown =
            assertFailsWith<IllegalStateException> {
              client.signOut { throw IllegalStateException("boom") }
            }
        assertEquals("boom", thrown.message)
        assertEquals("Bearer t9", requests.single().headers[HttpHeaders.Authorization])
        assertNull(storage.token())
        assertEquals(1, storage.epoch())
      }

  @Test
  fun storageFailureSkipsTheCallbackAndTheServerCall() =
      kotlinx.coroutines.test.runTest {
        val broken =
            object : AuthTokenStorage {
              override suspend fun epoch(): Long = 0

              override suspend fun token(): String? = "tS"

              override suspend fun writeTokenIfEpoch(expectedEpoch: Long, token: String) = false

              override suspend fun clear() = throw IllegalStateException("disk")
            }
        val sent = mutableListOf<HttpRequestData>()
        val brokenClient =
            BetterAuthClient(
                BetterAuthConfig("https://example.com"),
                broken,
                MockEngine { request ->
                  sent += request
                  respond("{}")
                },
            )
        var calls = 0
        val thrown = assertFailsWith<IllegalStateException> { brokenClient.signOut { calls++ } }
        assertEquals("disk", thrown.message)
        assertEquals(0, calls)
        assertEquals(0, sent.size)
      }
}
