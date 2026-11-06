package dev.fediim.auth.sample

import dev.fediim.auth.core.apikey.CreateApiKeyRequest
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SignInEmailRequest
import dev.fediim.auth.core.model.SignUpEmailRequest
import dev.fediim.auth.core.twofactor.SignInResult
import dev.fediim.auth.core.twofactor.signInResult
import dev.fediim.auth.sample.data.repository.DefaultSampleAuthRepository
import dev.fediim.auth.sample.domain.model.BackendMode
import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import dev.fediim.auth.sample.domain.model.EnvironmentConfig
import dev.fediim.auth.sample.domain.model.maskSessionToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

class SampleDomainAndDataTest {
  private fun makeDemoToken21(): String = buildString {
    append("demo")
    append("-session-")
    append("tok-")
    append("1234")
  }

  @Test
  fun testUrlValidation() = runTest {
    val repo = DefaultSampleAuthRepository()
    assertTrue(repo.validateServerUrl("https://auth.example.com").isSuccess)
    assertTrue(repo.validateServerUrl("http://127.0.0.1:3000").isSuccess)

    val evilRes = repo.validateServerUrl("http://evil.example.com")
    val ftpRes = repo.validateServerUrl("ftp://bad.example.com")
    assertTrue(evilRes.isFailure)
    assertTrue(ftpRes.isFailure)

    val switched =
        repo.switchEnvironment(
            EnvironmentConfig(BackendMode.LIVE_SERVER, "https://auth.example.com")
        )
    assertTrue(switched.isSuccess)
    assertEquals("https://auth.example.com", repo.environment.value.liveServerUrl)

    val failedSwitch =
        repo.switchEnvironment(
            EnvironmentConfig(BackendMode.LIVE_SERVER, "http://evil.example.com")
        )
    assertTrue(failedSwitch.isFailure)
    assertEquals("https://auth.example.com", repo.environment.value.liveServerUrl)
  }

  @Test
  fun testMaskSessionToken() {
    val raw = makeDemoToken21()
    assertEquals(21, raw.length)
    val masked = maskSessionToken(raw)
    assertEquals("demo••••1234", masked)
    assertFalse(masked.contains(raw))
    assertEquals("", maskSessionToken(null))
    assertEquals("", maskSessionToken(""))
    assertEquals("••••", maskSessionToken("short"))
  }

  @Test
  fun testStandardSignInAndRegistration() = runTest {
    val repo = DefaultSampleAuthRepository()
    val client = repo.client

    repo.setDemoFailureScenario(DemoFailureScenario.INVALID_CREDENTIALS)
    val badRes = client.signInEmail(SignInEmailRequest("alice@example.com", "wrongpass"))
    assertTrue(badRes is BetterAuthResult.Failure)
    assertNull(repo.inspectSession().getOrNull())

    repo.setDemoFailureScenario(DemoFailureScenario.NONE)
    val okRes = client.signInEmail(SignInEmailRequest("alice@example.com", "Password1!"))
    assertTrue(okRes is BetterAuthResult.Success)

    val session = repo.inspectSession().getOrNull()
    assertNotNull(session)
    assertEquals("alice@example.com", session.user.email)
    assertEquals("lead-architect", session.user.additionalFields["role"]?.jsonPrimitive?.content)
    assertEquals(
        "kmp-sample",
        session.sessionAdditionalFields["clientKind"]?.jsonPrimitive?.content,
    )
    assertTrue(session.maskedToken.contains("••••"))

    val regRes =
        client.signUpEmail(
            SignUpEmailRequest(
                email = "newuser@example.com",
                password = "Password1!",
                name = "New User",
            )
        )
    assertTrue(regRes is BetterAuthResult.Success)
  }

