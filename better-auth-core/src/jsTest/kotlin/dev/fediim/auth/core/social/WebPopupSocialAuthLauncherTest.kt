package dev.fediim.auth.core.social

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.js.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.w3c.dom.MessageEvent
import org.w3c.dom.MessageEventInit

class WebPopupSocialAuthLauncherTest {
  private val userJson = """{"id":"u1","email":"a@b.com"}"""
  private val sessionBody =
      """{"session":{"id":"s1","userId":"u1","token":"st"},"user":$userJson}"""
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  private fun dispatch(origin: String, nonce: String, token: String) {
    val data = json("type" to "better-auth:oauth-popup", "nonce" to nonce, "token" to token)
    window.dispatchEvent(MessageEvent("message", MessageEventInit(data = data, origin = origin)))
  }

  private fun client(launcher: WebPopupSocialAuthLauncher, storage: SettingsAuthTokenStorage) =
      BetterAuthClient(
          BetterAuthConfig("https://example.com"),
          storage,
          MockEngine { request ->
            val body =
                if (request.url.encodedPath.endsWith("sign-in/social"))
                    """{"url":"https://provider.example/a","redirect":false}"""
                else sessionBody
            respond(body, HttpStatusCode.OK, jsonHeaders)
          },
          launcher,
      )

  @Test
  fun popupMessageIsAcceptedOnlyFromServerOriginWithNonce() = runTest {
    var opened: String? = null
    val launcher =
        WebPopupSocialAuthLauncher(
            openWindow = {
              opened = it
              true
            },
            navigate = {},
        )
    val storage = SettingsAuthTokenStorage(MapSettings())
    val result = async { client(launcher, storage).signInSocial("google") }
    testScheduler.runCurrent()
    val nonce = Url(opened!!).parameters["popupNonce"]!!
    assertEquals(window.location.origin, Url(opened!!).parameters["popupOrigin"])

    dispatch("https://evil.example", nonce, "t.s")
    testScheduler.runCurrent()
    assertNull(storage.token())
    dispatch("https://example.com", "wrong", "t.s")
    testScheduler.runCurrent()
    assertNull(storage.token())
    assertTrue(result.isActive)

    dispatch("https://example.com", nonce, "t.s")
    assertIs<BetterAuthResult.Success<*>>(result.await())
    assertEquals("t.s", storage.token())
  }

  @Test
  fun blockedPopupFallsBackToRedirect() = runTest {
    val navigated = CompletableDeferred<String>()
    window.sessionStorage.removeItem(WEB_STATE_STORAGE_KEY)
    val launcher =
        WebPopupSocialAuthLauncher(openWindow = { false }, navigate = { navigated.complete(it) })
    val job = async { client(launcher, SettingsAuthTokenStorage(MapSettings())).signInSocial("g") }
    assertEquals("https://provider.example/a", navigated.await())
    val state = window.sessionStorage.getItem(WEB_STATE_STORAGE_KEY)!!
    assertTrue(state.length >= 32)
    job.cancelAndJoin()
  }

  @Test
  fun consumeRedirectCallbackChecksStoredState() {
    val launcher = WebPopupSocialAuthLauncher(openWindow = { true }, navigate = {})
    window.sessionStorage.setItem(WEB_STATE_STORAGE_KEY, "s1")
    assertNull(launcher.consumeRedirectCallback("https://app.example/?state=other&ott=o"))
    assertEquals(
        SocialCallback.OneTimeToken("o"),
        launcher.consumeRedirectCallback("https://app.example/?state=s1&ott=o"),
    )
    assertNull(window.sessionStorage.getItem(WEB_STATE_STORAGE_KEY))
    assertNull(launcher.consumeRedirectCallback("https://app.example/?state=s1&ott=o"))
  }
}
