@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.fediim.auth.compose

import dev.fediim.auth.compose.AuthUiIntent.EmailChanged
import dev.fediim.auth.compose.AuthUiIntent.Logout
import dev.fediim.auth.compose.AuthUiIntent.PasswordChanged
import dev.fediim.auth.compose.AuthUiIntent.SubmitAuth
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.storage.AuthTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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

  @Test
  fun logoutWithFailingStorageResetsStateAndReportsUnexpected() = runTest {
    val broken =
        object : AuthTokenStorage {
          override suspend fun epoch(): Long = 0

          override suspend fun token(): String? = "tS"

          override suspend fun writeTokenIfEpoch(expectedEpoch: Long, token: String) = false

          override suspend fun clear() = throw IllegalStateException("disk")
        }
    var sent = 0
    var cacheCalls = 0
    val client =
        BetterAuthClient(
            BetterAuthConfig("https://example.com"),
            broken,
            MockEngine {
              sent++
              respond("{}")
            },
        )
    val vm = BetterAuthViewModel(client, backgroundScope, null) { cacheCalls++ }
    val effects = collectEffects(vm)
    vm.dispatch(Logout)
    runCurrent()
    assertFalse(vm.state.value.isSignedIn)
    assertFalse(vm.state.value.isLoading)
    assertEquals(AuthUiErrorCodes.UNEXPECTED, vm.state.value.error?.code)
    assertTrue(effects.none { it is AuthUiEffect.SignedOut })
    assertEquals(0, cacheCalls)
    assertEquals(0, sent)
  }
}
