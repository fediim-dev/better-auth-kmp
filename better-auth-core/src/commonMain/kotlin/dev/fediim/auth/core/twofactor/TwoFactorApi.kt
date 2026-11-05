package dev.fediim.auth.core.twofactor

import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.http.OmitBearerKey
import dev.fediim.auth.core.http.SkipTokenCaptureKey
import dev.fediim.auth.core.http.TWO_FACTOR_COOKIE_SUFFIX
import dev.fediim.auth.core.model.AuthResponse
import dev.fediim.auth.core.model.BetterAuthResult
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Better Auth `twoFactor()` plugin endpoints under `/api/auth/two-factor/`, reached through
 * [BetterAuthClient.twoFactor]. TOTP URIs and backup codes are credentials: nothing here logs or
 * stores them.
 *
 * Enrolling: [enable] only prepares a secret; two-factor becomes active after a session-mode
 * [verifyTotp] with a code from it (the server then also rotates the session). The `password`
 * parameters are required for accounts with a password and may be null for accounts without one.
 *
 * Signing in: after a [BetterAuthClient.signInEmail] answered with
 * [SignInResult.TwoFactorRequired], [verifyTotp] and [verifyBackupCode] run in challenge mode: no
 * `Authorization` header is sent (a stored token of another user must not take over), the signed
 * challenge cookie captured at sign-in is sent as `Cookie` when one was captured (browsers use
 * their own cookie jar, which works same-origin), and the new session token is stored from
 * `set-auth-token`, else the body. A wrong code keeps the challenge (the server allows 5 attempts
 * and 600 s); success, the next [BetterAuthClient.signInEmail] or `signOut` end it. The challenge
 * lives in memory only: an abandoned one stays pending until the next sign-in or sign-out, and a
 * session-mode [verifyTotp] (for example during [enable]) fails in that time.
 *
 * Without a pending challenge both calls run in session mode: Bearer is sent and only a rotated
 * `set-auth-token` is stored.
 */
public class TwoFactorApi internal constructor(private val client: BetterAuthClient) {
  private val json = client.json

  /** Creates a TOTP secret and backup codes; see the class doc for activation. */
  public suspend fun enable(
      password: String?,
      issuer: String? = null,
  ): BetterAuthResult<TotpEnrollment> =
      client.send(
          HttpMethod.Post,
          "two-factor/enable",
          body {
            password?.let { put("password", it) }
            issuer?.let { put("issuer", it) }
          },
      ) { _, text ->
        val obj = json.parseToJsonElement(text).jsonObject
        TotpEnrollment(obj.string("totpURI"), obj.strings("backupCodes"))
      }

  /** Turns two-factor off; the server rotates the session and the header token is stored. */
  public suspend fun disable(password: String?): BetterAuthResult<Unit> =
      client.send(
          HttpMethod.Post,
          "two-factor/disable",
          body { password?.let { put("password", it) } },
      ) { _, _ ->
      }

  /**
   * The `otpauth://` URI of the current secret. The URI contains the secret and the result is not
   * redacted: never log or persist it.
   */
  public suspend fun getTotpUri(password: String?): BetterAuthResult<String> =
      client.send(
          HttpMethod.Post,
          "two-factor/get-totp-uri",
          body { password?.let { put("password", it) } },
      ) { _, text ->
        json.parseToJsonElement(text).jsonObject.string("totpURI")
      }

  /** Verifies a TOTP [code] in challenge or session mode; see the class doc. */
  public suspend fun verifyTotp(code: String): BetterAuthResult<AuthResponse> =
      verify("two-factor/verify-totp", code)

  /** Verifies and consumes a one-time backup [code]; modes as for [verifyTotp]. */
  public suspend fun verifyBackupCode(code: String): BetterAuthResult<AuthResponse> =
      verify("two-factor/verify-backup-code", code)

  /**
   * Replaces the backup codes and returns the new ones (not redacted: never log or persist them).
   */
  public suspend fun generateBackupCodes(password: String?): BetterAuthResult<List<String>> =
      client.send(
          HttpMethod.Post,
          "two-factor/generate-backup-codes",
          body { password?.let { put("password", it) } },
      ) { _, text ->
        json.parseToJsonElement(text).jsonObject.strings("backupCodes")
      }

  private suspend fun verify(path: String, code: String): BetterAuthResult<AuthResponse> {
    val body = body { put("code", code) }
    val cookies = client.challengeCookies
    if (!cookies.isTwoFactorPending()) {
      return client.send(HttpMethod.Post, path, body) { _, text -> decodeAuthResponse(text) }
    }
    val (generation, sent) = cookies.twoFactorChallenge()
    val result =
        client.send(
            HttpMethod.Post,
            path,
            body,
            configure = {
              attributes.put(OmitBearerKey, Unit)
              attributes.put(SkipTokenCaptureKey, Unit)
              sent?.let { header(HttpHeaders.Cookie, it) }
            },
        ) { response, text ->
          val parsed = decodeAuthResponse(text)
          // A newer sign-in (or signOut) since this challenge wins: its token is not overwritten.
          cookies.completeTwoFactorIf(generation) {
            client.captureHeaderOrBodyToken(response, parsed.token)
          }
          parsed
        }
    if (result is BetterAuthResult.Success && sent != null) {
      cookies.takeIfEquals(TWO_FACTOR_COOKIE_SUFFIX, sent)
    }
    return result
  }

  private fun decodeAuthResponse(text: String): AuthResponse =
      json.decodeFromString(AuthResponse.serializer(), text)

  private fun body(build: JsonObjectBuilder.() -> Unit): String = buildJsonObject(build).toString()

  private fun JsonObject.string(key: String): String =
      json.decodeFromJsonElement(String.serializer(), requireKey(key))

  private fun JsonObject.strings(key: String): List<String> =
      json.decodeFromJsonElement(ListSerializer(String.serializer()), requireKey(key))

  private fun JsonObject.requireKey(key: String): JsonElement =
      this[key] ?: throw IllegalArgumentException("missing $key")
}
