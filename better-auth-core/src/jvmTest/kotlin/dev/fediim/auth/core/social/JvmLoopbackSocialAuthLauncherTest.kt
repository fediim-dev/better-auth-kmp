package dev.fediim.auth.core.social

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class JvmLoopbackSocialAuthLauncherTest {
  private val http = HttpClient.newHttpClient()
  private val provider = "https://provider.example/authorize"

  private fun get(url: String): HttpResponse<String> =
      http.send(
          HttpRequest.newBuilder(URI(url)).GET().build(),
          HttpResponse.BodyHandlers.ofString(),
      )

  private fun connectable(receiver: String) {
    Socket().use { it.connect(InetSocketAddress("127.0.0.1", URI(receiver).port), 2000) }
  }

  private fun assertPortFreed(receiver: String) {
    assertFailsWith<ConnectException> { connectable(receiver) }
  }

  /** A running launch whose receiver is known; [opened] completes when the opener ran. */
  private class Flow(
      val receiver: String,
      val result: Deferred<SocialCallback>,
      val opened: CompletableDeferred<URI>,
  )

  private suspend fun start(
      timeout: Duration = 30.seconds,
      onOpen: (URI) -> Unit = {},
      authorizeHook: (String) -> Unit = {},
      authorizeFails: Boolean = false,
  ): Flow {
    val receiver = CompletableDeferred<String>()
    val opened = CompletableDeferred<URI>()
    val launcher =
        JvmLoopbackSocialAuthLauncher(timeout) {
          onOpen(it)
          opened.complete(it)
        }
    val result =
        CoroutineScope(Dispatchers.IO + SupervisorJob()).async {
          launcher.launch("st", "http://example") { flow, r ->
            assertEquals(SocialFlow.Redirect, flow)
            receiver.complete(r)
            authorizeHook(r)
            if (authorizeFails) throw IllegalStateException("authorize failed")
            provider
          }
        }
    return Flow(withTimeout(10.seconds) { receiver.await() }, result, opened)
  }

  @Test
  fun validCallbackCompletesAndFreesPort() = runBlocking {
    val flow = start()
    assertTrue(flow.receiver.startsWith("http://127.0.0.1:"), flow.receiver)
    assertEquals("/auth/callback", URI(flow.receiver).path)
    withContext(Dispatchers.IO) { connectable(flow.receiver) }
    assertEquals(URI(provider), withTimeout(10.seconds) { flow.opened.await() })

    val response = withContext(Dispatchers.IO) { get("${flow.receiver}&ott=o") }
    assertEquals(200, response.statusCode())
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null))
    assertFalse("ott" in response.body())
    assertEquals(SocialCallback.OneTimeToken("o"), withTimeout(10.seconds) { flow.result.await() })
    assertPortFreed(flow.receiver)
  }

  @Test
  fun wrongStateWrongPathAndMethodKeepWaiting() = runBlocking {
    val flow = start()
    val bad =
        withContext(Dispatchers.IO) {
          get("${flow.receiver.substringBefore('?')}?state=nope&ott=o")
        }
    assertEquals(400, bad.statusCode())
    assertFalse("nope" in bad.body())
    val noState = withContext(Dispatchers.IO) { get(flow.receiver.substringBefore('?') + "?ott=o") }
    assertEquals(400, noState.statusCode())
    val deep =
        withContext(Dispatchers.IO) {
          get(flow.receiver.replace("/auth/callback", "/auth/callback/x") + "&ott=o")
        }
    assertEquals(404, deep.statusCode())
    val post =
        withContext(Dispatchers.IO) {
          http.send(
              HttpRequest.newBuilder(URI("${flow.receiver}&ott=o"))
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString(),
          )
        }
    assertEquals(405, post.statusCode())
    assertFalse(flow.result.isCompleted)

    val ok = withContext(Dispatchers.IO) { get("${flow.receiver}&ott=good") }
    assertEquals(200, ok.statusCode())
    assertEquals(
        SocialCallback.OneTimeToken("good"),
        withTimeout(10.seconds) { flow.result.await() },
    )
    assertPortFreed(flow.receiver)
  }

  @Test
  fun timeoutReportsErrorAndFreesPort() = runBlocking {
    val flow = start(timeout = 200.milliseconds)
    withContext(Dispatchers.IO) { connectable(flow.receiver) }
    assertEquals(SocialCallback.Error("timeout"), withTimeout(10.seconds) { flow.result.await() })
    assertPortFreed(flow.receiver)
  }

  @Test
  fun cancellationFreesPort() = runBlocking {
    val flow = start()
    withContext(Dispatchers.IO) { connectable(flow.receiver) }
    flow.result.cancelAndJoin()
    assertPortFreed(flow.receiver)
  }

  @Test
  fun authorizeFailureFreesPort() = runBlocking {
    var receiver = ""
    val flow =
        start(
            authorizeHook = {
              receiver = it
              connectable(it)
            },
            authorizeFails = true,
        )
    assertFailsWith<IllegalStateException> { withTimeout(10.seconds) { flow.result.await() } }
    assertPortFreed(receiver)
  }

  @Test
  fun openerFailureFreesPort() = runBlocking {
    var receiver = ""
    val flow =
        start(
            authorizeHook = { receiver = it },
            onOpen = {
              connectable(receiver)
              throw java.io.IOException("no browser")
            },
        )
    assertFailsWith<java.io.IOException> { withTimeout(10.seconds) { flow.result.await() } }
    assertPortFreed(receiver)
  }
}
