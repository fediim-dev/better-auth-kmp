package dev.fediim.auth.core.social

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private fun pageOrigin(): String = js("window.location.origin")

private fun pageUrl(): String = js("window.location.href")

private fun openPopup(url: String): Boolean =
    js("window.open(url, '_blank', 'popup,width=500,height=700') != null")

private fun assignLocation(url: String): Unit = js("window.location.assign(url)")

private fun storageGet(key: String): String? = js("window.sessionStorage.getItem(key)")

private fun storageSet(key: String, value: String): Unit =
    js("window.sessionStorage.setItem(key, value)")

private fun storageRemove(key: String): Unit = js("window.sessionStorage.removeItem(key)")

/** Registers a `message` listener and returns the JS function that was registered. */
private fun addListener(onMessage: (String, String?) -> Unit): JsAny =
    js(
        """(() => {
  const listener = (e) => {
    let json = null;
    try { const s = JSON.stringify(e.data); if (typeof s === 'string') json = s; } catch (_) {}
    onMessage(e.origin, json);
  };
  window.addEventListener('message', listener);
  return listener;
})()"""
    )

private fun removeListener(listener: JsAny): Unit =
    js("window.removeEventListener('message', listener)")

private val platform =
    WebPlatform(
        pageOrigin = ::pageOrigin,
        pageUrl = ::pageUrl,
        addMessageListener = { onMessage ->
          val listener = addListener(onMessage)
          ({ removeListener(listener) })
        },
        storageGet = ::storageGet,
        storageSet = ::storageSet,
        storageRemove = ::storageRemove,
    )

/**
 * Browser launcher: opens the server's popup and accepts the session token it posts back, but only
 * from the auth server's origin with the flow's nonce. If [openWindow] returns false (popup
 * blocked) it navigates the page to the provider instead; after the provider redirects back, call
 * [consumeRedirectCallback] with the page URL and pass the result to
 * `BetterAuthClient.completeSocialSignIn`, then strip `ott` and `state` from the address bar.
 *
 * The page's origin must be in the server's `trustedOrigins` for the popup. A closed popup is not
 * detected; the flow ends at [timeout] with [SocialCallback.Error] `timeout`.
 */
public class WebPopupSocialAuthLauncher(
    private val timeout: Duration = 5.minutes,
    private val openWindow: (String) -> Boolean = ::openPopup,
    private val navigate: (String) -> Unit = ::assignLocation,
) : SocialAuthLauncher {
  override suspend fun launch(
      state: String,
      serverOrigin: String,
      authorize: suspend (flow: SocialFlow, receiver: String) -> String,
  ): SocialCallback =
      runWebPopup(platform, timeout, openWindow, navigate, state, serverOrigin, authorize)

  /**
   * Returns the callback in [url] if its `state` equals the one stored before the redirect
   * fallback, and forgets that state so a second call returns null; returns null otherwise.
   */
  public fun consumeRedirectCallback(url: String = pageUrl()): SocialCallback? =
      consumeWebRedirect(platform, url)
}
