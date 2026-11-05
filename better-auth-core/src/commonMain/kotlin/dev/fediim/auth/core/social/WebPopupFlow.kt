package dev.fediim.auth.core.social

import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull

/** Browser primitives the js and wasmJs launchers supply to [runWebPopup]. */
internal class WebPlatform(
    /** `window.location.origin`. */
    val pageOrigin: () -> String,
    /** `window.location.href`. */
    val pageUrl: () -> String,
    /**
     * Registers a `message` listener called with `event.origin` and the JSON of `event.data` (null
     * when it cannot be serialized); returns the function that removes it.
     */
    val addMessageListener: ((origin: String, dataJson: String?) -> Unit) -> () -> Unit,
    val storageGet: (key: String) -> String?,
    val storageSet: (key: String, value: String) -> Unit,
    val storageRemove: (key: String) -> Unit,
)

/**
 * Popup flow with a redirect fallback. A `message` is accepted only if `event.origin` equals
 * [serverOrigin] and its nonce equals [state]. When [openWindow] reports a blocked popup, [state]
 * goes to session storage, the page navigates to the redirect-flow provider URL and this call never
 * returns (the page is replaced); [consumeWebRedirect] finishes that flow after the reload.
 */
internal suspend fun runWebPopup(
    platform: WebPlatform,
    timeout: Duration,
    openWindow: (String) -> Boolean,
    navigate: (String) -> Unit,
    state: String,
    serverOrigin: String,
    authorize: suspend (flow: SocialFlow, receiver: String) -> String,
): SocialCallback {
  val popupUrl = authorize(SocialFlow.Popup, platform.pageOrigin())
  val message = CompletableDeferred<SocialCallback>()
  val removeListener = platform.addMessageListener { origin, dataJson ->
    parsePopupMessage(origin, dataJson, serverOrigin, state)?.let { message.complete(it) }
  }
  try {
    if (openWindow(popupUrl)) {
      return withTimeoutOrNull(timeout) { message.await() } ?: SocialCallback.Error("timeout")
    }
  } finally {
    removeListener()
  }
  platform.storageSet(WEB_STATE_STORAGE_KEY, state)
  val redirectUrl =
      try {
        val pageUrl = platform.pageUrl().substringBefore('#').substringBefore('?')
        authorize(SocialFlow.Redirect, withState(pageUrl, state))
      } catch (e: Throwable) {
        platform.storageRemove(WEB_STATE_STORAGE_KEY)
        throw e
      }
  navigate(redirectUrl)
  awaitCancellation()
}

/** Reads the redirect callback in [url] against the state stored by [runWebPopup], once. */
internal fun consumeWebRedirect(platform: WebPlatform, url: String): SocialCallback? {
  val state = platform.storageGet(WEB_STATE_STORAGE_KEY) ?: return null
  val callback = parseRedirectCallback(url, state) ?: return null
  platform.storageRemove(WEB_STATE_STORAGE_KEY)
  return callback
}
