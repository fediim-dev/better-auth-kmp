package dev.fediim.auth.sample.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.fediim.auth.compose.ApiKeyViewModel
import dev.fediim.auth.compose.AuthUiIntent
import dev.fediim.auth.compose.BetterAuthViewModel
import dev.fediim.auth.compose.OrganizationViewModel
import dev.fediim.auth.fds.OrganizationSwitcher
import dev.fediim.auth.sample.presentation.viewmodel.SampleWorkspaceViewModel
import dev.fediim.auth.sample.presentation.viewmodel.WorkspaceTab
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonSize
import dev.fediim.fds.components.ButtonVariant
import dev.fediim.fds.components.CodeBlock
import dev.fediim.fds.components.SegmentedControl
import dev.fediim.fds.components.TextField
import dev.fediim.fds.theme.FediimTheme
import dev.fediim.fds.tokens.FediimSize
import dev.fediim.fds.tokens.FediimSpace

/**
 * Screen presented when signed in, hosting organization switcher, overview tabs, passkeys, and API
 * keys.
 */
@Composable
public fun AuthenticatedWorkspaceScreen(
    authViewModel: BetterAuthViewModel,
    organizationViewModel: OrganizationViewModel,
    apiKeyViewModel: ApiKeyViewModel,
    workspaceViewModel: SampleWorkspaceViewModel,
    onOpenTwoFactorSetup: () -> Unit,
    onOpenApiKeyManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by workspaceViewModel.state.collectAsState()
  val sessionOverview = uiState.sessionOverview
  var newPasskeyName by remember { mutableStateOf("") }

  Column(
      modifier = modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(FediimSpace.Space3),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      OrganizationSwitcher(
          viewModel = organizationViewModel,
          currentUserId = sessionOverview?.userId,
      )

      Row(horizontalArrangement = Arrangement.spacedBy(FediimSpace.Space2)) {
        Button(
            text = "API Keys",
            onClick = onOpenApiKeyManager,
            size = ButtonSize.Small,
            variant = ButtonVariant.Secondary,
        )
        Button(
            text = "Sign Out",
            onClick = { authViewModel.dispatch(AuthUiIntent.Logout) },
            size = ButtonSize.Small,
            variant = ButtonVariant.Secondary,
        )
      }
    }

    SegmentedControl(
        label = "Workspace View",
        options = listOf("Overview", "Security & Keys"),
        selectedIndex = if (uiState.selectedTab == WorkspaceTab.OVERVIEW) 0 else 1,
        onSelectedIndexChange = {
          workspaceViewModel.selectTab(
              if (it == 0) WorkspaceTab.OVERVIEW else WorkspaceTab.SECURITY
          )
        },
    )

    when (uiState.selectedTab) {
      WorkspaceTab.OVERVIEW -> {
        Text(
            text = "User Profile & Session",
            style = FediimTheme.typography.title,
            color = FediimTheme.colors.textPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(FediimSpace.Space1)) {
          Text(
              text =
                  "User: ${sessionOverview?.user?.name ?: "(no name)"} (${sessionOverview?.user?.email ?: ""})",
              style = FediimTheme.typography.body,
              color = FediimTheme.colors.textPrimary,
          )
          Text(
              text = "Email Verified: ${sessionOverview?.user?.emailVerified == true}",
              style = FediimTheme.typography.bodySm,
              color = FediimTheme.colors.textSecondary,
          )
          Text(
              text = "Masked Token: ${sessionOverview?.maskedToken ?: "(none)"}",
              style = FediimTheme.typography.bodySm,
              color = FediimTheme.colors.textSecondary,
          )
        }

        Text(
            text = "User Additional Fields",
            style = FediimTheme.typography.label,
            color = FediimTheme.colors.textPrimary,
        )
        CodeBlock(
            sessionOverview?.user?.additionalFields?.toString() ?: "{}",
            "User Fields",
            lineNumbers = false,
            wrap = true,
        )

        Text(
            text = "Session Additional Fields",
            style = FediimTheme.typography.label,
            color = FediimTheme.colors.textPrimary,
        )
        CodeBlock(
            sessionOverview?.sessionAdditionalFields?.toString() ?: "{}",
            "Session Fields",
            lineNumbers = false,
            wrap = true,
        )
      }

      WorkspaceTab.SECURITY -> {
        Text(
            text = "Two-Factor Authentication",
            style = FediimTheme.typography.title,
            color = FediimTheme.colors.textPrimary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text =
                  if (uiState.twoFactorStatus.isEnabled) "Status: Enabled" else "Status: Disabled",
              style = FediimTheme.typography.body,
              color = FediimTheme.colors.textPrimary,
          )

          if (uiState.twoFactorStatus.isEnabled) {
            Button(
                text = "Disable 2FA",
                onClick = { workspaceViewModel.disableTwoFactor("Password1!") },
                size = ButtonSize.Small,
                variant = ButtonVariant.Danger,
            )
          } else {
            Button(
                text = "Setup 2FA",
                onClick = onOpenTwoFactorSetup,
                size = ButtonSize.Small,
                variant = ButtonVariant.Primary,
            )
          }
        }

        Text(
            text = "Passkeys",
            style = FediimTheme.typography.title,
            color = FediimTheme.colors.textPrimary,
        )

        if (uiState.passkeys.isEmpty()) {
          Text(
              text = "No passkeys registered",
              style = FediimTheme.typography.bodySm,
              color = FediimTheme.colors.textSecondary,
          )
        } else {
          uiState.passkeys.forEach { pk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                    text = pk.name,
                    style = FediimTheme.typography.body,
                    color = FediimTheme.colors.textPrimary,
                )
                Text(
                    text = "ID: ${pk.credentialId} | Transports: ${pk.transports ?: "none"}",
                    style = FediimTheme.typography.bodySm,
                    color = FediimTheme.colors.textSecondary,
                )
              }
              Button(
                  text = "Delete",
                  onClick = { workspaceViewModel.deletePasskey(pk.id) },
                  size = ButtonSize.Small,
                  variant = ButtonVariant.Secondary,
              )
            }
          }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FediimSpace.Space2),
            verticalAlignment = Alignment.Bottom,
        ) {
          TextField(
              value = newPasskeyName,
              onValueChange = { newPasskeyName = it },
              label = "Passkey Name",
              modifier = Modifier.weight(1f).testTag("sample.passkeyName"),
          )
          CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides FediimSize.Touch) {
            Button(
                text = "Register Passkey",
                onClick = {
                  val name = newPasskeyName.ifBlank { "My Security Key" }
                  workspaceViewModel.registerPasskey(name)
                  newPasskeyName = ""
                },
                size = ButtonSize.Medium,
                variant = ButtonVariant.Secondary,
            )
          }
        }
      }
    }
  }
}
