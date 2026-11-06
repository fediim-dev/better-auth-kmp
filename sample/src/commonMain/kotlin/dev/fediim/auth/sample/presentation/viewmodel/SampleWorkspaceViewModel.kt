package dev.fediim.auth.sample.presentation.viewmodel

import androidx.compose.runtime.Immutable
import dev.fediim.auth.compose.ApiKeyUiIntent
import dev.fediim.auth.compose.ApiKeyViewModel
import dev.fediim.auth.compose.AuthMode
import dev.fediim.auth.compose.AuthUiIntent
import dev.fediim.auth.compose.BetterAuthViewModel
import dev.fediim.auth.compose.OrganizationUiIntent
import dev.fediim.auth.compose.OrganizationViewModel
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.passkeys.BetterAuthPasskeyClient
import dev.fediim.auth.passkeys.PasskeyCapability
import dev.fediim.auth.sample.domain.model.BackendMode
import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import dev.fediim.auth.sample.domain.model.DemoPreset
import dev.fediim.auth.sample.domain.model.EnvironmentConfig
import dev.fediim.auth.sample.domain.model.PasskeyItem
import dev.fediim.auth.sample.domain.model.SessionOverview
import dev.fediim.auth.sample.domain.model.TwoFactorStatus
import dev.fediim.auth.sample.domain.repository.SampleAuthRepository
import dev.fediim.auth.sample.domain.usecase.SampleUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Navigation tab selection for the authenticated workspace. */
public enum class WorkspaceTab {
  OVERVIEW,
  SECURITY,
}

/** UI state for the sample workspace screen. */
@Immutable
public data class SampleWorkspaceUiState(
    val environment: EnvironmentConfig = EnvironmentConfig(),
    val failureScenario: DemoFailureScenario = DemoFailureScenario.NONE,
    val selectedTab: WorkspaceTab = WorkspaceTab.OVERVIEW,
    val sessionOverview: SessionOverview? = null,
    val passkeys: List<PasskeyItem> = emptyList(),
    val twoFactorStatus: TwoFactorStatus = TwoFactorStatus(isEnabled = false),
    val passkeyCapability: PasskeyCapability = PasskeyCapability.Supported,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val urlInputError: String? = null,
)

