package dev.fediim.auth.fds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions
import dev.fediim.auth.compose.ApiKeyUiState
import dev.fediim.auth.compose.AuthMode
import dev.fediim.auth.compose.AuthUiState
import dev.fediim.auth.compose.OrganizationUiState
import dev.fediim.auth.core.apikey.ApiKeyRecord
import dev.fediim.auth.core.apikey.CreatedApiKey
import dev.fediim.auth.core.organization.FullOrganization
import dev.fediim.auth.core.organization.Organization
import dev.fediim.auth.core.organization.OrganizationMember
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.fds.theme.FediimTheme
import dev.fediim.fds.tokens.FediimSpace
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * Goldens of the stateless contents in light and dark at 480x720, in `screenshots/`. Re-record with
 * `-Proborazzi.test.record=true`; they are verified on macOS only (plan #8 L23).
 */
@OptIn(ExperimentalTestApi::class)
class FdsScreenshotTest {
  // 1% of the pixels may differ (font antialiasing across JDK/Skiko builds).
  private val options = RoborazziOptions(compareOptions = CompareOptions(changeThreshold = 0.01f))

  private fun shoot(name: String, content: @Composable () -> Unit) {
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
        val suffix = if (dark) "dark" else "light"
        onNodeWithTag("shot").captureRoboImage("screenshots/$name-$suffix.png", options)
      }
    }
  }

  // The register form with its checklist is taller than the frame, so it omits the social and
  // passkey buttons.
  private fun panel(name: String, state: AuthUiState, password: String = "") =
      shoot(name) {
        val short = state.mode == AuthMode.REGISTER
        AuthPanelContent(
            state,
            password,
            {},
            if (short) PasskeyCapability.Unsupported else PasskeyCapability.Supported,
            if (short) emptyList() else listOf("google", "github"),
            null,
        )
      }

  @Test fun signin() = panel("signin", AuthUiState(email = "a@b.com"))

  @Test
  fun register() =
      panel(
          "register",
          AuthUiState(mode = AuthMode.REGISTER, email = "a@b.com", passwordStrength = 2),
          "Abcdefgh",
      )

  @Test
  fun twofactor() =
      panel(
          "twofactor",
          AuthUiState(mode = AuthMode.TWO_FACTOR_CHALLENGE, twoFactorMethods = listOf("totp")),
      )

  @Test
  fun lockout() = panel("lockout", AuthUiState(isLockedOut = true, lockoutSecondsRemaining = 30))

  @Test
  fun orgswitcher() =
      shoot("orgswitcher") {
        val acme = Organization("o1", "Acme", "acme")
        val state =
            OrganizationUiState(
                organizations = listOf(acme, Organization("o2", "Beta", "beta")),
                activeOrganization =
                    FullOrganization(
                        "o1",
                        "Acme",
                        "acme",
                        members = listOf(OrganizationMember("m1", "o1", "uA", "admin")),
                    ),
            )
        OrganizationSwitcherContent(state, "uA", {})
      }

  @Test
  fun apikeys() =
      shoot("apikeys") {
        val ci = ApiKeyRecord(id = "k1", name = "ci", start = "ba_x1")
        val fresh = ApiKeyRecord(id = "k2", name = "deploy", start = "ba_y2")
        val state =
            ApiKeyUiState(
                keys = listOf(ci, fresh),
                createdKey = CreatedApiKey("raw-secret-AAAA", fresh),
            )
        ApiKeyManagerContent(state, {}, {})
      }
}
