@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.fediim.auth.compose

import dev.fediim.auth.core.apikey.CreateApiKeyRequest
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class OrganizationAndApiKeyViewModelTest {
  private fun full(id: String) =
      Reply(
          """{"id":"$id","name":"N$id","slug":"$id","members":[""" +
              """{"id":"m1","organizationId":"$id","userId":"u1","role":"owner"}],"invitations":[]}"""
      )

  private fun member(org: String) =
      """{"id":"m1","organizationId":"$org","userId":"u1","role":"member"}"""

  private val orgList =
      Reply("""[{"id":"o1","name":"A","slug":"a"},{"id":"o2","name":"B","slug":"b"}]""")

  private fun TestScope.orgSetup(): Pair<ComposeTestRig, OrganizationViewModel> {
    val rig = ComposeTestRig(testScheduler)
    rig.on("organization/list", orgList)
    rig.on("organization/get-full-organization", full("o1"))
    return rig to OrganizationViewModel(rig.client, backgroundScope)
  }

  @Test
  fun t11LoadAndSetActive() = runTest {
    val (rig, vm) = orgSetup()
    vm.dispatch(OrganizationUiIntent.Load)
    runCurrent()
    assertEquals(listOf("o1", "o2"), vm.state.value.organizations.map { it.id })
    assertEquals("o1", vm.state.value.activeOrganization?.id)
    assertEquals(1, vm.state.value.activeOrganization?.members?.size)
    assertFalse(vm.state.value.isLoading)

    rig.on("organization/set-active", Reply("""{"id":"o2","name":"B","slug":"b"}"""))
    rig.on("organization/get-full-organization", full("o2"))
    vm.dispatch(OrganizationUiIntent.SetActive("o2"))
    runCurrent()
    assertEquals(
        """{"organizationId":"o2"}""",
        rig.bodyOf(rig.reqs("organization/set-active").single()),
    )
    assertEquals("o2", vm.state.value.activeOrganization?.id)

    rig.on(
        "organization/set-active",
        Reply(
            """{"code":"USER_IS_NOT_A_MEMBER_OF_THE_ORGANIZATION"}""",
            HttpStatusCode.Forbidden,
        ),
    )
    vm.dispatch(OrganizationUiIntent.SetActive("oEvil"))
    runCurrent()
    assertEquals("USER_IS_NOT_A_MEMBER_OF_THE_ORGANIZATION", vm.state.value.error?.code)
    assertEquals("o2", vm.state.value.activeOrganization?.id)

    rig.on("organization/set-active", Reply("null"))
    rig.on("organization/get-full-organization", Reply("null"))
    vm.dispatch(OrganizationUiIntent.SetActive(null))
    runCurrent()
    assertEquals(
        """{"organizationId":null}""",
        rig.bodyOf(rig.reqs("organization/set-active").last()),
    )
    assertNull(vm.state.value.activeOrganization)
    assertEquals(2, vm.state.value.organizations.size)
  }

  @Test
  fun t11LoadFailureKeepsThePriorLists() = runTest {
    val (rig, vm) = orgSetup()
    vm.dispatch(OrganizationUiIntent.Load)
    runCurrent()
    rig.on("organization/list", Reply(OK, HttpStatusCode.InternalServerError))
    vm.dispatch(OrganizationUiIntent.Load)
    runCurrent()
    assertEquals(2, vm.state.value.organizations.size)
    assertEquals("o1", vm.state.value.activeOrganization?.id)
    assertTrue(vm.state.value.error != null)
    rig.on("organization/list", orgList)
    rig.on("organization/get-full-organization", Reply(OK, HttpStatusCode.InternalServerError))
    vm.dispatch(OrganizationUiIntent.Load)
    runCurrent()
    assertEquals("o1", vm.state.value.activeOrganization?.id)
    assertTrue(vm.state.value.error != null)
  }

  @Test
  fun t11MemberOperationsUseTheActiveOrganizationAndRefresh() = runTest {
    val (rig, vm) = orgSetup()
    vm.dispatch(OrganizationUiIntent.Load)
    runCurrent()
    rig.on(
        "organization/invite-member",
        Reply(
            """{"id":"i1","organizationId":"o1","email":"c@d.com","role":"member","status":"pending"}"""
        ),
    )
    rig.on("organization/remove-member", Reply("""{"member":${member("o1")}}"""))
    rig.on("organization/update-member-role", Reply(member("o1")))
    val steps =
        listOf(
            OrganizationUiIntent.InviteMember("c@d.com", "member") to "organization/invite-member",
            OrganizationUiIntent.RemoveMember("m1") to "organization/remove-member",
            OrganizationUiIntent.UpdateMemberRole("m1", "admin") to
                "organization/update-member-role",
        )
    var refreshes = rig.reqs("organization/get-full-organization").size
    for ((intent, path) in steps) {
      vm.dispatch(intent)
      runCurrent()
      assertTrue(""""organizationId":"o1"""" in rig.bodyOf(rig.reqs(path).single()), path)
      refreshes++
      assertEquals(refreshes, rig.reqs("organization/get-full-organization").size, path)
    }
    rig.on("organization/remove-member", Reply("""{"code":"NOPE"}""", HttpStatusCode.Forbidden))
    vm.dispatch(OrganizationUiIntent.RemoveMember("m2"))
    runCurrent()
    assertEquals("NOPE", vm.state.value.error?.code)
    assertEquals(refreshes, rig.reqs("organization/get-full-organization").size)

    val sent = rig.requests.size
    vm.dispatch(OrganizationUiIntent.Clear)
    runCurrent()
    assertEquals(OrganizationUiState(), vm.state.value)
    assertEquals(sent, rig.requests.size)
  }

  @Test
  fun t11ClearDropsAnInFlightLoad() = runTest {
    val (rig, vm) = orgSetup()
    val gate = rig.gate("organization/list")
    vm.dispatch(OrganizationUiIntent.Load)
    runCurrent()
    vm.dispatch(OrganizationUiIntent.Clear)
    gate.complete(Unit)
    runCurrent()
    assertEquals(OrganizationUiState(), vm.state.value)
  }

  private fun TestScope.keySetup(): Pair<ComposeTestRig, ApiKeyViewModel> {
    val rig = ComposeTestRig(testScheduler)
    rig.on("api-key/list", Reply("""{"apiKeys":[{"id":"k1"}]}"""))
    rig.on("api-key/create", Reply("""{"id":"k2","name":"ci","key":"raw-secret-AAAA"}"""))
    rig.on("api-key/delete", Reply("""{"success":true}"""))
    return rig to ApiKeyViewModel(rig.client, backgroundScope)
  }

  @Test
  fun t12KeysLifecycle() = runTest {
    val (rig, vm) = keySetup()
    vm.dispatch(ApiKeyUiIntent.Load)
    runCurrent()
    assertEquals(listOf("k1"), vm.state.value.keys.map { it.id })

    vm.dispatch(ApiKeyUiIntent.Create(CreateApiKeyRequest(name = "ci")))
    runCurrent()
    assertEquals("raw-secret-AAAA", vm.state.value.createdKey?.key)
    assertEquals(listOf("k1", "k2"), vm.state.value.keys.map { it.id })
    assertFalse("raw-secret-AAAA" in vm.state.value.toString())

    vm.dispatch(ApiKeyUiIntent.DismissCreatedKey)
    assertNull(vm.state.value.createdKey)
    assertEquals(2, vm.state.value.keys.size)

    vm.dispatch(ApiKeyUiIntent.Revoke("k1"))
    runCurrent()
    assertEquals("""{"keyId":"k1"}""", rig.bodyOf(rig.reqs("api-key/delete").single()))
    assertEquals(listOf("k2"), vm.state.value.keys.map { it.id })

    rig.on("api-key/delete", Reply("""{"code":"KEY_NOT_FOUND"}""", HttpStatusCode.NotFound))
    vm.dispatch(ApiKeyUiIntent.Revoke("k9"))
    runCurrent()
    assertEquals("KEY_NOT_FOUND", vm.state.value.error?.code)
    assertEquals(listOf("k2"), vm.state.value.keys.map { it.id })

    vm.dispatch(ApiKeyUiIntent.Create())
    runCurrent()
    vm.dispatch(ApiKeyUiIntent.Clear)
    assertEquals(ApiKeyUiState(), vm.state.value)
  }

  @Test
  fun t12HandlerFailureAndClosedViewModels() = runTest {
    val (rig, keys) = keySetup()
    rig.failWith("api-key/list")
    keys.dispatch(ApiKeyUiIntent.Load)
    runCurrent()
    assertEquals(AuthUiErrorCodes.NETWORK_ERROR, keys.state.value.error?.code)
    assertFalse(keys.state.value.isLoading)

    val orgs = OrganizationViewModel(rig.client, backgroundScope)
    keys.close()
    orgs.close()
    val sent = rig.requests.size
    keys.dispatch(ApiKeyUiIntent.Create())
    orgs.dispatch(OrganizationUiIntent.Load)
    runCurrent()
    assertEquals(sent, rig.requests.size)
    assertFalse(keys.state.value.isLoading)
    assertFalse(orgs.state.value.isLoading)
  }

  @Test
  fun t12ClearDropsAnInFlightLoad() = runTest {
    val (rig, vm) = keySetup()
    val gate = rig.gate("api-key/list")
    vm.dispatch(ApiKeyUiIntent.Load)
    runCurrent()
    vm.dispatch(ApiKeyUiIntent.Clear)
    gate.complete(Unit)
    runCurrent()
    assertEquals(ApiKeyUiState(), vm.state.value)
  }
}
