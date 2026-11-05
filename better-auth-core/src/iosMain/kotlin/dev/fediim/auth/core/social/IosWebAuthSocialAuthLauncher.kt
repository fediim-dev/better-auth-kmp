package dev.fediim.auth.core.social

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorDomain
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * iOS launcher backed by `ASWebAuthenticationSession` (iOS 17.4+, which the non-deprecated
 * `callback:` initializer requires). The flow's receiver is `<callbackScheme>://auth/callback`; the
 * server must allow it and append `ott` to it. [anchor] supplies the presenting window and is
 * called on the main thread. With [prefersEphemeralSession] the browser shares no cookies with
 * Safari.
 *
 * A user cancel gives [SocialCallback.Error] `cancelled`; a callback whose `state` does not match
 * gives `state_mismatch`. Coroutine cancellation cancels the session.
 */
@OptIn(ExperimentalForeignApi::class)
public class IosWebAuthSocialAuthLauncher(
    private val callbackScheme: String,
    private val anchor: () -> ASPresentationAnchor,
    private val prefersEphemeralSession: Boolean = true,
) : SocialAuthLauncher {
  override suspend fun launch(
      state: String,
      serverOrigin: String,
      authorize: suspend (flow: SocialFlow, receiver: String) -> String,
  ): SocialCallback {
    val url = authorize(SocialFlow.Redirect, withState("$callbackScheme://auth/callback", state))
    val nsUrl = NSURL.URLWithString(url) ?: error("provider URL is not valid")
    return withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { continuation ->
        val provider = AnchorProvider(anchor)
        val session =
            ASWebAuthenticationSession(
                uRL = nsUrl,
                callback =
                    ASWebAuthenticationSessionCallback.callbackWithCustomScheme(callbackScheme),
            ) { callbackUrl, error ->
              val result =
                  when {
                    callbackUrl != null ->
                        parseRedirectCallback(callbackUrl.absoluteString.orEmpty(), state)
                            ?: SocialCallback.Error("state_mismatch")
                    error != null &&
                        error.domain == ASWebAuthenticationSessionErrorDomain &&
                        error.code == ASWebAuthenticationSessionErrorCodeCanceledLogin ->
                        SocialCallback.Error("cancelled")
                    else -> SocialCallback.Error("SOCIAL_LAUNCH_FAILED")
                  }
              provider.toString() // ASWebAuthenticationSession holds its provider weakly; keep it
              if (continuation.isActive) continuation.resume(result)
            }
        session.presentationContextProvider = provider
        session.prefersEphemeralWebBrowserSession = prefersEphemeralSession
        continuation.invokeOnCancellation { session.cancel() }
        if (!session.start() && continuation.isActive) {
          continuation.resume(SocialCallback.Error("SOCIAL_LAUNCH_FAILED"))
        }
      }
    }
  }
}

private class AnchorProvider(private val anchor: () -> ASPresentationAnchor) :
    NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
  override fun presentationAnchorForWebAuthenticationSession(
      session: ASWebAuthenticationSession
  ): ASPresentationAnchor = anchor()
}
