package dev.fediim.auth.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

private const val ADDITIONAL_FIELDS = "additionalFields"

/**
 * Wraps a generated serializer so keys it does not declare are collected into the
 * `additionalFields` property on decode and flattened back to the top level on encode. Declared
 * properties win over extras on name collisions; an incoming key literally named `additionalFields`
 * is treated as an extra.
 */
internal abstract class AdditionalFieldsSerializer<T : Any>(generated: KSerializer<T>) :
    JsonTransformingSerializer<T>(generated) {

  @OptIn(ExperimentalSerializationApi::class)
  private val known: Set<String> = generated.descriptor.elementNames.toSet() - ADDITIONAL_FIELDS

  override fun transformDeserialize(element: JsonElement): JsonElement {
    if (element !is JsonObject) return element
    val declared = LinkedHashMap<String, JsonElement>()
    val extras = LinkedHashMap<String, JsonElement>()
    for ((key, value) in element) {
      if (key in known) declared[key] = value else extras[key] = value
    }
    declared[ADDITIONAL_FIELDS] = JsonObject(extras)
    return JsonObject(declared)
  }

  override fun transformSerialize(element: JsonElement): JsonElement {
    if (element !is JsonObject) return element
    val flat = LinkedHashMap<String, JsonElement>()
    var extras: JsonObject? = null
    for ((key, value) in element) {
      if (key == ADDITIONAL_FIELDS) extras = value as? JsonObject else flat[key] = value
    }
    extras?.forEach { (key, value) -> if (key !in known) flat[key] = value }
    return JsonObject(flat)
  }
}

internal object AuthUserSerializer :
    AdditionalFieldsSerializer<AuthUser>(AuthUser.generatedSerializer())

internal object AuthSessionSerializer :
    AdditionalFieldsSerializer<AuthSession>(AuthSession.generatedSerializer())

internal object SessionEnvelopeSerializer :
    AdditionalFieldsSerializer<SessionEnvelope>(SessionEnvelope.generatedSerializer())

internal object AuthResponseSerializer :
    AdditionalFieldsSerializer<AuthResponse>(AuthResponse.generatedSerializer())

internal object BetterAuthErrorSerializer :
    AdditionalFieldsSerializer<BetterAuthError>(BetterAuthError.generatedSerializer())
