package dev.fediim.auth.sample.data.platform

import dev.fediim.auth.core.social.SocialAuthLauncher
import dev.fediim.auth.passkeys.PasskeyAuthenticator

/** Platform-specific authenticators and social authentication launchers for live connections. */
public data class PlatformAuthBridges(
    val socialAuthLauncher: SocialAuthLauncher?,
    val passkeyAuthenticator: PasskeyAuthenticator,
)

/** Instantiates platform-specific authentication bridges for the target operating system. */
public expect fun createLivePlatformAuthBridges(): PlatformAuthBridges
