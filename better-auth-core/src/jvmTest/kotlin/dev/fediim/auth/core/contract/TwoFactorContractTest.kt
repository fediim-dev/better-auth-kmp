package dev.fediim.auth.core.contract

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.model.AuthResponse
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SignInEmailRequest
import dev.fediim.auth.core.model.SignUpEmailRequest
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import dev.fediim.auth.core.twofactor.SignInResult
import dev.fediim.auth.core.twofactor.signInResult
import io.ktor.client.engine.okhttp.OkHttp
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Live two-factor flows against the Node better-auth contract server (plan #6 T6). */
class TwoFactorContractTest {
  /** A client that records the `set-auth-token` header of every response. */
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
  }

  private val base32Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

  private fun base32Decode(text: String): ByteArray {
    var buffer = 0
    var bits = 0
    val out = java.io.ByteArrayOutputStream()
    for (c in text) {
      buffer = (buffer shl 5) or base32Alphabet.indexOf(c)
      bits += 5
      if (bits >= 8) {
        bits -= 8
        out.write((buffer shr bits) and 0xff)
      }
    }
    return out.toByteArray()
  }

  /** RFC 6238 TOTP (HmacSHA1, 30 s, 6 digits) for the counter [stepOffset] periods from now. */
  private fun totp(uri: String, stepOffset: Long = 0): String {
    val secret = Regex("[?&]secret=([A-Z2-7]+)").find(uri)!!.groupValues[1]
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(base32Decode(secret), "HmacSHA1"))
    val counter = System.currentTimeMillis() / 30_000 + stepOffset
    val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
    val offset = hash.last().toInt() and 0xf
    val binary =
        ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
    return (binary % 1_000_000).toString().padStart(6, '0')
  }

  /** A code the server must reject: outside the +-1 step window around now. */
  private fun staleCode(uri: String): String {
    val accepted = (-1L..1L).map { totp(uri, it) }
    return (5L..40L).map { totp(uri, it) }.first { it !in accepted }
  }

  private fun <T> BetterAuthResult<T>.value(): T =
      (this as? BetterAuthResult.Success<T>)?.value ?: error("expected success, got $this")

  @Test
  fun totpEnrollmentSignInAndBackupCodes() = runBlocking {
    BetterAuthContractServerHarness.start().use { server ->
      val email = "twofactor-${UUID.randomUUID()}@example.com"
      val password = UUID.randomUUID().toString()
      val clients = mutableListOf<Recorded>()

      fun fresh() = Recorded(server.baseUrl).also { clients += it }

      try {
        val a = fresh()
        val userId =
            a.client
                .signUpEmail(SignUpEmailRequest("Two Factor", email, password))
                .value()
                .user!!
                .id
        val enrollment = a.client.twoFactor.enable(password).value()
        assertTrue(enrollment.totpUri.startsWith("otpauth://totp/"))
        assertTrue(enrollment.backupCodes.isNotEmpty())
        val before = a.storage.token()
        a.client.twoFactor.verifyTotp(totp(enrollment.totpUri)).value()
        assertNotEquals(before, a.storage.token())

        val b = fresh()
        val signedIn = b.client.signInEmail(SignInEmailRequest(email, password)).value()
        assertEquals(SignInResult.TwoFactorRequired(listOf("totp")), signedIn.signInResult)
        assertNull(b.storage.token())
        // Plan L7, observed against better-auth 1.7.17: the twoFactorRedirect response carries a
        // set-auth-token for the session the server deletes; the client must not store it.
        assertNotNull(b.headers.last())

        val wrong = b.client.twoFactor.verifyTotp(staleCode(enrollment.totpUri))
        assertEquals(401, (wrong as BetterAuthResult.Failure).status, "wrong=$wrong")
        assertEquals("INVALID_CODE", wrong.error.code)
        b.client.twoFactor.verifyTotp(totp(enrollment.totpUri)).value()
        assertEquals(b.headers.last(), b.storage.token())
        assertNotNull(b.storage.token())
        assertEquals(userId, b.client.getSession().value()!!.user.id)

        val d = fresh()
        val control = d.client.twoFactor.verifyTotp(totp(enrollment.totpUri))
        assertEquals(401, (control as BetterAuthResult.Failure).status)
        assertEquals("INVALID_TWO_FACTOR_COOKIE", control.error.code)

        val other = fresh()
        other.client.signUpEmail(SignUpEmailRequest("Other", "o-$email", password)).value()
        val otherToken = other.storage.token()
        assertNotNull(otherToken)
        assertTrue(
            other.client.signInEmail(SignInEmailRequest(email, password)).value().isChallenge()
        )
        assertEquals(otherToken, other.storage.token())
        other.client.twoFactor.verifyTotp(totp(enrollment.totpUri)).value()
        assertEquals(userId, other.client.getSession().value()!!.user.id)

        val e = fresh()
        e.client.signInEmail(SignInEmailRequest(email, password)).value()
        e.client.twoFactor.verifyBackupCode(enrollment.backupCodes[0]).value()
        val f = fresh()
        f.client.signInEmail(SignInEmailRequest(email, password)).value()
        val reused = f.client.twoFactor.verifyBackupCode(enrollment.backupCodes[0])
        assertEquals(401, (reused as BetterAuthResult.Failure).status)
        assertEquals("INVALID_BACKUP_CODE", reused.error.code)

        val regenerated = a.client.twoFactor.generateBackupCodes(password).value()
        assertNotEquals(enrollment.backupCodes, regenerated)
        a.client.twoFactor.disable(password).value()
        val g = fresh()
        val plain = g.client.signInEmail(SignInEmailRequest(email, password)).value()
        assertTrue(plain.signInResult is SignInResult.SignedIn)
      } finally {
        clients.forEach { it.client.close() }
      }
    }
  }

  private fun AuthResponse.isChallenge(): Boolean = signInResult is SignInResult.TwoFactorRequired
}
