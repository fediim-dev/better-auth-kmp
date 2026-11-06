package dev.fediim.auth.sample.domain.repository

import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import dev.fediim.auth.sample.domain.model.EnvironmentConfig
import dev.fediim.auth.sample.domain.model.PasskeyItem
import dev.fediim.auth.sample.domain.model.SessionOverview
import dev.fediim.auth.sample.domain.model.TwoFactorStatus
import kotlinx.coroutines.flow.StateFlow

/** Contract for sample backend operations, environment switching, and security management. */
public interface SampleAuthRepository {
  public val environment: StateFlow<EnvironmentConfig>
  public val failureScenario: StateFlow<DemoFailureScenario>

  public fun validateServerUrl(rawUrl: String): Result<String>

  public suspend fun switchEnvironment(config: EnvironmentConfig): Result<EnvironmentConfig>

  public fun setDemoFailureScenario(scenario: DemoFailureScenario)

  public suspend fun resetDemoState()

  public suspend fun inspectSession(): Result<SessionOverview?>

  public suspend fun listPasskeys(): Result<List<PasskeyItem>>

  public suspend fun registerPasskey(name: String): Result<PasskeyItem>

  public suspend fun deletePasskey(id: String): Result<Unit>

  public suspend fun getTwoFactorStatus(): Result<TwoFactorStatus>

  public suspend fun disableTwoFactor(password: String?): Result<TwoFactorStatus>
}
