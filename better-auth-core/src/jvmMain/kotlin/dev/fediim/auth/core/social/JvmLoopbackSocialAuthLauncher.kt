package dev.fediim.auth.core.social

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Desktop launcher: opens the system browser and receives the redirect on a one-shot HTTP server
 * bound to `127.0.0.1` on an ephemeral port, serving `GET /auth/callback` only.
 *
 * Requests with a wrong path (404), method (405), state or missing token (400) get an empty answer
 * that never echoes the request, and the launcher keeps waiting, so a local process cannot abort a
 * flow; requests are handled on a pool of four threads, so up to three stalled local connections do
 * not delay the real callback. The first valid callback gets a static page, then completes the
 * flow. After [timeout] the result is [SocialCallback.Error] `timeout`. The port is released before
 * [launch] returns or throws, also on cancellation; exceptions from [openBrowser] or `authorize`
 * propagate.
 *
 * The server must trust and allow the receiver `http://127.0.0.1:<any port>/auth/callback`.
 */
public class JvmLoopbackSocialAuthLauncher(
    private val timeout: Duration = 5.minutes,
    private val openBrowser: (URI) -> Unit = { Desktop.getDesktop().browse(it) },
) : SocialAuthLauncher {
  override suspend fun launch(
      state: String,
      serverOrigin: String,
      authorize: suspend (flow: SocialFlow, receiver: String) -> String,
  ): SocialCallback {
    val result = CompletableDeferred<SocialCallback>()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    val executor: ExecutorService = Executors.newFixedThreadPool(4)
    server.executor = executor
    try {
      server.createContext("/") { exchange -> handle(exchange, state, result) }
      server.start()
      val receiver = "http://127.0.0.1:${server.address.port}$CALLBACK_PATH"
      val url = authorize(SocialFlow.Redirect, withState(receiver, state))
      return withTimeoutOrNull(timeout) {
        withContext(Dispatchers.IO) { openBrowser(URI(url)) }
        result.await()
      } ?: SocialCallback.Error("timeout")
    } finally {
      server.stop(0)
      executor.shutdownNow()
    }
  }

  private fun handle(
      exchange: HttpExchange,
      state: String,
      result: CompletableDeferred<SocialCallback>,
  ) {
    exchange.use {
      val path = exchange.requestURI.rawPath
      val query = exchange.requestURI.rawQuery
      if (path != CALLBACK_PATH) return@use reply(exchange, 404)
      if (exchange.requestMethod != "GET") return@use reply(exchange, 405)
      val callback =
          query?.let { parseRedirectCallback("http://127.0.0.1$CALLBACK_PATH?$it", state) }
              ?: return@use reply(exchange, 400)
      reply(exchange, 200, DONE_PAGE)
      exchange.close()
      result.complete(callback)
    }
  }

  private fun reply(exchange: HttpExchange, status: Int, body: ByteArray? = null) {
    exchange.responseHeaders.add("Cache-Control", "no-store")
    exchange.responseHeaders.add("Referrer-Policy", "no-referrer")
    if (body == null) {
      exchange.sendResponseHeaders(status, -1)
      return
    }
    exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
  }

  private companion object {
    const val CALLBACK_PATH = "/auth/callback"
    val DONE_PAGE =
        "<!doctype html><meta charset=utf-8><title>Signed in</title>Sign-in finished. You can close this tab."
            .toByteArray()
  }
}
