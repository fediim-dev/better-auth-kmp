package dev.fediim.auth.passkeys

import dev.fediim.auth.core.BetterAuthCore
import kotlin.test.Test
import kotlin.test.assertEquals

class BetterAuthPasskeysTest {
  @Test
  fun versionMatchesCore() {
    assertEquals(BetterAuthCore.VERSION, BetterAuthPasskeys.VERSION)
  }
}
