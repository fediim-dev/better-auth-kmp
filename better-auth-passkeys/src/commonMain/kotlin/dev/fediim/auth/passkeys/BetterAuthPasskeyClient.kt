package dev.fediim.auth.passkeys

import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.authJson
import dev.fediim.auth.core.authRequest
import dev.fediim.auth.core.captureChallengeCookie
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SessionEnvelope
import dev.fediim.auth.core.takeChallengeCookie
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PASSKEY_COOKIE_SUFFIX = ".better-auth-passkey"
private const val UNSUPPORTED = "PASSKEY_UNSUPPORTED"
private const val FAILED = "PASSKEY_FAILED"

/**
 * Better Auth `passkey()` plugin calls under `/api/auth/passkey/` (server package
 * `@better-auth/passkey`), with the platform ceremony delegated to [authenticator]. Every call
 * returns a [BetterAuthResult]; a local ceremony failure is `Failure(0, code)` with code
 * `PASSKEY_UNSUPPORTED` (no request is sent), `cancelled`, `no_credential` or `PASSKEY_FAILED`.
 *
 * The signed challenge cookie from an options call is kept in memory by [client] and replayed once,
 * on the matching verify call (CONSTRAINTS C5); browsers use their own cookie jar instead. The
 * session of [signInWithPasskey] is stored by [client] from `set-auth-token`.
 */
public class BetterAuthPasskeyClient(
    private val client: BetterAuthClient,
    private val authenticator: PasskeyAuthenticator,
) {
  private val json = Json(from = client.authJson) { encodeDefaults = true }

  /**
   * Registers a passkey for the signed-in user (the session must be fresh). [name] labels it and
   * [authenticatorAttachment] (`platform` or `cross-platform`) narrows the authenticator.
   */
  public suspend fun registerPasskey(
      name: String? = null,
      authenticatorAttachment: String? = null,
  ): BetterAuthResult<Passkey> {
    if (authenticator.capability == PasskeyCapability.Unsupported) return localFailure(UNSUPPORTED)
    val options =
        client.authRequest(
            HttpMethod.Get,
            "passkey/generate-register-options",
            configure = {
              name?.let { parameter("name", it) }
              authenticatorAttachment?.let { parameter("authenticatorAttachment", it) }
            },
        ) { response, text ->
          client.captureChallengeCookie(response, PASSKEY_COOKIE_SUFFIX)
          json.decodeFromString(PublicKeyCredentialCreationOptions.serializer(), text)
        }
    if (options !is BetterAuthResult.Success) return options.asFailure()
    val registration = ceremony { authenticator.create(options.value) }
    if (registration !is BetterAuthResult.Success) return registration.asFailure()
    val body = buildJsonObject {
      put(
          "response",
          json.encodeToJsonElement(RegistrationResponse.serializer(), registration.value),
      )
      name?.let { put("name", it) }
    }
        .toString()
    val cookie = client.takeChallengeCookie(PASSKEY_COOKIE_SUFFIX)
    return client.authRequest(
        HttpMethod.Post,
        "passkey/verify-registration",
        body,
        configure = { cookie?.let { header(HttpHeaders.Cookie, it) } },
    ) { _, text ->
      json.decodeFromString(Passkey.serializer(), text)
    }
  }

  /** Signs in with a passkey; on success the session token is stored like any other sign-in. */
  public suspend fun signInWithPasskey(): BetterAuthResult<SessionEnvelope> {
    if (authenticator.capability == PasskeyCapability.Unsupported) return localFailure(UNSUPPORTED)
    val options =
        client.authRequest(HttpMethod.Get, "passkey/generate-authenticate-options") { response, text
          ->
          client.captureChallengeCookie(response, PASSKEY_COOKIE_SUFFIX)
          json.decodeFromString(PublicKeyCredentialRequestOptions.serializer(), text)
        }
    if (options !is BetterAuthResult.Success) return options.asFailure()
    val assertion = ceremony { authenticator.get(options.value) }
    if (assertion !is BetterAuthResult.Success) return assertion.asFailure()
    val body = buildJsonObject {
      put(
          "response",
          json.encodeToJsonElement(AuthenticationResponse.serializer(), assertion.value),
      )
    }
        .toString()
    val cookie = client.takeChallengeCookie(PASSKEY_COOKIE_SUFFIX)
    return client.authRequest(
        HttpMethod.Post,
        "passkey/verify-authentication",
        body,
        configure = { cookie?.let { header(HttpHeaders.Cookie, it) } },
    ) { _, text ->
      json.decodeFromString(SessionEnvelope.serializer(), text)
    }
  }

  /** Lists the signed-in user's passkeys. */
  public suspend fun listPasskeys(): BetterAuthResult<List<Passkey>> =
      client.authRequest(HttpMethod.Get, "passkey/list-user-passkeys") { _, text ->
        json.decodeFromString(ListSerializer(Passkey.serializer()), text)
      }

  /** Deletes the passkey [id]; an unknown or foreign id is a 404 failure. */
  public suspend fun deletePasskey(id: String): BetterAuthResult<Unit> =
      client.authRequest(
          HttpMethod.Post,
          "passkey/delete-passkey",
          buildJsonObject { put("id", id) }.toString(),
      ) { _, _ ->
      }

  private suspend fun <T> ceremony(block: suspend () -> T): BetterAuthResult<T> =
      try {
        BetterAuthResult.Success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: PasskeyException) {
        localFailure(e.code)
      } catch (e: Exception) {
        localFailure(FAILED)
      }

  private fun localFailure(code: String): BetterAuthResult.Failure =
      BetterAuthResult.Failure(0, BetterAuthError(code = code))

  /** Only called on non-Success results, which all carry no value. */
  @Suppress("UNCHECKED_CAST")
  private fun BetterAuthResult<*>.asFailure(): BetterAuthResult<Nothing> =
      this as BetterAuthResult<Nothing>
}
