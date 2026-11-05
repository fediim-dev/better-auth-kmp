package dev.fediim.auth.passkeys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val wireJson = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
  encodeDefaults = true
}

/**
 * Shared half of the browser authenticators (js, wasmJs). The platform glue gets the options as
 * JSON and answers with an envelope `{"ok": <response>}` or `{"error": "<code>"}`; this encodes the
 * options, runs [invoke] (`kind` is `create` or `get`) and turns the envelope into a response or a
 * [PasskeyException].
 */
internal suspend fun <O, R> runBrowserCeremony(
    kind: String,
    options: O,
    optionsSerializer: KSerializer<O>,
    responseSerializer: KSerializer<R>,
    invoke: suspend (kind: String, optionsJson: String) -> String,
): R {
  val envelope =
      wireJson
          .parseToJsonElement(invoke(kind, wireJson.encodeToString(optionsSerializer, options)))
          .jsonObject
  envelope["error"]?.let { throw PasskeyException(it.jsonPrimitive.content) }
  val ok = envelope["ok"] ?: throw PasskeyException("PASSKEY_FAILED", "empty answer")
  return wireJson.decodeFromJsonElement(responseSerializer, ok)
}
