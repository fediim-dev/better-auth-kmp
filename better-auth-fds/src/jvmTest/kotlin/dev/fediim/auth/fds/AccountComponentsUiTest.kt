package dev.fediim.auth.fds

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.fediim.auth.compose.ApiKeyUiState
import dev.fediim.auth.compose.ApiKeyViewModel
import dev.fediim.auth.compose.OrganizationViewModel
import dev.fediim.auth.core.apikey.ApiKeyRecord
import dev.fediim.fds.theme.FediimTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AccountComponentsUiTest {
  private fun org(id: String, name: String) = """{"id":"$id","name":"$name","slug":"$id"}"""

  private fun full(id: String, name: String, vararg members: Pair<String, String>) =
      Reply(
          """{"id":"$id","name":"$name","slug":"$id","members":[""" +
              members.joinToString(",") { (user, role) ->
                """{"id":"m-$user","organizationId":"$id","userId":"$user","role":"$role"}"""
              } +
              "]}"
      )

  private fun ComposeUiTest.switcher(rig: FdsTestRig, userId: String?): OrganizationViewModel =
      setVmContent({ OrganizationViewModel(rig.client, it) }) {
        OrganizationSwitcher(it, currentUserId = userId)
      }

  private fun ComposeUiTest.text(tag: String): String =
      onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

  private fun ComposeUiTest.orgRig(vararg memberOfO1: Pair<String, String>) =
      FdsTestRig().also {
        it.on("organization/list", Reply("[${org("o1", "Acme")},${org("o2", "Beta")}]"))
        it.on("organization/get-full-organization", full("o1", "Acme", *memberOfO1))
      }

  @Test
  fun switcherLoadsOnceAndShowsTheCurrentUsersRole() = runDesktopTest {
    val rig = orgRig("uA" to "admin", "uB" to "member,billing")
    switcher(rig, "uA")
    until("loaded") { hasText("Acme") }
    assertTrue(hasText("admin"))
    assertEquals(1, rig.to("organization/list").size)
    assertEquals(1, rig.to("organization/get-full-organization").size)
  }

  @Test
  fun switcherJoinsSeveralRoles() = runDesktopTest {
    switcher(orgRig("uA" to "admin", "uB" to "member,billing"), "uB")
    until("badge") { hasText("member, billing") }
  }

  @Test
  fun switcherShowsNoBadgeForANonMember() = runDesktopTest {
    switcher(orgRig("uA" to "admin"), "uZ")
    until("loaded") { hasText("Acme") }
    assertTrue(!hasText("admin") && !hasText("member", substring = true))
  }

  @Test
  fun switcherChoosingAnOrganizationSetsItActiveAndClosesTheMenu() = runDesktopTest {
    val rig = orgRig("uA" to "admin")
    rig.on("organization/set-active", Reply(org("o2", "Beta")))
    switcher(rig, "uA")
    until("loaded") { hasText("Acme") }
    onNodeWithTag(TAG_ORG_TRIGGER).performClick()
    until("menu") { hasText("Beta") }
    rig.on("organization/get-full-organization", full("o2", "Beta", "uA" to "owner"))
    onNodeWithText("Beta").performClick()
    until("switched") { rig.to("organization/set-active").size == 1 && hasText("owner") }
    assertEquals("""{"organizationId":"o2"}""", rig.to("organization/set-active").single().body)
    until("closed") { !hasText("Acme") }
    assertTrue(text(TAG_ORG_TRIGGER).contains("Beta"))
  }

  @Test
  fun switcherWithoutOrganizationsSaysSo() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("organization/list", Reply("[]"))
    rig.on("organization/get-full-organization", Reply("null"))
    switcher(rig, "uA")
    until("empty") { hasText("No organization") }
  }

  // --- API keys -------------------------------------------------------------------------------

  private val listK1 = Reply("""{"apiKeys":[{"id":"k1","name":"ci","start":"ba_x1"}]}""")

  private fun created(secret: String, id: String, name: String) =
      Reply("""{"key":"$secret","id":"$id","name":"$name"}""")

  private fun ComposeUiTest.keyDialog(
      rig: FdsTestRig,
      onDismiss: () -> Unit = {},
      visible: () -> Boolean = { true },
  ) =
      setVmContent({ ApiKeyViewModel(rig.client, it) }) {
        if (visible()) ApiKeyManagerDialog(it, onDismiss)
      }

  @Test
  fun keyDialogListsKeysWithoutASecret() = runDesktopTest {
    val rig = FdsTestRig().also { it.on("api-key/list", listK1) }
    keyDialog(rig)
    until("row") { hasText("ci") && hasText("ba_x1", substring = true) }
    assertTrue(!hasTag(TAG_KEY_SECRET))
    assertEquals(1, rig.to("api-key/list").size)
  }

  @Test
  fun createSendsTheTrimmedNameOnceAndShowsTheSecret() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("api-key/list", listK1)
    rig.on("api-key/create", created("raw-secret-AAAA", "k2", "deploy"))
    val gate = rig.gate("api-key/create")
    keyDialog(rig)
    until("row") { hasText("ci") }
    type(TAG_KEY_NAME, "  deploy ")
    onNodeWithText("Create key").performClick()
    until("request") { rig.to("api-key/create").size == 1 }
    onNodeWithText("Create key").assertIsNotEnabled()
    onNodeWithText("Create key").performClick()
    waitForIdle()
    assertEquals(1, rig.to("api-key/create").size)
    assertEquals("""{"name":"deploy"}""", rig.to("api-key/create").single().body)

    gate.complete(Unit)
    until("secret") { hasText("raw-secret-AAAA", substring = true) && hasTag(TAG_KEY_SECRET) }
    assertTrue(onAllNodesWithContentDescription("Copy API key").fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun blankNameSendsNoName() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("api-key/list", listK1)
    rig.on("api-key/create", created("raw-secret-CCCC", "k3", "x"))
    keyDialog(rig)
    until("row") { hasText("ci") }
    type(TAG_KEY_NAME, "   ")
    onNodeWithText("Create key").performClick()
    until("request") { rig.to("api-key/create").size == 1 }
    assertEquals("{}", rig.to("api-key/create").single().body)
  }

  @Test
  fun doneHidesTheSecretAndKeepsTheRows() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("api-key/list", listK1)
    rig.on("api-key/create", created("raw-secret-AAAA", "k2", "deploy"))
    val vm = keyDialog(rig)
    until("row") { hasText("ci") }
    onNodeWithText("Create key").performClick()
    until("secret") { hasTag(TAG_KEY_SECRET) }
    onNodeWithText("Done").performClick()
    until("hidden") { !hasTag(TAG_KEY_SECRET) && vm.state.value.createdKey == null }
    assertTrue(hasText("ci") && hasText("deploy"))
  }

  @Test
  fun leavingCompositionDropsTheSecretAndReshowReloads() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("api-key/list", listK1)
    rig.on("api-key/create", created("raw-secret-BBBB", "k2", "deploy"))
    var show by mutableStateOf(true)
    var closed = 0
    val vm =
        keyDialog(
            rig,
            onDismiss = {
              closed++
              show = false
            },
            visible = { show },
        )
    until("row") { hasText("ci") }
    onNodeWithText("Create key").performClick()
    until("secret") { hasTag(TAG_KEY_SECRET) }
    onNodeWithText("Close").performClick()
    until("gone") { !show && !hasText("ci") }
    assertEquals(1, closed)
    until("dropped") { vm.state.value.createdKey == null }

    runOnUiThread { show = true }
    until("reloaded") { rig.to("api-key/list").size == 2 && hasText("ci") }
    assertTrue(!hasText("raw-secret-BBBB", substring = true))
    assertNull(vm.state.value.createdKey)
  }

  @Test
  fun revokeAsksFirstThenDeletes() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("api-key/list", listK1)
    rig.on("api-key/delete", Reply())
    keyDialog(rig)
    until("row") { hasText("ci") }
    onNodeWithTag(TAG_REVOKE_PREFIX + "k1").performClick()
    until("confirm") { hasText("Revoke key") }
    onNodeWithText("Cancel").performClick()
    until("cancelled") { !hasText("Revoke key") }
    assertEquals(0, rig.to("api-key/delete").size)

    onNodeWithTag(TAG_REVOKE_PREFIX + "k1").performClick()
    until("confirm") { hasText("Revoke key") }
    onNodeWithText("Revoke key").performClick()
    until("deleted") { rig.to("api-key/delete").size == 1 && !hasText("ci") }
    assertEquals("""{"keyId":"k1"}""", rig.to("api-key/delete").single().body)
  }

  @Test
  fun failedRevokeShowsTheErrorAndKeepsTheRow() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("api-key/list", listK1)
    rig.on("api-key/delete", Reply("""{"code":"KEY_NOT_FOUND"}""", 404))
    keyDialog(rig)
    until("row") { hasText("ci") }
    onNodeWithTag(TAG_REVOKE_PREFIX + "k1").performClick()
    until("confirm") { hasText("Revoke key") }
    onNodeWithText("Revoke key").performClick()
    until("banner") { hasTag(TAG_ERROR) }
    assertTrue("KEY_NOT_FOUND" in text(TAG_ERROR) && hasText("ci"))
  }

  @Test
  fun loadingDisablesCreateAndRevoke() = runDesktopTest {
    val key = ApiKeyRecord(id = "k1", name = "ci")
    setContent {
      FediimTheme(darkTheme = false) {
        ApiKeyManagerContent(ApiKeyUiState(keys = listOf(key), isLoading = true), {}, {})
      }
    }
    onNodeWithText("Create key").assertIsNotEnabled()
    onNodeWithTag(TAG_REVOKE_PREFIX + "k1").assertIsNotEnabled()
  }

  @Test
  fun idleEnablesCreateAndRevoke() = runDesktopTest {
    val key = ApiKeyRecord(id = "k1", name = "ci")
    setContent {
      FediimTheme(darkTheme = false) {
        ApiKeyManagerContent(ApiKeyUiState(keys = listOf(key)), {}, {})
      }
    }
    onNodeWithText("Create key").assertIsEnabled()
    onNodeWithTag(TAG_REVOKE_PREFIX + "k1").assertIsEnabled()
  }

  // --- Two-factor setup -----------------------------------------------------------------------

  private val enrollment =
      Reply(
          """{"totpURI":"otpauth://totp/Acme:a@b.com?secret=JBSWY3DPEHPK3PXP","backupCodes":["bk-a1","bk-b2"]}"""
      )

  private fun ComposeUiTest.setup(rig: FdsTestRig, onEnabled: () -> Unit = {}) {
    setContent {
      FediimTheme(darkTheme = false) {
        TwoFactorSetupDialog(rig.client.twoFactor, {}, onEnabled, issuer = "Acme")
      }
    }
  }

  @Test
  fun enableIsSentOnceWhileRunningAndFailureKeepsThePasswordStep() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("two-factor/enable", Reply("""{"code":"INVALID_PASSWORD"}""", 400))
    val gate = rig.gate("two-factor/enable")
    var enabled = 0
    setup(rig) { enabled++ }
    type(TAG_SETUP_PASSWORD, "pw-setup-9")
    onNodeWithText("Continue").performClick()
    until("request") { rig.to("two-factor/enable").size == 1 }
    onNodeWithText("Continue").assertIsNotEnabled()
    onNodeWithText("Continue").performClick()
    waitForIdle()
    assertEquals(1, rig.to("two-factor/enable").size)
    assertEquals(
        """{"password":"pw-setup-9","issuer":"Acme"}""",
        rig.to("two-factor/enable").single().body,
    )

    gate.complete(Unit)
    until("banner") { hasTag(TAG_ERROR) }
    assertTrue("INVALID_PASSWORD" in text(TAG_ERROR) && hasTag(TAG_SETUP_PASSWORD))
    onNodeWithText("Continue").assertIsEnabled()
    assertEquals(0, enabled)
  }

  @Test
  fun blankPasswordIsSentAsNull() = runDesktopTest {
    val rig = FdsTestRig().also { it.on("two-factor/enable", enrollment) }
    setup(rig)
    onNodeWithText("Continue").performClick()
    until("request") { rig.to("two-factor/enable").size == 1 }
    assertEquals("""{"issuer":"Acme"}""", rig.to("two-factor/enable").single().body)
  }

  @Test
  fun enrollmentThenWrongAndRightCode() = runDesktopTest {
    val rig = FdsTestRig()
    rig.on("two-factor/enable", enrollment)
    rig.on("two-factor/verify-totp", Reply("""{"code":"INVALID_CODE"}""", 401), Reply(TOKEN_REPLY))
    var enabled = 0
    setup(rig) { enabled++ }
    type(TAG_SETUP_PASSWORD, "pw-setup-10")
    onNodeWithText("Continue").performClick()
    until("enrollment") { hasText("JBSWY3DPEHPK3PXP", substring = true) }
    assertTrue(hasText("bk-a1", substring = true) && hasText("bk-b2", substring = true))

    type(TAG_SETUP_CODE, "000111")
    onNodeWithText("Verify").performClick()
    until("rejected") { rig.to("two-factor/verify-totp").size == 1 && hasTag(TAG_ERROR) }
    assertEquals("""{"code":"000111"}""", rig.to("two-factor/verify-totp").single().body)
    assertTrue("INVALID_CODE" in text(TAG_ERROR) && hasTag(TAG_SETUP_CODE))
    assertEquals(0, enabled)

    retype(TAG_SETUP_CODE, "654321")
    onNodeWithText("Verify").performClick()
    until("enabled") { enabled == 1 }
    onNodeWithText("Verify").assertIsNotEnabled()
    assertEquals(2, rig.to("two-factor/verify-totp").size)
  }
}
