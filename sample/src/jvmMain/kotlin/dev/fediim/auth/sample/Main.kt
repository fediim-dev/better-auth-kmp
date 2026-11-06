package dev.fediim.auth.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.fediim.auth.sample.presentation.ui.SampleApp

/** Desktop JVM entry point launching [SampleApp] in a desktop application window. */
public fun main(): Unit = application {
  Window(onCloseRequest = ::exitApplication, title = "Better Auth KMP Sample") { SampleApp() }
}
