package dev.fediim.auth.core.twofactor

import dev.fediim.auth.core.model.AuthResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Outcome of a sign-in, read from an [AuthResponse] with [signInResult]. */
public sealed interface SignInResult {
  /** The server created a session; [response] is the sign-in answer. */
  public data class SignedIn(val response: AuthResponse) : SignInResult

  /** The server wants a second factor first; [methods] lists the accepted ones, e.g. `totp`. */
  public data class TwoFactorRequired(val methods: List<String>) : SignInResult
}

/**
 * Reads this sign-in answer: [SignInResult.TwoFactorRequired] when `twoFactorRedirect` is true
 * (with the offered `twoFactorMethods`, empty when absent), otherwise [SignInResult.SignedIn].
 */
public val AuthResponse.signInResult: SignInResult
  get() {
    val redirect = (additionalFields["twoFactorRedirect"] as? JsonPrimitive)?.booleanOrNull
    if (redirect != true) return SignInResult.SignedIn(this)
    val methods =
        (additionalFields["twoFactorMethods"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
            .orEmpty()
    return SignInResult.TwoFactorRequired(methods)
  }

/**
 * Result of [TwoFactorApi.enable]: the `otpauth://` URI for an authenticator app and the one-time
 * [backupCodes]. Both are credentials, so [toString] shows neither.
 */
public data class TotpEnrollment(val totpUri: String, val backupCodes: List<String>) {
  override fun toString(): String = "TotpEnrollment(backupCodes=${backupCodes.size})"
}
