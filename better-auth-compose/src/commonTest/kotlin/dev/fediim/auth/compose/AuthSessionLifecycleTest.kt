@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.fediim.auth.compose

import dev.fediim.auth.compose.AuthUiIntent.EmailChanged
import dev.fediim.auth.compose.AuthUiIntent.Logout
import dev.fediim.auth.compose.AuthUiIntent.PasswordChanged
import dev.fediim.auth.compose.AuthUiIntent.RestoreSession
import dev.fediim.auth.compose.AuthUiIntent.SignInSocial
import dev.fediim.auth.compose.AuthUiIntent.SubmitAuth
import dev.fediim.auth.core.model.BetterAuthResult
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class AuthSessionLifecycleTest {
  private fun TestScope.setup() =
      ComposeTestRig(testScheduler).let { it to it.viewModel(backgroundScope) }

  private fun rateLimited(seconds: String?) =
      Reply(
          """{"code":"TOO_MANY","message":"slow down"}""",
          HttpStatusCode.TooManyRequests,
          seconds?.let { listOf("Retry-After" to it) }.orEmpty(),
      )

  private fun BetterAuthViewModel.signIn(email: String) {
    dispatch(EmailChanged(email))
    dispatch(PasswordChanged("pw"))
    dispatch(SubmitAuth)
  }

  @Test
  fun t5LogoutDuringSuspendedSignInDiscardsItsToken() = runTest {
    val (rig, vm) = setup()
    val effects = collectEffects(vm)
    rig.seed("tA")
    rig.on(
        "sign-in/email",
        Reply("""{"token":"tB","user":$USER_B}""", headers = listOf("set-auth-token" to "tB")),
    )
    val gate = rig.gate("sign-in/email")
    vm.dispatch(SubmitAuth)
    runCurrent()
    assertEquals(
        listOf("req:sign-out", "req:sign-in/email"),
        rig.events.filter { it.startsWith("req:") },
    )
    assertEquals("Bearer tA", rig.authOf(rig.reqs("sign-out").single()))

    vm.dispatch(Logout)
    assertFalse(vm.state.value.isSignedIn)
    assertFalse(vm.state.value.isLoading)
    gate.complete(Unit)
    runCurrent()
    assertNull(rig.storage.token())
    assertTrue(effects.none { it is AuthUiEffect.SignedIn })
    assertEquals(1, rig.reqs("sign-out").size)
    assertEquals(2, rig.cacheClears)
  }

  @Test
  fun t5LogoutWithoutStoredTokenSendsNoSignOut() = runTest {
    val (rig, vm) = setup()
    rig.on("sign-in/email", tokenReply("tB"))
    val gate = rig.gate("sign-in/email")
    vm.dispatch(SubmitAuth)
    runCurrent()
    vm.dispatch(Logout)
    gate.complete(Unit)
    runCurrent()
    assertTrue(rig.reqs("sign-out").isEmpty())
    assertNull(rig.storage.token())
  }

  @Test
  fun t5RevokeInFlightSurvivesLogoutAndClose() = runTest {
    for (closing in listOf(false, true)) {
      val (rig, vm) = setup()
      rig.seed("tA")
      val gate = rig.gate("sign-out")
      vm.dispatch(SubmitAuth)
      runCurrent()
      if (closing) vm.close() else vm.dispatch(Logout)
      gate.complete(Unit)
      runCurrent()
      assertEquals("Bearer tA", rig.authOf(rig.reqs("sign-out").single()))
      assertTrue("sign-out 200" in rig.completed, "closing=$closing")
      assertTrue(rig.reqs("sign-in/email").isEmpty(), "closing=$closing")
      assertNull(rig.storage.token())
    }
  }

  @Test
  fun t5CancelledAttemptEmitsNothingWhenItsRevokeFails() = runTest {
    val (rig, vm) = setup()
    val effects = collectEffects(vm)
    rig.seed("tA")
    rig.on("sign-out", Reply(OK, HttpStatusCode.InternalServerError))
    val gate = rig.gate("sign-out")
    vm.dispatch(SubmitAuth)
    runCurrent()
    vm.dispatch(Logout)
    gate.complete(Unit)
    runCurrent()
    assertEquals(listOf<AuthUiEffect>(AuthUiEffect.SignedOut), effects)
  }

  @Test
  fun t5LogoutForgetsThePassword() = runTest {
    val (rig, vm) = setup()
    rig.on("sign-in/email", tokenReply("tB"))
    val gate = rig.gate("sign-in/email")
    vm.signIn("a@b.com")
    runCurrent()
    vm.dispatch(Logout)
    vm.dispatch(EmailChanged("b@b.com"))
    vm.dispatch(SubmitAuth)
    runCurrent()
    gate.complete(Unit)
    runCurrent()
    val second = rig.bodyOf(rig.reqs("sign-in/email").last())
    assertTrue(""""email":"b@b.com"""" in second)
    assertTrue("\"password\":\"\"" in second)
  }

  @Test
  fun t5StaleAttemptCannotTouchANewerOne() = runTest {
    val (rig, vm) = setup()
    val effects = collectEffects(vm)
    rig.seed("tA")
    rig.on("sign-in/email", tokenReply("tN"))
    rig.on("sign-out", Reply(OK, HttpStatusCode.InternalServerError))
    val signOutGate = rig.gate("sign-out")
    vm.dispatch(SubmitAuth)
    runCurrent()
    vm.dispatch(Logout)
    // Logout found no token and finished inside dispatch: its callback ran, no second request.
    assertEquals(2, rig.cacheClears)
    assertEquals(1, rig.reqs("sign-out").size)
    assertFalse(vm.state.value.isLoading)
    val signInGate = rig.gate("sign-in/email")
    vm.signIn("new@b.com")
    runCurrent()
    assertTrue(vm.state.value.isLoading)

    signOutGate.complete(Unit)
    runCurrent()
    assertTrue(vm.state.value.isLoading)
    assertEquals(1, rig.reqs("sign-in/email").size)
    signInGate.complete(Unit)
    runCurrent()
    val bodies = rig.reqs("sign-in/email").map { rig.bodyOf(it) }
    assertEquals(1, bodies.size)
    assertTrue(""""email":"new@b.com"""" in bodies.single())
    assertTrue(vm.state.value.isSignedIn)
    // The stale attempt's failed revoke (500) emitted nothing.
    assertTrue(effects.none { it is AuthUiEffect.SessionRevocationFailed })
    assertEquals(listOf(AuthUiEffect.SignedOut), effects.filterIsInstance<AuthUiEffect.SignedOut>())
    assertEquals(1, effects.filterIsInstance<AuthUiEffect.SignedIn>().size)
  }

  private suspend fun TestScope.restored():
      Triple<ComposeTestRig, BetterAuthViewModel, MutableList<AuthUiEffect>> {
    val (rig, vm) = setup()
    val effects = collectEffects(vm)
    rig.seed("t1")
    rig.on("get-session", Reply(ENVELOPE))
    vm.dispatch(RestoreSession)
    runCurrent()
    assertTrue(vm.state.value.isSignedIn)
    return Triple(rig, vm, effects)
  }

  @Test
  fun t6LogoutClearsLocallyBeforeTheServerCall() = runTest {
    val (rig, vm, effects) = restored()
    vm.dispatch(Logout)
    assertNull(rig.storage.token())
    assertFalse(vm.state.value.isSignedIn)
    assertEquals(CacheSnapshot(null, 1, rig.requests.size, null), rig.snapshots.single())
    assertTrue(rig.reqs("sign-out").isEmpty())
    runCurrent()
    assertEquals("Bearer t1", rig.authOf(rig.reqs("sign-out").single()))
    assertEquals(listOf<AuthUiEffect>(AuthUiEffect.SignedOut), effects)
  }

  @Test
  fun t6RevokeFailureStillSignsOut() = runTest {
    val (rig, vm, effects) = restored()
    rig.on("sign-out", Reply(OK, HttpStatusCode.InternalServerError))
    vm.dispatch(Logout)
    runCurrent()
    val failed = effects.filterIsInstance<AuthUiEffect.SessionRevocationFailed>().single()
    assertEquals(500, assertIs<BetterAuthResult.Failure>(failed.result).status)
    assertEquals(AuthUiEffect.SignedOut, effects.last())
    assertNull(rig.storage.token())
    assertFalse(vm.state.value.isSignedIn)
  }

  @Test
  fun t6CacheCallbackFailureDoesNotStopTheRevoke() = runTest {
    val (rig, vm, effects) = restored()
    rig.cacheFailure = IllegalStateException("cache")
    vm.dispatch(Logout)
    runCurrent()
    val failed = effects.filterIsInstance<AuthUiEffect.UserCacheClearFailed>().single()
    assertEquals("cache", failed.cause.message)
    assertEquals("Bearer t1", rig.authOf(rig.reqs("sign-out").single()))
  }

  @Test
  fun t7AuthIntentsAreIgnoredWhileLogoutRuns() = runTest {
    val (rig, vm, _) = restored()
    val gate = rig.gate("sign-out")
    vm.dispatch(Logout)
    vm.dispatch(Logout)
    runCurrent()
    assertEquals(1, rig.cacheClears)
    assertEquals(1, rig.reqs("sign-out").size)
    val sessions = rig.reqs("get-session").size
    vm.signIn("during@b.com")
    vm.dispatch(SignInSocial("github"))
    vm.dispatch(RestoreSession)
    runCurrent()
    assertTrue(rig.reqs("sign-in/email").isEmpty())
    assertEquals(0, rig.launcher.calls)
    assertEquals(sessions, rig.reqs("get-session").size)

    gate.complete(Unit)
    runCurrent()
    vm.dispatch(Logout)
    runCurrent()
    assertEquals(2, rig.cacheClears)
    assertEquals(1, rig.reqs("sign-out").size)
  }

  @Test
  fun t7SubmitAuthIsIgnoredInTheTwoFactorChallenge() = runTest {
    val (rig, vm) = setup()
    rig.on("sign-in/email", Reply("""{"twoFactorRedirect":true,"twoFactorMethods":["totp"]}"""))
    vm.signIn("a@b.com")
    runCurrent()
    assertEquals(AuthMode.TWO_FACTOR_CHALLENGE, vm.state.value.mode)
    vm.dispatch(SubmitAuth)
    runCurrent()
    assertEquals(1, rig.requests.size)
  }

  @Test
  fun t8ReLoginSignsOutFirstAndClearsTheOldUsersState() = runTest {
    val (rig, vm) = setup()
    val effects = collectEffects(vm)
    rig.seed("tA", org = "orgA")
    rig.on("sign-in/email", tokenReply("tB", USER_B))
    vm.signIn("b@b.com")
    runCurrent()
    assertEquals(listOf("cache-clear", "req:sign-out", "req:sign-in/email"), rig.events)
    assertEquals("Bearer tA", rig.authOf(rig.reqs("sign-out").single()))
    assertEquals("tB", rig.storage.token())
    assertNull(rig.storage.activeOrganizationId())
    assertEquals("uB", vm.state.value.user?.id)
    assertEquals("uB", (effects.single() as AuthUiEffect.SignedIn).user?.id)
  }

  @Test
  fun t8FailedReLoginLeavesTheUserSignedOut() = runTest {
    val (rig, vm) = setup()
    rig.seed("tA")
    rig.on(
        "sign-in/email",
        Reply("""{"code":"INVALID_EMAIL_OR_PASSWORD"}""", HttpStatusCode.Unauthorized),
    )
    vm.signIn("b@b.com")
    runCurrent()
    assertFalse(vm.state.value.isSignedIn)
    assertNull(rig.storage.token())
    assertEquals("Bearer tA", rig.authOf(rig.reqs("sign-out").single()))
    assertEquals("INVALID_EMAIL_OR_PASSWORD", vm.state.value.error?.code)
  }

  @Test
  fun t8RevokeFailureIsReportedAndTheSignInContinues() = runTest {
    for (network in listOf(false, true)) {
      val (rig, vm) = setup()
      val effects = collectEffects(vm)
      rig.seed("tA")
      rig.on("sign-in/email", tokenReply("tB"))
      if (network) rig.failWith("sign-out")
      else rig.on("sign-out", Reply(OK, HttpStatusCode.InternalServerError))
      vm.signIn("b@b.com")
      runCurrent()
      val failed = effects.filterIsInstance<AuthUiEffect.SessionRevocationFailed>().single().result
      if (network) assertIs<BetterAuthResult.NetworkError>(failed)
      else assertEquals(500, assertIs<BetterAuthResult.Failure>(failed).status)
      assertEquals("tB", rig.storage.token(), "network=$network")
      assertTrue(effects.last() is AuthUiEffect.SignedIn)
    }
  }

  @Test
  fun t9LockoutCountsDownAndBlocksSubmit() = runTest {
    val (rig, vm) = setup()
    rig.on("sign-in/email", rateLimited("30"))
    vm.signIn("a@b.com")
    runCurrent()
    assertTrue(vm.state.value.isLockedOut)
    assertEquals(30, vm.state.value.lockoutSecondsRemaining)
    assertEquals("TOO_MANY", vm.state.value.error?.code)
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(29, vm.state.value.lockoutSecondsRemaining)
    vm.signIn("locked@b.com")
    runCurrent()
    assertEquals(1, rig.reqs("sign-in/email").size)
    advanceTimeBy(29_000)
    runCurrent()
    assertEquals(0, vm.state.value.lockoutSecondsRemaining)
    assertFalse(vm.state.value.isLockedOut)
    vm.dispatch(SubmitAuth)
    runCurrent()
    assertEquals(2, rig.reqs("sign-in/email").size)
  }

  @Test
  fun t9NewRateLimitRestartsTheCountdown() = runTest {
    val (rig, vm) = setup()
    rig.on("sign-in/email", rateLimited("30"))
    vm.signIn("a@b.com")
    runCurrent()
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(20, vm.state.value.lockoutSecondsRemaining)
    rig.on("get-session", rateLimited("5"))
    vm.dispatch(RestoreSession)
    runCurrent()
    assertEquals(5, vm.state.value.lockoutSecondsRemaining)
    advanceTimeBy(5_000)
    runCurrent()
    assertEquals(0, vm.state.value.lockoutSecondsRemaining)
    assertFalse(vm.state.value.isLockedOut)
    advanceTimeBy(5_000)
    runCurrent()
    assertEquals(0, vm.state.value.lockoutSecondsRemaining)
  }

  @Test
  fun t9RateLimitWithoutUsableHeaderOnlySetsTheError() = runTest {
    for (header in listOf(null, "0")) {
      val (rig, vm) = setup()
      rig.on("sign-in/email", rateLimited(header))
      vm.signIn("a@b.com")
      runCurrent()
      assertEquals("TOO_MANY", vm.state.value.error?.code, "header=$header")
      assertFalse(vm.state.value.isLockedOut)
      assertEquals(0, vm.state.value.lockoutSecondsRemaining)
    }
  }

  @Test
  fun t10CloseCancelsTheSignInAndIgnoresLaterIntents() = runTest {
    val (rig, vm) = setup()
    rig.on("sign-in/email", tokenReply("tB"))
    val gate = rig.gate("sign-in/email")
    vm.dispatch(SubmitAuth)
    runCurrent()
    vm.close()
    gate.complete(Unit)
    runCurrent()
    assertTrue("sign-in/email 200" !in rig.completed)
    assertNull(rig.storage.token())
    assertFalse(vm.state.value.isSignedIn)
    vm.dispatch(SubmitAuth)
    runCurrent()
    assertEquals(1, rig.reqs("sign-in/email").size)
    vm.dispatch(EmailChanged("late@b.com"))
    assertEquals("", vm.state.value.email)
  }

  @Test
  fun t16EffectsNeverBlockTheViewModelWithoutACollector() = runTest {
    val (rig, vm) = setup()
    rig.seed("t1")
    rig.on("get-session", Reply(ENVELOPE))
    rig.on("sign-in/email", tokenReply("tN"))
    vm.dispatch(RestoreSession)
    runCurrent()
    vm.dispatch(Logout)
    runCurrent()
    assertEquals("Bearer t1", rig.authOf(rig.reqs("sign-out").single()))
    assertEquals(1, rig.cacheClears)
    vm.dispatch(Logout)
    assertEquals(2, rig.cacheClears)
    vm.signIn("next@b.com")
    runCurrent()
    assertEquals(3, rig.cacheClears)
    assertEquals("tN", rig.storage.token())
    assertEquals(1, rig.reqs("sign-out").size)
    repeat(70) {
      vm.dispatch(Logout)
      runCurrent()
    }
    assertEquals(2, rig.reqs("sign-out").size)
    assertEquals("Bearer tN", rig.authOf(rig.reqs("sign-out")[1]))
    assertFalse(vm.state.value.isSignedIn)
    assertEquals(3 + 70, rig.cacheClears)

    val late = mutableListOf<AuthUiEffect>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      vm.effects.collect { late += it }
    }
    runCurrent()
    assertEquals(64, late.size)
    assertTrue(late.all { it == AuthUiEffect.SignedOut })
    vm.signIn("after@b.com")
    runCurrent()
    assertEquals(2, rig.reqs("sign-in/email").size)
  }
}
