package dev.fediim.auth.fds

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dev.fediim.auth.compose.AuthMode
import dev.fediim.auth.compose.AuthUiEffect
import dev.fediim.auth.compose.AuthUiErrorCodes
import dev.fediim.auth.compose.AuthUiIntent
import dev.fediim.auth.compose.AuthUiState
import dev.fediim.auth.compose.BetterAuthViewModel
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.fds.theme.FediimTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BetterAuthDialogUiTest {
  private val social = """{"url":"https://github.com/login/oauth/authorize?x=1","redirect":true}"""

  private fun ComposeUiTest.panel(
      rig: FdsTestRig,
      capability: PasskeyCapability = PasskeyCapability.Unsupported,
      providers: List<String> = listOf("google", "github"),
  ) =
      setVmContent({ rig.viewModel(it) }) {
        BetterAuthPanel(it, passkeyCapability = capability, socialProviders = providers)
      }

  private fun ComposeUiTest.signIn(password: String) {
    type(TAG_EMAIL, "first@b.com")
    type(TAG_PASSWORD, password)
    onNodeWithTag(TAG_SUBMIT).performClick()
  }

  private fun ComposeUiTest.fieldText(tag: String): String =
      onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

  private fun ComposeUiTest.dispatch(vm: BetterAuthViewModel, intent: AuthUiIntent) =
      runOnUiThread {
        vm.dispatch(intent)
      }

  private fun ComposeUiTest.errorText(): String =
      onNodeWithTag(TAG_ERROR).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

  @Test
  fun modeSwitchingShowsAndHidesRegisterParts() = runDesktopTest {
    val vm = panel(FdsTestRig())
    assertTrue(hasTag(TAG_EMAIL) && hasTag(TAG_PASSWORD))
    assertTrue(!hasTag(TAG_NAME) && !hasTag(TAG_RULE_PREFIX + "0"))

    onNodeWithText("Register").performClick()
    until("register") { vm.state.value.mode == AuthMode.REGISTER && hasTag(TAG_NAME) }
    assertTrue((0..4).all { hasTag(TAG_RULE_PREFIX + it) })

    onNodeWithText("Sign in").performClick()
    until("sign in") { vm.state.value.mode == AuthMode.SIGN_IN && !hasTag(TAG_NAME) }
    assertTrue(!hasTag(TAG_RULE_PREFIX + "0"))
  }

  @Test
  fun submitSendsFieldsOnceAndClearsPasswordOnSignIn() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-in/email", Reply(TOKEN_REPLY))
    val gate = rig.gate("sign-in/email")
    val vm = panel(rig)
    signIn("pw-sign-in-1")
    until("request") { rig.to("sign-in/email").size == 1 }
    onNodeWithTag(TAG_SUBMIT).assertIsNotEnabled()
    onNodeWithTag(TAG_SUBMIT).performClick()
    waitForIdle()
    val body = rig.to("sign-in/email").single().body
    assertTrue("first@b.com" in body && "pw-sign-in-1" in body, body)

    gate.complete(Unit)
    until("signed in") { vm.state.value.isSignedIn }
    until("password cleared") { fieldText(TAG_PASSWORD) == "" }
    assertEquals(1, rig.to("sign-in/email").size)
  }

  @Test
  fun retypedPasswordIsSentAfterFailedSubmit() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-in/email", Reply("""{"code":"BAD_LOGIN"}""", 401), Reply(TOKEN_REPLY))
    val vm = panel(rig)
    signIn("pw-bad-8")
    until("failure") { vm.state.value.error?.code == "BAD_LOGIN" }
    assertTrue("BAD_LOGIN" in errorText())
    retype(TAG_PASSWORD, "pw-sign-in-2")
    onNodeWithTag(TAG_SUBMIT).performClick()
    until("second request") { rig.to("sign-in/email").size == 2 }
    val second = rig.to("sign-in/email")[1].body
    assertTrue("pw-sign-in-2" in second && "pw-bad-8" !in second, second)
  }

  @Test
  fun submitSendsTheFieldNotAStaleViewModelCopy() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-in/email", Reply(TWO_FACTOR_REPLY), Reply(TOKEN_REPLY))
    val seen = CopyOnWriteArrayList<AuthUiEffect>()
    val vm =
        setVmContent({ rig.viewModel(it) }) {
          LaunchedEffect(it) { it.effects.collect { effect -> seen += effect } }
          BetterAuthPanel(it, socialProviders = emptyList())
        }
    signIn("pw-stale-3")
    until("challenge") { vm.state.value.mode == AuthMode.TWO_FACTOR_CHALLENGE }
    dispatch(vm, AuthUiIntent.Logout)
    until("form back") { vm.state.value.mode == AuthMode.SIGN_IN && hasTag(TAG_SUBMIT) }
    onNodeWithTag(TAG_SUBMIT).performClick()
    until("second request") { rig.to("sign-in/email").size == 2 }
    assertTrue("pw-stale-3" in rig.to("sign-in/email")[1].body)
    until("signed in effect") { seen.any { it is AuthUiEffect.SignedIn && it.user?.id == "uA" } }
  }

  @Test
  fun registerSendsNameEmailAndPassword() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-up/email", Reply(TOKEN_REPLY))
    panel(rig)
    onNodeWithText("Register").performClick()
    until("name field") { hasTag(TAG_NAME) }
    type(TAG_NAME, "Nia")
    type(TAG_EMAIL, "new@b.com")
    type(TAG_PASSWORD, "Abcdefgh1!")
    onNodeWithTag(TAG_SUBMIT).performClick()
    until("sign-up") { rig.to("sign-up/email").size == 1 }
    val body = rig.to("sign-up/email").single().body
    assertTrue("Nia" in body && "new@b.com" in body && "Abcdefgh1!" in body, body)
  }

  @Test
  fun registerWithTooShortPasswordSendsNothing() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-up/email", Reply(TOKEN_REPLY))
    val vm = panel(rig)
    onNodeWithText("Register").performClick()
    until("name field") { hasTag(TAG_NAME) }
    type(TAG_NAME, "Nia")
    type(TAG_EMAIL, "new@b.com")
    type(TAG_PASSWORD, "short-7")
    onNodeWithTag(TAG_SUBMIT).performClick()
    until("banner") { vm.state.value.error?.code == AuthUiErrorCodes.PASSWORD_TOO_SHORT }
    assertTrue(AuthUiErrorCodes.PASSWORD_TOO_SHORT in errorText())
    assertEquals(0, rig.to("sign-up/email").size)
  }

  @Test
  fun twoFactorStepVerifiesTotpThenBackupCode() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-in/email", Reply(TWO_FACTOR_REPLY))
    rig.on("two-factor/verify-totp", Reply("""{"code":"INVALID_CODE"}""", 401))
    rig.on("two-factor/verify-backup-code", Reply(TOKEN_REPLY))
    val vm = panel(rig)
    signIn("pw-2fa-4")
    until("code field") { hasTag(TAG_CODE) }
    assertTrue(!hasTag(TAG_EMAIL) && !hasTag(TAG_PASSWORD) && !hasTag(TAG_ERROR))

    type(TAG_CODE, "123456")
    onNodeWithText("Verify").performClick()
    until("verify-totp") { rig.to("two-factor/verify-totp").size == 1 }
    assertEquals("""{"code":"123456"}""", rig.to("two-factor/verify-totp").single().body)
    until("banner") { hasTag(TAG_ERROR) }
    assertTrue("INVALID_CODE" in errorText() && hasTag(TAG_CODE))

    onNodeWithText("Use a backup code").performClick()
    until("toggled") { hasText("Use authenticator code") }
    assertEquals("", fieldText(TAG_CODE))
    type(TAG_CODE, "bk-7777")
    onNodeWithText("Verify").performClick()
    until("signed in") { vm.state.value.isSignedIn }
    assertEquals("""{"code":"bk-7777"}""", rig.to("two-factor/verify-backup-code").single().body)
  }

  @Test
  fun backToSignInLeavesTheChallenge() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-in/email", Reply(TWO_FACTOR_REPLY))
    val vm = panel(rig)
    signIn("pw-2fa-5")
    until("code field") { hasTag(TAG_CODE) }
    onNodeWithText("Back to sign in").performClick()
    until("form") { vm.state.value.mode == AuthMode.SIGN_IN && hasTag(TAG_EMAIL) }
    assertTrue(!hasTag(TAG_CODE))
  }

  @Test
  fun passkeyButtonRendersOnlyWhenSupported() = runDesktopTest {
    val rig = FdsTestRig()
    setVmContent({ rig.viewModel(it) }) { PasskeySignInButton(it, PasskeyCapability.Unsupported) }
    assertTrue(!hasText("Sign in with a passkey"))
  }

  @Test
  fun passkeyButtonIsDisabledWhileLoadingOrLockedOut() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-in/email", Reply("""{"code":"SLOW_DOWN"}""", 429, listOf("Retry-After" to "30")))
    val gate = rig.gate("sign-in/email")
    val vm =
        setVmContent({ rig.viewModel(it) }) { PasskeySignInButton(it, PasskeyCapability.Supported) }
    onNodeWithText("Sign in with a passkey").assertIsEnabled()
    dispatch(vm, AuthUiIntent.EmailChanged("first@b.com"))
    dispatch(vm, AuthUiIntent.PasswordChanged("pw-passkey-2"))
    dispatch(vm, AuthUiIntent.SubmitAuth)
    until("loading") { vm.state.value.isLoading }
    onNodeWithText("Sign in with a passkey").assertIsNotEnabled()
    gate.complete(Unit)
    until("locked out") { vm.state.value.isLockedOut && !vm.state.value.isLoading }
    onNodeWithText("Sign in with a passkey").assertIsNotEnabled()
  }

  @Test
  fun supportedPasskeyButtonReportsUnsupportedWithoutAClient() = runDesktopTest {
    val rig = FdsTestRig()
    val vm =
        setVmContent({ rig.viewModel(it) }) { PasskeySignInButton(it, PasskeyCapability.Supported) }
    onNodeWithText("Sign in with a passkey").performClick()
    until("error") { vm.state.value.error?.code == AuthUiErrorCodes.PASSKEY_UNSUPPORTED }
  }

  @Test
  fun socialButtonsFollowTheProviderList() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-in/social", Reply(social))
    val vm = panel(rig)
    onNodeWithText("Continue with Google").assertIsEnabled()
    onNodeWithText("Continue with GitHub").performClick()
    until("social request") { rig.to("sign-in/social").size == 1 }
    assertTrue("\"provider\":\"github\"" in rig.to("sign-in/social").single().body)
    until("denied") { hasTag(TAG_ERROR) }
    assertTrue("access_denied" in errorText() && vm.state.value.error?.code == "access_denied")
  }

  @Test
  fun googleButtonSendsGoogle() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("sign-in/social", Reply(social))
    panel(rig)
    onNodeWithText("Continue with Google").performClick()
    until("social request") { rig.to("sign-in/social").size == 1 }
    assertTrue("\"provider\":\"google\"" in rig.to("sign-in/social").single().body)
  }

  @Test
  fun providerListDecidesTheButtons() = runDesktopTest {
    panel(FdsTestRig(), providers = emptyList())
    assertTrue(!hasText("Continue with Google") && !hasText("Continue with GitHub"))
  }

  @Test
  fun unknownProviderShowsItsId() = runDesktopTest {
    panel(FdsTestRig(), providers = listOf("apple"))
    assertTrue(hasText("Continue with apple") && !hasText("Continue with Google"))
  }

  @Test
  fun checklistFollowsEachRule() = runDesktopTest {
    val cases =
        mapOf(
            "abcdefgh" to listOf(0),
            "abcdefghijkl" to listOf(0, 1),
            "Abcdefgh" to listOf(0, 2),
            "abcdefg1" to listOf(0, 3),
            "abcdefg!" to listOf(0, 4),
            "Ab1!" to listOf(2, 3, 4),
        )
    var password by mutableStateOf("")
    setContent {
      FediimTheme(darkTheme = false) {
        AuthPanelContent(
            AuthUiState(mode = AuthMode.REGISTER),
            password,
            {},
            PasskeyCapability.Unsupported,
            emptyList(),
            null,
        )
      }
    }
    for ((typed, on) in cases) {
      runOnUiThread { password = typed }
      waitForIdle()
      for (rule in 0..4) {
        val node = onNodeWithTag(TAG_RULE_PREFIX + rule)
        if (rule in on) node.assertIsOn() else node.assertIsOff()
      }
    }
  }

  @Test
  fun meterReadsTheStateScoreNotTheLocalRules() = runDesktopTest {
    content(AuthUiState(mode = AuthMode.REGISTER, passwordStrength = 3), "Abcdefgh")
    onNodeWithContentDescription("Password strength 3 of 4").assertExists()
  }

  @Test
  fun lockoutAndLoadingDisableEveryAction() = runDesktopTest {
    val locked = AuthUiState(isLockedOut = true, lockoutSecondsRemaining = 30)
    content(locked)
    assertTrue(
        "30" in
            onNodeWithTag(TAG_LOCKOUT)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString()
    )
    assertActionsEnabled(false)
  }

  @Test
  fun loadingAloneDisablesActionsWithoutLockoutBanner() = runDesktopTest {
    content(AuthUiState(isLoading = true))
    assertActionsEnabled(false)
    assertTrue(!hasTag(TAG_LOCKOUT))
  }

  @Test
  fun idleStateEnablesActionsAndShowsNoError() = runDesktopTest {
    content(AuthUiState(error = null))
    assertActionsEnabled(true)
    assertTrue(!hasTag(TAG_ERROR) && !hasTag(TAG_LOCKOUT))
  }

  @Test
  fun errorBannerPrefersMessageOverCode() = runDesktopTest {
    content(AuthUiState(error = BetterAuthError(code = "CODE_X", message = "Message Y")))
    assertTrue("Message Y" in errorText() && "CODE_X" !in errorText())
  }

  @Test
  fun errorBannerFallsBackToCode() = runDesktopTest {
    content(AuthUiState(error = BetterAuthError(code = "CODE_Z")))
    assertTrue("CODE_Z" in errorText())
  }

  @Test
  fun dialogEscapeCallsOnDismissRequestOnce() = runDesktopTest {
    var dismissed = 0
    val rig = FdsTestRig()
    setVmContent({ rig.viewModel(it) }) { BetterAuthDialog(it, { dismissed++ }) }
    onNodeWithTag(TAG_EMAIL).assertExists()
    onNodeWithTag(TAG_EMAIL).requestFocus()
    onNodeWithTag(TAG_EMAIL).performKeyInput { pressKey(Key.Escape) }
    waitForIdle()
    assertEquals(1, dismissed)
  }

  private fun ComposeUiTest.reachChallenge(
      rig: FdsTestRig,
      onDismiss: () -> Unit,
  ): BetterAuthViewModel {
    rig.on("sign-in/email", Reply(TWO_FACTOR_REPLY))
    val vm = setVmContent({ rig.viewModel(it) }) { TwoFactorDialog(it, onDismiss) }
    dispatch(vm, AuthUiIntent.EmailChanged("first@b.com"))
    dispatch(vm, AuthUiIntent.PasswordChanged("pw-t13-6"))
    assertTrue(!hasTag(TAG_CODE))
    dispatch(vm, AuthUiIntent.SubmitAuth)
    until("code field") { hasTag(TAG_CODE) }
    return vm
  }

  @Test
  fun twoFactorDialogVerifiesAndDisappearsWithoutDismissing() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("two-factor/verify-totp", Reply(TOKEN_REPLY))
    var dismissed = 0
    val vm = reachChallenge(rig) { dismissed++ }
    type(TAG_CODE, "246810")
    onNodeWithText("Verify").performClick()
    until("signed in") { vm.state.value.isSignedIn && !hasTag(TAG_CODE) }
    assertEquals("""{"code":"246810"}""", rig.to("two-factor/verify-totp").single().body)
    assertEquals(0, dismissed)
  }

  @Test
  fun twoFactorDialogEscapeCallsOnDismissRequestOnce() = runDesktopTest {
    var dismissed = 0
    reachChallenge(FdsTestRig()) { dismissed++ }
    onNodeWithTag(TAG_CODE).requestFocus()
    onNodeWithTag(TAG_CODE).performKeyInput { pressKey(Key.Escape) }
    waitForIdle()
    assertEquals(1, dismissed)
  }

  @Test
  fun twoFactorDialogBackToSignInHidesItWithoutDismissing() = runDesktopTest {
    var dismissed = 0
    val vm = reachChallenge(FdsTestRig()) { dismissed++ }
    onNodeWithText("Back to sign in").performClick()
    until("hidden") { vm.state.value.mode == AuthMode.SIGN_IN && !hasTag(TAG_CODE) }
    assertEquals(0, dismissed)
  }

  @Test
  fun twoFactorActionsAreDisabledWhileLoadingOrLockedOut() = runDesktopTest {
    var state by mutableStateOf(AuthUiState(mode = AuthMode.TWO_FACTOR_CHALLENGE))
    setContent {
      FediimTheme(darkTheme = false) {
        AuthPanelContent(state, "", {}, PasskeyCapability.Unsupported, emptyList(), null)
      }
    }
    val actions = listOf("Verify", "Use a backup code", "Back to sign in")
    actions.forEach { onNodeWithText(it).assertIsEnabled() }
    for (busy in listOf(AuthUiState(isLoading = true), AuthUiState(isLockedOut = true))) {
      runOnUiThread { state = busy.copy(mode = AuthMode.TWO_FACTOR_CHALLENGE) }
      waitForIdle()
      actions.forEach { onNodeWithText(it).assertIsNotEnabled() }
    }
  }

  private fun ComposeUiTest.content(state: AuthUiState, password: String = "") = setContent {
    FediimTheme(darkTheme = false) {
      AuthPanelContent(
          state,
          password,
          {},
          PasskeyCapability.Supported,
          listOf("google", "github"),
          null,
      )
    }
  }

  private fun ComposeUiTest.assertActionsEnabled(enabled: Boolean) {
    val nodes =
        listOf(
            onNodeWithTag(TAG_SUBMIT),
            onNodeWithText("Continue with Google"),
            onNodeWithText("Continue with GitHub"),
            onNodeWithText("Sign in with a passkey"),
        )
    nodes.forEach { if (enabled) it.assertIsEnabled() else it.assertIsNotEnabled() }
  }
}
