package dev.fediim.auth.core.contract

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SignInEmailRequest
import dev.fediim.auth.core.model.SignUpEmailRequest
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.okhttp.OkHttp
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

/** Live email/password and session flow against the Node better-auth contract server. */
class EmailSessionContractTest {
  /** A client whose every response's `set-auth-token` header value (or null) is recorded. */
  private class Recorded(baseUrl: String) {
    val headers = CopyOnWriteArrayList<String?>()
    val storage = SettingsAuthTokenStorage(MapSettings())
    val client =
        BetterAuthClient(
            BetterAuthConfig(baseUrl),
            storage,
            OkHttp.create {
              addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                headers.add(response.header("set-auth-token"))
                response
              }
            },
        )

    /** The `set-auth-token` header of the most recent response. */
    fun lastHeader(): String? = headers.last()
  }

  /** Observed against better-auth 1.7.17 (plan L16): a revoked bearer yields 200 `null`. */
  private fun assertRevoked(result: BetterAuthResult<*>) {
    assertEquals(BetterAuthResult.Success(null), result)
  }

  @Test
  fun emailSignUpSignInSessionRevokeSignOut() = runBlocking {
    BetterAuthContractServerHarness.start().use { server ->
      val email = "contract-${UUID.randomUUID()}@example.com"
      val secret = UUID.randomUUID().toString()
      val a = Recorded(server.baseUrl)
      val b = Recorded(server.baseUrl)
      val c = Recorded(server.baseUrl)
      try {
        val signUp = a.client.signUpEmail(SignUpEmailRequest("Contract", email, secret))
        assertEquals(email, (signUp as BetterAuthResult.Success).value.user?.email)
        val signUpToken = assertNotNull(a.lastHeader())
        assertEquals(signUpToken, a.storage.token())

        val signIn = a.client.signInEmail(SignInEmailRequest(email, secret))
        assertTrue(signIn is BetterAuthResult.Success, "signIn: $signIn")
        val signInToken = assertNotNull(a.lastHeader())
        assertEquals(signInToken, a.storage.token())
        assertNotEquals(signUpToken, signInToken)

        val bad = Recorded(server.baseUrl)
        val wrong = bad.client.signInEmail(SignInEmailRequest(email, UUID.randomUUID().toString()))
        assertTrue(
            wrong is BetterAuthResult.Failure && wrong.status == 401,
            "wrong: $wrong",
        )
        assertNotNull((wrong as BetterAuthResult.Failure).error.code)
        bad.client.close()

        val envelope =
            (a.client.getSession() as BetterAuthResult.Success).value ?: fail("no session")
        assertEquals(envelope.user.id, envelope.session.userId)
        assertEquals(JsonPrimitive("member"), envelope.user.additionalFields["role"])

        assertTrue(
            b.client.signInEmail(SignInEmailRequest(email, secret)) is BetterAuthResult.Success
        )
        val bId = (b.client.getSession() as BetterAuthResult.Success).value!!.session.id

        val listed = (a.client.listSessions() as BetterAuthResult.Success).value
        assertTrue(listed.size >= 3, "sessions: ${listed.size}")
        val bEntry = listed.single { it.id == bId }
        assertTrue(a.client.revokeSession(bEntry.token) is BetterAuthResult.Success)
        assertRevoked(b.client.getSession())
        val after = (a.client.listSessions() as BetterAuthResult.Success).value
        assertTrue(after.none { it.id == bId })

        val old = assertNotNull(a.storage.token())
        assertTrue(a.client.signOut() is BetterAuthResult.Success)
        assertNull(a.storage.token())
        assertEquals(BetterAuthResult.Success(null), a.client.getSession())

        assertTrue(c.storage.writeTokenIfEpoch(0, old))
        assertRevoked(c.client.getSession())
      } finally {
        a.client.close()
        b.client.close()
        c.client.close()
      }
    }
  }
}
