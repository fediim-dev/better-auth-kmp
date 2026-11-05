package dev.fediim.auth.fds

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.fediim.fds.components.Checkbox
import dev.fediim.fds.components.StackedBar
import dev.fediim.fds.components.StackedBarKind
import dev.fediim.fds.components.StackedBarSegment

/**
 * The five password rules with their label and whether [password] meets each, in this order: at
 * least 8 characters, at least 12 characters, upper and lower case, a digit, a character that is
 * neither letter nor digit. Restated from the view model's scoring (AuthUiContract.kt).
 */
internal fun passwordRules(password: String): List<Pair<String, Boolean>> =
    listOf(
        "At least 8 characters" to (password.length >= 8),
        "At least 12 characters" to (password.length >= 12),
        "Upper and lower case letters" to
            (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }),
        "A digit" to password.any { it.isDigit() },
        "A symbol" to password.any { !it.isLetterOrDigit() },
    )

/**
 * One read-only checkbox per rule of [passwordRules]; the box for rule `i` is tagged `fds.rule.i`.
 */
@Composable
internal fun PasswordChecklist(password: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    passwordRules(password).forEachIndexed { index, (label, met) ->
      Checkbox(
          checked = met,
          onCheckedChange = null,
          modifier = Modifier.testTag(TAG_RULE_PREFIX + index),
          label = label,
      )
    }
  }
}

/** A bar for [score] 0..4 (clamped), described as "Password strength $score of 4". */
@Composable
internal fun PasswordStrengthMeter(score: Int, modifier: Modifier = Modifier) {
  val clamped = score.coerceIn(0, 4)
  val segments =
      remember(clamped) {
        listOf(
            StackedBarSegment(clamped.toFloat(), StackedBarKind.Series1),
            StackedBarSegment((4 - clamped).toFloat(), StackedBarKind.Track),
        )
      }
  StackedBar(segments, "Password strength $clamped of 4", modifier)
}
