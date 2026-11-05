@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.fediim.auth.compose

import dev.fediim.auth.compose.AuthUiIntent.EmailChanged
import dev.fediim.auth.compose.AuthUiIntent.Logout
import dev.fediim.auth.compose.AuthUiIntent.PasswordChanged
import dev.fediim.auth.compose.AuthUiIntent.SubmitAuth
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Review-driven lifecycle cases (plan #7 L25). */
class AuthLifecycleEdgeCaseTest {
  @Test
  fun cancellationFromTheCacheCallbackDoesNotStickTheAttempt() = runTest {
    val rig = ComposeTestRig(testScheduler)
    val vm = rig.viewModel(backgroundScope)
    val effects = collectEffects(vm)
    rig.seed("tA")
    rig.on("sign-in/email", tokenReply("tB"))
    rig.cacheFailure = CancellationException("timeout")
    vm.dispatch(EmailChanged("b@b.com"))
    vm.dispatch(PasswordChanged("pw"))
    vm.dispatch(SubmitAuth)
    runCurrent()

    val failed = effects.filterIsInstance<AuthUiEffect.UserCacheClearFailed>().single()
    assertIs<CancellationException>(failed.cause)
    assertEquals("Bearer tA", rig.authOf(rig.reqs("sign-out").single()))
    assertEquals(1, rig.reqs("sign-in/email").size)
    assertFalse(vm.state.value.isLoading)
    assertTrue(vm.state.value.isSignedIn)
    assertEquals("tB", rig.storage.token())
  }

  @Test
  fun logoutRevokeSurvivesClose() = runTest {
    val rig = ComposeTestRig(testScheduler)
    val vm = rig.viewModel(backgroundScope)
    rig.seed("t1")
    val gate = rig.gate("sign-out")
    vm.dispatch(Logout)
    runCurrent()
    vm.close()
    gate.complete(Unit)
    runCurrent()
    assertEquals("Bearer t1", rig.authOf(rig.reqs("sign-out").single()))
    assertTrue("sign-out 200" in rig.completed)
  }
}
