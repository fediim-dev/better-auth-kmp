package dev.fediim.auth.fds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.fediim.auth.compose.AuthMode
import dev.fediim.auth.compose.AuthUiIntent
import dev.fediim.auth.compose.AuthUiState
import dev.fediim.auth.compose.BetterAuthViewModel
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.fds.components.Banner
import dev.fediim.fds.components.BannerTone
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonVariant
import dev.fediim.fds.components.Card
import dev.fediim.fds.components.SegmentedControl
import dev.fediim.fds.components.TextField
import dev.fediim.fds.components.TextFieldType
import dev.fediim.fds.tokens.FediimLayout
import dev.fediim.fds.tokens.FediimSpace

internal const val TAG_EMAIL = "fds.email"
internal const val TAG_NAME = "fds.name"
internal const val TAG_PASSWORD = "fds.password"
internal const val TAG_SUBMIT = "fds.submit"
internal const val TAG_ERROR = "fds.error"
internal const val TAG_LOCKOUT = "fds.lockout"
internal const val TAG_CODE = "fds.code"
internal const val TAG_RULE_PREFIX = "fds.rule."

/**
 * A window `Dialog` holding an FDS `Card` (scrollable when taller than the window); Esc inside it
 * calls [onDismissRequest]. FDS 1.0.0 has no content-slot dialog, so this copies the Esc handling
 * of its confirm dialog.
 */
@Composable
internal fun FdsDialogFrame(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Dialog(
      onDismissRequest = onDismissRequest,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
        Modifier.fillMaxSize().padding(FediimSpace.Space6),
        contentAlignment = Alignment.Center,
    ) {
      Card(
          modifier.widthIn(max = FediimLayout.Sheet).fillMaxWidth().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
              onDismissRequest()
              true
            } else {
              false
            }
          }
      ) {
        Column(Modifier.verticalScroll(rememberScrollState())) { content() }
      }
    }
  }
}

/** An FDS [Banner] with the (empty) icon slot filled, as FDS 1.0.0 ships no icon set. */
@Composable
internal fun FdsBanner(
    title: String,
    body: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
) {
  Banner(title = title, body = body, tone = tone, modifier = modifier.fillMaxWidth(), icon = {})
}

/** The lockout (Warning) and error (Critical, `message ?: code`) banners of [state]. */
@Composable
internal fun AuthBanners(state: AuthUiState) {
  if (state.isLockedOut) {
    FdsBanner(
        "Too many attempts",
        "Try again in ${state.lockoutSecondsRemaining} seconds.",
        BannerTone.Warning,
        Modifier.testTag(TAG_LOCKOUT),
    )
  }
  state.error?.let {
    FdsBanner(
        "Something went wrong",
        it.message ?: it.code ?: "UNKNOWN",
        BannerTone.Critical,
        Modifier.testTag(TAG_ERROR),
    )
  }
}

/**
 * Shows [BetterAuthPanel] in a modal window. Esc calls [onDismissRequest]. It never closes itself:
 * stop composing it, for example when `state.isSignedIn`. Must be inside `FediimTheme`.
 */
@Composable
public fun BetterAuthDialog(
    viewModel: BetterAuthViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    passkeyCapability: PasskeyCapability = PasskeyCapability.Unsupported,
    socialProviders: List<String> = listOf("google", "github"),
    socialCallbackUrl: String? = null,
) {
  FdsDialogFrame(onDismissRequest, modifier) {
    BetterAuthPanel(viewModel, Modifier, passkeyCapability, socialProviders, socialCallbackUrl)
  }
}

/**
 * The sign-in / register form over [viewModel], embeddable anywhere inside `FediimTheme`.
 *
 * The Sign in / Register switch dispatches `SetMode`; the Email, Name (register only) and Password
 * fields dispatch their intents. The password lives in composition memory only, never in saved
 * state; it is cleared when `isSignedIn` turns true, and submit sends the field's text, not an
 * older copy held by the view model. The password checklist and strength meter show in register
 * mode only (the meter is `state.passwordStrength`). In `TWO_FACTOR_CHALLENGE` the form is replaced
 * by the code step. [socialProviders] gets one "Continue with <label>" button per id (Google and
 * GitHub are labelled, other ids show as given; an empty list shows none) that dispatches
 * `SignInSocial` with [socialCallbackUrl]; [passkeyCapability] decides the passkey button as in
 * [PasskeySignInButton]. Every action is disabled while `isLoading` or `isLockedOut`. A lockout
 * shows a Warning banner with the seconds left; `error` shows a Critical banner with `message ?:
 * code`. It never collects `viewModel.effects`. [socialCallbackUrl] must be an origin the server
 * trusts; never pass a user-controlled value.
 */
