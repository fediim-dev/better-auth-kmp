package dev.fediim.auth.sample.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.fediim.auth.compose.BetterAuthViewModel
import dev.fediim.auth.fds.BetterAuthPanel
import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import dev.fediim.auth.sample.domain.model.DemoPreset
import dev.fediim.auth.sample.presentation.viewmodel.SampleWorkspaceViewModel
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonSize
import dev.fediim.fds.components.ButtonVariant
import dev.fediim.fds.theme.FediimTheme
import dev.fediim.fds.tokens.FediimSpace

/**
 * Screen presented when signed out, hosting demo presets, failure simulators, and BetterAuthPanel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun UnauthenticatedScreen(
    authViewModel: BetterAuthViewModel,
    workspaceViewModel: SampleWorkspaceViewModel,
    onOpenAuthDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by workspaceViewModel.state.collectAsState()

  Column(
      modifier = modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(FediimSpace.Space3),
  ) {
    Text(
        text = "Offline Demo Presets",
        style = FediimTheme.typography.title,
        color = FediimTheme.colors.textPrimary,
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FediimSpace.Space2),
        verticalArrangement = Arrangement.spacedBy(FediimSpace.Space2),
    ) {
      Button(
          text = "Preset: Standard (Alice)",
          onClick = { workspaceViewModel.applyPreset(DemoPreset.STANDARD) },
          size = ButtonSize.Small,
          variant = ButtonVariant.Secondary,
      )
      Button(
          text = "Preset: 2FA (Bob)",
          onClick = { workspaceViewModel.applyPreset(DemoPreset.TWO_FACTOR) },
          size = ButtonSize.Small,
          variant = ButtonVariant.Secondary,
      )
      Button(
          text = "Preset: Passkey (Carol)",
          onClick = { workspaceViewModel.applyPreset(DemoPreset.PASSKEY) },
          size = ButtonSize.Small,
          variant = ButtonVariant.Secondary,
      )
    }

    Text(
        text = "Failure Scenario",
        style = FediimTheme.typography.title,
        color = FediimTheme.colors.textPrimary,
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FediimSpace.Space2),
        verticalArrangement = Arrangement.spacedBy(FediimSpace.Space2),
    ) {
      DemoFailureScenario.entries.forEach { scenario ->
        val selected = uiState.failureScenario == scenario
        Button(
            text = scenario.name.replace("_", " "),
            onClick = { workspaceViewModel.setFailureScenario(scenario) },
            size = ButtonSize.Small,
            variant = if (selected) ButtonVariant.Primary else ButtonVariant.Secondary,
        )
      }
    }

    Button(
        text = "Open Sign-In Modal",
        onClick = onOpenAuthDialog,
        modifier = Modifier.fillMaxWidth(),
        variant = ButtonVariant.Secondary,
    )

    BetterAuthPanel(
        viewModel = authViewModel,
        modifier = Modifier.fillMaxWidth(),
        passkeyCapability = uiState.passkeyCapability,
        socialProviders = listOf("github", "google", "apple"),
    )
  }
}
