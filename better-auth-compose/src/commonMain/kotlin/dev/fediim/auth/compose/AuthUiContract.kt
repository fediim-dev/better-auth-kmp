package dev.fediim.auth.compose

import androidx.compose.runtime.Immutable
import dev.fediim.auth.core.model.AuthUser
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult

/** Screen the auth form shows; [BetterAuthViewModel] derives the submit action from it. */
public enum class AuthMode {
  /** Email and password sign-in. */
  SIGN_IN,

  /** Account creation; submit is blocked while [AuthUiState.passwordStrength] is 0. */
  REGISTER,

  /**
   * Waiting for a second factor after a sign-in that asked for one; entered by the view model only.
   */
  TWO_FACTOR_CHALLENGE,
}

/**
 * Everything an auth screen renders. It never holds the password; the view model keeps it
 * privately. [error] is null when there is nothing to show. [passwordStrength] is 0..4 and 0 means
 * too short to register. [lockoutSecondsRemaining] counts down while [isLockedOut] is true.
 */
@Immutable
public data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val name: String = "",
    val passwordStrength: Int = 0,
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val user: AuthUser? = null,
    val twoFactorMethods: List<String> = emptyList(),
    val error: BetterAuthError? = null,
    val isLockedOut: Boolean = false,
    val lockoutSecondsRemaining: Long = 0,
)

/** User actions accepted by [BetterAuthViewModel.dispatch]. */
public sealed interface AuthUiIntent {
  /**
   * Switches between [AuthMode.SIGN_IN] and [AuthMode.REGISTER]; [AuthMode.TWO_FACTOR_CHALLENGE] is
   * ignored. Leaving the challenge drops its methods.
   */
  public data class SetMode(val mode: AuthMode) : AuthUiIntent

  /** Updates [AuthUiState.email]. */
  public data class EmailChanged(val email: String) : AuthUiIntent

  /** Updates [AuthUiState.name], used by registration. */
  public data class NameChanged(val name: String) : AuthUiIntent

  /** Hands the password to the view model; [toString] omits it. */
  public data class PasswordChanged(val password: String) : AuthUiIntent {
    override fun toString(): String = "PasswordChanged(password=<redacted>)"
  }

  /** Signs in or registers, depending on [AuthUiState.mode]. */
  public data object SubmitAuth : AuthUiIntent

  /**
   * Completes a two-factor challenge with a TOTP [code], or a backup code when [useBackupCode];
   * [toString] omits [code].
   */
  public data class SubmitTwoFactor(val code: String, val useBackupCode: Boolean = false) :
      AuthUiIntent {
    override fun toString(): String =
        "SubmitTwoFactor(code=<redacted>, useBackupCode=$useBackupCode)"
  }

  /** Signs in through the social [provider]; see `BetterAuthClient.signInSocial`. */
  public data class SignInSocial(
      val provider: String,
      val callbackUrl: String? = null,
      val scopes: List<String>? = null,
  ) : AuthUiIntent

  /**
   * Signs in with a passkey; fails with [AuthUiErrorCodes.PASSKEY_UNSUPPORTED] without a passkey
   * client.
   */
  public data object SignInPasskey : AuthUiIntent

  /** Loads the session of the stored token, for example after an app restart. */
  public data object RestoreSession : AuthUiIntent

  /** Signs out locally at once and revokes the session on the server. */
  public data object Logout : AuthUiIntent
}

/**
 * One-shot notifications from [BetterAuthViewModel.effects]; [AuthUiState] stays the source of
 * truth.
 */
public sealed interface AuthUiEffect {
  /**
   * A sign-in, registration or second factor completed; [user] is null when the server sent none.
   */
  public data class SignedIn(val user: AuthUser?) : AuthUiEffect

  /** A logout finished locally and its server revoke was attempted. */
  public data object SignedOut : AuthUiEffect

  /**
   * The server did not confirm a sign-out; [result] is never a success. Local state is cleared
   * anyway.
   */
  public data class SessionRevocationFailed(val result: BetterAuthResult<Unit>) : AuthUiEffect

  /** The `onClearUserCache` callback threw [cause]; the sign-out went on. */
  public data class UserCacheClearFailed(val cause: Throwable) : AuthUiEffect
}

/** Codes of [BetterAuthError] values the view models create themselves. */
public object AuthUiErrorCodes {
  /** The request could not be sent or answered. */
  public const val NETWORK_ERROR: String = "NETWORK_ERROR"

  /** A call threw an exception that is not a cancellation. */
  public const val UNEXPECTED: String = "UNEXPECTED"

  /** Registration was blocked because the password scores 0. */
  public const val PASSWORD_TOO_SHORT: String = "PASSWORD_TOO_SHORT"

  /** A passkey sign-in was requested but no passkey client was given. */
  public const val PASSKEY_UNSUPPORTED: String = "PASSKEY_UNSUPPORTED"
}

/** The error to show for a non-success result, or null for [BetterAuthResult.Success]. */
internal fun BetterAuthResult<*>.toUiError(): BetterAuthError? =
    when (this) {
      is BetterAuthResult.Success -> null
      is BetterAuthResult.Failure -> error
      is BetterAuthResult.RateLimited -> error
      is BetterAuthResult.NetworkError -> BetterAuthError(code = AuthUiErrorCodes.NETWORK_ERROR)
    }

/**
 * Password score 0..4: 0 below 8 characters, else 1 plus one point each for upper and lower case
 * letters, a digit, and a character that is neither letter nor digit.
 */
internal fun passwordStrength(password: String): Int {
  if (password.length < 8) return 0
  val points =
      listOf(
              password.any { it.isUpperCase() } && password.any { it.isLowerCase() },
              password.any { it.isDigit() },
              password.any { !it.isLetterOrDigit() },
          )
          .count { it }
  return 1 + points
}
