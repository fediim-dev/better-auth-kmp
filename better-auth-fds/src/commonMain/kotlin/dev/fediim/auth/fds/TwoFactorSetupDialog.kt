package dev.fediim.auth.fds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.twofactor.TotpEnrollment
import dev.fediim.auth.core.twofactor.TwoFactorApi
import dev.fediim.fds.components.BannerTone
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonVariant
import dev.fediim.fds.components.CodeBlock
import dev.fediim.fds.components.TextField
import dev.fediim.fds.components.TextFieldType
import dev.fediim.fds.theme.FediimTheme
import dev.fediim.fds.tokens.FediimSpace
import kotlinx.coroutines.launch

internal const val TAG_SETUP_PASSWORD = "fds.setup.password"
internal const val TAG_SETUP_CODE = "fds.setup.code"

/** The text of a failed result (`message ?: code`, else the status), or null on success. */
private fun BetterAuthResult<*>.failureText(): String? =
    when (this) {
      is BetterAuthResult.Success -> null
      is BetterAuthResult.Failure -> error.message ?: error.code ?: "HTTP $status"
      is BetterAuthResult.RateLimited -> error.message ?: error.code ?: "RATE_LIMITED"
      is BetterAuthResult.NetworkError -> "NETWORK_ERROR"
    }

/**
 * Two-factor enrolment in a modal window: password, then `enable` shows the TOTP URI and backup
 * codes (copyable, never logged or saved), then a code is checked with `verifyTotp`. [onEnabled] is
 * called once on success. A failure keeps the step and shows the error. Continue and Verify are
 * disabled while their request runs, and Verify stays disabled after success. A blank password is
 * sent as null (accounts without a password). There is no QR code. Call it while signed in with no
 * pending two-factor sign-in. The password, URI and backup codes are dropped when the composition
 * is disposed, including on an Android configuration change, by design (never in saved state); the
 * host app should set `FLAG_SECURE` or the platform equivalent if it needs screenshot protection.
 * Must be inside `FediimTheme`.
 */
@Composable
public fun TwoFactorSetupDialog(
    twoFactor: TwoFactorApi,
    onDismissRequest: () -> Unit,
    onEnabled: () -> Unit,
    modifier: Modifier = Modifier,
    issuer: String? = null,
) {
  val scope = rememberCoroutineScope()
  var password by remember { mutableStateOf("") }
  var code by remember { mutableStateOf("") }
  var enrollment by remember { mutableStateOf<TotpEnrollment?>(null) }
  var failure by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf(false) }

  FdsDialogFrame(onDismissRequest, modifier) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FediimSpace.Space4),
    ) {
      Text(
          "Set up two-step verification",
          style = FediimTheme.typography.headlineSm,
          color = FediimTheme.colors.textStrong,
      )
      failure?.let {
        FdsBanner("Something went wrong", it, BannerTone.Critical, Modifier.testTag(TAG_ERROR))
      }
      val current = enrollment
      if (current == null) {
        TextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            help = "Leave blank if your account has no password.",
            type = TextFieldType.Password,
            modifier = Modifier.fillMaxWidth().testTag(TAG_SETUP_PASSWORD),
        )
        Button(
            onClick = {
              if (busy) return@Button
              busy = true
              failure = null
              scope.launch {
                val result = twoFactor.enable(password.ifBlank { null }, issuer)
                if (result is BetterAuthResult.Success) enrollment = result.value
                failure = result.failureText()
                busy = false
              }
            },
            variant = ButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            text = "Continue",
        )
      } else {
        FdsBanner(
            "Save your backup codes",
            "Each backup code works once. They are not shown again.",
            BannerTone.Warning,
        )
        CodeBlock(current.totpUri, "Authenticator URI", lineNumbers = false, wrap = true)
        CodeBlock(current.backupCodes.joinToString("\n"), "Backup codes", lineNumbers = false)
        TextField(
            value = code,
            onValueChange = { code = it },
            label = "Code from your authenticator app",
            type = TextFieldType.Number,
            modifier = Modifier.fillMaxWidth().testTag(TAG_SETUP_CODE),
        )
        Button(
            onClick = {
              if (busy) return@Button
              busy = true
              failure = null
              scope.launch {
                val result = twoFactor.verifyTotp(code.trim())
                failure = result.failureText()
                if (result is BetterAuthResult.Success) {
                  done = true
                  onEnabled()
                }
                busy = false
              }
            },
            variant = ButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && !done,
            text = "Verify",
        )
      }
      Button(
          onClick = onDismissRequest,
          variant = ButtonVariant.Ghost,
          modifier = Modifier.fillMaxWidth(),
          text = "Close",
      )
    }
  }
}
