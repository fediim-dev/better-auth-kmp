package dev.fediim.auth.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions
import dev.fediim.auth.compose.AuthMode
import dev.fediim.auth.compose.AuthUiIntent
import dev.fediim.auth.sample.di.SampleAppContainer
import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import dev.fediim.auth.sample.domain.model.DemoPreset
import dev.fediim.auth.sample.presentation.ui.AuthenticatedWorkspaceScreen
import dev.fediim.auth.sample.presentation.ui.UnauthenticatedScreen
import dev.fediim.auth.sample.presentation.viewmodel.WorkspaceTab
import dev.fediim.fds.theme.FediimTheme
import dev.fediim.fds.tokens.FediimSpace
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SampleScreenshotTest {
  private val options = RoborazziOptions(compareOptions = CompareOptions(changeThreshold = 0.01f))

  private fun shoot(
      name: String,
      waitCondition: (ComposeUiTest.() -> Boolean)? = null,
      content: @Composable () -> Unit,
  ) {
    for (dark in listOf(false, true)) {
      runDesktopComposeUiTest(width = 480, height = 720) {
        setContent {
          FediimTheme(darkTheme = dark) {
            Box(
                Modifier.fillMaxSize()
                    .background(FediimTheme.colors.bgCanvas)
                    .testTag("shot")
                    .padding(FediimSpace.Space4)
            ) {
              content()
            }
          }
        }
        waitForIdle()
        if (waitCondition != null) {
          waitUntil("settle", 5_000) { waitCondition() }
        }
        val suffix = if (dark) "dark" else "light"
        onNodeWithTag("shot").captureRoboImage("screenshots/$name-$suffix.png", options)
      }
    }
  }

  @Test
  fun unauthenticatedSignin() =
      shoot("unauthenticated-signin") {
        val scope = rememberCoroutineScope()
        val container = remember(scope) { SampleAppContainer(scope) }
        LaunchedEffect(container) { container.workspaceViewModel.applyPreset(DemoPreset.STANDARD) }
        UnauthenticatedScreen(
            authViewModel = container.workspaceViewModel.authViewModel,
            workspaceViewModel = container.workspaceViewModel,
            onOpenAuthDialog = {},
        )
      }

  @Test
  fun unauthenticatedRegister() =
      shoot("unauthenticated-register") {
        val scope = rememberCoroutineScope()
        val container = remember(scope) { SampleAppContainer(scope) }
        LaunchedEffect(container) {
          val vm = container.workspaceViewModel
          vm.authViewModel.dispatch(AuthUiIntent.SetMode(AuthMode.REGISTER))
          vm.authViewModel.dispatch(AuthUiIntent.EmailChanged("alice@example.com"))
        }
        UnauthenticatedScreen(
            authViewModel = container.workspaceViewModel.authViewModel,
            workspaceViewModel = container.workspaceViewModel,
            onOpenAuthDialog = {},
        )
      }

  @Test
  fun unauthenticatedTwofactor() =
      shoot("unauthenticated-twofactor") {
        val scope = rememberCoroutineScope()
        val container = remember(scope) { SampleAppContainer(scope) }
        LaunchedEffect(container) {
          val vm = container.workspaceViewModel
          vm.applyPreset(DemoPreset.TWO_FACTOR)
          vm.authViewModel.dispatch(AuthUiIntent.SubmitAuth)
        }
        UnauthenticatedScreen(
            authViewModel = container.workspaceViewModel.authViewModel,
            workspaceViewModel = container.workspaceViewModel,
            onOpenAuthDialog = {},
        )
      }

  @Test
  fun unauthenticatedLockout() =
      shoot("unauthenticated-lockout") {
        val scope = rememberCoroutineScope()
        val container = remember(scope) { SampleAppContainer(scope) }
        LaunchedEffect(container) {
          val vm = container.workspaceViewModel
          vm.setFailureScenario(DemoFailureScenario.RATE_LIMITED_429)
          vm.applyPreset(DemoPreset.STANDARD)
          vm.authViewModel.dispatch(AuthUiIntent.SubmitAuth)
        }
        UnauthenticatedScreen(
            authViewModel = container.workspaceViewModel.authViewModel,
            workspaceViewModel = container.workspaceViewModel,
            onOpenAuthDialog = {},
        )
      }

  @Test
  fun workspaceOverview() =
      shoot(
          name = "workspace-overview",
          waitCondition = {
            onAllNodesWithText("Masked Token: demo••••1234", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
          },
      ) {
        val scope = rememberCoroutineScope()
        val container = remember(scope) { SampleAppContainer(scope) }
        LaunchedEffect(container) {
          val vm = container.workspaceViewModel
          vm.applyPreset(DemoPreset.STANDARD)
          vm.authViewModel.dispatch(AuthUiIntent.SubmitAuth)
          vm.selectTab(WorkspaceTab.OVERVIEW)
        }
        AuthenticatedWorkspaceScreen(
            authViewModel = container.workspaceViewModel.authViewModel,
            organizationViewModel = container.workspaceViewModel.organizationViewModel,
            apiKeyViewModel = container.workspaceViewModel.apiKeyViewModel,
            workspaceViewModel = container.workspaceViewModel,
            onOpenTwoFactorSetup = {},
            onOpenApiKeyManager = {},
        )
      }

  @Test
  fun workspaceSecurity() =
      shoot(
          name = "workspace-security",
          waitCondition = {
            onAllNodesWithText("Two-Factor Authentication", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
          },
      ) {
        val scope = rememberCoroutineScope()
        val container = remember(scope) { SampleAppContainer(scope) }
        LaunchedEffect(container) {
          val vm = container.workspaceViewModel
          vm.applyPreset(DemoPreset.STANDARD)
          vm.authViewModel.dispatch(AuthUiIntent.SubmitAuth)
          vm.selectTab(WorkspaceTab.SECURITY)
        }
        AuthenticatedWorkspaceScreen(
            authViewModel = container.workspaceViewModel.authViewModel,
            organizationViewModel = container.workspaceViewModel.organizationViewModel,
            apiKeyViewModel = container.workspaceViewModel.apiKeyViewModel,
            workspaceViewModel = container.workspaceViewModel,
            onOpenTwoFactorSetup = {},
            onOpenApiKeyManager = {},
        )
      }
}
