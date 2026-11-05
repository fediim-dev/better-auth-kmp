@file:OptIn(ExperimentalSerializationApi::class)

package dev.fediim.auth.core.organization

import dev.fediim.auth.core.model.AdditionalFieldsSerializer
import dev.fediim.auth.core.model.AuthUser
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val EmptyFields = JsonObject(emptyMap())

/**
 * Returns [element] with `metadata` normalised to a JSON object: a string holding a JSON object is
 * parsed; anything else (unparseable string, array, scalar) is dropped so it decodes as null.
 */
internal fun normalizeMetadata(element: JsonElement): JsonElement {
  if (element !is JsonObject) return element
  val metadata = element["metadata"] ?: return element
  val parsed =
      when {
        metadata is JsonObject -> metadata
        metadata is JsonPrimitive && metadata.isString ->
            try {
              Json.parseToJsonElement(metadata.content) as? JsonObject
            } catch (e: IllegalArgumentException) {
              null
            }
        else -> null
      }
  val copy = LinkedHashMap(element)
  if (parsed == null) copy.remove("metadata") else copy["metadata"] = parsed
  return JsonObject(copy)
}

internal object OrganizationSerializer :
    AdditionalFieldsSerializer<Organization>(Organization.generatedSerializer()) {
  override fun transformDeserialize(element: JsonElement): JsonElement =
      super.transformDeserialize(normalizeMetadata(element))
}

internal object FullOrganizationSerializer :
    AdditionalFieldsSerializer<FullOrganization>(FullOrganization.generatedSerializer()) {
  override fun transformDeserialize(element: JsonElement): JsonElement =
      super.transformDeserialize(normalizeMetadata(element))
}

internal object OrganizationMemberSerializer :
    AdditionalFieldsSerializer<OrganizationMember>(OrganizationMember.generatedSerializer())

internal object OrganizationInvitationSerializer :
    AdditionalFieldsSerializer<OrganizationInvitation>(OrganizationInvitation.generatedSerializer())

internal object CreateOrganizationRequestSerializer :
    AdditionalFieldsSerializer<CreateOrganizationRequest>(
        CreateOrganizationRequest.generatedSerializer()
    )

/**
 * A Better Auth organization. [metadata] is the server's JSON object (the server returns it as a
 * JSON string on most endpoints; a value that is not a JSON object decodes as null). Unmodelled
 * server fields are kept in [additionalFields].
 */
@Serializable(with = OrganizationSerializer::class)
@KeepGeneratedSerializer
public data class Organization(
    val id: String,
    val name: String,
    val slug: String,
    val logo: String? = null,
    val metadata: JsonObject? = null,
    val createdAt: Instant? = null,
    val additionalFields: JsonObject = EmptyFields,
)

/**
 * An organization with its [members] and [invitations], as returned by `get-full-organization`.
 * Fields as in [Organization].
 */
@Serializable(with = FullOrganizationSerializer::class)
@KeepGeneratedSerializer
public data class FullOrganization(
    val id: String,
    val name: String,
    val slug: String,
    val logo: String? = null,
    val metadata: JsonObject? = null,
    val createdAt: Instant? = null,
    val members: List<OrganizationMember> = emptyList(),
    val invitations: List<OrganizationInvitation> = emptyList(),
    val additionalFields: JsonObject = EmptyFields,
)

/**
 * A member of an organization. [role] is the server's comma-separated role string (custom roles are
 * valid). [user] is present only on `get-full-organization`; its `emailVerified` defaults to false
 * because the server omits it there.
 */
@Serializable(with = OrganizationMemberSerializer::class)
@KeepGeneratedSerializer
public data class OrganizationMember(
    val id: String,
    val organizationId: String,
    val userId: String,
    val role: String,
    val createdAt: Instant? = null,
    val user: AuthUser? = null,
    val additionalFields: JsonObject = EmptyFields,
) {
  /** [role] split on commas, trimmed, blanks dropped. */
  val roles: List<String>
    get() = role.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/** An invitation to join an organization; [status] is the server's value, e.g. `pending`. */
@Serializable(with = OrganizationInvitationSerializer::class)
@KeepGeneratedSerializer
public data class OrganizationInvitation(
    val id: String,
    val organizationId: String,
    val email: String,
    val role: String,
    val status: String,
    val inviterId: String? = null,
    val expiresAt: Instant? = null,
    val createdAt: Instant? = null,
    val additionalFields: JsonObject = EmptyFields,
)

/**
 * Body of `organization/create`. Null optionals are not sent; [additionalFields] are sent at the
 * top level of the body. With [keepCurrentActiveOrganization] true the server keeps the current
 * active organization instead of activating the new one.
 */
@Serializable(with = CreateOrganizationRequestSerializer::class)
@KeepGeneratedSerializer
public data class CreateOrganizationRequest(
    val name: String,
    val slug: String,
    val logo: String? = null,
    val metadata: JsonObject? = null,
    val keepCurrentActiveOrganization: Boolean? = null,
    val additionalFields: JsonObject = EmptyFields,
)
