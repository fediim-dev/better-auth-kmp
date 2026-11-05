package dev.fediim.auth.fds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.fediim.auth.compose.AuthUiIntent
import dev.fediim.auth.compose.BetterAuthViewModel
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonVariant

/**
 * Renders nothing for [PasskeyCapability.Unsupported]; otherwise a Secondary button that dispatches
 * `SignInPasskey`, disabled while the view model is loading or locked out. Must be inside
 * `FediimTheme`.
 */
@Composable
public fun PasskeySignInButton(
    viewModel: BetterAuthViewModel,
    capability: PasskeyCapability,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  PasskeyButton(
      capability,
      enabled = !state.isLoading && !state.isLockedOut,
      onClick = { viewModel.dispatch(AuthUiIntent.SignInPasskey) },
      modifier = modifier,
  )
}

/** Stateless passkey button, reused by the panel; nothing for an unsupported [capability]. */
@Composable
internal fun PasskeyButton(
    capability: PasskeyCapability,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  if (capability == PasskeyCapability.Unsupported) return
  Button(
      onClick = onClick,
      variant = ButtonVariant.Secondary,
      modifier = modifier,
      enabled = enabled,
      text = "Sign in with a passkey",
  )
}
