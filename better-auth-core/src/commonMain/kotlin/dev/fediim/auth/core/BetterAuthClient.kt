package dev.fediim.auth.core

import dev.fediim.auth.core.http.AUTH_TOKEN_HEADER
import dev.fediim.auth.core.http.BearerTokenPlugin
import dev.fediim.auth.core.http.SkipTokenCaptureKey
import dev.fediim.auth.core.http.StartEpochKey
import dev.fediim.auth.core.model.AuthResponse
import dev.fediim.auth.core.model.AuthSession
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SessionEnvelope
import dev.fediim.auth.core.model.SignInEmailRequest
import dev.fediim.auth.core.model.SignUpEmailRequest
import dev.fediim.auth.core.social.SocialAuthLauncher
import dev.fediim.auth.core.social.SocialCallback
import dev.fediim.auth.core.social.socialComplete
import dev.fediim.auth.core.social.socialSignIn
import dev.fediim.auth.core.storage.AuthTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client for Better Auth email/password and session endpoints under `<baseUrl>/api/auth`.
 *
 * Every call returns a [BetterAuthResult] and never throws except for coroutine cancellation and,
 * in [signOut], exceptions thrown by [AuthTokenStorage] while reading or clearing the token.
 * Requests send `Authorization: Bearer <token>` from [AuthTokenStorage] (unless the request already
 * carries one) and `X-API-Key` when [BetterAuthConfig.apiKey] is set. A non-blank `set-auth-token`
 * response header is stored only if the storage epoch is unchanged since the request started;
 * sign-in and sign-up fall back to the body `token` under the same rule when the header is absent.
 * A sign-in or sign-up that is fenced out by [signOut] still returns [BetterAuthResult.Success] but
 * its token is discarded. 3xx responses are not followed.
 *
 * [signInSocial] needs the `socialAuthLauncher` constructor argument and throws
 * [IllegalStateException] without one; [completeSocialSignIn] does not.
 *
 * Call [close] when finished; it releases the HTTP client and the engine it created. An engine
 * passed to the constructor stays owned by the caller.
 */
