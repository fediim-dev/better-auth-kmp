package dev.fediim.auth.core.twofactor

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent

internal const val USER_JSON = """{"id":"u1","email":"a@b.com"}"""
internal const val TWO_FACTOR_BODY = """{"twoFactorRedirect":true,"twoFactorMethods":["totp"]}"""
internal const val SIGN_IN_PATH = "/api/auth/sign-in/email"
internal const val VERIFY_TOTP_PATH = "/api/auth/two-factor/verify-totp"

/** A client over a [MockEngine] that records every request. */
internal class TwoFactorRig(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
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
      )

  fun bodyOf(index: Int): String = (requests[index].body as TextContent).text

  fun cookieOf(index: Int): String? = requests[index].headers[HttpHeaders.Cookie]

  fun authOf(index: Int): String? = requests[index].headers[HttpHeaders.Authorization]

  /** Stores [token] as if an earlier sign-in had. */
  suspend fun preStore(token: String) {
    storage.writeTokenIfEpoch(storage.epoch(), token)
  }
}

/** A JSON answer with one `Set-Cookie` header per entry of [cookies] and the [extra] headers. */
internal fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    cookies: List<String> = emptyList(),
    extra: List<Pair<String, String>> = emptyList(),
): HttpResponseData {
  val headers =
      HeadersBuilder().apply {
        append(HttpHeaders.ContentType, "application/json")
        cookies.forEach { append(HttpHeaders.SetCookie, it) }
        extra.forEach { (name, value) -> append(name, value) }
      }
  return respond(body, status, headers.build())
}
