package dev.fediim.auth.core.contract

import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A running Node better-auth contract server (`tests/contract-server/server.mjs`) on a loopback
 * port. [close] is idempotent and stops the process; the server also exits by itself when its stdin
 * closes, so it cannot outlive a killed JVM.
 */
internal class BetterAuthContractServerHarness
private constructor(val process: Process, port: Int) : AutoCloseable {
  /** Origin of the server, without the `/api/auth` path. */
  val baseUrl: String = "http://127.0.0.1:$port"

  private val closed = AtomicBoolean(false)
  private val hook = Thread { close() }

  init {
    Runtime.getRuntime().addShutdownHook(hook)
  }

  /**
   * Stops the server: `destroy()`, wait 5 s, then `destroyForcibly()`. Safe to call repeatedly and
   * from the shutdown hook.
   */
  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    stop(process)
    try {
      Runtime.getRuntime().removeShutdownHook(hook)
    } catch (_: IllegalStateException) {
      // JVM is already shutting down; the hook is running or about to.
    }
  }

  companion object {
    private const val START_TIMEOUT_SECONDS = 60L
    private const val PORT_PREFIX = "PORT="

    /**
     * Starts `node <serverDir>/server.mjs` and returns once it printed `PORT=<n>`. The child gets
     * only [childEnvironment] of this JVM's environment, so CI secrets never reach it.
     *
     * @throws IllegalStateException when the property `betterauth.contractServerDir` is unset and
     *   no [serverDir] is given, the process exits before printing the port (message holds the exit
     *   code and the last output lines), or no port appears within 60 s. The process is destroyed
     *   before throwing.
     * @throws IOException when `node` is not installed (message names Node and
     *   `tests/contract-server`).
     */
    fun start(serverDir: File = defaultServerDir()): BetterAuthContractServerHarness {
      val builder =
          ProcessBuilder("node", File(serverDir, "server.mjs").absolutePath)
              .redirectErrorStream(true)
      val env = builder.environment()
      val allowed = childEnvironment(System.getenv())
      env.clear()
      env.putAll(allowed)
      val process =
          try {
            builder.start()
          } catch (e: IOException) {
            throw IOException(
                "Cannot start the contract server: Node.js (see tests/contract-server) is " +
                    "required on PATH for jvmTest",
                e,
            )
          }
      val port = CompletableFuture<Int>()
      val tail = ArrayDeque<String>()
      val reader = Thread {
        try {
          process.inputStream.bufferedReader().forEachLine { line ->
            if (line.startsWith(PORT_PREFIX) && !port.isDone) {
              port.complete(line.removePrefix(PORT_PREFIX).trim().toInt())
            } else {
              System.err.println(line)
              synchronized(tail) {
                tail.addLast(line)
                if (tail.size > 20) tail.removeFirst()
              }
            }
          }
        } catch (_: IOException) {
          // stream closed by destroy()
        }
        val code = if (process.waitFor(5, TimeUnit.SECONDS)) process.exitValue() else -1
        val lines = synchronized(tail) { tail.joinToString("\n") }
        port.completeExceptionally(
            IllegalStateException(
                "contract server exited (code $code) before printing PORT=; output:\n$lines"
            )
        )
      }
      reader.isDaemon = true
      reader.start()
      try {
        val value = port.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return BetterAuthContractServerHarness(process, value)
      } catch (e: ExecutionException) {
        stop(process)
        throw (e.cause as? IllegalStateException ?: IllegalStateException(e.cause))
      } catch (e: TimeoutException) {
        stop(process)
        throw IllegalStateException("contract server printed no PORT= within 60 s", e)
      } catch (e: Throwable) {
        stop(process)
        throw e
      }
    }

    private val ALLOWED_ENV = listOf("PATH", "HOME", "TMPDIR", "TEMP", "TMP", "SystemRoot", "LANG")

    /**
     * The variables of [parent] named in the allowlist (PATH, HOME, TMPDIR, TEMP, TMP, SystemRoot,
     * LANG; names matched case-insensitively, stored under the allowlist spelling, so Windows
     * `Path` becomes `PATH`), plus `BETTER_AUTH_TELEMETRY=false`, which overrides any parent value.
     */
    internal fun childEnvironment(parent: Map<String, String>): Map<String, String> {
      val result = mutableMapOf<String, String>()
      for ((key, value) in parent) {
        val name = ALLOWED_ENV.firstOrNull { it.equals(key, ignoreCase = true) } ?: continue
        result[name] = value
      }
      result["BETTER_AUTH_TELEMETRY"] = "false"
      return result
    }

    private fun defaultServerDir(): File =
        File(
            System.getProperty("betterauth.contractServerDir")
                ?: error("system property betterauth.contractServerDir is not set")
        )

    private fun stop(process: Process) {
      process.destroy()
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor()
      }
    }
  }
}
