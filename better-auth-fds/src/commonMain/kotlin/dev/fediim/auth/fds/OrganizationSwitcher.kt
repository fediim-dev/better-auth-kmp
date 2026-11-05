package dev.fediim.auth.fds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.fediim.auth.compose.OrganizationUiIntent
import dev.fediim.auth.compose.OrganizationUiState
import dev.fediim.auth.compose.OrganizationViewModel
import dev.fediim.fds.components.Badge
import dev.fediim.fds.components.Button
import dev.fediim.fds.components.ButtonVariant
import dev.fediim.fds.components.Menu
import dev.fediim.fds.components.MenuItem
import dev.fediim.fds.tokens.FediimSpace

internal const val TAG_ORG_TRIGGER = "fds.org.trigger"

/**
 * Dispatches `Load` each time it enters composition. The trigger shows the active organization's
 * name, or "No organization", and the roles [currentUserId] has in it (comma-joined; none when not
 * a member). The menu lists the organizations; choosing one dispatches `SetActive(id)` and closes
 * the menu. There is no error UI: a failed switch keeps the previous organization. Must be inside
 * `FediimTheme`.
 */
@Composable
public fun OrganizationSwitcher(
    viewModel: OrganizationViewModel,
    modifier: Modifier = Modifier,
    currentUserId: String? = null,
) {
  val state by viewModel.state.collectAsState()
  LaunchedEffect(viewModel) { viewModel.dispatch(OrganizationUiIntent.Load) }
  OrganizationSwitcherContent(
      state,
      currentUserId,
      { viewModel.dispatch(OrganizationUiIntent.SetActive(it)) },
      modifier,
  )
}

/** Stateless body of [OrganizationSwitcher]. */
@Composable
internal fun OrganizationSwitcherContent(
    state: OrganizationUiState,
    currentUserId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  val active = state.activeOrganization
  val roles = active?.members?.firstOrNull { it.userId == currentUserId }?.roles.orEmpty()
  Box(modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FediimSpace.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
          onClick = { open = true },
          variant = ButtonVariant.Secondary,
          modifier = Modifier.testTag(TAG_ORG_TRIGGER),
          text = active?.name ?: "No organization",
      )
      if (roles.isNotEmpty()) Badge(roles.joinToString(", "))
    }
    Menu(expanded = open, onDismissRequest = { open = false }) {
      state.organizations.forEach { organization ->
        MenuItem(
            text = organization.name,
            onClick = {
              open = false
              onSelect(organization.id)
            },
        )
      }
    }
  }
}
