package dev.fediim.auth.core.contract

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lifecycle of [BetterAuthContractServerHarness]: shutdown, orphan guard and start failure. */
class BetterAuthContractServerHarnessTest {
  @Test
  fun closeStopsProcessAndIsIdempotent() {
    val server = BetterAuthContractServerHarness.start()
    val pid = server.process.pid()
    server.close()
    assertFalse(server.process.isAlive)
    assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
    server.close()
  }

  @Test
  fun serverExitsWhenStdinCloses() {
    val server = BetterAuthContractServerHarness.start()
    try {
      server.process.outputStream.close()
      assertTrue(server.process.waitFor(10, TimeUnit.SECONDS))
    } finally {
      server.close()
    }
  }

  @Test
  fun startFailureThrowsAndLeavesNoChild() {
    val empty = Files.createTempDirectory("contract-empty").toFile()
    try {
      val started = System.nanoTime()
      val e =
          assertFailsWith<IllegalStateException> { BetterAuthContractServerHarness.start(empty) }
      assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(30))
      assertTrue("Cannot find module" in e.message.orEmpty(), e.message)
      assertEquals(0L, ProcessHandle.current().children().filter { it.isAlive }.count())
    } finally {
      empty.delete()
    }
  }
}
