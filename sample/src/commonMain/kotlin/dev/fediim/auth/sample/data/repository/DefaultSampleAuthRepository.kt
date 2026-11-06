package dev.fediim.auth.sample.data.repository

import com.russhwolf.settings.Settings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import dev.fediim.auth.passkeys.BetterAuthPasskeyClient
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.auth.sample.data.demo.DemoPasskeyAuthenticator
import dev.fediim.auth.sample.data.demo.DemoServerStore
import dev.fediim.auth.sample.data.demo.DemoSocialAuthLauncher
import dev.fediim.auth.sample.data.demo.createDemoMockEngine
import dev.fediim.auth.sample.data.mapper.SampleMappers
import dev.fediim.auth.sample.data.platform.createLivePlatformAuthBridges
import dev.fediim.auth.sample.domain.model.BackendMode
import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import dev.fediim.auth.sample.domain.model.EnvironmentConfig
import dev.fediim.auth.sample.domain.model.PasskeyItem
import dev.fediim.auth.sample.domain.model.SessionOverview
import dev.fediim.auth.sample.domain.model.TwoFactorStatus
import dev.fediim.auth.sample.domain.repository.SampleAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default repository implementation managing sample store, live bridges, and BetterAuthClient
 * lifecycle.
 */
public class DefaultSampleAuthRepository(
    initialEnvironment: EnvironmentConfig = EnvironmentConfig(),
) : SampleAuthRepository {
  public val demoStore: DemoServerStore = DemoServerStore()

  private val _environment = MutableStateFlow(initialEnvironment)
  override val environment: StateFlow<EnvironmentConfig> = _environment.asStateFlow()

  private val _failureScenario = MutableStateFlow(DemoFailureScenario.NONE)
  override val failureScenario: StateFlow<DemoFailureScenario> = _failureScenario.asStateFlow()

  private var currentStorage: SettingsAuthTokenStorage =
      SettingsAuthTokenStorage(InMemorySettings())
  private var currentClient: BetterAuthClient
  private var currentPasskeyClient: BetterAuthPasskeyClient?
  private var currentCapability: PasskeyCapability = PasskeyCapability.Supported

  public val client: BetterAuthClient
    get() = currentClient

  public val passkeyClient: BetterAuthPasskeyClient?
    get() = currentPasskeyClient

  public val passkeyCapability: PasskeyCapability
    get() = currentCapability

  init {
    val (c, p) = createClients(initialEnvironment, currentStorage)
    currentClient = c
    currentPasskeyClient = p
  }

  private fun createClients(
      config: EnvironmentConfig,
      storage: SettingsAuthTokenStorage,
  ): Pair<BetterAuthClient, BetterAuthPasskeyClient?> {
    return when (config.mode) {
      BackendMode.OFFLINE_DEMO -> {
        currentCapability = PasskeyCapability.Supported
        val mockEngine = createDemoMockEngine(demoStore)
        val socialLauncher = DemoSocialAuthLauncher(demoStore)
        val authenticator = DemoPasskeyAuthenticator { _failureScenario.value }
        val authClient =
            BetterAuthClient(
                config =
                    BetterAuthConfig(
                        baseUrl = "http://127.0.0.1:3000",
                        allowInsecureHttpForTesting = true,
                    ),
                tokenStorage = storage,
                engine = mockEngine,
                socialAuthLauncher = socialLauncher,
            )
        val passkeys = BetterAuthPasskeyClient(authClient, authenticator)
        Pair(authClient, passkeys)
      }
      BackendMode.LIVE_SERVER -> {
        val liveBridges = createLivePlatformAuthBridges()
        currentCapability = liveBridges.passkeyAuthenticator.capability
        val authClient =
            BetterAuthClient(
                config =
                    BetterAuthConfig(
                        baseUrl = config.liveServerUrl,
                        allowInsecureHttpForTesting = false,
                    ),
                tokenStorage = storage,
                socialAuthLauncher = liveBridges.socialAuthLauncher,
            )
        val passkeys = BetterAuthPasskeyClient(authClient, liveBridges.passkeyAuthenticator)
        Pair(authClient, passkeys)
      }
    }
  }

  override fun validateServerUrl(rawUrl: String): Result<String> = runCatching {
    BetterAuthConfig(rawUrl, allowInsecureHttpForTesting = false).baseUrl
  }

  override suspend fun switchEnvironment(config: EnvironmentConfig): Result<EnvironmentConfig> {
    if (config == _environment.value) {
      return Result.success(config)
    }
    val targetConfig =
        if (config.mode == BackendMode.LIVE_SERVER) {
          val validUrl =
              validateServerUrl(config.liveServerUrl).getOrElse {
                return Result.failure(it)
              }
          config.copy(liveServerUrl = validUrl)
        } else {
          config
        }

    currentClient.close()
    val newStorage = SettingsAuthTokenStorage(InMemorySettings())
    val (newClient, newPasskeyClient) = createClients(targetConfig, newStorage)
    currentStorage = newStorage
    currentClient = newClient
    currentPasskeyClient = newPasskeyClient
    _environment.value = targetConfig
    return Result.success(targetConfig)
  }

  override fun setDemoFailureScenario(scenario: DemoFailureScenario) {
    _failureScenario.value = scenario
    demoStore.failureScenario = scenario
  }

  override suspend fun resetDemoState() {
    demoStore.reset()
    _failureScenario.value = DemoFailureScenario.NONE
    currentStorage.clear()
  }

  override suspend fun inspectSession(): Result<SessionOverview?> {
    return when (val res = client.getSession()) {
      is BetterAuthResult.Success -> {
        val envelope = res.value
        if (envelope != null) {
          val activeOrgId = currentStorage.activeOrganizationId()
          Result.success(SampleMappers.toSessionOverview(envelope, activeOrgId))
        } else {
          Result.success(null)
        }
      }
      is BetterAuthResult.Failure -> Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.RateLimited ->
          Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.NetworkError -> Result.failure(res.cause)
    }
  }

  override suspend fun listPasskeys(): Result<List<PasskeyItem>> {
    val pkClient = currentPasskeyClient ?: return Result.success(emptyList())
    return when (val res = pkClient.listPasskeys()) {
      is BetterAuthResult.Success ->
          Result.success(res.value.map { SampleMappers.toPasskeyItem(it) })
      is BetterAuthResult.Failure -> Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.RateLimited ->
          Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.NetworkError -> Result.failure(res.cause)
    }
  }

  override suspend fun registerPasskey(name: String): Result<PasskeyItem> {
    val pkClient =
        currentPasskeyClient ?: return Result.failure(IllegalStateException("Passkeys unsupported"))
    return when (val res = pkClient.registerPasskey(name = name)) {
      is BetterAuthResult.Success -> Result.success(SampleMappers.toPasskeyItem(res.value))
      is BetterAuthResult.Failure -> Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.RateLimited ->
          Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.NetworkError -> Result.failure(res.cause)
    }
  }

  override suspend fun deletePasskey(id: String): Result<Unit> {
    val pkClient =
        currentPasskeyClient ?: return Result.failure(IllegalStateException("Passkeys unsupported"))
    return when (val res = pkClient.deletePasskey(id)) {
      is BetterAuthResult.Success -> Result.success(Unit)
      is BetterAuthResult.Failure -> Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.RateLimited ->
          Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.NetworkError -> Result.failure(res.cause)
    }
  }

  override suspend fun getTwoFactorStatus(): Result<TwoFactorStatus> {
    val user = inspectSession().getOrNull()?.user
    val demoUser = user?.id?.let { demoStore.findUserById(it) }
    return Result.success(TwoFactorStatus(isEnabled = demoUser?.twoFactorEnabled == true))
  }

  override suspend fun disableTwoFactor(password: String?): Result<TwoFactorStatus> {
    val pass = password ?: "Password1!"
    return when (val res = client.twoFactor.disable(pass)) {
      is BetterAuthResult.Success -> Result.success(TwoFactorStatus(isEnabled = false))
      is BetterAuthResult.Failure -> Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.RateLimited ->
          Result.failure(Exception(res.error.message ?: res.error.code))
      is BetterAuthResult.NetworkError -> Result.failure(res.cause)
    }
  }
}

