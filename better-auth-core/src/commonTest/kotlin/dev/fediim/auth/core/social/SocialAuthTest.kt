package dev.fediim.auth.core.social

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SocialAuthTest {
  private val userJson = """{"id":"u1","email":"a@b.com"}"""
  private val sessionBody =
      """{"session":{"id":"s1","userId":"u1","token":"st"},"user":$userJson}"""
  private val providerUrl = "https://provider.example/authorize?x=1"
  private val loopback = "http://127.0.0.1:5555/auth/callback"

  /** Records what the client hands to a launcher, then answers with [result]. */
  private class FakeLauncher(
      val flow: SocialFlow,
      val base: String,
      val result: suspend (state: String) -> SocialCallback,
  ) : SocialAuthLauncher {
    val states = mutableListOf<String>()
    var serverOrigin: String? = null
    var receiver: String? = null
    var url: String? = null
    var authorizeError: Throwable? = null

    override suspend fun launch(
        state: String,
        serverOrigin: String,
        authorize: suspend (flow: SocialFlow, receiver: String) -> String,
    ): SocialCallback {
      states += state
      this.serverOrigin = serverOrigin
      val r = if (flow == SocialFlow.Redirect) withState(base, state) else base
      receiver = r
      try {
        url = authorize(flow, r)
      } catch (e: Exception) {
        authorizeError = e
        throw e
      }
      return result(state)
    }
  }

  private class Rig(
      launcher: SocialAuthLauncher?,
      handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
  ) {
    val requests = mutableListOf<HttpRequestData>()
    val storage = SettingsAuthTokenStorage(MapSettings())
    val client =
        BetterAuthClient(
            BetterAuthConfig("https://example.com"),
            storage,
            MockEngine { request ->
              requests += request
              handler(request)
            },
            launcher,
        )

    fun bodyOf(index: Int) = Json.parseToJsonElement((requests[index].body as TextContent).text)
  }

  private fun MockRequestHandleScope.json(
      body: String,
      status: HttpStatusCode = HttpStatusCode.OK,
      vararg extra: Pair<String, String>,
  ): HttpResponseData =
      respond(
          body,
          status,
          io.ktor.http
              .HeadersBuilder()
              .apply {
                append(HttpHeaders.ContentType, "application/json")
                extra.forEach { (k, v) -> append(k, v) }
              }
              .build(),
      )

  private fun redirectRig(
      launcher: SocialAuthLauncher?,
      verifyHeader: String? = "hdr",
      verifyStatus: HttpStatusCode = HttpStatusCode.OK,
  ) =
      Rig(launcher) { request ->
        when (request.url.encodedPath) {
          "/api/auth/sign-in/social" -> json("""{"url":"$providerUrl","redirect":false}""")
          "/api/auth/one-time-token/verify" ->
              if (verifyStatus != HttpStatusCode.OK)
                  json("""{"code":"BAD","message":"m"}""", verifyStatus)
              else if (verifyHeader != null)
                  json(sessionBody, extra = arrayOf("set-auth-token" to verifyHeader))
              else json(sessionBody)
          else -> json("null")
        }
      }

  @Test
  fun t1RedirectRequestAndOneTimeTokenExchange() = runTest {
    val launcher = FakeLauncher(SocialFlow.Redirect, loopback) { SocialCallback.OneTimeToken("o") }
    val rig = redirectRig(launcher)
    val result = rig.client.signInSocial("google")

    assertEquals(HttpMethod.Post, rig.requests[0].method)
    assertEquals("https://example.com/api/auth/sign-in/social", rig.requests[0].url.toString())
    val body = rig.bodyOf(0).jsonObject
    val r = launcher.receiver!!
    assertTrue(r.startsWith("$loopback?state="), r)
    assertEquals(JsonPrimitive("google"), body["provider"])
    assertEquals(JsonPrimitive(r), body["callbackURL"])
    assertEquals(JsonPrimitive(r), body["errorCallbackURL"])
    assertTrue(body["disableRedirect"]!!.jsonPrimitive.boolean)
    assertNull(body["scopes"])
    assertEquals(providerUrl, launcher.url)
    assertEquals("https://example.com", launcher.serverOrigin)

    assertEquals(HttpMethod.Post, rig.requests[1].method)
    assertEquals(
        "https://example.com/api/auth/one-time-token/verify",
        rig.requests[1].url.toString(),
    )
    assertEquals("""{"token":"o"}""", (rig.requests[1].body as TextContent).text)
    assertEquals("hdr", rig.storage.token())
    assertEquals(
        "s1",
        (assertIs<BetterAuthResult.Success<*>>(result).value
                as dev.fediim.auth.core.model.AuthSession)
            .id,
    )
  }

  @Test
  fun t1BodyTokenFallbackScopesAndCallbackUrl() = runTest {
    val launcher = FakeLauncher(SocialFlow.Redirect, loopback) { SocialCallback.OneTimeToken("o") }
    val rig = redirectRig(launcher, verifyHeader = null)
    rig.client.signInSocial("google", scopes = listOf("a", "b"))
    assertEquals(
        JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))),
        rig.bodyOf(0).jsonObject["scopes"]!!.jsonArray,
    )
    assertEquals("st", rig.storage.token())

    rig.client.signInSocial("google", callbackUrl = "http://127.0.0.1:5555/other")
    val state = launcher.states.last()
    val sameOrigin = rig.bodyOf(2).jsonObject
    assertEquals(
        JsonPrimitive("http://127.0.0.1:5555/other?state=$state"),
        sameOrigin["callbackURL"],
    )
    assertEquals(
        JsonPrimitive("http://127.0.0.1:5555/other?state=$state"),
        sameOrigin["errorCallbackURL"],
    )

    rig.client.signInSocial("google", callbackUrl = "https://other.example/cb")
    val foreign = rig.bodyOf(4).jsonObject
    assertEquals(JsonPrimitive(launcher.receiver), foreign["callbackURL"])
  }

  @Test
  fun t2PopupStartUrlAndSessionToken() = runTest {
    val launcher =
        FakeLauncher(SocialFlow.Popup, "https://app.example") { SocialCallback.SessionToken("t.s") }
    val rig = Rig(launcher) { json(sessionBody) }
    val result = rig.client.signInSocial("google", scopes = listOf("a", "b"))

    val url = Url(launcher.url!!)
    assertEquals("/api/auth/oauth-popup/start", url.encodedPath)
    assertEquals("example.com", url.host)
    assertEquals("google", url.parameters["provider"])
    assertEquals("https://app.example", url.parameters["popupOrigin"])
    assertEquals(launcher.states.single(), url.parameters["popupNonce"])
    assertEquals("a,b", url.parameters["scopes"])

    assertEquals(1, rig.requests.size)
    assertEquals(HttpMethod.Get, rig.requests[0].method)
    assertEquals("Bearer t.s", rig.requests[0].headers[HttpHeaders.Authorization])
    assertEquals("t.s", rig.storage.token())
    assertIs<BetterAuthResult.Success<*>>(result)
  }

  @Test
  fun t2SessionTokenWithoutSessionFails() = runTest {
    val launcher =
        FakeLauncher(SocialFlow.Popup, "https://app.example") { SocialCallback.SessionToken("t.s") }
    val rig = Rig(launcher) { json("null") }
    val result = assertIs<BetterAuthResult.Failure>(rig.client.signInSocial("google"))
    assertEquals(0, result.status)
    assertEquals("SESSION_NOT_FOUND", result.error.code)
    assertNull(rig.storage.token())
  }

  @Test
  fun t3ErrorsAndLauncherFailures() = runTest {
    val denied =
        FakeLauncher(SocialFlow.Redirect, loopback) { SocialCallback.Error("access_denied", "d") }
    val rig = redirectRig(denied)
    val failure = assertIs<BetterAuthResult.Failure>(rig.client.signInSocial("google"))
    assertEquals(0, failure.status)
    assertEquals("access_denied", failure.error.code)
    assertEquals(1, rig.requests.size)

    val broken = FakeLauncher(SocialFlow.Redirect, loopback) { throw IllegalStateException("boom") }
    val failed = assertIs<BetterAuthResult.Failure>(redirectRig(broken).client.signInSocial("g"))
    assertEquals("SOCIAL_LAUNCH_FAILED", failed.error.code)
    assertEquals(0, failed.status)
    assertNull(failed.error.message)

    val notFound = FakeLauncher(SocialFlow.Redirect, loopback) { SocialCallback.OneTimeToken("o") }
    val rig404 =
        Rig(notFound) { json("""{"code":"NOT_FOUND","message":"m"}""", HttpStatusCode.NotFound) }
    val missing = assertIs<BetterAuthResult.Failure>(rig404.client.signInSocial("g"))
    assertEquals(404, missing.status)
    assertEquals("NOT_FOUND", missing.error.code)
    assertNotNull(notFound.authorizeError)

    val ott = FakeLauncher(SocialFlow.Redirect, loopback) { SocialCallback.OneTimeToken("o") }
    val rigVerify = redirectRig(ott, verifyStatus = HttpStatusCode.BadRequest)
    val bad = assertIs<BetterAuthResult.Failure>(rigVerify.client.signInSocial("g"))
    assertEquals(400, bad.status)
    assertNull(rigVerify.storage.token())
  }

  @Test
  fun t3NoLauncherAndCancellation() = runTest {
    val rig = Rig(null) { json("null") }
    assertFailsWith<IllegalStateException> { rig.client.signInSocial("g") }

    val cancelling =
        FakeLauncher(SocialFlow.Redirect, loopback) { throw CancellationException("c") }
    assertFailsWith<CancellationException> { redirectRig(cancelling).client.signInSocial("g") }
  }

  @Test
  fun t4SignOutRaceAndDistinctStates() = runTest {
    val gate = CompletableDeferred<Unit>()
    val started = CompletableDeferred<Unit>()
    val launcher =
        FakeLauncher(SocialFlow.Redirect, loopback) {
          started.complete(Unit)
          gate.await()
          SocialCallback.OneTimeToken("o")
        }
    val rig = redirectRig(launcher)
    var result: BetterAuthResult<*>? = null
    val job = launch { result = rig.client.signInSocial("google") }
    started.await()
    rig.client.signOut()
    gate.complete(Unit)
    job.join()
    assertIs<BetterAuthResult.Success<*>>(result)
    assertNull(rig.storage.token())
    assertEquals(1L, rig.storage.epoch())

    rig.client.signInSocial("google")
    val (a, b) = launcher.states
    assertNotEquals(a, b)
    assertTrue(a.length >= 32 && a.all { it in '0'..'9' || it in 'a'..'f' }, a)
  }
}
