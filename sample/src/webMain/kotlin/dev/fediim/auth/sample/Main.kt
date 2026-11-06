package dev.fediim.auth.sample

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.fediim.auth.sample.presentation.ui.SampleApp

/** Web browser entry point binding [SampleApp] to the DOM viewport. */
@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
  ComposeViewport("root") { SampleApp() }
}
