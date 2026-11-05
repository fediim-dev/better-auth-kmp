package dev.fediim.auth.compose

import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.model.AuthUser
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SignInEmailRequest
import dev.fediim.auth.core.model.SignUpEmailRequest
import dev.fediim.auth.core.twofactor.SignInResult
import dev.fediim.auth.core.twofactor.signInResult
import dev.fediim.auth.passkeys.BetterAuthPasskeyClient
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Headless state holder for email, social, passkey and two-factor sign-in, session restore and
 * logout over [client]. Collect [state] and [effects] and send user actions to [dispatch].
 *
 * Threading: [dispatch] is not thread-safe. Call it from the single thread [scope] runs on (for
 * example `Dispatchers.Main`); the guards below are plain state checks.
 *
 * One auth attempt (sign-in, register, social, passkey, two-factor, restore) runs at a time; an
 * intent that would start another is ignored, and so is every attempt while a [AuthUiIntent.Logout]
 * runs, so [AuthUiEffect.SignedOut] always precedes a later [AuthUiEffect.SignedIn]. Each sign-in,
 * register, social and passkey attempt first signs out the previous session (revoking its token and
 * clearing the token, cached organization id and challenge cookies) and calls [onClearUserCache],
 * even for the same user; a failed re-login therefore leaves the user signed out. A failed revoke
 * emits [AuthUiEffect.SessionRevocationFailed] and the attempt continues. [onClearUserCache] runs
 * after local state is cleared and before the revoke request; an exception it throws, a
 * cancellation included, is reported as [AuthUiEffect.UserCacheClearFailed] and does not stop the
 * sign-out.
 *
 * Logout cancels the running attempt, resets [state] at once (a lockout countdown is kept), and
 * never restores a session if the revoke fails. If the token storage itself throws, the state is
 * still reset, the error is `UNEXPECTED`, and the token may remain stored. A revoke started by an
 * attempt or by Logout is never cancelled by Logout or [close]; a hung revoke therefore outlives
 * [close] until the HTTP client times out. Local state is cleared inside [dispatch] when the
 * storage never suspends, as `SettingsAuthTokenStorage` does.
 *
 * [effects] supports one collector, buffers 64 effects and drops the oldest, and never suspends the
 * view model. After [close], [dispatch] does nothing.
 *
 * @param scope the scope the jobs run in; the view model uses a child job of it.
 * @param passkeys used by [AuthUiIntent.SignInPasskey]; null reports
 *   [AuthUiErrorCodes.PASSKEY_UNSUPPORTED].
 * @param onClearUserCache called once per sign-out, see above.
 */
