package dev.fediim.auth.compose

import androidx.compose.runtime.Immutable
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.organization.FullOrganization
import dev.fediim.auth.core.organization.Organization
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
 * What an organization screen renders. [error] is null when there is nothing to show; a failed
 * request keeps the previous [organizations] and [activeOrganization].
 */
@Immutable
public data class OrganizationUiState(
    val organizations: List<Organization> = emptyList(),
    val activeOrganization: FullOrganization? = null,
    val isLoading: Boolean = false,
    val error: BetterAuthError? = null,
)

/** User actions accepted by [OrganizationViewModel.dispatch]. */
public sealed interface OrganizationUiIntent {
  /** Loads the organization list and the active organization with its members. */
  public data object Load : OrganizationUiIntent

  /** Activates [organizationId], or clears the active organization with null, then reloads it. */
  public data class SetActive(val organizationId: String?) : OrganizationUiIntent

  /** Invites [email] to the active organization with [role]. */
  public data class InviteMember(val email: String, val role: String) : OrganizationUiIntent

  /** Removes a member of the active organization by member id or email. */
  public data class RemoveMember(val memberIdOrEmail: String) : OrganizationUiIntent

  /** Changes the [role] of the member [memberId] in the active organization. */
  public data class UpdateMemberRole(val memberId: String, val role: String) : OrganizationUiIntent

  /** Cancels running requests and resets the state without sending any request. */
  public data object Clear : OrganizationUiIntent
}

/**
 * Headless state holder for organizations over `client.organizations`. [dispatch] is not
 * thread-safe: call it on [scope]'s single thread. There is no single-flight guard, so the last
 * response to arrive wins; [OrganizationUiIntent.Clear] cancels requests still running. Member
 * operations pass the active organization id and, on success, reload the active organization. After
 * [close], [dispatch] does nothing.
 *
 * Not tied to the session: send [OrganizationUiIntent.Clear] from the `onClearUserCache` callback
 * of [BetterAuthViewModel], or the previous user's organizations stay visible and a late response
 * to their request can fill the next user's screen.
 */
public class OrganizationViewModel(private val client: BetterAuthClient, scope: CoroutineScope) :
    AutoCloseable {
  private val rootJob = SupervisorJob(scope.coroutineContext[Job])
  private val scope = CoroutineScope(scope.coroutineContext + rootJob)
  private val _state = MutableStateFlow(OrganizationUiState())
  private var closed = false

  /** The current state. */
  public val state: StateFlow<OrganizationUiState> = _state.asStateFlow()

  /** Handles [intent]; see [OrganizationUiIntent]. */
  public fun dispatch(intent: OrganizationUiIntent) {
    if (closed) return
    when (intent) {
      OrganizationUiIntent.Load -> launchOp { load() }
      is OrganizationUiIntent.SetActive -> launchOp { setActive(intent.organizationId) }
      is OrganizationUiIntent.InviteMember ->
          mutate { id -> client.organizations.inviteMember(intent.email, intent.role, id) }
      is OrganizationUiIntent.RemoveMember ->
          mutate { id -> client.organizations.removeMember(intent.memberIdOrEmail, id) }
      is OrganizationUiIntent.UpdateMemberRole ->
          mutate { id -> client.organizations.updateMemberRole(intent.memberId, intent.role, id) }
      OrganizationUiIntent.Clear -> {
        rootJob.cancelChildren()
        _state.value = OrganizationUiState()
      }
    }
  }

  /** Cancels running requests and ignores later intents. */
  override fun close() {
    closed = true
    rootJob.cancel()
  }

  private suspend fun load() {
    val list = client.organizations.list()
    if (list !is BetterAuthResult.Success) return fail(list)
    val full = client.organizations.getFullOrganization()
    if (full !is BetterAuthResult.Success) return fail(full)
    _state.update { it.copy(organizations = list.value, activeOrganization = full.value) }
  }

  private suspend fun setActive(organizationId: String?) {
    val set = client.organizations.setActive(organizationId)
    if (set !is BetterAuthResult.Success) return fail(set)
    refreshActive()
  }

  private fun mutate(call: suspend (activeOrganizationId: String?) -> BetterAuthResult<*>) =
      launchOp {
        val result = call(_state.value.activeOrganization?.id)
        if (result !is BetterAuthResult.Success) fail(result) else refreshActive()
      }

  private suspend fun refreshActive() {
    val full = client.organizations.getFullOrganization()
    if (full is BetterAuthResult.Success) {
      _state.update { it.copy(activeOrganization = full.value) }
    } else {
      fail(full)
    }
  }

  private fun fail(result: BetterAuthResult<*>) {
    _state.update { it.copy(error = result.toUiError()) }
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
