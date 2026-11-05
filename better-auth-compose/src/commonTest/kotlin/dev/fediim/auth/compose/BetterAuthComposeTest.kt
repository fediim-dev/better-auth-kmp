package dev.fediim.auth.compose

import dev.fediim.auth.core.BetterAuthCore
import dev.fediim.auth.passkeys.BetterAuthPasskeys
import kotlin.test.Test
import kotlin.test.assertEquals

class BetterAuthComposeTest {
  @Test
  fun versionMatchesCoreAndPasskeys() {
    assertEquals(BetterAuthCore.VERSION, BetterAuthCompose.VERSION)
    assertEquals(BetterAuthPasskeys.VERSION, BetterAuthCompose.VERSION)
  }
}
