@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.fediim.auth.compose

import dev.fediim.auth.compose.AuthUiIntent.EmailChanged
import dev.fediim.auth.compose.AuthUiIntent.NameChanged
import dev.fediim.auth.compose.AuthUiIntent.PasswordChanged
import dev.fediim.auth.compose.AuthUiIntent.RestoreSession
import dev.fediim.auth.compose.AuthUiIntent.SetMode
import dev.fediim.auth.compose.AuthUiIntent.SignInPasskey
import dev.fediim.auth.compose.AuthUiIntent.SignInSocial
import dev.fediim.auth.compose.AuthUiIntent.SubmitAuth
import dev.fediim.auth.compose.AuthUiIntent.SubmitTwoFactor
import dev.fediim.auth.core.social.SocialCallback
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class BetterAuthViewModelTest {
  private fun BetterAuthViewModel.signIn(email: String, password: String = "pw") {
    dispatch(EmailChanged(email))
    dispatch(PasswordChanged(password))
    dispatch(SubmitAuth)
  }

  private fun TestScope.setup(passkeys: Boolean = false, withLauncher: Boolean = true) =
      ComposeTestRig(testScheduler, withLauncher).let {
        it to it.viewModel(backgroundScope, passkeys)
      }

  @Test
  fun t1OnlyOneAttemptRunsAtATime() = runTest {
    val (rig, vm) = setup(passkeys = true)
    rig.on("sign-in/email", tokenReply("t1", USER_A))
    val gate = rig.gate("sign-in/email")
    vm.signIn("first@b.com")
    runCurrent()
    assertTrue(vm.state.value.isLoading)

    vm.dispatch(EmailChanged("dup@b.com"))
    vm.dispatch(SubmitAuth)
    vm.dispatch(SignInPasskey)
    vm.dispatch(SignInSocial("github"))
    vm.dispatch(RestoreSession)
    runCurrent()
    assertEquals(1, rig.reqs("sign-in/email").size)
    assertTrue(""""email":"first@b.com"""" in rig.bodyOf(rig.reqs("sign-in/email").single()))
    assertEquals(0, rig.launcher.calls)
    assertEquals(0, rig.authenticator.calls)
    assertTrue(rig.reqs("get-session").isEmpty())

    gate.complete(Unit)
    runCurrent()
    assertTrue(vm.state.value.isSignedIn)
    vm.dispatch(SubmitAuth)
    runCurrent()
    val bodies = rig.reqs("sign-in/email").map { rig.bodyOf(it) }
    assertEquals(2, bodies.size)
    assertTrue(""""email":"dup@b.com"""" in bodies[1])
    assertTrue(""""password":"pw"""" in bodies[0])
    assertTrue("\"password\":\"\"" in bodies[1])
  }

  @Test
  fun t2PasswordStrengthPolicyAndRegisterGate() = runTest {
    val scores =
        mapOf(
            "" to 0,
            "Ab1!xyz" to 0,
            "abcdefgh" to 1,
            "abcdefghijkl" to 2,
            "Abcdefgh" to 2,
            "abcdefg1" to 2,
            "abcdefg!" to 2,
            "Abcdefgh1" to 3,
            "Abcdefgh1!" to 4,
            "Abcdefghijk1!" to 4,
        )
    scores.forEach { (password, score) ->
      assertEquals(score, passwordStrength(password), password)
    }

    val (rig, vm) = setup()
    rig.on("sign-up/email", tokenReply("tn"))
    vm.dispatch(SetMode(AuthMode.REGISTER))
    vm.dispatch(NameChanged("N"))
    vm.dispatch(EmailChanged("n@b.com"))
    for (weak in listOf("Ab1!xyz", "short")) {
      vm.dispatch(PasswordChanged(weak))
      vm.dispatch(SubmitAuth)
      runCurrent()
      assertEquals(AuthUiErrorCodes.PASSWORD_TOO_SHORT, vm.state.value.error?.code, weak)
      assertTrue(rig.requests.isEmpty())
    }
    val password = "Abcdefgh1!"
    vm.dispatch(PasswordChanged(password))
    assertEquals(4, vm.state.value.passwordStrength)
    vm.dispatch(SubmitAuth)
    runCurrent()
    val body = rig.bodyOf(rig.reqs("sign-up/email").single())
    assertTrue(""""password":"$password"""" in body && """"name":"N"""" in body)
    assertTrue(vm.state.value.isSignedIn)
    assertEquals(0, vm.state.value.passwordStrength)
    assertFalse(PasswordChanged(password).toString().contains(password))
    assertFalse(vm.state.value.toString().contains(password))

    val (rig2, vm2) = setup()
    rig2.on("sign-up/email", Reply("""{"token":null,"user":$USER_B}"""))
    vm2.dispatch(SetMode(AuthMode.REGISTER))
    vm2.dispatch(PasswordChanged(password))
    vm2.dispatch(SubmitAuth)
    runCurrent()
    assertEquals(AuthMode.SIGN_IN, vm2.state.value.mode)
    assertFalse(vm2.state.value.isSignedIn)

    val (rig3, vm3) = setup()
    rig3.on("sign-in/email", tokenReply("t3"))
    vm3.signIn("s@b.com", "Ab1!xyz")
    runCurrent()
    assertEquals(1, rig3.reqs("sign-in/email").size)
    assertFalse(vm3.state.value.toString().contains("Ab1!xyz"))
  }

  @Test
  fun t3TwoFactorChallengeFlow() = runTest {
    val (rig, vm) = setup()
    val effects = collectEffects(vm)
    rig.on(
        "sign-in/email",
        Reply(
            """{"twoFactorRedirect":true,"twoFactorMethods":["totp","otp"]}""",
            headers = listOf("Set-Cookie" to "better-auth.two_factor=chal.sig; Path=/; HttpOnly"),
        ),
    )
    vm.dispatch(SubmitTwoFactor("111111"))
    vm.dispatch(SetMode(AuthMode.TWO_FACTOR_CHALLENGE))
    runCurrent()
    assertTrue(rig.requests.isEmpty())
    assertEquals(AuthMode.SIGN_IN, vm.state.value.mode)

    vm.signIn("a@b.com")
    runCurrent()
    val state = vm.state.value
    assertEquals(AuthMode.TWO_FACTOR_CHALLENGE, state.mode)
    assertEquals(listOf("totp", "otp"), state.twoFactorMethods)
    assertFalse(state.isSignedIn)

    rig.on(
        "two-factor/verify-totp",
        Reply("""{"code":"INVALID_CODE"}""", HttpStatusCode.Unauthorized),
    )
    vm.dispatch(SubmitTwoFactor("000000"))
    runCurrent()
    assertEquals(AuthMode.TWO_FACTOR_CHALLENGE, vm.state.value.mode)
    assertEquals("INVALID_CODE", vm.state.value.error?.code)

    rig.on(
        "two-factor/verify-totp",
        Reply(
            """{"token":"b","user":$USER_A}""",
            headers = listOf("set-auth-token" to "t2fa"),
        ),
    )
    vm.dispatch(SubmitTwoFactor("123456"))
    runCurrent()
    val verify = rig.reqs("two-factor/verify-totp").last()
    assertEquals("""{"code":"123456"}""", rig.bodyOf(verify))
    assertEquals("better-auth.two_factor=chal.sig", verify.headers["Cookie"])
    assertTrue(rig.reqs("sign-out").isEmpty())
    assertEquals("t2fa", rig.storage.token())
    assertTrue(vm.state.value.isSignedIn)
    assertTrue(effects.single() is AuthUiEffect.SignedIn)

    rig.on("sign-in/email", Reply("""{"twoFactorRedirect":true,"twoFactorMethods":["totp"]}"""))
    rig.on("two-factor/verify-backup-code", Reply("""{"token":"c","user":$USER_A}"""))
    vm.dispatch(SubmitAuth)
    runCurrent()
    vm.dispatch(SubmitTwoFactor("backup-1", useBackupCode = true))
    runCurrent()
    assertEquals(1, rig.reqs("two-factor/verify-backup-code").size)

    rig.on("sign-in/email", Reply("""{"twoFactorRedirect":true,"twoFactorMethods":["totp"]}"""))
    vm.dispatch(SubmitAuth)
    runCurrent()
    vm.dispatch(SetMode(AuthMode.SIGN_IN))
    assertEquals(AuthMode.SIGN_IN, vm.state.value.mode)
    assertTrue(vm.state.value.twoFactorMethods.isEmpty())
    assertFalse(SubmitTwoFactor("123456").toString().contains("123456"))
  }

  @Test
  fun t4SocialPasskeyAndRestore() = runTest {
    val (rig, vm) = setup(passkeys = true)
    rig.on("get-session", Reply(ENVELOPE))
    vm.dispatch(SignInSocial("github"))
    runCurrent()
    assertTrue(vm.state.value.isSignedIn)
    assertEquals("u1", vm.state.value.user?.id)

    rig.launcher.result = SocialCallback.Error("access_denied")
    vm.dispatch(SignInSocial("github"))
    runCurrent()
    assertEquals("access_denied", vm.state.value.error?.code)
    assertFalse(vm.state.value.isSignedIn)

    rig.on("passkey/generate-authenticate-options", Reply("""{"challenge":"Y2hhbA"}"""))
    rig.on(
        "passkey/verify-authentication",
        Reply(ENVELOPE, headers = listOf("set-auth-token" to "tpk")),
    )
    vm.dispatch(SignInPasskey)
    runCurrent()
    assertEquals("tpk", rig.storage.token())
    assertTrue(vm.state.value.isSignedIn)

    val before = rig.cacheClears
    val signOuts = rig.reqs("sign-out").size
    vm.dispatch(RestoreSession)
    assertTrue(vm.state.value.isLoading)
    assertTrue(vm.state.value.isSignedIn)
    runCurrent()
    assertEquals(before, rig.cacheClears)
    assertEquals(signOuts, rig.reqs("sign-out").size)
    assertTrue(vm.state.value.isSignedIn)

    rig.on("get-session", Reply("null"))
    vm.dispatch(RestoreSession)
    runCurrent()
    assertFalse(vm.state.value.isSignedIn)
    assertNull(vm.state.value.user)
    assertEquals("tpk", rig.storage.token())

    rig.failWith("get-session")
    vm.dispatch(RestoreSession)
    runCurrent()
    assertEquals(AuthUiErrorCodes.NETWORK_ERROR, vm.state.value.error?.code)
  }

  @Test
  fun t4MissingLauncherAndPasskeyClient() = runTest {
    val (rig, vm) = setup(withLauncher = false)
    vm.dispatch(SignInSocial("github"))
    runCurrent()
    assertEquals(AuthUiErrorCodes.UNEXPECTED, vm.state.value.error?.code)
    assertFalse(vm.state.value.isLoading)
    assertTrue(backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)

    vm.dispatch(SignInPasskey)
    runCurrent()
    assertEquals(AuthUiErrorCodes.PASSKEY_UNSUPPORTED, vm.state.value.error?.code)
    assertTrue(rig.requests.isEmpty())
  }
}
