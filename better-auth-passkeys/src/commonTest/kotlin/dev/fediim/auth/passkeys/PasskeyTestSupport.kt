package dev.fediim.auth.passkeys

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
import kotlinx.serialization.json.Json

internal const val REGISTER_OPTIONS_PATH = "/api/auth/passkey/generate-register-options"
internal const val VERIFY_REGISTRATION_PATH = "/api/auth/passkey/verify-registration"
internal const val AUTH_OPTIONS_PATH = "/api/auth/passkey/generate-authenticate-options"
internal const val VERIFY_AUTHENTICATION_PATH = "/api/auth/passkey/verify-authentication"

internal val wireJson = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
  encodeDefaults = true
}

internal val registerOptionsJson =
    """{"challenge":"Y2hhbGxlbmdlLTE2Ynl0ZXM","rp":{"name":"App","id":"localhost"},""" +
        """"user":{"id":"dXNlci1pZA","name":"Laptop","displayName":"a@b.com"},""" +
        """"pubKeyCredParams":[{"alg":-8,"type":"public-key"},{"alg":-7,"type":"public-key"}],""" +
        """"timeout":60000,"excludeCredentials":[{"id":"ZXhjbHVkZWQ","type":"public-key",""" +
        """"transports":["internal"]}],"authenticatorSelection":{"residentKey":"preferred",""" +
        """"userVerification":"preferred","authenticatorAttachment":"platform"},""" +
        """"attestation":"none","extensions":{"credProps":true}}"""

internal val authOptionsJson =
    """{"challenge":"YXV0aC1jaGFsbGVuZ2U","rpId":"localhost","timeout":60000,""" +
        """"allowCredentials":[],"userVerification":"preferred"}"""

internal val sessionJson =
    """{"session":{"id":"s1","userId":"u1","token":"st"},"user":{"id":"u1","email":"a@b.com"}}"""

/** A client over a [MockEngine] that records every request. */
internal class PasskeyRig(
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

/** Records what it was asked and answers with the configured behaviour. */
internal class FakeAuthenticator(
    override val capability: PasskeyCapability = PasskeyCapability.Supported,
    private val onCreate: suspend (PublicKeyCredentialCreationOptions) -> RegistrationResponse = {
      RegistrationResponse(
          "cred-1",
          "cred-1",
          AttestationResponse("Y2Q", "YW8", listOf("internal")),
          "platform",
      )
    },
    private val onGet: suspend (PublicKeyCredentialRequestOptions) -> AuthenticationResponse = {
      AuthenticationResponse("cred-2", "cred-2", AssertionResponse("Y2Q", "YWQ", "c2ln", "dWg"))
    },
) : PasskeyAuthenticator {
  val created = mutableListOf<PublicKeyCredentialCreationOptions>()
  val got = mutableListOf<PublicKeyCredentialRequestOptions>()

  override suspend fun create(options: PublicKeyCredentialCreationOptions): RegistrationResponse {
    created += options
    return onCreate(options)
  }

  override suspend fun get(options: PublicKeyCredentialRequestOptions): AuthenticationResponse {
    got += options
    return onGet(options)
  }
}