private class InMemorySettings(
    private val values: MutableMap<String, Any?> = mutableMapOf(),
) : Settings {
  override val keys: Set<String>
    get() = values.keys

  override val size: Int
    get() = values.size

  override fun clear() {
    values.clear()
  }

  override fun remove(key: String) {
    values.remove(key)
  }

  override fun hasKey(key: String): Boolean = values.containsKey(key)

  override fun putInt(key: String, value: Int) {
    values[key] = value
  }

  override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue

  override fun getIntOrNull(key: String): Int? = values[key] as? Int

  override fun putLong(key: String, value: Long) {
    values[key] = value
  }

  override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue

  override fun getLongOrNull(key: String): Long? = values[key] as? Long

  override fun putString(key: String, value: String) {
    values[key] = value
  }

  override fun getString(key: String, defaultValue: String): String =
      values[key] as? String ?: defaultValue

  override fun getStringOrNull(key: String): String? = values[key] as? String

  override fun putFloat(key: String, value: Float) {
    values[key] = value
  }

  override fun getFloat(key: String, defaultValue: Float): Float =
      values[key] as? Float ?: defaultValue

  override fun getFloatOrNull(key: String): Float? = values[key] as? Float

  override fun putDouble(key: String, value: Double) {
    values[key] = value
  }

  override fun getDouble(key: String, defaultValue: Double): Double =
      values[key] as? Double ?: defaultValue

  override fun getDoubleOrNull(key: String): Double? = values[key] as? Double

  override fun putBoolean(key: String, value: Boolean) {
    values[key] = value
  }

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
      values[key] as? Boolean ?: defaultValue

  override fun getBooleanOrNull(key: String): Boolean? = values[key] as? Boolean
}
