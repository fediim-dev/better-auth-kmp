package dev.fediim.auth.sample

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.fediim.auth.sample.di.SampleAppContainer
import dev.fediim.auth.sample.presentation.ui.SampleApp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SampleAppUiTest {
  private fun ComposeUiTest.hasText(text: String, substring: Boolean = false): Boolean =
      onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

  private fun ComposeUiTest.until(what: String, condition: () -> Boolean) =
      waitUntil(what, 5_000, condition)

  @Test
  fun testInteractiveAppFlow() =
      runDesktopComposeUiTest(width = 900, height = 900) {
        lateinit var testContainer: SampleAppContainer
        setContent {
          val scope = rememberCoroutineScope()
          testContainer = remember(scope) { SampleAppContainer(scope) }
          SampleApp(testContainer)
        }
        waitForIdle()

        // 1. Interactive sign-in via Alice preset
        onNodeWithText("Preset: Standard (Alice)").performClick()
        waitForIdle()
        onAllNodesWithTag("fds.password")[0].performTextReplacement("Password1!")
        waitForIdle()
        onAllNodesWithTag("fds.submit")[0].performClick()
        until("Signed in") { hasText("User Profile & Session") }

        // 2. Transition to AuthenticatedWorkspaceScreen and inspect user/session/token
        assertTrue(hasText("User Profile & Session"))
        assertTrue(hasText("Masked Token: demo••••1234", substring = true))
        assertTrue(hasText("alice@example.com", substring = true))

        // 3. Switch organization (Acme Corp -> Beta Labs)
        onNodeWithText("Acme Corp", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Beta Labs", substring = true).performClick()
        waitForIdle()

        // 4. Switch to Security & Keys tab
        onNodeWithText("Security & Keys").performClick()
        waitForIdle()

        // 5. Register and delete passkey
        onNodeWithText("Register Passkey").performClick()
        until("Passkey registered") { hasText("My Security Key", substring = true) }
        onAllNodesWithText("Delete")[1].performClick()
        waitForIdle()

        // 6. Open and close TwoFactorSetupDialog
        onNodeWithText("Setup 2FA").performClick()
        until("Two factor dialog") { hasText("Set up two-step verification", substring = true) }
        onNodeWithText("Close").performClick()
        waitForIdle()

        // 7. Open and close ApiKeyManagerDialog
        onNodeWithText("API Keys").performClick()
        waitForIdle()
        onNodeWithText("Close").performClick()
        waitForIdle()

        // 8. Sign out back to unauthenticated screen
        onNodeWithText("Sign Out").performClick()
        until("Signed out") { hasText("Offline Demo Presets") }

        // 9. Trigger demo reset
        onNodeWithText("Reset Demo").performClick()
        waitForIdle()

        // 10. Validated Live Server URL error banner
        onNodeWithText("Live Server").performClick()
        waitForIdle()
        onNodeWithText("http://127.0.0.1:3000").performTextReplacement("http://evil.example.com")
        waitForIdle()
        onNodeWithText("Connect").performClick()
        until("Error banner") { hasText("Connection Error") }

        // Restore Offline Demo
        onNodeWithText("Offline Demo").performClick()
        waitForIdle()

        // 11. Open BetterAuthDialog modal and complete authentication
        onNodeWithText("Open Sign-In Modal").performClick()
        until("Modal open") { onAllNodesWithTag("fds.submit").fetchSemanticsNodes().size == 2 }

        onAllNodesWithTag("fds.email")[1].performTextReplacement("alice@example.com")
        waitForIdle()
        onAllNodesWithTag("fds.password")[1].performTextReplacement("Password1!")
        waitForIdle()
        onAllNodesWithTag("fds.submit")[1].performClick()
        until("Signed in from modal") { hasText("User Profile & Session") }

        // Sign out to finish
        onNodeWithText("Sign Out").performClick()
        until("Final signed out") { hasText("Offline Demo Presets") }
      }
}
