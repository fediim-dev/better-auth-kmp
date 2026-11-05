package dev.fediim.auth.fds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch

/**
 * A clip entry holding [text] marked sensitive for the platform clipboard, or null where this
 * module sets no sensitive flag (then the caller copies plain text).
 */
internal expect fun sensitiveClipEntry(text: String): ClipEntry?

/**
 * Returns a copy action for one-time secrets; uses [sensitiveClipEntry] when non-null, else plain
 * text; never logs the text; does not clear the clipboard.
 */
@Composable
@Suppress("DEPRECATION")
internal fun rememberSecretCopy(): (String) -> Unit {
  val clipboard = LocalClipboard.current
  val manager = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  return remember(clipboard, manager, scope) {
    { text ->
      val entry = sensitiveClipEntry(text)
      if (entry != null) scope.launch { clipboard.setClipEntry(entry) }
      else manager.setText(AnnotatedString(text))
    }
  }
}
