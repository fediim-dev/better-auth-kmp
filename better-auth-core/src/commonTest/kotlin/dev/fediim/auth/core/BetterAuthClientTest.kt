package dev.fediim.auth.core

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.model.AuthResponse
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SignInEmailRequest
import dev.fediim.auth.core.model.SignUpEmailRequest
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

class BetterAuthClientTest {
  private val userJson = """{"id":"u1","email":"a@b.com","role":"admin"}"""
  private val authBody = """{"redirect":false,"token":"body","user":$userJson}"""
  private val sessionBody =
      """{"session":{"id":"s1","userId":"u1","token":"st"},"user":$userJson}"""
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  private class Rig(
      baseUrl: String = "https://example.com",
      apiKey: String? = null,
      handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
  ) {
    val requests = mutableListOf<HttpRequestData>()
    val storage = SettingsAuthTokenStorage(MapSettings())
    val client =
        BetterAuthClient(
            BetterAuthConfig(baseUrl, apiKey),
            storage,
            MockEngine { request ->
              requests += request
              handler(request)
            },
        )

    fun bodyOf(index: Int): String = (requests[index].body as TextContent).text
  }

  private fun MockRequestHandleScope.ok(
      body: String,
      vararg extra: Pair<String, String>,
  ): HttpResponseData {
    val headers =
        io.ktor.http.HeadersBuilder().apply {
          append(HttpHeaders.ContentType, "application/json")
          extra.forEach { (k, v) -> append(k, v) }
        }
    return respond(body, HttpStatusCode.OK, headers.build())
  }

  private val signIn = SignInEmailRequest("a@b.com", "pw")
  private val signUp = SignUpEmailRequest("N", "a@b.com", "pw")

  @Test
  fun t10SignInCapturesHeaderTokenAndSendsBearer() = runTest {
    val rig = Rig { request ->
      when (request.url.encodedPath) {
        "/api/auth/sign-in/email" -> ok(authBody, "set-auth-token" to "hdr")
        "/api/auth/get-session" -> ok(sessionBody)
        else -> ok("""{"success":true}""")
      }
    }
    val result = rig.client.signInEmail(signIn)
    assertIs<BetterAuthResult.Success<AuthResponse>>(result)
    assertEquals(HttpMethod.Post, rig.requests[0].method)
    assertEquals("https://example.com/api/auth/sign-in/email", rig.requests[0].url.toString())
    assertTrue("\"password\":\"pw\"" in rig.bodyOf(0))
    assertEquals("hdr", rig.storage.token())
    rig.client.getSession()
    assertEquals("Bearer hdr", rig.requests[1].headers[HttpHeaders.Authorization])
    rig.client.signOut()
    assertEquals("Bearer hdr", rig.requests[2].headers[HttpHeaders.Authorization])
    assertNull(rig.storage.token())
  }

  @Test
  fun t11SignUpTokenSources() = runTest {
    var header: String? = "hdr"
    var body = """{"token":"body","user":$userJson}"""
    val rig = Rig { request ->
      if (request.url.encodedPath.endsWith("sign-up/email")) {
        val h = header
        if (h != null) ok(body, "set-auth-token" to h) else ok(body)
      } else ok("null")
    }
    rig.client.signUpEmail(signUp)
    assertEquals("hdr", rig.storage.token())
    rig.client.getSession()
    assertEquals("Bearer hdr", rig.requests.last().headers[HttpHeaders.Authorization])

    rig.storage.clear()
    header = null
    rig.client.signUpEmail(signUp)
    assertEquals("body", rig.storage.token())

    rig.storage.clear()
    body = """{"token":null,"user":$userJson}"""
    assertIs<BetterAuthResult.Success<AuthResponse>>(rig.client.signUpEmail(signUp))
    assertNull(rig.storage.token())

    header = " "
    body = """{"token":" ","user":$userJson}"""
    rig.client.signUpEmail(signUp)
    assertNull(rig.storage.token())
    rig.client.getSession()
    assertNull(rig.requests.last().headers[HttpHeaders.Authorization])
  }

  @Test
  fun t12LogoutRaceDiscardsInFlightSignIn() = runTest {
    val inFlight = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()
    val rig = Rig { request ->
      inFlight.complete(Unit)
      gate.await()
      ok(authBody, "set-auth-token" to "hdr")
    }
    val signingIn = launch { rig.client.signInEmail(signIn) }
    inFlight.await()
    rig.client.signOut()
    gate.complete(Unit)
    signingIn.join()
    assertNull(rig.storage.token())
    assertEquals(1L, rig.storage.epoch())
    assertTrue(rig.requests.none { it.url.encodedPath.endsWith("/sign-out") })
  }

  @Test
  fun t12LogoutRaceDiscardsInFlightBodyToken() = runTest {
    val inFlight = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()
    val rig = Rig { request ->
      inFlight.complete(Unit)
      gate.await()
      ok(authBody)
    }
    val signingIn = launch { rig.client.signInEmail(signIn) }
    inFlight.await()
    rig.client.signOut()
    gate.complete(Unit)
    signingIn.join()
    assertNull(rig.storage.token())
    assertEquals(1L, rig.storage.epoch())
  }

  @Test
  fun redirectsAreNotFollowed() = runTest {
    val rig = Rig {
      respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://evil.example/x"))
    }
    assertEquals(BetterAuthResult.Failure(302, BetterAuthError()), rig.client.getSession())
    assertEquals(1, rig.requests.size)
  }

