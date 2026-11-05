package dev.fediim.auth.core.social

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.browser.window
import org.w3c.dom.MessageEvent

private val platform =
    WebPlatform(
        pageOrigin = { window.location.origin },
        pageUrl = { window.location.href },
        addMessageListener = { onMessage ->
          val listener: (org.w3c.dom.events.Event) -> Unit = { event ->
            val message = event as MessageEvent
            val json =
                try {
                  JSON.stringify(message.data).unsafeCast<String?>()
                } catch (e: Throwable) {
                  null
                }
            onMessage(message.origin, json)
          }
          window.addEventListener("message", listener)
          ({ window.removeEventListener("message", listener) })
        },
        storageGet = { window.sessionStorage.getItem(it) },
        storageSet = { key, value -> window.sessionStorage.setItem(key, value) },
        storageRemove = { window.sessionStorage.removeItem(it) },
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
    private val openWindow: (String) -> Boolean = {
      window.open(it, "_blank", "popup,width=500,height=700") != null
    },
    private val navigate: (String) -> Unit = { window.location.assign(it) },
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
  public fun consumeRedirectCallback(url: String = window.location.href): SocialCallback? =
      consumeWebRedirect(platform, url)
}
