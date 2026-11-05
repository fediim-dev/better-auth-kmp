package dev.fediim.auth.core.organization

import com.russhwolf.settings.MapSettings
import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.BetterAuthConfig
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.storage.SettingsAuthTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OrganizationApiTest {
  private val orgBody = """{"id":"o1","name":"Acme","slug":"acme"}"""

  private class Rig(
      apiKey: String? = null,
      handler: suspend (HttpRequestData) -> Reply,
  ) {
    val requests = mutableListOf<HttpRequestData>()
    val storage = SettingsAuthTokenStorage(MapSettings())
    val client =
        BetterAuthClient(
            BetterAuthConfig("https://example.com", apiKey),
            storage,
            MockEngine { request ->
              requests += request
              val reply = handler(request)
              respond(
                  reply.body,
                  reply.status,
                  headersOf(HttpHeaders.ContentType, "application/json"),
              )
            },
        )

    fun bodyOf(index: Int): String = (requests[index].body as TextContent).text

    suspend fun cache(id: String?) {
      assertTrue(storage.writeActiveOrganizationIdIfEpoch(storage.epoch(), id))
    }
  }

  private class Reply(val body: String, val status: HttpStatusCode)

  private fun ok(body: String, status: HttpStatusCode = HttpStatusCode.OK) = Reply(body, status)

  private fun <T> BetterAuthResult<T>.value(): T = (this as BetterAuthResult.Success).value

  @Test
  fun t1CreateSendsFlattenedBodyAndCachesId() = runTest {
    val rig = Rig {
      ok(
          """{"id":"o1","name":"Acme","slug":"acme","metadata":{"projectId":"p1"},"members":[],"custom":1}"""
      )
    }
    rig.storage.writeTokenIfEpoch(0, "t")
    val request =
        CreateOrganizationRequest(
            "Acme",
            "acme",
            metadata = buildJsonObject { put("projectId", "p1") },
            additionalFields = buildJsonObject { put("tenant", "t") },
        )
    val org = rig.client.organizations.create(request).value()
    assertEquals(HttpMethod.Post, rig.requests[0].method)
    assertEquals("https://example.com/api/auth/organization/create", rig.requests[0].url.toString())
    assertEquals("Bearer t", rig.requests[0].headers[HttpHeaders.Authorization])
    assertEquals(
        """{"name":"Acme","slug":"acme","metadata":{"projectId":"p1"},"tenant":"t"}""",
        rig.bodyOf(0),
    )
    assertEquals(JsonPrimitive("p1"), org.metadata?.get("projectId"))
    assertTrue("members" in org.additionalFields && "custom" in org.additionalFields)
    assertEquals("o1", rig.storage.activeOrganizationId())

    rig.cache("orgPrev")
    rig.client.organizations.create(
        CreateOrganizationRequest("A", "a", keepCurrentActiveOrganization = true)
    )
    assertEquals("orgPrev", rig.storage.activeOrganizationId())
  }

  @Test
  fun t2ListDecodesMetadataStringsLeniently() = runTest {
    val rig = Rig {
      ok(
          """[{"id":"a","name":"A","slug":"a","metadata":"{\"projectId\":\"p2\"}","extra":1},
          {"id":"b","name":"B","slug":"b","metadata":"not json"},
          {"id":"c","name":"C","slug":"c","metadata":"[1]"},
          {"id":"d","name":"D","slug":"d"}]"""
      )
    }
    val list = rig.client.organizations.list().value()
    assertEquals(HttpMethod.Get, rig.requests[0].method)
    assertEquals("https://example.com/api/auth/organization/list", rig.requests[0].url.toString())
    assertEquals(JsonPrimitive("p2"), list[0].metadata?.get("projectId"))
    assertEquals(JsonPrimitive(1), list[0].additionalFields["extra"])
    assertEquals(listOf(null, null, null), list.drop(1).map { it.metadata })
    assertEquals(listOf("a", "b", "c", "d"), list.map { it.id })
  }

  @Test
  fun t3SetActiveBodiesAndFailureLeavesCache() = runTest {
    var next = ok("""{"id":"o2","name":"B","slug":"b"}""")
    val rig = Rig { next }
    rig.client.organizations.setActive("o2").value()
    assertEquals("""{"organizationId":"o2"}""", rig.bodyOf(0))
    assertEquals("o2", rig.storage.activeOrganizationId())

    next = ok("null")
    assertEquals(BetterAuthResult.Success(null), rig.client.organizations.setActive(null))
    assertEquals("""{"organizationId":null}""", rig.bodyOf(1))
    assertNull(rig.storage.activeOrganizationId())

    rig.cache("orgA")
    next = ok("""{"code":"USER_IS_NOT_A_MEMBER_OF_THE_ORGANIZATION"}""", HttpStatusCode.Forbidden)
    val failure = rig.client.organizations.setActive("orgEvil")
    assertIs<BetterAuthResult.Failure>(failure)
    assertEquals(403, failure.status)
    assertEquals("USER_IS_NOT_A_MEMBER_OF_THE_ORGANIZATION", failure.error.code)
    assertEquals("orgA", rig.storage.activeOrganizationId())
  }

  @Test
  fun t4GetFullOrganization() = runTest {
    var next = ok("null")
    val rig = Rig { next }
    assertEquals(BetterAuthResult.Success(null), rig.client.organizations.getFullOrganization())
    assertEquals(
        "https://example.com/api/auth/organization/get-full-organization",
        rig.requests[0].url.toString(),
    )
    next =
        ok(
            """{"id":"o1","name":"Acme","slug":"acme","teams":[],
            "members":[{"id":"m1","organizationId":"o1","userId":"u1","role":"admin, member",
              "user":{"id":"u1","name":"N","email":"a@b.com","image":null}}],
            "invitations":[{"id":"i1","organizationId":"o1","email":"c@d.com","role":"member",
              "status":"pending","inviterId":"u1","expiresAt":"2030-01-01T00:00:00.000Z"}]}"""
        )
    val full = rig.client.organizations.getFullOrganization("o1").value()!!
    assertEquals("o1", rig.requests[1].url.parameters["organizationId"])
    assertEquals(listOf("admin", "member"), full.members.single().roles)
    assertEquals("a@b.com", full.members.single().user?.email)
    assertEquals("pending", full.invitations.single().status)
    assertTrue("teams" in full.additionalFields)
  }

  private fun member(role: String = "member", org: String = "o1") =
      """{"id":"m1","organizationId":"$org","userId":"u1","role":"$role"}"""

  @Test
  fun t5MemberAndInvitationCalls() = runTest {
    var next = ok("{}")
    val rig = Rig { next }
    val api = rig.client.organizations
    val url = "https://example.com/api/auth/organization/"

    next =
        ok(
            """{"id":"i1","organizationId":"o1","email":"a@b.com","role":"member","status":"pending"}"""
        )
    assertEquals("i1", api.inviteMember("a@b.com", "member").value().id)
    assertEquals("""{"email":"a@b.com","role":"member"}""", rig.bodyOf(0))
    api.inviteMember("a@b.com", "admin", "o1", true)
    assertEquals(
        """{"email":"a@b.com","role":"admin","organizationId":"o1","resend":true}""",
        rig.bodyOf(1),
    )
    assertEquals(url + "invite-member", rig.requests[0].url.toString())

    next = ok("""{"invitation":{"id":"i1"},"member":${member(org = "o7")}}""")
    assertEquals("o7", api.acceptInvitation("i1").value().organizationId)
    assertEquals("""{"invitationId":"i1"}""", rig.bodyOf(2))
    assertEquals(url + "accept-invitation", rig.requests[2].url.toString())
    assertEquals("o7", rig.storage.activeOrganizationId())

    next = ok("""{"invitation":{"id":"i1"},"member":null}""")
    assertEquals(BetterAuthResult.Success(Unit), api.rejectInvitation("i1"))
    assertEquals(url + "reject-invitation", rig.requests[3].url.toString())
    assertEquals("""{"invitationId":"i1"}""", rig.bodyOf(3))

    next = ok("""{"member":${member()}}""")
    assertEquals("m1", api.removeMember("a@b.com").value().id)
    assertEquals("""{"memberIdOrEmail":"a@b.com"}""", rig.bodyOf(4))
    assertEquals(url + "remove-member", rig.requests[4].url.toString())

    next = ok(member("admin"))
    assertEquals("admin", api.updateMemberRole("m1", "admin").value().role)
    assertEquals("""{"memberId":"m1","role":"admin"}""", rig.bodyOf(5))
    assertEquals(url + "update-member-role", rig.requests[5].url.toString())

    next = ok(member())
    rig.cache("o1")
    api.leave("o1").value()
    assertEquals("""{"organizationId":"o1"}""", rig.bodyOf(6))
    assertEquals(url + "leave", rig.requests[6].url.toString())
    assertNull(rig.storage.activeOrganizationId())
    rig.cache("o9")
    api.leave("o1").value()
    assertEquals("o9", rig.storage.activeOrganizationId())

    next = ok("""{"code":"UNAUTHORIZED"}""", HttpStatusCode.Unauthorized)
    assertEquals(401, (api.acceptInvitation("i1") as BetterAuthResult.Failure).status)
    assertEquals("o9", rig.storage.activeOrganizationId())
    next = ok("not json")
    assertEquals(200, (api.acceptInvitation("i1") as BetterAuthResult.Failure).status)
  }

  @Test
  fun t6SignOutFencesInFlightSetActive() = runTest {
    val gate = CompletableDeferred<Unit>()
    var gated = true
    val rig = Rig { request ->
      if (request.url.encodedPath.endsWith("sign-out")) return@Rig ok("{}")
      if (gated) gate.await()
      if (gated) ok(orgBody) else ok("""{"id":"oAfter","name":"B","slug":"b"}""")
    }
    rig.storage.writeTokenIfEpoch(0, "t")
    val pending = async { rig.client.organizations.setActive("o1") }
    testScheduler.advanceUntilIdle()
    rig.client.signOut()
    gate.complete(Unit)
    assertEquals("o1", pending.await().value()?.id)
    assertNull(rig.storage.activeOrganizationId())
    assertEquals(1L, rig.storage.epoch())

    gated = false
    rig.client.organizations.setActive("oAfter").value()
    assertEquals("oAfter", rig.storage.activeOrganizationId())
  }

  @Test
  fun t7AccessorsAreStableAndSendApiKey() = runTest {
    val rig = Rig(apiKey = "k") { ok("[]") }
    assertSame(rig.client.organizations, rig.client.organizations)
    assertSame(rig.client.apiKeys, rig.client.apiKeys)
    rig.client.organizations.list()
    assertEquals("k", rig.requests[0].headers["X-API-Key"])
  }

  @Test
  fun t11SignOutClearsTokenAndOrganization() = runTest {
    val rig = Rig { ok("{}") }
    rig.storage.writeTokenIfEpoch(0, "t")
    rig.cache("o1")
    rig.client.signOut()
    assertNull(rig.storage.token())
    assertNull(rig.storage.activeOrganizationId())
    assertEquals("Bearer t", rig.requests.single().headers[HttpHeaders.Authorization])

    val bare = Rig { ok("{}") }
    bare.cache("o1")
    bare.client.signOut()
    assertNull(bare.storage.activeOrganizationId())
    assertTrue(bare.requests.isEmpty())
  }
}
