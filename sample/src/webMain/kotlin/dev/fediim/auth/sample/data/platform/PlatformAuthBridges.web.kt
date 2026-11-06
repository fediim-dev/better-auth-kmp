package dev.fediim.auth.sample.data.platform

import dev.fediim.auth.core.social.WebPopupSocialAuthLauncher
import dev.fediim.auth.passkeys.BrowserPasskeyAuthenticator

/**
 * Creates Web browser platform authentication bridges using popup social launcher and
 * navigator.credentials.
 */
public actual fun createLivePlatformAuthBridges(): PlatformAuthBridges =
    PlatformAuthBridges(
        socialAuthLauncher = WebPopupSocialAuthLauncher(),
        passkeyAuthenticator = BrowserPasskeyAuthenticator(),
    )
