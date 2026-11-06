package dev.fediim.auth.sample.data.platform

import dev.fediim.auth.core.social.JvmLoopbackSocialAuthLauncher
import dev.fediim.auth.passkeys.JvmPasskeyAuthenticator

/** Creates JVM desktop platform authentication bridges using loopback social launcher. */
public actual fun createLivePlatformAuthBridges(): PlatformAuthBridges =
    PlatformAuthBridges(
        socialAuthLauncher = JvmLoopbackSocialAuthLauncher(),
        passkeyAuthenticator = JvmPasskeyAuthenticator(),
    )
