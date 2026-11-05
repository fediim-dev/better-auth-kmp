package dev.fediim.auth.core.social

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.contract.BetterAuthContractServerHarness
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.okhttp.OkHttp
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Live social sign-in against the Node contract server and its mock GitLab provider. */
class SocialContractTest {
  /** SDK client that records `(path, set-auth-token)` for every response. */
  private class Recorded(baseUrl: String, launcher: SocialAuthLauncher) {
    val headers = CopyOnWriteArrayList<Pair<String, String?>>()
    val storage = SettingsAuthTokenStorage(MapSettings())
    val client =
        BetterAuthClient(
            BetterAuthConfig(baseUrl),
            storage,
            OkHttp.create {
              addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                headers.add(chain.request().url.encodedPath to response.header("set-auth-token"))
                response
              }
            },
            launcher,
        )
  }

  private fun browser(followRedirects: Boolean): HttpClient =
      HttpClient.newBuilder()
          .followRedirects(
              if (followRedirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER
          )
          .cookieHandler(CookieManager())
          .build()

  private fun HttpClient.get(url: String): HttpResponse<String> =
      send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofString())

  /** Launcher that opens the provider URL in a redirect-following simulated browser. */
  private fun loopback() =
      JvmLoopbackSocialAuthLauncher(30.seconds) { uri ->
        thread(isDaemon = true) { browser(true).get(uri.toString()) }
      }

  @Test
  fun loopbackSignInSucceedsAndDenyFails() = runBlocking {
    BetterAuthContractServerHarness.start().use { server ->
      val rec = Recorded(server.baseUrl, loopback())
      try {
        val result = rec.client.signInSocial("gitlab")
        assertTrue(result is BetterAuthResult.Success, "result: $result")
        val verify = rec.headers.last { it.first == "/api/auth/one-time-token/verify" }
        assertNotNull(verify.second)
        assertEquals(verify.second, rec.storage.token())
        val envelope = (rec.client.getSession() as BetterAuthResult.Success).value
        assertEquals("social-user@example.com", envelope?.user?.email)

        val denied = Recorded(server.baseUrl, loopback())
        val failure = denied.client.signInSocial("gitlab", scopes = listOf("deny"))
        assertIs<BetterAuthResult.Failure>(failure)
        assertEquals(0, failure.status)
        assertEquals("access_denied", failure.error.code)
        assertNull(denied.storage.token())
        denied.client.close()
      } finally {
        rec.client.close()
      }
    }
  }

  /** Launcher that follows the provider hops by hand and records the final callback `Location`. */
  private class LocationProbe(val base: String) : SocialAuthLauncher {
    var location: String? = null

    override suspend fun launch(
        state: String,
        serverOrigin: String,
        authorize: suspend (flow: SocialFlow, receiver: String) -> String,
    ): SocialCallback {
      val providerUrl = authorize(SocialFlow.Redirect, withState(base, state))
      val manual =
          HttpClient.newBuilder()
              .followRedirects(HttpClient.Redirect.NEVER)
              .cookieHandler(CookieManager())
              .build()
      fun hop(url: String) =
          manual
              .send(
                  HttpRequest.newBuilder(URI(url)).GET().build(),
                  HttpResponse.BodyHandlers.ofString(),
              )
              .headers()
              .firstValue("location")
              .get()
      location = hop(hop(providerUrl))
      return SocialCallback.Error("probe")
    }
  }

  @Test
  fun ottIsAppendedOnlyToAllowlistedReceivers() = runBlocking {
    BetterAuthContractServerHarness.start().use { server ->
      suspend fun locationFor(base: String): String {
        val probe = LocationProbe(base)
        val rec = Recorded(server.baseUrl, probe)
        try {
          rec.client.signInSocial("gitlab")
        } finally {
          rec.client.close()
        }
        return assertNotNull(probe.location)
      }
      val landing = locationFor("${server.baseUrl}/sdk-landing")
      assertTrue("ott=" in landing, landing)
      assertTrue(landing.startsWith("${server.baseUrl}/sdk-landing?"), landing)
      for (rejected in
          listOf(
              "${server.baseUrl}/sdk-landingX",
              "http://127.0.0.1:${URI(server.baseUrl).port}/other",
          )) {
        val location = locationFor(rejected)
        assertFalse("ott=" in location, location)
        assertTrue(location.startsWith(rejected), location)
      }
    }
  }

  @Test
  fun popupCompletionPageFeedsSessionToken() = runBlocking {
    BetterAuthContractServerHarness.start().use { server ->
      val rec = Recorded(server.baseUrl, loopback())
      try {
        val origin = "http://127.0.0.1:1"
        val page =
            browser(true)
                .get(
                    "${server.baseUrl}/api/auth/oauth-popup/start?provider=gitlab" +
                        "&popupOrigin=$origin&popupNonce=n"
                )
        assertEquals(200, page.statusCode())
        val json =
            Regex("""id="better-auth-oauth-popup">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .find(page.body())!!
                .groupValues[1]
        assertEquals(
            JsonPrimitive(origin),
            Json.parseToJsonElement(json).jsonObject["targetOrigin"],
        )
        val callback = parsePopupMessage(origin, json, origin, "n")
        assertIs<SocialCallback.SessionToken>(callback)
        val result = rec.client.completeSocialSignIn(callback)
        assertTrue(result is BetterAuthResult.Success, "result: $result")
        assertEquals(callback.token, rec.storage.token())
      } finally {
        rec.client.close()
      }
    }
  }
}