  @Test
  fun testTwoFactorChallengeAndManagement() = runTest {
    val repo = DefaultSampleAuthRepository()
    val client = repo.client

    val res = client.signInEmail(SignInEmailRequest("bob-2fa@example.com", "Password1!"))
    assertTrue(res is BetterAuthResult.Success)
    assertTrue(
        (res as BetterAuthResult.Success).value.signInResult is SignInResult.TwoFactorRequired
    )

    repo.setDemoFailureScenario(DemoFailureScenario.INVALID_TWO_FACTOR_CODE)
    val badTotp = client.twoFactor.verifyTotp("000111")
    assertTrue(badTotp is BetterAuthResult.Failure)

    repo.setDemoFailureScenario(DemoFailureScenario.NONE)
    val okTotp = client.twoFactor.verifyTotp("123456")
    assertTrue(okTotp is BetterAuthResult.Success)

    val bobRes = client.signInEmail(SignInEmailRequest("bob-2fa@example.com", "Password1!"))
    assertTrue(bobRes is BetterAuthResult.Success)
    assertTrue(
        (bobRes as BetterAuthResult.Success).value.signInResult is SignInResult.TwoFactorRequired
    )
    val backupRes = client.twoFactor.verifyBackupCode("bk-bob-1")
    assertTrue(backupRes is BetterAuthResult.Success)

    val disableRes = repo.disableTwoFactor("Password1!")
    assertTrue(disableRes.isSuccess)
    assertFalse(disableRes.getOrThrow().isEnabled)

    val aliceRes = client.signInEmail(SignInEmailRequest("alice@example.com", "Password1!"))
    assertTrue(aliceRes is BetterAuthResult.Success)
    val enableRes = client.twoFactor.enable("Password1!")
    assertTrue(enableRes is BetterAuthResult.Success)
    val verifyTotp = client.twoFactor.verifyTotp("123456")
    assertTrue(verifyTotp is BetterAuthResult.Success)
    assertTrue(repo.getTwoFactorStatus().getOrThrow().isEnabled)
  }

  @Test
  fun testSocialSignInFlow() = runTest {
    val repo = DefaultSampleAuthRepository()
    val client = repo.client

    for (provider in listOf("github", "google", "apple")) {
      val res = client.signInSocial(provider)
      assertTrue(res is BetterAuthResult.Success)
    }
  }

  @Test
  fun testPasskeySignInAndManagement() = runTest {
    val repo = DefaultSampleAuthRepository()
    val passkeyClient = repo.passkeyClient
    assertNotNull(passkeyClient)

    val res = passkeyClient.signInWithPasskey()
    assertTrue(res is BetterAuthResult.Success)

    val reg = repo.registerPasskey("My Security Key")
    assertTrue(reg.isSuccess)

    val list = repo.listPasskeys().getOrThrow()
    assertTrue(list.any { it.name == "My Security Key" })

    val del = repo.deletePasskey(reg.getOrThrow().id)
    assertTrue(del.isSuccess)

    repo.setDemoFailureScenario(DemoFailureScenario.PASSKEY_FAILED)
    val failRes = passkeyClient.signInWithPasskey()
    assertTrue(failRes is BetterAuthResult.Failure)
  }

  @Test
  fun testOrganizationsAndApiKeys() = runTest {
    val repo = DefaultSampleAuthRepository()
    val client = repo.client

    client.signInEmail(SignInEmailRequest("alice@example.com", "Password1!"))

    val orgsRes = client.organizations.list()
    assertTrue(orgsRes is BetterAuthResult.Success)
    val orgs = (orgsRes as BetterAuthResult.Success).value
    assertEquals(2, orgs.size)

    val setActive = client.organizations.setActive(orgs[1].id)
    assertTrue(setActive is BetterAuthResult.Success)

    val keysRes = client.apiKeys.list()
    assertTrue(keysRes is BetterAuthResult.Success)

    val created = client.apiKeys.create(CreateApiKeyRequest(name = "test-key"))
    assertTrue(created is BetterAuthResult.Success)

    val keyId = (created as BetterAuthResult.Success).value.record.id
    val delKey = client.apiKeys.delete(keyId)
    assertTrue(delKey is BetterAuthResult.Success)
  }

  @Test
  fun testRateLimitAndReset() = runTest {
    val repo = DefaultSampleAuthRepository()
    val client = repo.client

    repo.setDemoFailureScenario(DemoFailureScenario.RATE_LIMITED_429)
    val res = client.signInEmail(SignInEmailRequest("alice@example.com", "Password1!"))
    assertTrue(res is BetterAuthResult.RateLimited)
    assertEquals(30L, (res as BetterAuthResult.RateLimited).retryAfterSeconds)

    repo.resetDemoState()
    assertEquals(DemoFailureScenario.NONE, repo.failureScenario.value)
  }
}