@Composable
public fun BetterAuthPanel(
    viewModel: BetterAuthViewModel,
    modifier: Modifier = Modifier,
    passkeyCapability: PasskeyCapability = PasskeyCapability.Unsupported,
    socialProviders: List<String> = listOf("google", "github"),
    socialCallbackUrl: String? = null,
) {
  val state by viewModel.state.collectAsState()
  var password by remember { mutableStateOf("") }
  val signedIn = state.isSignedIn
  var wasSignedIn by remember { mutableStateOf(signedIn) }
  LaunchedEffect(signedIn) {
    if (signedIn && !wasSignedIn) {
      password = ""
      viewModel.dispatch(AuthUiIntent.PasswordChanged(""))
    }
    wasSignedIn = signedIn
  }
  AuthPanelContent(
      state = state,
      password = password,
      onIntent = { intent ->
        when (intent) {
          is AuthUiIntent.PasswordChanged -> {
            password = intent.password
            viewModel.dispatch(intent)
          }
          AuthUiIntent.SubmitAuth -> {
            viewModel.dispatch(AuthUiIntent.PasswordChanged(password))
            viewModel.dispatch(AuthUiIntent.SubmitAuth)
          }
          else -> viewModel.dispatch(intent)
        }
      },
      passkeyCapability = passkeyCapability,
      socialProviders = socialProviders,
      socialCallbackUrl = socialCallbackUrl,
      modifier = modifier,
  )
}

private fun providerLabel(id: String): String =
    when (id) {
      "google" -> "Google"
      "github" -> "GitHub"
      else -> id
    }

/** Stateless body of [BetterAuthPanel]; [password] is the field's current text. */
@Composable
internal fun AuthPanelContent(
    state: AuthUiState,
    password: String,
    onIntent: (AuthUiIntent) -> Unit,
    passkeyCapability: PasskeyCapability,
    socialProviders: List<String>,
    socialCallbackUrl: String?,
    modifier: Modifier = Modifier,
) {
  val register = state.mode == AuthMode.REGISTER
  val enabled = !state.isLoading && !state.isLockedOut
  Column(
      modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(FediimSpace.Space4),
  ) {
    if (state.mode == AuthMode.TWO_FACTOR_CHALLENGE) {
      TwoFactorStep(state, onIntent)
      return@Column
    }
    SegmentedControl(
        label = "Account mode",
        options = listOf("Sign in", "Register"),
        selectedIndex = if (register) 1 else 0,
        onSelectedIndexChange = {
          onIntent(AuthUiIntent.SetMode(if (it == 1) AuthMode.REGISTER else AuthMode.SIGN_IN))
        },
    )
    if (register) {
      TextField(
          value = state.name,
          onValueChange = { onIntent(AuthUiIntent.NameChanged(it)) },
          label = "Name",
          modifier = Modifier.fillMaxWidth().testTag(TAG_NAME),
      )
    }
    TextField(
        value = state.email,
        onValueChange = { onIntent(AuthUiIntent.EmailChanged(it)) },
        label = "Email",
        type = TextFieldType.Email,
        modifier = Modifier.fillMaxWidth().testTag(TAG_EMAIL),
    )
    TextField(
        value = password,
        onValueChange = { onIntent(AuthUiIntent.PasswordChanged(it)) },
        label = "Password",
        type = TextFieldType.Password,
        modifier = Modifier.fillMaxWidth().testTag(TAG_PASSWORD),
    )
    if (register) {
      PasswordStrengthMeter(state.passwordStrength)
      PasswordChecklist(password)
    }
    AuthBanners(state)
    Button(
        onClick = { onIntent(AuthUiIntent.SubmitAuth) },
        variant = ButtonVariant.Primary,
        modifier = Modifier.fillMaxWidth().testTag(TAG_SUBMIT),
        enabled = enabled,
        text = if (register) "Create account" else "Sign in with email",
    )
    socialProviders.forEach { id ->
      Button(
          onClick = { onIntent(AuthUiIntent.SignInSocial(id, socialCallbackUrl)) },
          variant = ButtonVariant.Secondary,
          modifier = Modifier.fillMaxWidth(),
          enabled = enabled,
          text = "Continue with ${providerLabel(id)}",
      )
    }
    PasskeyButton(
        passkeyCapability,
        enabled,
        { onIntent(AuthUiIntent.SignInPasskey) },
        Modifier.fillMaxWidth(),
    )
  }
}
