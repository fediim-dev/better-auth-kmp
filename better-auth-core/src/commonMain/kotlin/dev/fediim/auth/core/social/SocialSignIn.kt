package dev.fediim.auth.core.social

import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.http.AUTH_TOKEN_HEADER
import dev.fediim.auth.core.http.SkipTokenCaptureKey
import dev.fediim.auth.core.model.AuthResponse
import dev.fediim.auth.core.model.AuthSession
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SessionEnvelope
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Body of [BetterAuthClient.signInSocial]; see its contract. */
internal suspend fun BetterAuthClient.socialSignIn(
    provider: String,
    callbackUrl: String?,
    scopes: List<String>?,
): BetterAuthResult<AuthSession> {
  val launcher =
      socialAuthLauncher
          ?: throw IllegalStateException("signInSocial requires a socialAuthLauncher")
  return withStartEpoch { startEpoch ->
    val state = newSocialState()
    val callback =
        try {
          launcher.launch(state, config.origin) { flow, receiver ->
            when (flow) {
              SocialFlow.Redirect ->
                  redirectAuthorize(provider, receiver, callbackUrl, state, scopes)
              SocialFlow.Popup ->
                  popupStartUrl(config.baseUrl, provider, receiver, state, scopes, callbackUrl)
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: SocialRequestException) {
          return@withStartEpoch e.result
        } catch (e: Exception) {
          return@withStartEpoch BetterAuthResult.Failure(
              0,
              BetterAuthError(code = SOCIAL_LAUNCH_FAILED),
          )
        }
    finishSocial(callback, startEpoch)
  }
}

/** Body of [BetterAuthClient.completeSocialSignIn]; see its contract. */
internal suspend fun BetterAuthClient.socialComplete(
    callback: SocialCallback
): BetterAuthResult<AuthSession> = withStartEpoch { finishSocial(callback, it) }

private suspend fun BetterAuthClient.withStartEpoch(
    block: suspend (startEpoch: Long) -> BetterAuthResult<AuthSession>
): BetterAuthResult<AuthSession> {
  val epoch =
      try {
        tokenStorage.epoch()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        return BetterAuthResult.NetworkError(e)
      }
  return block(epoch)
}

private suspend fun BetterAuthClient.redirectAuthorize(
    provider: String,
    receiver: String,
    callbackUrl: String?,
    state: String,
    scopes: List<String>?,
): String {
  val receiverOrigin = originOf(receiver)
  val target =
      if (
          callbackUrl != null && receiverOrigin != null && originOf(callbackUrl) == receiverOrigin
      ) {
        withState(callbackUrl, state)
      } else {
        receiver
      }
  val request =
      SocialSignInRequest(provider, target, target, disableRedirect = true, scopes = scopes)
  val result =
      send(
          HttpMethod.Post,
          "sign-in/social",
          json.encodeToString(SocialSignInRequest.serializer(), request),
      ) { _, text ->
        json.decodeFromString(AuthResponse.serializer(), text).url
            ?: throw IllegalArgumentException("sign-in/social returned no url")
      }
  when (result) {
    is BetterAuthResult.Success -> return result.value
    is BetterAuthResult.Failure -> throw SocialRequestException(result)
    is BetterAuthResult.RateLimited -> throw SocialRequestException(result)
    is BetterAuthResult.NetworkError -> throw SocialRequestException(result)
  }
}

private suspend fun BetterAuthClient.finishSocial(
    callback: SocialCallback,
    startEpoch: Long,
): BetterAuthResult<AuthSession> =
    when (callback) {
      is SocialCallback.Error ->
          BetterAuthResult.Failure(
              0,
              BetterAuthError(code = callback.code, message = callback.description),
          )
      is SocialCallback.OneTimeToken ->
          send(
              HttpMethod.Post,
              "one-time-token/verify",
              buildJsonObject { put("token", callback.token) }.toString(),
              configure = { attributes.put(SkipTokenCaptureKey, Unit) },
          ) { response, text ->
            val session = json.decodeFromString(SessionEnvelope.serializer(), text).session
            val token = response.headers[AUTH_TOKEN_HEADER]?.takeIf { it.isNotBlank() }
            tokenStorage.writeTokenIfEpoch(startEpoch, token ?: session.token)
            session
          }
      is SocialCallback.SessionToken ->
          send(
                  HttpMethod.Get,
                  "get-session",
                  configure = {
                    header(HttpHeaders.Authorization, "Bearer ${callback.token}")
                    attributes.put(SkipTokenCaptureKey, Unit)
                  },
              ) { _, text ->
                json.decodeFromString(SessionEnvelope.serializer().nullable, text)?.session?.also {
                  tokenStorage.writeTokenIfEpoch(startEpoch, callback.token)
                }
              }
              .orSessionNotFound()
    }

private fun BetterAuthResult<AuthSession?>.orSessionNotFound(): BetterAuthResult<AuthSession> =
    when (this) {
      is BetterAuthResult.Success ->
          value?.let { BetterAuthResult.Success(it) }
              ?: BetterAuthResult.Failure(0, BetterAuthError(code = SESSION_NOT_FOUND))
      is BetterAuthResult.Failure -> this
      is BetterAuthResult.RateLimited -> this
      is BetterAuthResult.NetworkError -> this
    }
