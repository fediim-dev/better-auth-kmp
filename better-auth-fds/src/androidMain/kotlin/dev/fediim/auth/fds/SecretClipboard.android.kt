package dev.fediim.auth.fds

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry

internal actual fun sensitiveClipEntry(text: String): ClipEntry? {
  val clip = ClipData.newPlainText("", text)
  clip.description.extras =
      PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
  return ClipEntry(clip)
}
