package dev.fediim.auth.fds

import dev.fediim.auth.compose.BetterAuthCompose
import dev.fediim.auth.core.BetterAuthCore
import kotlin.test.Test
import kotlin.test.assertEquals

class BetterAuthFdsTest {
  @Test
  fun versionMatchesComposeAndCore() {
    assertEquals(BetterAuthCompose.VERSION, BetterAuthFds.VERSION)
    assertEquals(BetterAuthCore.VERSION, BetterAuthFds.VERSION)
  }
}
