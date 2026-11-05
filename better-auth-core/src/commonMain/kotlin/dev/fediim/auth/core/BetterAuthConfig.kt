package dev.fediim.auth.core

import io.ktor.http.Url

/**
 * Connection settings for [BetterAuthClient].
 *
 * @property baseUrl Server origin without the `/api/auth` path; a trailing slash is removed. Must
 *   use `https`, or `http` for host `localhost`, `127.0.0.1` or `::1`, or `http` when
 *   [allowInsecureHttpForTesting] is set. Violations, other schemes, userinfo and blank or
 *   unparseable values throw [IllegalArgumentException] from the constructor.
 * @property apiKey Sent as `X-API-Key` on every request when not null; omitted from [toString].
 */
public class BetterAuthConfig(
    baseUrl: String,
    public val apiKey: String? = null,
    public val allowInsecureHttpForTesting: Boolean = false,
) {
  public val baseUrl: String

  init {
    require(Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(baseUrl)) {
      "baseUrl must start with <scheme>://"
    }
    val authority = baseUrl.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
    require(authority.isNotEmpty()) { "baseUrl must have a host" }
    require('@' !in authority) { "baseUrl must not contain userinfo" }
    val url =
        try {
          Url(baseUrl)
        } catch (e: Exception) {
          throw IllegalArgumentException("baseUrl is not a valid URL", e)
        }
    require(url.host.isNotEmpty()) { "baseUrl must have a host" }
    require(url.encodedQuery.isEmpty() && url.fragment.isEmpty()) {
      "baseUrl must not contain a query or fragment"
    }
    val host = url.host.lowercase()
    val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]"
    require(
        url.protocol.name == "https" ||
            (url.protocol.name == "http" && (isLoopback || allowInsecureHttpForTesting))
    ) {
      "baseUrl must use https (http only for localhost or with allowInsecureHttpForTesting)"
    }
    this.baseUrl = baseUrl.trimEnd('/')
  }

  override fun toString(): String = "BetterAuthConfig(baseUrl=$baseUrl)"
}
