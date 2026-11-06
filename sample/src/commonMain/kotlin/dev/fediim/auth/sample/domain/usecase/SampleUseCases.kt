package dev.fediim.auth.sample.domain.usecase

import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import dev.fediim.auth.sample.domain.model.DemoPreset
import dev.fediim.auth.sample.domain.model.EnvironmentConfig
import dev.fediim.auth.sample.domain.model.PasskeyItem
import dev.fediim.auth.sample.domain.model.SessionOverview
import dev.fediim.auth.sample.domain.model.TwoFactorStatus
import dev.fediim.auth.sample.domain.repository.SampleAuthRepository

/** Validates and switches between offline demo and live backend environments. */
public class SwitchEnvironmentUseCase(private val repository: SampleAuthRepository) {
  public suspend operator fun invoke(config: EnvironmentConfig): Result<EnvironmentConfig> =
      repository.switchEnvironment(config)
}

/** Credentials and target preset configuration for quick demo sign-in. */
public data class DemoPresetConfig(
    val email: String,
    val password: String,
    val preset: DemoPreset,
)

/** Resolves credentials and configuration for a selected demo persona preset. */
public class ApplyDemoPresetUseCase(private val repository: SampleAuthRepository) {
  public operator fun invoke(preset: DemoPreset): DemoPresetConfig =
      when (preset) {
        DemoPreset.STANDARD ->
            DemoPresetConfig("alice@example.com", "Password1!", DemoPreset.STANDARD)
        DemoPreset.TWO_FACTOR ->
            DemoPresetConfig("bob-2fa@example.com", "Password1!", DemoPreset.TWO_FACTOR)
        DemoPreset.PASSKEY -> DemoPresetConfig("carol-passkey@example.com", "", DemoPreset.PASSKEY)
      }
}

/** Configures simulated error scenarios on the offline demo repository. */
public class SetDemoFailureScenarioUseCase(private val repository: SampleAuthRepository) {
  public operator fun invoke(scenario: DemoFailureScenario): Unit =
      repository.setDemoFailureScenario(scenario)
}

/** Resets all in-memory demo data, sessions, and credentials to initial seed state. */
public class ResetDemoStateUseCase(private val repository: SampleAuthRepository) {
  public suspend operator fun invoke(): Result<Unit> = runCatching { repository.resetDemoState() }
}

/** Reads current session information and maps it to a sanitized presentation overview. */
public class InspectSessionUseCase(private val repository: SampleAuthRepository) {
  public suspend operator fun invoke(): Result<SessionOverview?> = repository.inspectSession()
}

/** Lists, registers, and deletes WebAuthn passkey credentials. */
public class ManagePasskeysUseCase(private val repository: SampleAuthRepository) {
  public suspend fun list(): Result<List<PasskeyItem>> = repository.listPasskeys()

  public suspend fun register(name: String): Result<PasskeyItem> = repository.registerPasskey(name)

  public suspend fun delete(id: String): Result<Unit> = repository.deletePasskey(id)
}

/** Reads two-factor status and handles disabling two-factor authentication. */
public class ManageTwoFactorUseCase(private val repository: SampleAuthRepository) {
  public suspend fun getStatus(): Result<TwoFactorStatus> = repository.getTwoFactorStatus()

  public suspend fun disable(password: String? = null): Result<TwoFactorStatus> =
      repository.disableTwoFactor(password)
}

/** Consolidated domain use cases container for the sample application. */
public data class SampleUseCases(
    val switchEnvironment: SwitchEnvironmentUseCase,
    val applyDemoPreset: ApplyDemoPresetUseCase,
    val setDemoFailureScenario: SetDemoFailureScenarioUseCase,
    val resetDemoState: ResetDemoStateUseCase,
    val inspectSession: InspectSessionUseCase,
    val managePasskeys: ManagePasskeysUseCase,
    val manageTwoFactor: ManageTwoFactorUseCase,
)
