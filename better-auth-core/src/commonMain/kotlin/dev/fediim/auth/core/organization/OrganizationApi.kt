package dev.fediim.auth.core.organization

import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.http.StartEpochKey
import dev.fediim.auth.core.model.BetterAuthResult
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Better Auth `organization()` plugin endpoints under `/api/auth/organization/`, reached through
 * [BetterAuthClient.organizations]. Every call returns a [BetterAuthResult] as described on
 * [BetterAuthClient].
 *
 * The client caches the active organization id in the token storage, mirroring only this client's
 * own successful calls (create unless kept, [setActive], [acceptInvitation], [leave] of the cached
 * organization). A write is discarded when `signOut` ran since the request started. The server
 * session stays authoritative: after a 403 not-a-member the server clears its active organization
 * while the cache keeps the previous id.
 */
public class OrganizationApi internal constructor(private val client: BetterAuthClient) {
  private val json = client.json

  /** Creates an organization; it becomes the cached active one unless the request keeps current. */
  public suspend fun create(request: CreateOrganizationRequest): BetterAuthResult<Organization> =
      client.send(
          HttpMethod.Post,
          "organization/create",
          json.encodeToString(CreateOrganizationRequestSerializer, request),
      ) { response, text ->
        val org = json.decodeFromString(Organization.serializer(), text)
        if (request.keepCurrentActiveOrganization != true) cache(response, org.id)
        org
      }

  /** Lists the organizations of the signed-in user. */
  public suspend fun list(): BetterAuthResult<List<Organization>> =
      client.send(HttpMethod.Get, "organization/list") { _, text ->
        json.decodeFromString(ListSerializer(Organization.serializer()), text)
      }

  /** Sets the active organization, or clears it with null; the server answers null for none. */
  public suspend fun setActive(organizationId: String?): BetterAuthResult<Organization?> =
      // Explicit null: an omitted id means "keep current" on the server.
      client.send(
          HttpMethod.Post,
          "organization/set-active",
          buildJsonObject { put("organizationId", organizationId) }.toString(),
      ) { response, text ->
        val org = json.decodeFromString(Organization.serializer().nullable, text)
        cache(response, org?.id)
        org
      }

  /** Fetches the active (or given) organization with members and invitations; null for none. */
  public suspend fun getFullOrganization(
      organizationId: String? = null
  ): BetterAuthResult<FullOrganization?> =
      client.send(
          HttpMethod.Get,
          "organization/get-full-organization",
          configure = { organizationId?.let { parameter("organizationId", it) } },
      ) { _, text ->
        json.decodeFromString(FullOrganization.serializer().nullable, text)
      }

  /** Invites [email] with [role]; [resend] re-sends an existing invitation. */
  public suspend fun inviteMember(
      email: String,
      role: String,
      organizationId: String? = null,
      resend: Boolean? = null,
  ): BetterAuthResult<OrganizationInvitation> =
      post(
          "invite-member",
          buildJsonObject {
            put("email", email)
            put("role", role)
            organizationId?.let { put("organizationId", it) }
            resend?.let { put("resend", it) }
          },
      ) { _, text ->
        json.decodeFromString(OrganizationInvitation.serializer(), text)
      }

  /** Accepts an invitation; the joined organization becomes the cached active one. */
  public suspend fun acceptInvitation(invitationId: String): BetterAuthResult<OrganizationMember> =
      post("accept-invitation", buildJsonObject { put("invitationId", invitationId) }) {
          response,
          text ->
        val member = unwrapMember(text)
        cache(response, member.organizationId)
        member
      }

  /** Rejects an invitation. */
  public suspend fun rejectInvitation(invitationId: String): BetterAuthResult<Unit> =
      post("reject-invitation", buildJsonObject { put("invitationId", invitationId) }) { _, _ -> }

  /** Removes a member by member id or email; returns the removed member. */
  public suspend fun removeMember(
      memberIdOrEmail: String,
      organizationId: String? = null,
  ): BetterAuthResult<OrganizationMember> =
      post(
          "remove-member",
          buildJsonObject {
            put("memberIdOrEmail", memberIdOrEmail)
            organizationId?.let { put("organizationId", it) }
          },
      ) { _, text ->
        unwrapMember(text)
      }

  /** Changes a member's role (comma-separated for several). */
  public suspend fun updateMemberRole(
      memberId: String,
      role: String,
      organizationId: String? = null,
  ): BetterAuthResult<OrganizationMember> =
      post(
          "update-member-role",
          buildJsonObject {
            put("memberId", memberId)
            put("role", role)
            organizationId?.let { put("organizationId", it) }
          },
      ) { _, text ->
        json.decodeFromString(OrganizationMember.serializer(), text)
      }

  /** Leaves an organization; clears the cached id when it equals [organizationId]. */
  public suspend fun leave(organizationId: String): BetterAuthResult<OrganizationMember> =
      post("leave", buildJsonObject { put("organizationId", organizationId) }) { response, text ->
        val member = json.decodeFromString(OrganizationMember.serializer(), text)
        // Read-compare-write is not atomic with a concurrent setActive on this client.
        if (client.tokenStorage.activeOrganizationId() == organizationId) cache(response, null)
        member
      }

  private suspend fun <T> post(
      path: String,
      body: JsonObject,
      decode: suspend (HttpResponse, String) -> T,
  ): BetterAuthResult<T> =
      client.send(HttpMethod.Post, "organization/$path", body.toString(), decode = decode)

  private fun unwrapMember(text: String): OrganizationMember {
    val member =
        json.parseToJsonElement(text).jsonObject["member"]
            ?: throw IllegalArgumentException("missing member")
    return json.decodeFromJsonElement(OrganizationMember.serializer(), member)
  }

  /** Writes the cached id, fenced by the epoch read when the request started. */
  private suspend fun cache(response: HttpResponse, organizationId: String?) {
    val epoch = response.call.request.attributes.getOrNull(StartEpochKey) ?: return
    client.tokenStorage.writeActiveOrganizationIdIfEpoch(epoch, organizationId)
  }
}
