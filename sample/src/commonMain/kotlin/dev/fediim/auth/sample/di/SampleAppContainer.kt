package dev.fediim.auth.sample.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.sample.data.repository.DefaultSampleAuthRepository
import dev.fediim.auth.sample.domain.model.EnvironmentConfig
import dev.fediim.auth.sample.domain.repository.SampleAuthRepository
import dev.fediim.auth.sample.domain.usecase.ApplyDemoPresetUseCase
import dev.fediim.auth.sample.domain.usecase.InspectSessionUseCase
import dev.fediim.auth.sample.domain.usecase.ManagePasskeysUseCase
import dev.fediim.auth.sample.domain.usecase.ManageTwoFactorUseCase
import dev.fediim.auth.sample.domain.usecase.ResetDemoStateUseCase
import dev.fediim.auth.sample.domain.usecase.SampleUseCases
import dev.fediim.auth.sample.domain.usecase.SetDemoFailureScenarioUseCase
import dev.fediim.auth.sample.domain.usecase.SwitchEnvironmentUseCase
import dev.fediim.auth.sample.presentation.viewmodel.SampleWorkspaceViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Dependency injection container providing repository, use cases, client, and workspace view model.
 */
public class SampleAppContainer(
    scope: CoroutineScope,
    initialEnvironment: EnvironmentConfig = EnvironmentConfig(),
) : AutoCloseable {
  private val defaultRepository = DefaultSampleAuthRepository(initialEnvironment)
  public val repository: SampleAuthRepository = defaultRepository
  public val client: BetterAuthClient
    get() = defaultRepository.client

  public val useCases: SampleUseCases =
      SampleUseCases(
          switchEnvironment = SwitchEnvironmentUseCase(repository),
          applyDemoPreset = ApplyDemoPresetUseCase(repository),
          setDemoFailureScenario = SetDemoFailureScenarioUseCase(repository),
          resetDemoState = ResetDemoStateUseCase(repository),
          inspectSession = InspectSessionUseCase(repository),
          managePasskeys = ManagePasskeysUseCase(repository),
          manageTwoFactor = ManageTwoFactorUseCase(repository),
      )
  public val workspaceViewModel: SampleWorkspaceViewModel =
      SampleWorkspaceViewModel(
          repository = repository,
          useCases = useCases,
          scope = scope,
          clientProvider = { defaultRepository.client },
          passkeyClientProvider = { defaultRepository.passkeyClient },
          passkeyCapabilityProvider = { defaultRepository.passkeyCapability },
      )

  override fun close() {
    workspaceViewModel.close()
  }
}

/** Remembers and manages the lifecycle of a [SampleAppContainer] across compositions. */
@Composable
public fun rememberSampleAppContainer(
    initialEnvironment: EnvironmentConfig = EnvironmentConfig(),
): SampleAppContainer {
  val scope = rememberCoroutineScope()
  val container = remember(scope) { SampleAppContainer(scope, initialEnvironment) }
  DisposableEffect(container) { onDispose { container.close() } }
  return container
}
