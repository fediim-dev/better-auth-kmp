package dev.fediim.auth.core

import dev.fediim.auth.core.model.BetterAuthResult
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json

/**
 * Marks the SDK-internal surface that sibling modules (better-auth-passkeys) build on. It is not
 * meant for apps, carries no compatibility promise and may change or disappear in any release.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalBetterAuthApi

/**
 * Sends `<baseUrl>/api/auth/<path>` through this client's pipeline (Bearer, `X-API-Key`,
 * `set-auth-token` capture) and maps the answer like every other call: [decode] runs on 2xx only,
 * an [IllegalArgumentException] from it becomes `Failure(status, BetterAuthError())`.
 */
@InternalBetterAuthApi
public suspend fun <T> BetterAuthClient.authRequest(
    method: HttpMethod,
    path: String,
    body: String? = null,
    configure: HttpRequestBuilder.() -> Unit = {},
    decode: suspend (HttpResponse, String) -> T,
): BetterAuthResult<T> = send(method, path, body, configure, decode)

/** The lenient [Json] this client decodes with (unknown keys ignored, nulls omitted). */
@InternalBetterAuthApi
public val BetterAuthClient.authJson: Json
  get() = json

/**
 * Remembers the signed challenge cookie whose name ends with [nameSuffix] (`.two_factor` or
 * `.better-auth-passkey`) from [response], when it belongs to a request that started in the current
 * sign-in epoch. Returns true when a cookie was stored; it replaces an older one.
 *
 * @throws IllegalArgumentException for any other suffix, so a session cookie is never captured.
 */
@InternalBetterAuthApi
public suspend fun BetterAuthClient.captureChallengeCookie(
    response: HttpResponse,
    nameSuffix: String,
): Boolean = challengeCookies.capture(response, nameSuffix)

/**
 * Removes and returns the remembered challenge cookie for [nameSuffix] as `name=value`, ready to
 * send as the `Cookie` header of the completing call, or null when none is held.
 *
 * @throws IllegalArgumentException for a suffix outside the allowlist.
 */
@InternalBetterAuthApi
public suspend fun BetterAuthClient.takeChallengeCookie(nameSuffix: String): String? =
    challengeCookies.take(nameSuffix)
