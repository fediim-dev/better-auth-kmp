package dev.fediim.auth.core.social

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android launcher: opens the provider in a Custom Tab and waits for the app to receive
 * [callbackUri] with `state` and `ott` (or `error`) appended. The Activity that owns the
 * [callbackUri] intent filter must pass `intent.data?.toString()` to
 * [AndroidSocialAuthCallbacks.handle].
 *
 * One flow can be pending per process: a new launch completes the old one with
 * [SocialCallback.Error] `superseded`. Closing the tab sends no signal, so an abandoned flow ends
 * at [timeout] with `timeout`. A custom-scheme [callbackUri] can be claimed by other apps; prefer a
 * verified App Link. The server must allow [callbackUri] as a callback and append `ott` to it.
 * Exceptions from `authorize` or the opener propagate; the pending slot is always released.
 *
 * Only `context.applicationContext` is kept, so no Activity leaks.
 */
public class AndroidCustomTabsSocialAuthLauncher
internal constructor(
    private val callbackUri: String,
    private val timeout: Duration,
    private val openUrl: (String) -> Unit,
) : SocialAuthLauncher {
  /**
   * @param openUrl replaces the Custom Tabs opener (tests, or a browser of your choice); the
   *   default starts a Custom Tab with `FLAG_ACTIVITY_NEW_TASK`.
   */
  public constructor(
      context: Context,
      callbackUri: String,
      timeout: Duration = 10.minutes,
      openUrl: ((String) -> Unit)? = null,
  ) : this(callbackUri, timeout, openUrl ?: customTabsOpener(context.applicationContext))

  override suspend fun launch(
      state: String,
      serverOrigin: String,
      authorize: suspend (flow: SocialFlow, receiver: String) -> String,
  ): SocialCallback {
    val pending = AndroidSocialAuthCallbacks.register(state)
    try {
      val url = authorize(SocialFlow.Redirect, withState(callbackUri, state))
      return withTimeoutOrNull(timeout) {
        openUrl(url)
        pending.result.await()
      } ?: SocialCallback.Error("timeout")
    } finally {
      AndroidSocialAuthCallbacks.clear(pending)
    }
  }
}

private fun customTabsOpener(appContext: Context): (String) -> Unit = { url ->
  val tab = CustomTabsIntent.Builder().build()
  tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  tab.launchUrl(appContext, Uri.parse(url))
}

/** Receives the callback intent for [AndroidCustomTabsSocialAuthLauncher]. */
public object AndroidSocialAuthCallbacks {
  internal class Pending(val state: String) {
    val result = CompletableDeferred<SocialCallback>()
  }

  private val lock = Any()
  private var current: Pending? = null

  /**
   * Delivers [uri] (for example `intent.data?.toString()`) to the pending flow. Returns true only
   * when a flow is pending, the `state` in [uri] equals its state and the flow had not finished;
   * false otherwise, leaving the flow pending.
   */
  public fun handle(uri: String?): Boolean {
    if (uri == null) return false
    val pending = synchronized(lock) { current } ?: return false
    val callback = parseRedirectCallback(uri, pending.state) ?: return false
    return pending.result.complete(callback)
  }

  internal fun register(state: String): Pending {
    val pending = Pending(state)
    val previous = synchronized(lock) { current.also { current = pending } }
    previous?.result?.complete(SocialCallback.Error("superseded"))
    return pending
  }

  internal fun clear(pending: Pending) {
    synchronized(lock) { if (current === pending) current = null }
  }
}
