package dev.fediim.auth.sample

import androidx.compose.runtime.Composable
import dev.fediim.auth.core.social.AndroidSocialAuthCallbacks
import dev.fediim.auth.sample.presentation.ui.SampleApp

/** Android Activity composable host rendering [SampleApp]. */
@Composable
public fun SampleActivityContent(): Unit {
  SampleApp()
}

/** Forwards incoming Android deep link URIs to social authentication callback handlers. */
public fun handleSampleSocialAuthIntent(uri: String?): Boolean =
    AndroidSocialAuthCallbacks.handle(uri)