public class BetterAuthClient(
    internal val config: BetterAuthConfig,
    internal val tokenStorage: AuthTokenStorage,
    engine: HttpClientEngine? = null,
    internal val socialAuthLauncher: SocialAuthLauncher? = null,
) : AutoCloseable {
  internal val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  private val http: HttpClient =
      engine?.let { HttpClient(it) { configure() } } ?: HttpClient { configure() }

  private fun HttpClientConfig<*>.configure() {
    expectSuccess = false
    followRedirects = false
    install(BearerTokenPlugin) {
      storage = tokenStorage
      apiKey = config.apiKey
    }
  }

  /** Creates an account; see [BetterAuthClient] for token capture. */
  public suspend fun signUpEmail(request: SignUpEmailRequest): BetterAuthResult<AuthResponse> =
      authCall("sign-up/email", json.encodeToString(SignUpEmailRequest.serializer(), request))

  /**
   * Signs in with email and password. A two-factor challenge answers [BetterAuthResult.Success]
   * with a null [AuthResponse.user] and `twoFactorRedirect` in its additional fields.
   */
  public suspend fun signInEmail(request: SignInEmailRequest): BetterAuthResult<AuthResponse> =
      authCall("sign-in/email", json.encodeToString(SignInEmailRequest.serializer(), request))

  /** Fetches the current session; `Success(null)` means the server reports no session. */
  public suspend fun getSession(): BetterAuthResult<SessionEnvelope?> =
      send(HttpMethod.Get, "get-session") { _, text ->
        json.decodeFromString(SessionEnvelope.serializer().nullable, text)
      }

  /**
   * Clears local token state first (advancing the storage epoch so no in-flight response can
   * restore a token), then asks the server to end the session with the previously stored token. The
   * request is skipped when no token was stored. Local state is cleared even when the server call
   * fails, and the server response never stores a token. If the stored token rotates between the
   * read and the clear, the server call revokes the token that was read; the rotated token is
   * discarded locally but not revoked on the server.
   */
  public suspend fun signOut(): BetterAuthResult<Unit> {
    val token = tokenStorage.token()
    tokenStorage.clear()
    if (token == null) return BetterAuthResult.Success(Unit)
    return send(
        HttpMethod.Post,
        "sign-out",
        body = "{}",
        configure = {
          header(HttpHeaders.Authorization, "Bearer $token")
          attributes.put(SkipTokenCaptureKey, Unit)
        },
    ) { _, _ ->
    }
  }

  /** Lists the signed-in user's active sessions. */
  public suspend fun listSessions(): BetterAuthResult<List<AuthSession>> =
      send(HttpMethod.Get, "list-sessions") { _, text ->
        json.decodeFromString(ListSerializer(AuthSession.serializer()), text)
      }

  /** Revokes the session identified by its [token]. */
  public suspend fun revokeSession(token: String): BetterAuthResult<Unit> =
      send(
          HttpMethod.Post,
          "revoke-session",
          body = buildJsonObject { put("token", token) }.toString(),
      ) { _, _ ->
      }

  /** Revokes every session of the signed-in user except the current one. */
  public suspend fun revokeOtherSessions(): BetterAuthResult<Unit> =
      send(HttpMethod.Post, "revoke-other-sessions", body = "{}") { _, _ -> }

  /**
   * Signs in through the social [provider] using the [SocialAuthLauncher] given to the constructor.
   *
   * Native and redirect flows POST `sign-in/social` and open the returned provider URL; the web
   * popup flow opens `oauth-popup/start`. A [callbackUrl] replaces the launcher's receiver only
   * when its origin (scheme, for custom schemes) equals the receiver's; otherwise the launcher's
   * receiver is used so it never waits on a URL it cannot receive. [scopes] are sent unchanged.
   *
   * Returns [BetterAuthResult.Failure] with status 0 and the provider's `error` code when the user
   * denies access, `timeout`, `cancelled`, `superseded` or `state_mismatch` when the flow does not
   * finish, `SOCIAL_LAUNCH_FAILED` when the launcher throws, and `SESSION_NOT_FOUND` when a popup
   * token maps to no session. A non-2xx `sign-in/social` answer is returned as-is.
   *
   * @throws IllegalStateException when the client was created without a launcher.
   */
  public suspend fun signInSocial(
      provider: String,
      callbackUrl: String? = null,
      scopes: List<String>? = null,
  ): BetterAuthResult<AuthSession> {
    return socialSignIn(provider, callbackUrl, scopes)
  }

  /**
   * Finishes a flow the caller ran itself, for example after
   * `WebPopupSocialAuthLauncher.consumeRedirectCallback`. A [SocialCallback.OneTimeToken] is
   * exchanged at `one-time-token/verify`, a [SocialCallback.SessionToken] is checked with
   * `get-session`, and a [SocialCallback.Error] becomes a status 0 failure with its code. The token
   * is stored only if [signOut] did not run since this call started.
   */
  public suspend fun completeSocialSignIn(callback: SocialCallback): BetterAuthResult<AuthSession> =
      socialComplete(callback)

  /** Closes the underlying HTTP client. */
  override fun close() {
    http.close()
  }

  private suspend fun authCall(path: String, body: String): BetterAuthResult<AuthResponse> =
      send(HttpMethod.Post, path, body) { response, text ->
        val parsed = json.decodeFromString(AuthResponse.serializer(), text)
        captureBodyToken(response, parsed.token)
        parsed
      }

  private suspend fun captureBodyToken(response: HttpResponse, token: String?) {
    if (!response.headers[AUTH_TOKEN_HEADER].isNullOrBlank() || token.isNullOrBlank()) return
    val epoch = response.call.request.attributes.getOrNull(StartEpochKey) ?: return
    tokenStorage.writeTokenIfEpoch(epoch, token)
  }

  internal suspend fun <T> send(
      method: HttpMethod,
      path: String,
      body: String? = null,
      configure: HttpRequestBuilder.() -> Unit = {},
      decode: suspend (HttpResponse, String) -> T,
  ): BetterAuthResult<T> {
    try {
      val response = http.request {
        this.method = method
        url("${config.baseUrl}/api/auth/$path")
        if (body != null) setBody(TextContent(body, ContentType.Application.Json))
        configure()
      }
      val text = response.bodyAsText()
      val status = response.status
      return when {
        status.isSuccess() ->
            try {
              BetterAuthResult.Success(decode(response, text))
            } catch (e: IllegalArgumentException) {
              // SerializationException is an IllegalArgumentException; Instant.parse throws the
              // base type.
              BetterAuthResult.Failure(status.value, BetterAuthError())
            }
        status.value == 429 ->
            BetterAuthResult.RateLimited(parseRetryAfter(response), parseError(text))
        else -> BetterAuthResult.Failure(status.value, parseError(text))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      return BetterAuthResult.NetworkError(e)
    }
  }

  private fun parseError(text: String): BetterAuthError =
      try {
        json.decodeFromString(BetterAuthError.serializer(), text)
      } catch (e: SerializationException) {
        BetterAuthError()
      }

  /** Delta-seconds only; better-auth's rate limiter sends `X-Retry-After`. */
  private fun parseRetryAfter(response: HttpResponse): Long? =
      (response.headers[HttpHeaders.RetryAfter] ?: response.headers["X-Retry-After"])
          ?.trim()
          ?.toLongOrNull()
          ?.takeIf { it >= 0 }
}
