@file:OptIn(ExperimentalSerializationApi::class)

package dev.fediim.auth.core.model

import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A Better Auth user. Server fields this class does not model (for example a custom `role`) are
 * kept in [additionalFields] and written back at the top level when encoded. See CONSTRAINTS C4.
 */
@Serializable(with = AuthUserSerializer::class)
@KeepGeneratedSerializer
public data class AuthUser(
    val id: String,
    val email: String,
    val name: String? = null,
    val emailVerified: Boolean = false,
    val image: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val additionalFields: JsonObject = JsonObject(emptyMap()),
)

/**
 * A Better Auth session. [token] is a credential and is omitted from [toString]. Unmodelled server
 * fields are kept in [additionalFields].
 */
@Serializable(with = AuthSessionSerializer::class)
@KeepGeneratedSerializer
public data class AuthSession(
    val id: String,
    val userId: String,
    val token: String,
    val expiresAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val additionalFields: JsonObject = JsonObject(emptyMap()),
) {
  override fun toString(): String =
      "AuthSession(id=$id, userId=$userId, expiresAt=$expiresAt, createdAt=$createdAt, " +
          "updatedAt=$updatedAt, ipAddress=$ipAddress, userAgent=$userAgent, " +
          "additionalFields=$additionalFields)"
}

/**
 * The `{session, user}` body of `get-session`. Unmodelled envelope-level keys go to
 * [additionalFields].
 */
@Serializable(with = SessionEnvelopeSerializer::class)
@KeepGeneratedSerializer
public data class SessionEnvelope(
    val session: AuthSession,
    val user: AuthUser,
    val additionalFields: JsonObject = JsonObject(emptyMap()),
)

/**
 * Body of a sign-in or sign-up response. [token] is a credential and is omitted from [toString]; it
 * is null when sign-up creates no session. [user] is null when the server answers a sign-in with
 * `{"twoFactorRedirect":true}`, which then appears in [additionalFields].
 */
@Serializable(with = AuthResponseSerializer::class)
@KeepGeneratedSerializer
public data class AuthResponse(
    val token: String? = null,
    val user: AuthUser? = null,
    val redirect: Boolean? = null,
    val url: String? = null,
    val additionalFields: JsonObject = JsonObject(emptyMap()),
) {
  override fun toString(): String =
      "AuthResponse(user=$user, redirect=$redirect, url=$url, additionalFields=$additionalFields)"
}

/** Error body `{code, message}` of a non-2xx response; either field may be absent. */
@Serializable(with = BetterAuthErrorSerializer::class)
@KeepGeneratedSerializer
public data class BetterAuthError(
    val code: String? = null,
    val message: String? = null,
    val additionalFields: JsonObject = JsonObject(emptyMap()),
)

/** Request body of `sign-up/email`. Null optionals are not sent; [toString] omits [password]. */
@Serializable
public data class SignUpEmailRequest(
    val name: String,
    val email: String,
    val password: String,
    val image: String? = null,
    val callbackURL: String? = null,
) {
  override fun toString(): String =
      "SignUpEmailRequest(name=$name, email=$email, image=$image, callbackURL=$callbackURL)"
}

/** Request body of `sign-in/email`. Null optionals are not sent; [toString] omits [password]. */
@Serializable
public data class SignInEmailRequest(
    val email: String,
    val password: String,
    val rememberMe: Boolean? = null,
    val callbackURL: String? = null,
) {
  override fun toString(): String =
      "SignInEmailRequest(email=$email, rememberMe=$rememberMe, callbackURL=$callbackURL)"
}
