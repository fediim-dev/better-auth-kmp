package dev.fediim.auth.fds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.fediim.auth.compose.ApiKeyUiIntent
import dev.fediim.auth.compose.ApiKeyUiState
import dev.fediim.auth.compose.ApiKeyViewModel
import dev.fediim.auth.core.apikey.ApiKeyRecord
import dev.fediim.auth.core.apikey.CreateApiKeyRequest
import dev.fediim.fds.components.BannerTone
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonSize
import dev.fediim.fds.components.ButtonVariant
import dev.fediim.fds.components.CodeBlock
import dev.fediim.fds.components.Dialog as ConfirmDialog
import dev.fediim.fds.components.TextField
import dev.fediim.fds.theme.FediimTheme
import dev.fediim.fds.tokens.FediimSpace

internal const val TAG_KEY_NAME = "fds.key.name"
internal const val TAG_KEY_SECRET = "fds.key.secret"
internal const val TAG_REVOKE_PREFIX = "fds.key.revoke."

/**
 * Dispatches `Load` each time it enters composition and lists the keys (name or id, and `start`).
 * "Create key" dispatches `Create` with the trimmed name, or none when blank, and is disabled while
 * a request runs or a new secret is shown (press Done first). The new secret shows once in a
 * Warning banner with a copyable code block until "Done" (`DismissCreatedKey`) or until the dialog
 * leaves composition (also `DismissCreatedKey`). "Revoke" asks a destructive confirmation, then
 * dispatches `Revoke(id)`; it is disabled while a request runs. Failures show a Critical banner
 * with `message ?: code`. "Close" and Esc call [onDismissRequest]. The secret is dropped when the
 * composition is disposed, including on an Android configuration change, by design (never in saved
 * state); the host app should set `FLAG_SECURE` or the platform equivalent if it needs screenshot
 * protection. Must be inside `FediimTheme`.
 */
@Composable
public fun ApiKeyManagerDialog(
    viewModel: ApiKeyViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  LaunchedEffect(viewModel) { viewModel.dispatch(ApiKeyUiIntent.Load) }
  DisposableEffect(viewModel) { onDispose { viewModel.dispatch(ApiKeyUiIntent.DismissCreatedKey) } }
  FdsDialogFrame(onDismissRequest, modifier) {
    ApiKeyManagerContent(state, viewModel::dispatch, onDismissRequest)
  }
}

/** Stateless body of [ApiKeyManagerDialog]. */
@Composable
internal fun ApiKeyManagerContent(
    state: ApiKeyUiState,
    onIntent: (ApiKeyUiIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var name by remember { mutableStateOf("") }
  var pendingRevoke by remember { mutableStateOf<ApiKeyRecord?>(null) }
  val colors = FediimTheme.colors
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FediimSpace.Space4)) {
    Text("API keys", style = FediimTheme.typography.headlineSm, color = colors.textStrong)
    state.error?.let {
      FdsBanner(
          "Something went wrong",
          it.message ?: it.code ?: "UNKNOWN",
          BannerTone.Critical,
          Modifier.testTag(TAG_ERROR),
      )
    }
    state.createdKey?.let { created ->
      FdsBanner(
          "Copy your new key now",
          "It is shown only once. Store it somewhere safe.",
          BannerTone.Warning,
          Modifier.testTag(TAG_KEY_SECRET),
      )
      CodeBlock(created.key, "API key", lineNumbers = false, wrap = true)
      Button(
          onClick = { onIntent(ApiKeyUiIntent.DismissCreatedKey) },
          variant = ButtonVariant.Secondary,
          text = "Done",
      )
    }
    if (state.keys.isEmpty()) {
      Text("No keys yet.", style = FediimTheme.typography.body, color = colors.textSecondary)
    }
    state.keys.forEach { key ->
      Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(FediimSpace.Space3),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(key.name ?: key.id, style = FediimTheme.typography.body, color = colors.textPrimary)
          key.start?.let {
            Text(it, style = FediimTheme.typography.bodySm, color = colors.textSecondary)
          }
        }
        Button(
            onClick = { pendingRevoke = key },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.testTag(TAG_REVOKE_PREFIX + key.id),
            size = ButtonSize.Small,
            enabled = !state.isLoading,
            text = "Revoke",
        )
      }
    }
    TextField(
        value = name,
        onValueChange = { name = it },
        label = "Key name",
        modifier = Modifier.fillMaxWidth().testTag(TAG_KEY_NAME),
    )
    Button(
        onClick = {
          onIntent(ApiKeyUiIntent.Create(CreateApiKeyRequest(name = name.trim().ifEmpty { null })))
        },
        variant = ButtonVariant.Primary,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading && state.createdKey == null,
        text = "Create key",
    )
    Button(
        onClick = onClose,
        variant = ButtonVariant.Ghost,
        modifier = Modifier.fillMaxWidth(),
        text = "Close",
    )
  }
  pendingRevoke?.let { key ->
    ConfirmDialog(
        title = "Revoke this key?",
        body = "Anything using ${key.name ?: key.id} stops working at once.",
        confirmLabel = "Revoke key",
        onConfirm = {
          pendingRevoke = null
          onIntent(ApiKeyUiIntent.Revoke(key.id))
        },
        dismissLabel = "Cancel",
        onDismiss = { pendingRevoke = null },
        destructive = true,
    )
  }
}
