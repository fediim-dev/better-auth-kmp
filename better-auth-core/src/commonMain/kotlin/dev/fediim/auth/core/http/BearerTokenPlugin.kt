package dev.fediim.auth.core.http

import dev.fediim.auth.core.storage.AuthTokenStorage
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey

internal class BearerPluginConfig {
  lateinit var storage: AuthTokenStorage
  var apiKey: String? = null

  /** `scheme://host[:port]` of the server, sent as `Origin` on requests that carry a `Cookie`. */
  var origin: String? = null
}

internal const val AUTH_TOKEN_HEADER = "set-auth-token"

/** Request attribute holding the storage epoch read when the request started. */
internal val StartEpochKey = AttributeKey<Long>("BetterAuthStartEpoch")

/** Request attribute that disables `set-auth-token` capture for that request. */
internal val SkipTokenCaptureKey = AttributeKey<Unit>("BetterAuthSkipTokenCapture")

/** Request attribute that stops the stored token from being sent as `Authorization`. */
internal val OmitBearerKey = AttributeKey<Unit>("BetterAuthOmitBearer")

internal val BearerTokenPlugin =
    createClientPlugin("BetterAuthBearer", ::BearerPluginConfig) {
      val storage = pluginConfig.storage
      val apiKey = pluginConfig.apiKey
      val origin = pluginConfig.origin

      onRequest { request, _ ->
        request.attributes.put(StartEpochKey, storage.epoch())
        if (
            !request.headers.contains(HttpHeaders.Authorization) &&
                !request.attributes.contains(OmitBearerKey)
        ) {
          storage.token()?.let { request.header(HttpHeaders.Authorization, "Bearer $it") }
        }
        // better-auth rejects a cookie-bearing POST without a trusted Origin (CSRF check); the
        // server's own origin is always trusted (plan #6 L22).
        if (origin != null && request.headers.contains(HttpHeaders.Cookie)) {
          if (!request.headers.contains(HttpHeaders.Origin))
              request.header(HttpHeaders.Origin, origin)
        }
        if (apiKey != null) request.header("X-API-Key", apiKey)
      }

      onResponse { response ->
        val attributes = response.call.request.attributes
        if (attributes.contains(SkipTokenCaptureKey)) return@onResponse
        val token = response.headers[AUTH_TOKEN_HEADER]
        val epoch = attributes.getOrNull(StartEpochKey)
        if (!token.isNullOrBlank() && epoch != null) storage.writeTokenIfEpoch(epoch, token)
      }
    }
