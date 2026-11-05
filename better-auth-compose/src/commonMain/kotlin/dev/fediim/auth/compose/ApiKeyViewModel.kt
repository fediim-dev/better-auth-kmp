package dev.fediim.auth.compose

import androidx.compose.runtime.Immutable
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.apikey.ApiKeyRecord
import dev.fediim.auth.core.apikey.CreateApiKeyRequest
import dev.fediim.auth.core.apikey.CreatedApiKey
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What an API key screen renders. [createdKey] holds the raw secret of the key just created for
 * one-time display, until it is dismissed or the state is cleared; [CreatedApiKey.toString] omits
 * the secret. [error] is null when there is nothing to show.
 */
@Immutable
public data class ApiKeyUiState(
    val keys: List<ApiKeyRecord> = emptyList(),
    val createdKey: CreatedApiKey? = null,
    val isLoading: Boolean = false,
    val error: BetterAuthError? = null,
)

/** User actions accepted by [ApiKeyViewModel.dispatch]. */
public sealed interface ApiKeyUiIntent {
  /** Loads the key list. */
  public data object Load : ApiKeyUiIntent

  /** Creates a key from [request]; it is appended to the list and shown in `createdKey`. */
  public data class Create(val request: CreateApiKeyRequest = CreateApiKeyRequest()) :
      ApiKeyUiIntent

  /** Drops `createdKey` from the state; the list is kept. */
  public data object DismissCreatedKey : ApiKeyUiIntent

  /** Deletes the key [keyId]; it leaves the list only when the server confirms. */
  public data class Revoke(val keyId: String) : ApiKeyUiIntent

  /** Cancels running requests and resets the state, the created key included. */
  public data object Clear : ApiKeyUiIntent
}

/**
 * Headless state holder for API keys over `client.apiKeys`. [dispatch] is not thread-safe: call it
 * on [scope]'s single thread. There is no single-flight guard, so the last response to arrive wins;
 * [ApiKeyUiIntent.Clear] cancels requests still running. After [close], [dispatch] does nothing.
 */
public class ApiKeyViewModel(private val client: BetterAuthClient, scope: CoroutineScope) :
    AutoCloseable {
  private val rootJob = SupervisorJob(scope.coroutineContext[Job])
  private val scope = CoroutineScope(scope.coroutineContext + rootJob)
  private val _state = MutableStateFlow(ApiKeyUiState())
  private var closed = false

  /** The current state. */
  public val state: StateFlow<ApiKeyUiState> = _state.asStateFlow()

  /** Handles [intent]; see [ApiKeyUiIntent]. */
  public fun dispatch(intent: ApiKeyUiIntent) {
    if (closed) return
    when (intent) {
      ApiKeyUiIntent.Load -> launchOp { load() }
      is ApiKeyUiIntent.Create -> launchOp { create(intent.request) }
      ApiKeyUiIntent.DismissCreatedKey -> _state.update { it.copy(createdKey = null) }
      is ApiKeyUiIntent.Revoke -> launchOp { revoke(intent.keyId) }
      ApiKeyUiIntent.Clear -> {
        rootJob.cancelChildren()
        _state.value = ApiKeyUiState()
      }
    }
  }

  /** Cancels running requests and ignores later intents. */
  override fun close() {
    closed = true
    rootJob.cancel()
  }

  private suspend fun load() = applyResult(client.apiKeys.list()) { keys -> copy(keys = keys) }

  private suspend fun create(request: CreateApiKeyRequest) =
      applyResult(client.apiKeys.create(request)) { created ->
        copy(createdKey = created, keys = keys + created.record)
      }

  private suspend fun revoke(keyId: String) =
      applyResult(client.apiKeys.delete(keyId)) { copy(keys = keys.filterNot { it.id == keyId }) }

  private fun <T> applyResult(
      result: BetterAuthResult<T>,
      onSuccess: ApiKeyUiState.(T) -> ApiKeyUiState,
  ) {
    if (result is BetterAuthResult.Success) {
      _state.update { it.onSuccess(result.value) }
    } else {
      _state.update { it.copy(error = result.toUiError()) }
    }
  }

  private fun launchOp(block: suspend () -> Unit) {
    _state.update { it.copy(isLoading = true, error = null) }
    scope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _state.update { it.copy(error = BetterAuthError(code = AuthUiErrorCodes.UNEXPECTED)) }
      } finally {
        _state.update { it.copy(isLoading = false) }
      }
    }
  }
}
