package dev.fediim.auth.core.storage

import com.russhwolf.settings.MapSettings
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsAuthTokenStorageStressTest {
  @Test
  fun t19WritersRaceOneClear() = runBlocking {
    val storage = SettingsAuthTokenStorage(MapSettings())
    val cleared = AtomicBoolean(false)
    val lateWins = AtomicInteger(0)
    val writers =
        List(1000) {
          launch(Dispatchers.Default) {
            val flagBefore = cleared.get()
            val won = storage.writeTokenIfEpoch(0, "t")
            // A write that began after clear() returned must never succeed.
            if (won && flagBefore) lateWins.incrementAndGet()
          }
        }
    val clearer =
        launch(Dispatchers.Default) {
          storage.clear()
          cleared.set(true)
        }
    (writers + clearer).joinAll()
    assertEquals(0, lateWins.get())
    // Writers that lost the race to clear() were rejected; a writer that ran before clear() was
    // wiped by it, so nothing may remain.
    assertNull(storage.token())
    assertEquals(1L, storage.epoch())
  }
}
