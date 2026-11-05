package dev.fediim.auth.core.apikey

import dev.fediim.auth.core.BetterAuthClient
import dev.fediim.auth.core.model.BetterAuthResult
import io.ktor.client.request.parameter
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Better Auth `apiKey()` plugin endpoints under `/api/auth/api-key/`, reached through
 * [BetterAuthClient.apiKeys]. The raw secret is only ever returned by [create]; nothing here logs
 * or stores it. Servers need `enableMetadata` to accept metadata.
 */
public class ApiKeyApi internal constructor(private val client: BetterAuthClient) {
  private val json = client.json

  /** Creates a key; a 2xx answer without a string `key` is a status-200 failure. */
  public suspend fun create(
      request: CreateApiKeyRequest = CreateApiKeyRequest()
  ): BetterAuthResult<CreatedApiKey> =
      client.send(
          HttpMethod.Post,
          "api-key/create",
          json.encodeToString(CreateApiKeyRequest.serializer(), request),
      ) { _, text ->
        val obj = json.parseToJsonElement(text).jsonObject
        val key =
            (obj["key"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("missing key")
        CreatedApiKey(key, json.decodeFromJsonElement(ApiKeyRecord.serializer(), obj))
      }

  /** Lists the signed-in user's keys, without secrets. */
  public suspend fun list(): BetterAuthResult<List<ApiKeyRecord>> =
      client.send(HttpMethod.Get, "api-key/list") { _, text ->
        val items =
            json.parseToJsonElement(text).jsonObject["apiKeys"]
                ?: throw IllegalArgumentException("missing apiKeys")
        json.decodeFromJsonElement(ListSerializer(ApiKeyRecord.serializer()), items)
      }

  /** Fetches one key by [id]. */
  public suspend fun get(id: String): BetterAuthResult<ApiKeyRecord> =
      client.send(HttpMethod.Get, "api-key/get", configure = { parameter("id", id) }) { _, text ->
        json.decodeFromString(ApiKeyRecord.serializer(), text)
      }

  /** Updates a key. */
  public suspend fun update(request: UpdateApiKeyRequest): BetterAuthResult<ApiKeyRecord> =
      client.send(
          HttpMethod.Post,
          "api-key/update",
          json.encodeToString(UpdateApiKeyRequest.serializer(), request),
      ) { _, text ->
        json.decodeFromString(ApiKeyRecord.serializer(), text)
      }

  /** Deletes (revokes) the key [keyId]. */
  public suspend fun delete(keyId: String): BetterAuthResult<Unit> =
      client.send(
          HttpMethod.Post,
          "api-key/delete",
          buildJsonObject { put("keyId", keyId) }.toString(),
      ) { _, _ ->
      }
}
