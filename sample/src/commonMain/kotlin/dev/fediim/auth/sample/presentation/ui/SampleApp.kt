package dev.fediim.auth.sample.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fediim.auth.fds.ApiKeyManagerDialog
import dev.fediim.auth.fds.BetterAuthDialog
import dev.fediim.auth.fds.TwoFactorSetupDialog
import dev.fediim.auth.sample.di.SampleAppContainer
import dev.fediim.auth.sample.di.rememberSampleAppContainer
import dev.fediim.auth.sample.domain.model.BackendMode
import dev.fediim.fds.components.Banner
import dev.fediim.fds.components.BannerTone
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonSize
import dev.fediim.fds.components.ButtonVariant
import dev.fediim.fds.components.SegmentedControl
import dev.fediim.fds.components.TextField
import dev.fediim.fds.theme.FediimTheme
import dev.fediim.fds.tokens.FediimSpace

/**
 * Root composable for the Clean Architecture KMP sample application.
 *
 * Always wraps content inside [FediimTheme]. Renders an environment selector top bar allowing
 * switching between [BackendMode.OFFLINE_DEMO] and [BackendMode.LIVE_SERVER]. Renders
 * [AuthenticatedWorkspaceScreen] when [dev.fediim.auth.compose.AuthUiState.isSignedIn] is true, and
 * [UnauthenticatedScreen] otherwise. Presents modal dialogs for [BetterAuthDialog],
 * [TwoFactorSetupDialog], and [ApiKeyManagerDialog] upon request.
 */
@Composable
public fun SampleApp(
    container: SampleAppContainer = rememberSampleAppContainer(),
    modifier: Modifier = Modifier,
) {
  val workspaceVm = container.workspaceViewModel
  val uiState by workspaceVm.state.collectAsState()
  val authVm = workspaceVm.authViewModel
  val authState by authVm.state.collectAsState()

  var showAuthDialog by remember { mutableStateOf(false) }
  var showTwoFactorDialog by remember { mutableStateOf(false) }
  var showApiKeyDialog by remember { mutableStateOf(false) }
  var liveUrlInput by
      remember(uiState.environment.liveServerUrl) {
        mutableStateOf(uiState.environment.liveServerUrl)
      }

  FediimTheme {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FediimTheme.colors.bgCanvas)
                .padding(FediimSpace.Space4),
        verticalArrangement = Arrangement.spacedBy(FediimSpace.Space3),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = "Better Auth KMP Sample",
            style = FediimTheme.typography.titleLg,
            color = FediimTheme.colors.textPrimary,
        )

        if (uiState.environment.mode == BackendMode.OFFLINE_DEMO) {
          Button(
              text = "Reset Demo",
              onClick = { workspaceVm.resetDemoState() },
              size = ButtonSize.Small,
              variant = ButtonVariant.Secondary,
          )
        }
      }

      var selectedBackendTab by remember {
        mutableStateOf(if (uiState.environment.mode == BackendMode.OFFLINE_DEMO) 0 else 1)
      }

      SegmentedControl(
          label = "Backend Environment",
          options = listOf("Offline Demo", "Live Server"),
          selectedIndex = selectedBackendTab,
          onSelectedIndexChange = { idx ->
            selectedBackendTab = idx
            if (idx == 0) {
              workspaceVm.switchEnvironment(BackendMode.OFFLINE_DEMO)
            }
          },
      )

      if (selectedBackendTab == 1) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FediimSpace.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          TextField(
              value = liveUrlInput,
              onValueChange = { liveUrlInput = it },
              label = "Live Server URL",
              modifier = Modifier.weight(1f),
          )
          Button(
              text = "Connect",
              onClick = { workspaceVm.switchEnvironment(BackendMode.LIVE_SERVER, liveUrlInput) },
              size = ButtonSize.Small,
              variant = ButtonVariant.Primary,
          )
        }
      }

      uiState.urlInputError?.let { err ->
        Banner(
            title = "Connection Error",
            body = err,
            tone = BannerTone.Critical,
            modifier = Modifier.fillMaxWidth(),
            icon = {},
        )
      }

      uiState.errorMessage?.let { err ->
        Banner(
            title = "Error",
            body = err,
            tone = BannerTone.Critical,
            modifier = Modifier.fillMaxWidth(),
            icon = {},
        )
      }

      if (authState.isSignedIn) {
        AuthenticatedWorkspaceScreen(
            authViewModel = authVm,
            organizationViewModel = workspaceVm.organizationViewModel,
            apiKeyViewModel = workspaceVm.apiKeyViewModel,
            workspaceViewModel = workspaceVm,
            onOpenTwoFactorSetup = { showTwoFactorDialog = true },
            onOpenApiKeyManager = { showApiKeyDialog = true },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
      } else {
        UnauthenticatedScreen(
            authViewModel = authVm,
            workspaceViewModel = workspaceVm,
            onOpenAuthDialog = { showAuthDialog = true },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
      }

      if (showAuthDialog && !authState.isSignedIn) {
        BetterAuthDialog(
            viewModel = authVm,
            onDismissRequest = { showAuthDialog = false },
            passkeyCapability = uiState.passkeyCapability,
            socialProviders = listOf("github", "google", "apple"),
        )
      }

      if (showTwoFactorDialog) {
        TwoFactorSetupDialog(
            twoFactor = container.client.twoFactor,
            onDismissRequest = { showTwoFactorDialog = false },
            onEnabled = {
              workspaceVm.refreshSecurity()
              showTwoFactorDialog = false
            },
        )
      }

      if (showApiKeyDialog) {
        ApiKeyManagerDialog(
            viewModel = workspaceVm.apiKeyViewModel,
            onDismissRequest = { showApiKeyDialog = false },
        )
      }
    }
  }
}
