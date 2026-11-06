package dev.fediim.auth.sample.data.platform

import dev.fediim.auth.core.social.IosWebAuthSocialAuthLauncher
import dev.fediim.auth.passkeys.IosPasskeyAuthenticator
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AuthenticationServices.ASPresentationAnchor
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow

/** Holds optional presentation anchor provider for iOS ASWebAuthenticationSession ceremonies. */
public object IosPlatformBridgeHooks {
  public var presentationAnchor: (() -> ASPresentationAnchor)? = null
}

@OptIn(ExperimentalForeignApi::class)
private fun defaultAnchor(): ASPresentationAnchor {
  IosPlatformBridgeHooks.presentationAnchor?.let {
    return it()
  }
  val window =
      UIApplication.sharedApplication.keyWindow
          ?: (UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow)
  return window ?: ASPresentationAnchor()
}

/**
 * Creates iOS platform authentication bridges using ASWebAuthenticationSession and
 * AuthenticationServices.
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun createLivePlatformAuthBridges(): PlatformAuthBridges =
    PlatformAuthBridges(
        socialAuthLauncher = IosWebAuthSocialAuthLauncher("better-auth-sample", ::defaultAnchor),
        passkeyAuthenticator = IosPasskeyAuthenticator(::defaultAnchor),
    )
