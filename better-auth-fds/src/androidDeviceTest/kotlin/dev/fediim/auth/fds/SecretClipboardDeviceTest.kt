package dev.fediim.auth.fds

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretClipboardDeviceTest {
  @Test
  fun entryIsMarkedSensitiveAndCanBeSetAsPrimaryClip() {
    val entry = sensitiveClipEntry("s3cr3t")
    assertNotNull(entry)
    val clip = entry!!.clipData
    assertEquals("s3cr3t", clip.getItemAt(0).text.toString())
    assertTrue(clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(clip)
  }
}
