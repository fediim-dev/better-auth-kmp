package dev.fediim.auth.sample

import androidx.compose.ui.window.ComposeUIViewController
import dev.fediim.auth.sample.presentation.ui.SampleApp
import platform.UIKit.UIViewController

/** iOS UIKit entry point returning a [UIViewController] hosting [SampleApp]. */
public fun MainViewController(): UIViewController = ComposeUIViewController { SampleApp() }