  @Test
  fun t13ResponseMapping() = runTest {
    var next: suspend MockRequestHandleScope.() -> HttpResponseData = { ok("[1]") }
    val rig = Rig { next() }
    assertEquals(BetterAuthResult.Failure(200, BetterAuthError()), rig.client.signInEmail(signIn))

    next = { ok("""{"twoFactorRedirect":true}""") }
    val twoFactor = rig.client.signInEmail(signIn)
    assertNull((twoFactor as BetterAuthResult.Success).value.user)

    next = { ok("""{"token":"t","user":{"id":"u1","email":"a@b.com","createdAt":"garbage"}}""") }
    assertEquals(BetterAuthResult.Failure(200, BetterAuthError()), rig.client.signInEmail(signIn))

    val errorBody = """{"code":"SLOW","message":"down"}"""
    val parsed = BetterAuthError(code = "SLOW", message = "down")
    next = { respond(errorBody, HttpStatusCode.TooManyRequests, headersOf("Retry-After", "30")) }
    assertEquals(BetterAuthResult.RateLimited(30, parsed), rig.client.signInEmail(signIn))
    for (value in listOf(null, "Wed, 21 Oct 2026 07:28:00 GMT", "-5", "abc")) {
      next = {
        val h = if (value == null) headersOf() else headersOf("Retry-After", value)
        respond(errorBody, HttpStatusCode.TooManyRequests, h)
      }
      assertEquals(
          BetterAuthResult.RateLimited(null, parsed),
          rig.client.signInEmail(signIn),
          "$value",
      )
    }
    next = { respond("{}", HttpStatusCode.TooManyRequests, headersOf("X-Retry-After", "7")) }
    assertEquals(BetterAuthResult.RateLimited(7, BetterAuthError()), rig.client.signInEmail(signIn))

    next = { respond(errorBody, HttpStatusCode.Unauthorized, jsonHeaders) }
    assertEquals(BetterAuthResult.Failure(401, parsed), rig.client.signInEmail(signIn))

    next = { respond("<html>boom</html>", HttpStatusCode.InternalServerError) }
    assertEquals(BetterAuthResult.Failure(500, BetterAuthError()), rig.client.signInEmail(signIn))

    next = { throw IllegalStateException("offline") }
    assertIs<BetterAuthResult.NetworkError>(rig.client.signInEmail(signIn))
  }

  @Test
  fun t14ApiKeyHeader() = runTest {
    val withKey = Rig(apiKey = "k") { ok("null") }
    withKey.client.getSession()
    withKey.client.revokeOtherSessions()
    assertTrue(withKey.requests.all { it.headers["X-API-Key"] == "k" })

    val noKey = Rig { ok("null") }
    noKey.client.getSession()
    assertNull(noKey.requests[0].headers["X-API-Key"])
    assertNull(noKey.requests[0].headers[HttpHeaders.Authorization])
  }

  @Test
  fun t15GetSession() = runTest {
    var body = "null"
    var header: String? = null
    val rig = Rig { if (header != null) ok(body, "set-auth-token" to header!!) else ok(body) }
    assertEquals(BetterAuthResult.Success(null), rig.client.getSession())

    body = sessionBody
    val envelope = (rig.client.getSession() as BetterAuthResult.Success).value
    assertEquals(JsonPrimitive("admin"), envelope!!.user.additionalFields["role"])

    rig.storage.writeTokenIfEpoch(0, "old")
    header = "rotated"
    rig.client.getSession()
    assertEquals("rotated", rig.storage.token())
  }

  @Test
  fun t16SessionManagement() = runTest {
    val rig =
        Rig(baseUrl = "https://example.com/") { request ->
          if (request.url.encodedPath.endsWith("list-sessions")) {
            ok("""[{"id":"s1","userId":"u1","token":"a"},{"id":"s2","userId":"u1","token":"b"}]""")
          } else ok("""{"status":true}""")
        }
    rig.storage.writeTokenIfEpoch(0, "tok")
    val sessions = (rig.client.listSessions() as BetterAuthResult.Success).value
    assertEquals(listOf("s1", "s2"), sessions.map { it.id })
    rig.client.revokeSession("s2")
    rig.client.revokeOtherSessions()
    assertEquals(HttpMethod.Get, rig.requests[0].method)
    assertEquals("https://example.com/api/auth/list-sessions", rig.requests[0].url.toString())
    assertEquals("""{"token":"s2"}""", rig.bodyOf(1))
    assertEquals("/api/auth/revoke-session", rig.requests[1].url.encodedPath)
    assertEquals("/api/auth/revoke-other-sessions", rig.requests[2].url.encodedPath)
    assertEquals(HttpMethod.Post, rig.requests[2].method)
    assertTrue(rig.requests.all { it.headers[HttpHeaders.Authorization] == "Bearer tok" })
  }

  @Test
  fun t17SignOutAlwaysClearsLocalState() = runTest {
    var mode = "500"
    val rig = Rig { request ->
      when (mode) {
        "500" -> respond("{}", HttpStatusCode.InternalServerError)
        "throw" -> throw IllegalStateException("offline")
        else -> ok("""{"success":true}""", "set-auth-token" to "revived")
      }
    }
    rig.storage.writeTokenIfEpoch(0, "tok")
    assertIs<BetterAuthResult.Failure>(rig.client.signOut())
    assertNull(rig.storage.token())
    assertEquals(1L, rig.storage.epoch())

    rig.storage.writeTokenIfEpoch(1, "tok")
    mode = "throw"
    assertIs<BetterAuthResult.NetworkError>(rig.client.signOut())
    assertNull(rig.storage.token())
    assertEquals(2L, rig.storage.epoch())

    rig.storage.writeTokenIfEpoch(2, "tok")
    mode = "ok"
    assertIs<BetterAuthResult.Success<Unit>>(rig.client.signOut())
    assertNull(rig.storage.token())
    assertEquals("Bearer tok", rig.requests.last().headers[HttpHeaders.Authorization])
  }
}
