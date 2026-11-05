package dev.fediim.auth.core.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AndroidCustomTabsSocialAuthLauncherTest {
  private val callbackUri = "myapp://cb?x=1"
  private val opened = java.util.concurrent.CopyOnWriteArrayList<String>()

  private class Started(val receiver: String, val result: Deferred<SocialCallback>)

  private suspend fun CoroutineScope.start(state: String, timeout: Duration = 30.seconds): Started {
    val launcher = AndroidCustomTabsSocialAuthLauncher(callbackUri, timeout) { opened += it }
    val receiver = CompletableDeferred<String>()
    val result =
        async(Dispatchers.Default) {
          launcher.launch(state, "https://example.com") { flow, r ->
            assertEquals(SocialFlow.Redirect, flow)
            receiver.complete(r)
            "https://provider.example/auth"
          }
        }
    val r = withTimeout(10.seconds) { receiver.await() }
    withTimeout(10.seconds) { while (opened.isEmpty()) kotlinx.coroutines.yield() }
    return Started(r, result)
  }

  @Test
  fun handleDeliversOnlyMatchingState() = runBlocking {
    val started = start("st")
    assertEquals("myapp://cb?x=1&state=st", started.receiver)
    assertEquals("https://provider.example/auth", opened.single())

    assertFalse(AndroidSocialAuthCallbacks.handle("myapp://cb?x=1&state=wrong&ott=o"))
    assertFalse(AndroidSocialAuthCallbacks.handle(null))
    assertFalse(started.result.isCompleted)
    assertTrue(AndroidSocialAuthCallbacks.handle("myapp://cb?x=1&state=st&ott=o"))
    assertEquals(
        SocialCallback.OneTimeToken("o"),
        withTimeout(10.seconds) { started.result.await() },
    )
    assertFalse(AndroidSocialAuthCallbacks.handle("myapp://cb?x=1&state=st&ott=o"))
  }

  @Test
  fun timeoutThenHandleIsFalse() = runBlocking {
    val started = start("st", timeout = 100.milliseconds)
    assertEquals(
        SocialCallback.Error("timeout"),
        withTimeout(10.seconds) { started.result.await() },
    )
    assertFalse(AndroidSocialAuthCallbacks.handle("myapp://cb?x=1&state=st&ott=o"))
  }

  @Test
  fun secondLaunchSupersedesFirst() = runBlocking {
    val first = start("one")
    opened.clear()
    val second = start("two")
    assertEquals(
        SocialCallback.Error("superseded"),
        withTimeout(10.seconds) { first.result.await() },
    )
    assertFalse(AndroidSocialAuthCallbacks.handle("myapp://cb?x=1&state=one&ott=o"))
    assertTrue(AndroidSocialAuthCallbacks.handle("myapp://cb?x=1&state=two&ott=o2"))
    assertEquals(
        SocialCallback.OneTimeToken("o2"),
        withTimeout(10.seconds) { second.result.await() },
    )
  }
}
