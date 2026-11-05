package dev.fediim.auth.fds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.fediim.auth.compose.AuthMode
import dev.fediim.auth.compose.AuthUiIntent
import dev.fediim.auth.compose.AuthUiState
import dev.fediim.auth.compose.BetterAuthViewModel
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonVariant
import dev.fediim.fds.components.TextField
import dev.fediim.fds.components.TextFieldType
import dev.fediim.fds.theme.FediimTheme
import dev.fediim.fds.tokens.FediimSpace

/**
 * A standalone modal for the second sign-in step, for apps that build their own sign-in form. It
 * shows the authenticator / backup-code step only while `state.mode == TWO_FACTOR_CHALLENGE` and
 * renders nothing otherwise. Verify dispatches `SubmitTwoFactor`; "Back to sign in" dispatches
 * `SetMode(SIGN_IN)`, which hides it. Esc calls [onDismissRequest]; it never calls it itself and
 * simply disappears when the mode leaves the challenge. [BetterAuthDialog] shows the same step
 * inline, so do not compose both. Must be inside `FediimTheme`.
 */
@Composable
public fun TwoFactorDialog(
    viewModel: BetterAuthViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  if (state.mode == AuthMode.TWO_FACTOR_CHALLENGE) {
    FdsDialogFrame(onDismissRequest, modifier) { TwoFactorStep(state, viewModel::dispatch) }
  }
}

/**
 * The code step: an authenticator code or, after the toggle, a backup code; Verify; and Back to
 * sign in. Every action is disabled while `isLoading` or `isLockedOut`.
 */
@Composable
internal fun TwoFactorStep(
    state: AuthUiState,
    onIntent: (AuthUiIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
  var code by remember { mutableStateOf("") }
  var useBackupCode by remember { mutableStateOf(false) }
  val enabled = !state.isLoading && !state.isLockedOut
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FediimSpace.Space4)) {
    Text(
        "Two-step verification",
        style = FediimTheme.typography.headlineSm,
        color = FediimTheme.colors.textStrong,
    )
    Text(
        if (useBackupCode) "Enter one of your unused backup codes."
        else "Enter the code from your authenticator app.",
        style = FediimTheme.typography.body,
        color = FediimTheme.colors.textSecondary,
    )
    AuthBanners(state)
    TextField(
        value = code,
        onValueChange = { code = it },
        label = if (useBackupCode) "Backup code" else "Authenticator code",
        type = if (useBackupCode) TextFieldType.Text else TextFieldType.Number,
        modifier = Modifier.fillMaxWidth().testTag(TAG_CODE),
    )
    Button(
        onClick = { onIntent(AuthUiIntent.SubmitTwoFactor(code, useBackupCode)) },
        variant = ButtonVariant.Primary,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        text = "Verify",
    )
    Button(
        onClick = {
          useBackupCode = !useBackupCode
          code = ""
        },
        variant = ButtonVariant.Ghost,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        text = if (useBackupCode) "Use authenticator code" else "Use a backup code",
    )
    Button(
        onClick = { onIntent(AuthUiIntent.SetMode(AuthMode.SIGN_IN)) },
        variant = ButtonVariant.Ghost,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        text = "Back to sign in",
    )
  }
}
