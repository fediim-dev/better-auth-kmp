package dev.fediim.auth.core

import kotlin.test.Test
import kotlin.test.assertTrue

class BetterAuthCoreTest {
  @Test
  fun versionIsSemver() {
    assertTrue(Regex("""^\d+\.\d+\.\d+(-SNAPSHOT)?$""").matches(BetterAuthCore.VERSION))
  }
}
