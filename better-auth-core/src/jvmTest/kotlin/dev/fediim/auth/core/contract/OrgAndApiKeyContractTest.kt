package dev.fediim.auth.core.contract

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.apikey.CreateApiKeyRequest
import dev.fediim.auth.core.apikey.UpdateApiKeyRequest
import dev.fediim.auth.core.model.AuthUser
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SignUpEmailRequest
import dev.fediim.auth.core.organization.CreateOrganizationRequest
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.okhttp.OkHttp
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Live organization and API key flows against the Node better-auth contract server. */
class OrgAndApiKeyContractTest {
  private class Actor(baseUrl: String, apiKey: String? = null) {
    val storage = SettingsAuthTokenStorage(MapSettings())
    val lastListBody = AtomicReference<String?>(null)
    val client =
        BetterAuthClient(
            BetterAuthConfig(baseUrl, apiKey),
            storage,
            OkHttp.create {
              addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().url.encodedPath.endsWith("/api-key/list")) {
                  lastListBody.set(response.peekBody(1_000_000).string())
                }
                response
              }
            },
        )
    val email = "contract-${UUID.randomUUID()}@example.com"
    lateinit var user: AuthUser

    suspend fun signUp() {
      val result =
          client.signUpEmail(SignUpEmailRequest("Actor", email, UUID.randomUUID().toString()))
      user = (result as BetterAuthResult.Success).value.user!!
    }
  }

  private fun <T> BetterAuthResult<T>.value(): T {
    assertIs<BetterAuthResult.Success<T>>(this, "result: $this")
    return value
  }

  @Test
  fun organizationLifecycle() = runBlocking {
    BetterAuthContractServerHarness.start().use { server ->
      val a = Actor(server.baseUrl)
      val b = Actor(server.baseUrl)
      try {
        a.signUp()
        b.signUp()
        val api = a.client.organizations
        val metadata = buildJsonObject { put("projectId", "p1") }
        val org1 =
            api.create(
                    CreateOrganizationRequest(
                        "Org One",
                        "org-${UUID.randomUUID()}",
                        metadata = metadata,
                    )
                )
                .value()
        assertEquals(JsonPrimitive("p1"), org1.metadata?.get("projectId"))
        assertEquals(org1.id, a.storage.activeOrganizationId())
        val org2 =
            api.create(CreateOrganizationRequest("Org Two", "org-${UUID.randomUUID()}")).value()
        assertEquals(org2.id, a.storage.activeOrganizationId())
        assertEquals(setOf(org1.id, org2.id), api.list().value().map { it.id }.toSet())

        api.setActive(org1.id).value()
        assertEquals(org1.id, a.storage.activeOrganizationId())
        val session = a.client.getSession().value()!!.session
        assertEquals(JsonPrimitive(org1.id), session.additionalFields["activeOrganizationId"])

        val full = api.getFullOrganization().value()!!
        assertEquals(org1.id, full.id)
        assertEquals(JsonPrimitive("p1"), full.metadata?.get("projectId"))
        assertEquals(listOf("owner"), full.members.single { it.userId == a.user.id }.roles)

        val invitation = api.inviteMember(b.email, "member").value()
        assertEquals("pending", invitation.status)
        val joined = b.client.organizations.acceptInvitation(invitation.id).value()
        assertEquals(org1.id, joined.organizationId)
        assertEquals("member", joined.role)
        assertEquals(org1.id, b.storage.activeOrganizationId())

        assertEquals("admin", api.updateMemberRole(joined.id, "admin").value().role)
        val after = api.getFullOrganization().value()!!
        assertEquals("admin", after.members.single { it.userId == b.user.id }.role)

        b.client.organizations.leave(org1.id).value()
        assertNull(b.storage.activeOrganizationId())
        val denied = b.client.organizations.setActive(org1.id)
        assertTrue(denied is BetterAuthResult.Failure && denied.status == 403, "denied: $denied")

        assertEquals(BetterAuthResult.Success(null), api.setActive(null))
        val cleared =
            a.client.getSession().value()!!.session.additionalFields["activeOrganizationId"]
        assertTrue(cleared == null || cleared is JsonNull, "cleared: $cleared")

        api.setActive(org1.id).value()
        a.client.signOut().value()
        assertNull(a.storage.token())
        assertNull(a.storage.activeOrganizationId())
      } finally {
        a.client.close()
        b.client.close()
      }
    }
  }

  @Test
  fun apiKeyLifecycle() = runBlocking {
    BetterAuthContractServerHarness.start().use { server ->
      val a = Actor(server.baseUrl)
      var d: Actor? = null
      try {
        a.signUp()
        val api = a.client.apiKeys
        val created =
            api.create(
                    CreateApiKeyRequest(
                        name = "ci",
                        metadata = buildJsonObject { put("projectId", "p1") },
                    )
                )
                .value()
        assertTrue(created.key.length >= 64, "key length ${created.key.length}")
        assertEquals(JsonPrimitive("p1"), created.record.metadata?.get("projectId"))
        val id = created.record.id

        assertTrue(api.list().value().any { it.id == id })
        assertFalse(a.lastListBody.get()!!.contains(created.key))
        assertEquals(id, api.get(id).value().id)
        val updated = api.update(UpdateApiKeyRequest(id, name = "ci2", enabled = true)).value()
        assertEquals("ci2", updated.name)

        d = Actor(server.baseUrl, apiKey = created.key)
        val envelope = d.client.getSession().value()!!
        assertEquals(a.user.id, envelope.user.id)
        assertNull(d.storage.token())

        api.delete(id).value()
        val rejected = d.client.getSession()
        assertTrue(
            rejected is BetterAuthResult.Failure && rejected.status == 401,
            "rejected: $rejected",
        )
        assertEquals("INVALID_API_KEY", (rejected as BetterAuthResult.Failure).error.code)
        assertTrue(api.list().value().none { it.id == id })
      } finally {
        a.client.close()
        d?.client?.close()
      }
    }
  }
}
