package dev.fediim.auth.passkeys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Fake navigator.credentials: records what the authenticator passed (as plain byte arrays) in
// `calls`
// and answers with ArrayBuffers; `denied` rejects like a dismissed prompt.
private fun fakeCredentials(denied: Boolean): JsAny =
    js(
        """(function () {
  var calls = [];
  function buf() { return new Uint8Array(Array.prototype.slice.call(arguments)).buffer; }
  function bytes(b) { return Array.prototype.slice.call(new Uint8Array(b)); }
  function summary(pk) {
    return {
      challenge: bytes(pk.challenge),
      challengeIsBuffer: pk.challenge instanceof ArrayBuffer,
      userId: pk.user ? bytes(pk.user.id) : null,
      exclude: (pk.excludeCredentials || []).map(function (c) { return bytes(c.id); }),
      allow: (pk.allowCredentials || []).map(function (c) { return bytes(c.id); }),
      extensions: pk.extensions || null
    };
  }
  function dismissed() { var e = new Error("dismissed"); e.name = "NotAllowedError"; return Promise.reject(e); }
  return {
    calls: calls,
    create: function (o) {
      calls.push(summary(o.publicKey));
      if (denied) return dismissed();
      return Promise.resolve({ id: "cred-id", rawId: buf(1, 2, 3), type: "public-key", authenticatorAttachment: "platform",
        response: { clientDataJSON: buf(123, 125), attestationObject: buf(251, 255), getTransports: function () { return ["internal", "hybrid"]; } },
        getClientExtensionResults: function () { return { credProps: { rk: true } }; } });
    },
    get: function (o) {
      calls.push(summary(o.publicKey));
      if (denied) return dismissed();
      return Promise.resolve({ id: "cred-id", rawId: buf(1, 2, 3), type: "public-key", authenticatorAttachment: "platform",
        response: { clientDataJSON: buf(123, 125), authenticatorData: buf(9, 8, 7), signature: buf(251, 255), userHandle: buf(5) },
        getClientExtensionResults: function () { return {}; } });
    }
  };
})()"""
    )

private fun callsJson(fake: JsAny): String = js("JSON.stringify(fake.calls)")

class BrowserPasskeyAuthenticatorTest {
  private fun ints(bytes: ByteArray) = bytes.map { it.toInt() and 0xff }

  private fun JsonArray.ints() = map { it.jsonPrimitive.int }

  private val credentialId = byteArrayOf(0xFB.toByte(), 0xFF.toByte())
  private val challenge = byteArrayOf(1, 2, 3, 4)
  private val userId = byteArrayOf(9, 9)
  private val extensions = JsonObject(mapOf("credProps" to JsonPrimitive(true)))

  private suspend fun <T> BrowserPasskeyAuthenticator.callAndRecord(
      fake: JsAny,
      block: suspend BrowserPasskeyAuthenticator.() -> T,
  ): Pair<T, JsonObject> {
    val result = block()
    return result to Json.parseToJsonElement(callsJson(fake)).jsonArray.single().jsonObject
  }

  @Test
  fun createConvertsBuffersBothWays() = runTest {
    val fake = fakeCredentials(denied = false)
    val options =
        PublicKeyCredentialCreationOptions(
            challenge = Base64Url.encode(challenge),
            rp = RelyingParty("localhost", "App"),
            user = PublicKeyCredentialUser(Base64Url.encode(userId), "n", "d"),
            pubKeyCredParams = listOf(CredentialParameter("public-key", -7)),
            excludeCredentials = listOf(CredentialDescriptor(Base64Url.encode(credentialId))),
            extensions = extensions,
        )
    val (response, seen) = BrowserPasskeyAuthenticator(fake).callAndRecord(fake) { create(options) }

    assertEquals(ints(challenge), seen["challenge"]!!.jsonArray.ints())
    assertEquals(JsonPrimitive(true), seen["challengeIsBuffer"])
    assertEquals(ints(userId), seen["userId"]!!.jsonArray.ints())
    assertEquals(
        listOf(ints(credentialId)),
        seen["exclude"]!!.jsonArray.map { it.jsonArray.ints() },
    )
    assertEquals(extensions, seen["extensions"])
    assertEquals("cred-id", response.id)
    assertEquals(Base64Url.encode(byteArrayOf(1, 2, 3)), response.rawId)
    assertEquals(Base64Url.encode(byteArrayOf(123, 125)), response.response.clientDataJSON)
    assertEquals(Base64Url.encode(credentialId), response.response.attestationObject)
    assertEquals(listOf("internal", "hybrid"), response.response.transports)
    assertEquals("platform", response.authenticatorAttachment)
    assertEquals("public-key", response.type)
    assertEquals(
        JsonObject(mapOf("credProps" to JsonObject(mapOf("rk" to JsonPrimitive(true))))),
        response.clientExtensionResults,
    )
  }

  @Test
  fun getConvertsBuffersBothWays() = runTest {
    val fake = fakeCredentials(denied = false)
    val options =
        PublicKeyCredentialRequestOptions(
            challenge = Base64Url.encode(challenge),
            rpId = "localhost",
            allowCredentials = listOf(CredentialDescriptor(Base64Url.encode(credentialId))),
        )
    val (response, seen) = BrowserPasskeyAuthenticator(fake).callAndRecord(fake) { get(options) }

    assertEquals(ints(challenge), seen["challenge"]!!.jsonArray.ints())
    assertEquals(listOf(ints(credentialId)), seen["allow"]!!.jsonArray.map { it.jsonArray.ints() })
    assertEquals(Base64Url.encode(byteArrayOf(9, 8, 7)), response.response.authenticatorData)
    assertEquals(Base64Url.encode(credentialId), response.response.signature)
    assertEquals(Base64Url.encode(byteArrayOf(5)), response.response.userHandle)
  }

  @Test
  fun notAllowedErrorMeansCancelled() = runTest {
    val authenticator = BrowserPasskeyAuthenticator(fakeCredentials(denied = true))
    val error =
        assertFailsWith<PasskeyException> {
          authenticator.get(
              PublicKeyCredentialRequestOptions(challenge = Base64Url.encode(challenge))
          )
        }
    assertEquals("cancelled", error.code)
  }

  @Test
  fun headlessChromeSupportsPasskeys() {
    assertEquals(PasskeyCapability.Supported, BrowserPasskeyAuthenticator().capability)
  }
}
