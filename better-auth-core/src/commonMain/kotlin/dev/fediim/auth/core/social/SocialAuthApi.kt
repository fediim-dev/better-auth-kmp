package dev.fediim.auth.core.social

import dev.fediim.auth.core.model.BetterAuthResult
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** `type` of the postMessage the server's popup completion page sends. */
internal const val OAUTH_POPUP_MESSAGE_TYPE = "better-auth:oauth-popup"

/** Query parameter carrying the one-time token on a redirect callback. */
internal const val OTT_PARAM = "ott"

/** Local error code: the launcher threw (message only, never URLs). */
internal const val SOCIAL_LAUNCH_FAILED = "SOCIAL_LAUNCH_FAILED"

/** Local error code: the popup session token no longer maps to a session. */
internal const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"

/** Body of `POST /sign-in/social`; [disableRedirect] has no default so it is always sent. */
@Serializable
internal data class SocialSignInRequest(
    val provider: String,
    val callbackURL: String,
    val errorCallbackURL: String,
    val disableRedirect: Boolean,
    val scopes: List<String>? = null,
)

/** Carries a non-success [result] out of a launcher's `authorize` so the launcher sees it. */
internal class SocialRequestException(val result: BetterAuthResult<Nothing>) :
    RuntimeException("social sign-in request failed")

/** A fresh single-use CSRF nonce: 32 hex characters from a CSPRNG (`Uuid.random`). */
internal fun newSocialState(): String = Uuid.random().toHexString()

/** [receiver] with `state=<state>` appended to its query (before any fragment). */
internal fun withState(receiver: String, state: String): String {
  val base = receiver.substringBefore('#')
  val fragment = receiver.substring(base.length)
  val separator = if ('?' in base) "&" else "?"
  return "$base${separator}state=${state.encodeURLParameter()}$fragment"
}

/** Server URL the popup navigates to; scopes are comma-joined as `oauth-popup` expects. */
internal fun popupStartUrl(
    baseUrl: String,
    provider: String,
    popupOrigin: String,
    nonce: String,
    scopes: List<String>?,
    callbackUrl: String?,
): String =
    URLBuilder("$baseUrl/api/auth/oauth-popup/start")
        .apply {
          parameters.append("provider", provider)
          parameters.append("popupOrigin", popupOrigin)
          parameters.append("popupNonce", nonce)
          if (!scopes.isNullOrEmpty()) parameters.append("scopes", scopes.joinToString(","))
          if (callbackUrl != null) parameters.append("callbackURL", callbackUrl)
        }
        .buildString()

/**
 * Reads a redirect callback: null unless the query `state` equals [expectedState]; then
 * [SocialCallback.OneTimeToken] for a non-blank `ott`, else [SocialCallback.Error] for `error`.
 */
internal fun parseRedirectCallback(url: String, expectedState: String): SocialCallback? {
  val parameters =
      try {
        Url(url).parameters
      } catch (e: Exception) {
        return null
      }
  if (expectedState.isEmpty() || parameters["state"] != expectedState) return null
  parameters[OTT_PARAM]
      ?.takeIf { it.isNotBlank() }
      ?.let {
        return SocialCallback.OneTimeToken(it)
      }
  val error = parameters["error"]?.takeIf { it.isNotBlank() } ?: return null
  return SocialCallback.Error(error, parameters["error_description"])
}

/**
 * Reads a popup `message` event: null unless [origin] equals [expectedOrigin] exactly, the JSON
 * `type` is the popup type and `nonce` equals [expectedNonce].
 */
internal fun parsePopupMessage(
    origin: String,
    dataJson: String?,
    expectedOrigin: String,
    expectedNonce: String,
): SocialCallback? {
  if (dataJson == null || origin != expectedOrigin) return null
  val data =
      try {
        Json.parseToJsonElement(dataJson).jsonObject
      } catch (e: IllegalArgumentException) {
        return null
      }
  if (data.string("type") != OAUTH_POPUP_MESSAGE_TYPE || data.string("nonce") != expectedNonce) {
    return null
  }
  data
      .string("token")
      ?.takeIf { it.isNotBlank() }
      ?.let {
        return SocialCallback.SessionToken(it)
      }
  val error = data["error"] as? JsonObject ?: return null
  val code = error.string("code")?.takeIf { it.isNotBlank() } ?: return null
  return SocialCallback.Error(code, error.string("description"))
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

/**
 * Normalized `scheme://host[:port]` of an http(s) [url] (lower-case, default port omitted, IPv6
 * bracketed), `scheme:` for any other scheme, or null if [url] is unparseable.
 */
internal fun originOf(url: String): String? {
  val parsed =
      try {
        Url(url)
      } catch (e: Exception) {
        return null
      }
  val scheme = parsed.protocol.name.lowercase()
  if (scheme != "http" && scheme != "https") return "$scheme:"
  val host = parsed.host.lowercase().let { if (':' in it && !it.startsWith("[")) "[$it]" else it }
  val port = parsed.specifiedPort
  val suffix = if (port > 0 && port != parsed.protocol.defaultPort) ":$port" else ""
  return "$scheme://$host$suffix"
}

/** `sessionStorage` key holding the pending state across the web redirect fallback. */
internal const val WEB_STATE_STORAGE_KEY = "better-auth.social.state"
