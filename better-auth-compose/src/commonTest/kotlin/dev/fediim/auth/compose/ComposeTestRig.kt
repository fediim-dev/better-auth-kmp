@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.fediim.auth.compose

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.social.SocialAuthLauncher
import dev.fediim.auth.core.social.SocialCallback
import dev.fediim.auth.core.social.SocialFlow
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import dev.fediim.auth.passkeys.AssertionResponse
import dev.fediim.auth.passkeys.AuthenticationResponse
import dev.fediim.auth.passkeys.BetterAuthPasskeyClient
import dev.fediim.auth.passkeys.PasskeyAuthenticator
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.auth.passkeys.PublicKeyCredentialCreationOptions
import dev.fediim.auth.passkeys.PublicKeyCredentialRequestOptions
import dev.fediim.auth.passkeys.RegistrationResponse
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

internal const val USER_A = """{"id":"uA","email":"a@b.com"}"""
internal const val USER_B = """{"id":"uB","email":"b@b.com"}"""
internal const val ENVELOPE =
    """{"session":{"id":"s1","userId":"u1","token":"st"},"user":{"id":"u1","email":"a@b.com"}}"""
internal const val OK = "{}"

/** A canned server answer. */
internal class Reply(
    val body: String = OK,
    val status: HttpStatusCode = HttpStatusCode.OK,
    val headers: List<Pair<String, String>> = emptyList(),
)

internal fun tokenReply(token: String, user: String = USER_B) =
    Reply("""{"token":"$token","user":$user}""")

/** What the cache callback saw when it ran. */
internal data class CacheSnapshot(
    val token: String?,
    val epoch: Long,
    val requestCount: Int,
    val org: String?,
)

internal class FakeLauncher : SocialAuthLauncher {
  var calls = 0
  var result: SocialCallback = SocialCallback.SessionToken("soc")

  override suspend fun launch(
      state: String,
      serverOrigin: String,
      authorize: suspend (flow: SocialFlow, receiver: String) -> String,
  ): SocialCallback {
    calls++
    return result
  }
}

internal class FakeAuthenticator : PasskeyAuthenticator {
  var calls = 0
  override val capability: PasskeyCapability = PasskeyCapability.Supported

  override suspend fun create(options: PublicKeyCredentialCreationOptions): RegistrationResponse =
      throw UnsupportedOperationException()

  override suspend fun get(options: PublicKeyCredentialRequestOptions): AuthenticationResponse {
    calls++
    return AuthenticationResponse("cred", "cred", AssertionResponse("Y2Q", "YWQ", "c2ln", "dWg"))
  }
}

/**
 * A client over a [MockEngine] on the test scheduler. Routes are keyed by the path after
 * `/api/auth/`; `requests`, `events` (arrival order, plus `cache-clear`) and `completed` record
 * what happened. A gated path holds its answer until the gate completes.
 */
internal class ComposeTestRig(scheduler: TestCoroutineScheduler, withLauncher: Boolean = true) {
  val storage = SettingsAuthTokenStorage(MapSettings())
  val requests = mutableListOf<HttpRequestData>()
  val events = mutableListOf<String>()
  val completed = mutableListOf<String>()
  val snapshots = mutableListOf<CacheSnapshot>()
  var cacheFailure: Throwable? = null
  val launcher = FakeLauncher()
  val authenticator = FakeAuthenticator()
  private val routes = mutableMapOf<String, Reply>("sign-out" to Reply("""{"success":true}"""))
  private val throwing = mutableSetOf<String>()
  private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

  val client =
      BetterAuthClient(
          BetterAuthConfig("https://example.com"),
          storage,
          MockEngine.create {
            dispatcher = StandardTestDispatcher(scheduler)
            addHandler { request ->
              val path = request.url.encodedPath.removePrefix("/api/auth/")
              requests += request
              events += "req:$path"
              gates[path]?.await()
              if (path in throwing) throw IllegalStateException("handler failure for $path")
              val reply =
                  routes[path]
                      ?: Reply("""{"code":"UNROUTED"}""", HttpStatusCode.InternalServerError)
              completed += "$path ${reply.status.value}"
              val headers = HeadersBuilder()
              headers.append(HttpHeaders.ContentType, "application/json")
              reply.headers.forEach { (name, value) -> headers.append(name, value) }
              respond(reply.body, reply.status, headers.build())
            }
          },
          if (withLauncher) launcher else null,
      )

  val passkeyClient = BetterAuthPasskeyClient(client, authenticator)

  val cacheClears: Int
    get() = snapshots.size

  fun on(path: String, reply: Reply) {
    routes[path] = reply
    throwing -= path
  }

  fun failWith(path: String) {
    throwing += path
  }

  fun gate(path: String): CompletableDeferred<Unit> =
      CompletableDeferred<Unit>().also { gates[path] = it }

  fun reqs(path: String): List<HttpRequestData> = requests.filter {
    it.url.encodedPath == "/api/auth/$path"
  }

  fun bodyOf(request: HttpRequestData): String = (request.body as TextContent).text

  fun authOf(request: HttpRequestData): String? = request.headers[HttpHeaders.Authorization]

  suspend fun seed(token: String, org: String? = null) {
    storage.writeTokenIfEpoch(storage.epoch(), token)
    if (org != null) storage.writeActiveOrganizationIdIfEpoch(storage.epoch(), org)
  }

  private suspend fun onClearUserCache() {
    snapshots +=
        CacheSnapshot(
            storage.token(),
            storage.epoch(),
            requests.size,
            storage.activeOrganizationId(),
        )
    events += "cache-clear"
    cacheFailure?.let { throw it }
  }

  fun viewModel(scope: CoroutineScope, passkeys: Boolean = false) =
      BetterAuthViewModel(client, scope, if (passkeys) passkeyClient else null, ::onClearUserCache)
}

/** Collects the effects of [vm] from now on; the collector lives on the background scope. */
internal fun TestScope.collectEffects(vm: BetterAuthViewModel): MutableList<AuthUiEffect> {
  val seen = mutableListOf<AuthUiEffect>()
  backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
    vm.effects.collect { seen += it }
  }
  return seen
}