public class BetterAuthViewModel(
    private val client: BetterAuthClient,
    scope: CoroutineScope,
    private val passkeys: BetterAuthPasskeyClient? = null,
    private val onClearUserCache: suspend () -> Unit = {},
) : AutoCloseable {
  private val rootJob = SupervisorJob(scope.coroutineContext[Job])
  private val scope = CoroutineScope(scope.coroutineContext + rootJob)
  private val _state = MutableStateFlow(AuthUiState())
  private val channel =
      Channel<AuthUiEffect>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var password = ""
  private var closed = false
  private var authJob: Job? = null
  private var logoutJob: Job? = null
  private var lockoutJob: Job? = null

  /** The current screen state; never contains the password. */
  public val state: StateFlow<AuthUiState> = _state.asStateFlow()

  /** One-shot notifications; see the class contract for buffering. */
  public val effects: Flow<AuthUiEffect> = channel.receiveAsFlow()

  /** Handles [intent]; see the class contract for which intents are ignored and when. */
  public fun dispatch(intent: AuthUiIntent) {
    if (closed) return
    when (intent) {
      is AuthUiIntent.SetMode -> setMode(intent.mode)
      is AuthUiIntent.EmailChanged -> _state.update { it.copy(email = intent.email) }
      is AuthUiIntent.NameChanged -> _state.update { it.copy(name = intent.name) }
      is AuthUiIntent.PasswordChanged -> {
        password = intent.password
        _state.update { it.copy(passwordStrength = passwordStrength(intent.password)) }
      }
      AuthUiIntent.SubmitAuth -> submitAuth()
      is AuthUiIntent.SubmitTwoFactor -> submitTwoFactor(intent)
      is AuthUiIntent.SignInSocial -> signInSocial(intent)
      AuthUiIntent.SignInPasskey -> signInPasskey()
      AuthUiIntent.RestoreSession -> restoreSession()
      AuthUiIntent.Logout -> logout()
    }
  }

  /** Cancels the running attempt and the lockout countdown, stops effects, and ignores intents. */
  override fun close() {
    closed = true
    rootJob.cancel()
    channel.close()
  }

  private fun setMode(mode: AuthMode) {
    if (mode == AuthMode.TWO_FACTOR_CHALLENGE) return
    _state.update { it.copy(mode = mode, twoFactorMethods = emptyList()) }
  }

  private fun canStartAttempt(): Boolean = logoutJob?.isActive != true && !_state.value.isLoading

  private fun canStartLockedAttempt(): Boolean = canStartAttempt() && !_state.value.isLockedOut

  private fun submitAuth() {
    val current = _state.value
    if (!canStartLockedAttempt() || current.mode == AuthMode.TWO_FACTOR_CHALLENGE) return
    val secret = password
    if (current.mode == AuthMode.REGISTER && passwordStrength(secret) == 0) {
      _state.update { it.copy(error = BetterAuthError(code = AuthUiErrorCodes.PASSWORD_TOO_SHORT)) }
      return
    }
    val register = current.mode == AuthMode.REGISTER
    startAttempt(signOutFirst = true) {
      val result =
          if (register) {
            client.signUpEmail(SignUpEmailRequest(current.name, current.email, secret))
          } else {
            client.signInEmail(SignInEmailRequest(current.email, secret))
          }
      if (result !is BetterAuthResult.Success) return@startAttempt fail(result)
      val response = result.value
      val challenge =
          if (register) null else response.signInResult as? SignInResult.TwoFactorRequired
      when {
        challenge != null ->
            finish {
              it.copy(mode = AuthMode.TWO_FACTOR_CHALLENGE, twoFactorMethods = challenge.methods)
            }
        register && response.token == null -> finish { it.copy(mode = AuthMode.SIGN_IN) }
        else -> signedIn(response.user)
      }
    }
  }

  private fun submitTwoFactor(intent: AuthUiIntent.SubmitTwoFactor) {
    if (!canStartLockedAttempt() || _state.value.mode != AuthMode.TWO_FACTOR_CHALLENGE) return
    startAttempt(signOutFirst = false) {
      val api = client.twoFactor
      val result =
          if (intent.useBackupCode) api.verifyBackupCode(intent.code)
          else api.verifyTotp(intent.code)
      if (result is BetterAuthResult.Success) signedIn(result.value.user) else fail(result)
    }
  }

  private fun signInSocial(intent: AuthUiIntent.SignInSocial) {
    if (!canStartLockedAttempt()) return
    startAttempt(signOutFirst = true) {
      val social = client.signInSocial(intent.provider, intent.callbackUrl, intent.scopes)
      if (social !is BetterAuthResult.Success) return@startAttempt fail(social)
      val session = client.getSession()
      if (session is BetterAuthResult.Success) signedIn(session.value?.user) else fail(session)
    }
  }

  private fun signInPasskey() {
    if (!canStartLockedAttempt()) return
    val passkeyClient = passkeys
    if (passkeyClient == null) {
      _state.update {
        it.copy(error = BetterAuthError(code = AuthUiErrorCodes.PASSKEY_UNSUPPORTED))
      }
      return
    }
    startAttempt(signOutFirst = true) {
      val result = passkeyClient.signInWithPasskey()
      if (result is BetterAuthResult.Success) signedIn(result.value.user) else fail(result)
    }
  }

  private fun restoreSession() {
    if (!canStartAttempt()) return
    startAttempt(signOutFirst = false, resetSession = false) {
      when (val result = client.getSession()) {
        is BetterAuthResult.Success -> {
          val user = result.value?.user
          finish { it.copy(isSignedIn = user != null, user = user) }
        }
        else -> fail(result)
      }
    }
  }

  private fun logout() {
    if (logoutJob?.isActive == true) return
    authJob?.cancel()
    authJob = null
    password = ""
    _state.update {
      AuthUiState(
          isLockedOut = it.isLockedOut,
          lockoutSecondsRemaining = it.lockoutSecondsRemaining,
      )
    }
    // UNDISPATCHED: the local clear inside signOut runs before dispatch returns.
    logoutJob =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
          try {
            val result = withContext(NonCancellable) { client.signOut(::clearUserCache) }
            if (result !is BetterAuthResult.Success) {
              emit(AuthUiEffect.SessionRevocationFailed(result))
            }
            emit(AuthUiEffect.SignedOut)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            _state.update { it.copy(error = BetterAuthError(code = AuthUiErrorCodes.UNEXPECTED)) }
          }
        }
  }

  /**
   * Launches one attempt as [authJob]. The body updates state only through [finish], [fail] and
   * [signedIn], which do nothing once this job is no longer the current, active attempt.
   */
  private fun startAttempt(
      signOutFirst: Boolean,
      resetSession: Boolean = true,
      body: suspend () -> Unit,
  ) {
    _state.update {
      if (resetSession) {
        it.copy(isLoading = true, error = null, isSignedIn = false, user = null)
      } else {
        it.copy(isLoading = true, error = null)
      }
    }
    // LAZY: authJob must be assigned before the body can run, also on an immediate dispatcher.
    val job =
        scope.launch(start = CoroutineStart.LAZY) {
          try {
            if (signOutFirst) {
              val result = withContext(NonCancellable) { client.signOut(::clearUserCache) }
              // A NonCancellable block resumes a cancelled caller without checking.
              ensureActive()
              if (result !is BetterAuthResult.Success) {
                emit(AuthUiEffect.SessionRevocationFailed(result))
              }
            }
            body()
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            failUnexpectedly()
          }
        }
    authJob = job
    job.start()
  }

  private suspend fun isCurrentAttempt(): Boolean {
    val context = currentCoroutineContext()
    return context.isActive && context.job === authJob
  }

  private suspend fun finish(transform: (AuthUiState) -> AuthUiState) {
    if (!isCurrentAttempt()) return
    _state.update { transform(it).copy(isLoading = false) }
  }

  private suspend fun fail(result: BetterAuthResult<*>) {
    if (!isCurrentAttempt()) return
    if (result is BetterAuthResult.RateLimited) {
      val seconds = result.retryAfterSeconds
      if (seconds != null && seconds > 0) startLockout(seconds)
    }
    _state.update { it.copy(isLoading = false, error = result.toUiError()) }
  }

  private suspend fun failUnexpectedly() {
    if (!isCurrentAttempt()) return
    _state.update {
      it.copy(isLoading = false, error = BetterAuthError(code = AuthUiErrorCodes.UNEXPECTED))
    }
  }

  private suspend fun signedIn(user: AuthUser?) {
    if (!isCurrentAttempt()) return
    password = ""
    _state.update {
      it.copy(
          mode = AuthMode.SIGN_IN,
          passwordStrength = 0,
          isLoading = false,
          isSignedIn = true,
          user = user,
          twoFactorMethods = emptyList(),
          error = null,
      )
    }
    emit(AuthUiEffect.SignedIn(user))
  }

  private fun startLockout(seconds: Long) {
    lockoutJob?.cancel()
    _state.update { it.copy(isLockedOut = true, lockoutSecondsRemaining = seconds) }
    lockoutJob = scope.launch {
      var remaining = seconds
      while (remaining > 0) {
        delay(1_000)
        remaining--
        _state.update { it.copy(isLockedOut = remaining > 0, lockoutSecondsRemaining = remaining) }
      }
    }
  }

  private suspend fun clearUserCache() {
    try {
      onClearUserCache()
    } catch (e: Exception) {
      // Inside NonCancellable every exception, a CancellationException included, comes from the
      // callback itself, so it is reported instead of rethrown.
      emit(AuthUiEffect.UserCacheClearFailed(e))
    }
  }

  private fun emit(effect: AuthUiEffect) {
    channel.trySend(effect)
  }
}
