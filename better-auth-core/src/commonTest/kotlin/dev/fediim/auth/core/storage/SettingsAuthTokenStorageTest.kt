package dev.fediim.auth.core.storage

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

class SettingsAuthTokenStorageTest {
  @Test
  fun t7EpochFencedWrites() = runTest {
    val settings = MapSettings()
    val storage = SettingsAuthTokenStorage(settings)
    assertEquals(0L, storage.epoch())
    assertNull(storage.token())
    assertTrue(storage.writeTokenIfEpoch(0, "t"))
    assertEquals("t", storage.token())
    storage.clear()
    assertNull(storage.token())
    assertEquals(1L, storage.epoch())
    assertFalse(storage.writeTokenIfEpoch(0, "x"))
    assertNull(storage.token())
    assertTrue(storage.writeTokenIfEpoch(1, "y"))

    val second = SettingsAuthTokenStorage(settings)
    assertEquals("y", second.token())
    assertEquals(1L, second.epoch())

    var last = storage.epoch()
    repeat(10) {
      storage.clear()
      val now = storage.epoch()
      assertTrue(now > last)
      last = now
    }
  }

  @Test
  fun t8InFlightWritesAreFencedOut() = runTest {
    val storage = SettingsAuthTokenStorage(MapSettings())
    val gate = CompletableDeferred<Unit>()
    val writers =
        List(50) {
          async {
            val epoch = storage.epoch()
            gate.await()
            storage.writeTokenIfEpoch(epoch, "late")
          }
        }
    testScheduler.advanceUntilIdle()
    storage.clear()
    gate.complete(Unit)
    assertTrue(writers.awaitAll().none { it })
    assertNull(storage.token())
  }

  @Test
  fun t10ActiveOrganizationIsFencedAndCleared() = runTest {
    val settings = MapSettings()
    val storage = SettingsAuthTokenStorage(settings)
    assertNull(storage.activeOrganizationId())
    assertTrue(storage.writeActiveOrganizationIdIfEpoch(0, "o1"))
    assertEquals("o1", storage.activeOrganizationId())
    assertTrue(storage.writeActiveOrganizationIdIfEpoch(0, null))
    assertNull(storage.activeOrganizationId())

    assertTrue(storage.writeActiveOrganizationIdIfEpoch(0, "o1"))
    assertTrue(storage.writeTokenIfEpoch(0, "t"))
    assertEquals("o1", storage.activeOrganizationId())
    storage.clear()
    assertNull(storage.token())
    assertNull(storage.activeOrganizationId())
    assertEquals(1L, storage.epoch())
    assertFalse(storage.writeActiveOrganizationIdIfEpoch(0, "o2"))
    assertNull(storage.activeOrganizationId())
    assertTrue(storage.writeActiveOrganizationIdIfEpoch(1, "o3"))
    assertEquals("o3", SettingsAuthTokenStorage(settings).activeOrganizationId())

    val legacy =
        object : AuthTokenStorage {
          override suspend fun epoch(): Long = 0

          override suspend fun token(): String? = null

          override suspend fun writeTokenIfEpoch(expectedEpoch: Long, token: String) = false

          override suspend fun clear() {}
        }
    assertNull(legacy.activeOrganizationId())
    assertFalse(legacy.writeActiveOrganizationIdIfEpoch(0, "o1"))
  }

  @Test
  fun t10InFlightOrganizationWritesAreFencedOut() = runTest {
    val storage = SettingsAuthTokenStorage(MapSettings())
    val gate = CompletableDeferred<Unit>()
    val writers =
        List(50) {
          async {
            val epoch = storage.epoch()
            gate.await()
            storage.writeActiveOrganizationIdIfEpoch(epoch, "late")
          }
        }
    testScheduler.advanceUntilIdle()
    storage.clear()
    gate.complete(Unit)
    assertTrue(writers.awaitAll().none { it })
    assertNull(storage.activeOrganizationId())
  }
}