/** Headless view model coordinating auth, organization, API keys, and demo environment state. */
public class SampleWorkspaceViewModel(
    private val repository: SampleAuthRepository,
    private val useCases: SampleUseCases,
    scope: CoroutineScope,
    private val clientProvider: () -> BetterAuthClient = {
      error("BetterAuthClient provider required")
    },
    private val passkeyClientProvider: () -> BetterAuthPasskeyClient? = { null },
    private val passkeyCapabilityProvider: () -> PasskeyCapability = {
      PasskeyCapability.Supported
    },
) : AutoCloseable {
  private val rootJob = SupervisorJob(scope.coroutineContext[Job])
  private val vmScope = CoroutineScope(scope.coroutineContext + rootJob)

  private val _state =
      MutableStateFlow(
          SampleWorkspaceUiState(
              environment = repository.environment.value,
              failureScenario = repository.failureScenario.value,
              passkeyCapability = passkeyCapabilityProvider(),
          )
      )
  public val state: StateFlow<SampleWorkspaceUiState> = _state.asStateFlow()

  private var _organizationViewModel: OrganizationViewModel =
      OrganizationViewModel(clientProvider(), vmScope)
  public val organizationViewModel: OrganizationViewModel
    get() = _organizationViewModel

  private var _apiKeyViewModel: ApiKeyViewModel = ApiKeyViewModel(clientProvider(), vmScope)
  public val apiKeyViewModel: ApiKeyViewModel
    get() = _apiKeyViewModel

  private fun createAuthViewModel(): BetterAuthViewModel {
    return BetterAuthViewModel(
        client = clientProvider(),
        scope = vmScope,
        passkeys = passkeyClientProvider(),
        onClearUserCache = {
          _organizationViewModel.dispatch(OrganizationUiIntent.Clear)
          _apiKeyViewModel.dispatch(ApiKeyUiIntent.Clear)
          _state.update {
            it.copy(
                sessionOverview = null,
                passkeys = emptyList(),
                twoFactorStatus = TwoFactorStatus(isEnabled = false),
            )
          }
        },
    )
  }

  private var _authViewModel: BetterAuthViewModel = createAuthViewModel()
  public val authViewModel: BetterAuthViewModel
    get() = _authViewModel

  private var authObserverJob: Job? = null

  private fun setupAuthObserver() {
    authObserverJob?.cancel()
    authObserverJob = vmScope.launch {
      _authViewModel.state.collect { authState ->
        if (authState.isSignedIn) {
          refreshSessionOverview()
          refreshSecurity()
        }
      }
    }
  }

  private fun recreateViewModels() {
    _authViewModel.close()
    _organizationViewModel.close()
    _apiKeyViewModel.close()
    _organizationViewModel = OrganizationViewModel(clientProvider(), vmScope)
    _apiKeyViewModel = ApiKeyViewModel(clientProvider(), vmScope)
    _authViewModel = createAuthViewModel()
    setupAuthObserver()
    _state.update { it.copy(passkeyCapability = passkeyCapabilityProvider()) }
  }

  init {
    vmScope.launch {
      repository.environment.collect { env -> _state.update { it.copy(environment = env) } }
    }
    vmScope.launch {
      repository.failureScenario.collect { scenario ->
        _state.update { it.copy(failureScenario = scenario) }
      }
    }
    setupAuthObserver()
  }

  public fun selectTab(tab: WorkspaceTab) {
    _state.update { it.copy(selectedTab = tab) }
  }

  public fun applyPreset(preset: DemoPreset) {
    val config = useCases.applyDemoPreset(preset)
    when (preset) {
      DemoPreset.STANDARD,
      DemoPreset.TWO_FACTOR -> {
        authViewModel.dispatch(AuthUiIntent.SetMode(AuthMode.SIGN_IN))
        authViewModel.dispatch(AuthUiIntent.EmailChanged(config.email))
        authViewModel.dispatch(AuthUiIntent.PasswordChanged(config.password))
      }
      DemoPreset.PASSKEY -> {
        authViewModel.dispatch(AuthUiIntent.SignInPasskey)
      }
    }
  }

  public fun setFailureScenario(scenario: DemoFailureScenario) {
    useCases.setDemoFailureScenario(scenario)
  }

  public fun switchEnvironment(mode: BackendMode, rawUrl: String = "") {
    vmScope.launch {
      if (mode == _state.value.environment.mode && mode == BackendMode.OFFLINE_DEMO) {
        _state.update { it.copy(isLoading = false, urlInputError = null) }
        return@launch
      }
      _state.update { it.copy(isLoading = true, urlInputError = null) }
      val targetConfig =
          if (mode == BackendMode.LIVE_SERVER) {
            val valid = repository.validateServerUrl(rawUrl)
            if (valid.isFailure) {
              _state.update {
                it.copy(
                    isLoading = false,
                    urlInputError = "Invalid server URL: ${valid.exceptionOrNull()?.message}",
                )
              }
              return@launch
            }
            EnvironmentConfig(
                mode = BackendMode.LIVE_SERVER,
                liveServerUrl = valid.getOrThrow(),
            )
          } else {
            EnvironmentConfig(mode = BackendMode.OFFLINE_DEMO)
          }

      val res = useCases.switchEnvironment(targetConfig)
      if (res.isSuccess) {
        recreateViewModels()
        _state.update { it.copy(isLoading = false, urlInputError = null) }
      } else {
        _state.update { it.copy(isLoading = false, urlInputError = res.exceptionOrNull()?.message) }
      }
    }
  }

  public fun resetDemoState() {
    vmScope.launch {
      _state.update { it.copy(isLoading = true, selectedTab = WorkspaceTab.OVERVIEW) }
      useCases.resetDemoState()
      authViewModel.dispatch(AuthUiIntent.Logout)
      _state.update { it.copy(isLoading = false) }
    }
  }

  public fun refreshSessionOverview() {
    vmScope.launch {
      val res = useCases.inspectSession()
      _state.update { it.copy(sessionOverview = res.getOrNull()) }
    }
  }

  public fun refreshSecurity() {
    vmScope.launch {
      val pks = useCases.managePasskeys.list().getOrDefault(emptyList())
      val tfa =
          useCases.manageTwoFactor.getStatus().getOrDefault(TwoFactorStatus(isEnabled = false))
      _state.update { it.copy(passkeys = pks, twoFactorStatus = tfa) }
    }
  }

  public fun registerPasskey(name: String) {
    vmScope.launch {
      _state.update { it.copy(isLoading = true) }
      val res = useCases.managePasskeys.register(name)
      if (res.isSuccess) {
        refreshSecurity()
      } else {
        _state.update { it.copy(errorMessage = res.exceptionOrNull()?.message) }
      }
      _state.update { it.copy(isLoading = false) }
    }
  }

  public fun deletePasskey(id: String) {
    vmScope.launch {
      _state.update { it.copy(isLoading = true) }
      val res = useCases.managePasskeys.delete(id)
      if (res.isSuccess) {
        refreshSecurity()
      }
      _state.update { it.copy(isLoading = false) }
    }
  }

  public fun disableTwoFactor(password: String? = null) {
    vmScope.launch {
      _state.update { it.copy(isLoading = true) }
      val res = useCases.manageTwoFactor.disable(password)
      if (res.isSuccess) {
        _state.update { it.copy(twoFactorStatus = res.getOrThrow()) }
      }
      _state.update { it.copy(isLoading = false) }
    }
  }

  override fun close() {
    authObserverJob?.cancel()
    _authViewModel.close()
    _organizationViewModel.close()
    _apiKeyViewModel.close()
    rootJob.cancel()
  }
}
