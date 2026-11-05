@file:OptIn(ExperimentalTestApi::class)

package dev.fediim.auth.fds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.russhwolf.settings.MapSettings
import dev.fediim.auth.compose.BetterAuthViewModel
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.social.SocialAuthLauncher
import dev.fediim.auth.core.social.SocialCallback
import dev.fediim.auth.core.social.SocialFlow
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import dev.fediim.auth.passkeys.BetterAuthPasskeyClient
import dev.fediim.fds.theme.FediimTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope

internal const val USER_A = """{"id":"uA","email":"first@b.com"}"""
internal const val TOKEN_REPLY = """{"token":"t1","user":{"id":"uA","email":"first@b.com"}}"""
internal const val TWO_FACTOR_REPLY = """{"twoFactorRedirect":true,"twoFactorMethods":["totp"]}"""

/** A canned server answer. */
internal class Reply(
    val body: String = "{}",
    val status: Int = 200,
    val headers: List<Pair<String, String>> = emptyList(),
)

/** One request the mock server saw: the path after `/api/auth/` and its JSON body ("" if none). */
internal data class Recorded(val path: String, val body: String)

/** Asks a social provider's authorize URL, then reports the user denied access. */
internal class DenyingLauncher : SocialAuthLauncher {
  override suspend fun launch(
      state: String,
      serverOrigin: String,
      authorize: suspend (flow: SocialFlow, receiver: String) -> String,
  ): SocialCallback {
    authorize(SocialFlow.Redirect, "app://cb?state=$state")
    return SocialCallback.Error("access_denied")
  }
}

/**
 * A real [BetterAuthClient] over a [MockEngine]. Replies are queued per path and consumed in order;
 * the last one repeats. An unrouted path answers 404. [gate] holds the answer of a path until the
 * returned deferred completes. [sign-out] always answers success.
 */
internal class FdsTestRig {
  val requests = CopyOnWriteArrayList<Recorded>()
  private val queues = ConcurrentHashMap<String, MutableList<Reply>>()
  private val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

  val client =
      BetterAuthClient(
          BetterAuthConfig("https://example.com"),
          SettingsAuthTokenStorage(MapSettings()),
          MockEngine.create {
            addHandler { request ->
              val path = request.url.encodedPath.removePrefix("/api/auth/")
              requests += Recorded(path, (request.body as? TextContent)?.text.orEmpty())
              gates[path]?.await()
              val reply = next(path)
              val headers = HeadersBuilder()
              headers.append(HttpHeaders.ContentType, "application/json")
              reply.headers.forEach { (name, value) -> headers.append(name, value) }
              respond(reply.body, HttpStatusCode.fromValue(reply.status), headers.build())
            }
          },
          DenyingLauncher(),
      )

  init {
    on("sign-out", Reply("""{"success":true}"""))
  }

  fun on(path: String, vararg replies: Reply) {
    queues[path] = replies.toMutableList()
  }

  fun gate(path: String): CompletableDeferred<Unit> =
      CompletableDeferred<Unit>().also { gates[path] = it }

  fun to(path: String): List<Recorded> = requests.filter { it.path == path }

  private fun next(path: String): Reply {
    val queue = queues[path] ?: return Reply("""{"code":"UNROUTED"}""", 404)
    synchronized(queue) {
      return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }
  }

  /**
   * Build inside `setContent` on `rememberCoroutineScope()` (the view model's threading contract).
   */
  fun viewModel(scope: CoroutineScope, passkeys: BetterAuthPasskeyClient? = null) =
      BetterAuthViewModel(client, scope, passkeys)
}

/**
 * Composes [content] inside `FediimTheme` with a view model built by [create] on the composition's
 * own scope (the view models' threading contract) and returns that view model.
 */
internal fun <VM : Any> ComposeUiTest.setVmContent(
    create: (CoroutineScope) -> VM,
    content: @Composable (VM) -> Unit,
): VM {
  var holder: VM? = null
  setContent {
    FediimTheme(darkTheme = false) {
      val scope = rememberCoroutineScope()
      content(remember { create(scope).also { holder = it } })
    }
  }
  return checkNotNull(holder)
}

/** Polls until [condition] holds, failing after 5 seconds. */
internal fun ComposeUiTest.until(what: String, condition: () -> Boolean) =
    waitUntil(what, 5_000, condition)

internal fun ComposeUiTest.hasTag(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeUiTest.hasText(text: String, substring: Boolean = false): Boolean =
    onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeUiTest.type(tag: String, text: String) =
    onNodeWithTag(tag).performTextInput(text)

internal fun ComposeUiTest.retype(tag: String, text: String) =
    onNodeWithTag(tag).performTextReplacement(text)

/** Runs [block] in a desktop Compose test window big enough for a dialog. */
internal fun runDesktopTest(block: suspend DesktopComposeUiTest.() -> Unit) =
    runDesktopComposeUiTest(width = 800, height = 900, block = block)
