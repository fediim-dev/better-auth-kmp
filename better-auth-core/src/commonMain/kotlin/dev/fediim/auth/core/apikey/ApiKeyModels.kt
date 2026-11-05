@file:OptIn(ExperimentalSerializationApi::class)

package dev.fediim.auth.core.apikey

import dev.fediim.auth.core.model.AdditionalFieldsSerializer
import dev.fediim.auth.core.organization.normalizeMetadata
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal object ApiKeyRecordSerializer :
    AdditionalFieldsSerializer<ApiKeyRecord>(ApiKeyRecord.generatedSerializer()) {
  override fun transformDeserialize(element: JsonElement): JsonElement {
    val cleaned = if (element is JsonObject) JsonObject(element - "key") else element
    return super.transformDeserialize(normalizeMetadata(cleaned))
  }
}

/**
 * An API key as listed by the server; it never carries the secret: an incoming `key` is dropped and
 * not kept in [additionalFields]. [start] is the stored key prefix, not the secret. A string
 * [metadata] holding a JSON object is parsed; any other non-object decodes as null. Rate-limit,
 * refill and [permissions] fields are read-only (server-only). Unmodelled server fields are kept in
 * [additionalFields].
 */
@Serializable(with = ApiKeyRecordSerializer::class)
@KeepGeneratedSerializer
public data class ApiKeyRecord(
    val id: String,
    val name: String? = null,
    val start: String? = null,
    val prefix: String? = null,
    val referenceId: String? = null,
    val configId: String? = null,
    val enabled: Boolean = true,
    val expiresAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val lastRequest: Instant? = null,
    val lastRefillAt: Instant? = null,
    val remaining: Long? = null,
    val refillAmount: Long? = null,
    val refillInterval: Long? = null,
    val rateLimitEnabled: Boolean = true,
    val rateLimitTimeWindow: Long? = null,
    val rateLimitMax: Long? = null,
    val requestCount: Long = 0,
    val permissions: Map<String, List<String>>? = null,
    val metadata: JsonObject? = null,
    val additionalFields: JsonObject = JsonObject(emptyMap()),
)

/**
 * Result of `api-key/create`: the one-time plaintext [key] secret and the stored [record]. The
 * server never returns [key] again; [toString] omits it. Only [toString] is redacted; [key] remains
 * in `copy`/`componentN`/`equals`, so never serialize or persist this object.
 */
public data class CreatedApiKey(val key: String, val record: ApiKeyRecord) {
  override fun toString(): String = "CreatedApiKey(record=$record)"
}

/** Body of `api-key/create`; [expiresIn] is in seconds. Null optionals are not sent. */
@Serializable
public data class CreateApiKeyRequest(
    val name: String? = null,
    val expiresIn: Long? = null,
    val prefix: String? = null,
    val metadata: JsonObject? = null,
)

/** Body of `api-key/update`. Null optionals are not sent, so expiry cannot be cleared. */
@Serializable
public data class UpdateApiKeyRequest(
    val keyId: String,
    val name: String? = null,
    val enabled: Boolean? = null,
    val expiresIn: Long? = null,
    val metadata: JsonObject? = null,
)
