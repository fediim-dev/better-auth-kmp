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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private fun dispatchMessage(origin: String, nonce: String, token: String): Unit =
    js(
        "window.dispatchEvent(new MessageEvent('message', {data: {type: 'better-auth:oauth-popup', nonce: nonce, token: token}, origin: origin}))"
    )

private fun pageOrigin(): String = js("window.location.origin")

private fun storedState(): String? = js("window.sessionStorage.getItem('better-auth.social.state')")

private fun storeState(value: String): Unit =
    js("window.sessionStorage.setItem('better-auth.social.state', value)")

private fun clearState(): Unit = js("window.sessionStorage.removeItem('better-auth.social.state')")

class WebPopupSocialAuthLauncherTest {
  private val userJson = """{"id":"u1","email":"a@b.com"}"""
  private val sessionBody =
      """{"session":{"id":"s1","userId":"u1","token":"st"},"user":$userJson}"""
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

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
    assertEquals(pageOrigin(), Url(opened!!).parameters["popupOrigin"])

    dispatchMessage("https://evil.example", nonce, "evil.t")
    testScheduler.runCurrent()
    assertNull(storage.token())
    dispatchMessage("https://example.com", "wrong", "nonce.t")
    testScheduler.runCurrent()
    assertNull(storage.token())
    assertTrue(result.isActive)

    dispatchMessage("https://example.com", nonce, "t.s")
    assertIs<BetterAuthResult.Success<*>>(result.await())
    assertEquals("t.s", storage.token())
  }

  @Test
  fun blockedPopupFallsBackToRedirect() = runTest {
    val navigated = CompletableDeferred<String>()
    clearState()
    val launcher =
        WebPopupSocialAuthLauncher(openWindow = { false }, navigate = { navigated.complete(it) })
    val job = async { client(launcher, SettingsAuthTokenStorage(MapSettings())).signInSocial("g") }
    assertEquals("https://provider.example/a", navigated.await())
    assertTrue(storedState()!!.length >= 32)
    job.cancelAndJoin()
  }

  @Test
  fun consumeRedirectCallbackChecksStoredState() {
    val launcher = WebPopupSocialAuthLauncher(openWindow = { true }, navigate = {})
    storeState("s1")
    assertNull(launcher.consumeRedirectCallback("https://app.example/?state=other&ott=o"))
    assertEquals(
        SocialCallback.OneTimeToken("o"),
        launcher.consumeRedirectCallback("https://app.example/?state=s1&ott=o"),
    )
    assertNull(storedState())
    assertNull(launcher.consumeRedirectCallback("https://app.example/?state=s1&ott=o"))
  }
}
