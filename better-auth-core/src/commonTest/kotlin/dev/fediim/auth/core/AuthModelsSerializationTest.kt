package dev.fediim.auth.core

import dev.fediim.auth.core.model.AuthResponse
import dev.fediim.auth.core.model.AuthSession
import dev.fediim.auth.core.model.AuthUser
import dev.fediim.auth.core.model.BetterAuthError
import dev.fediim.auth.core.model.SessionEnvelope
import dev.fediim.auth.core.model.SignInEmailRequest
import dev.fediim.auth.core.model.SignUpEmailRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class AuthModelsSerializationTest {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  @Test
  fun t1UnknownUserKeyLandsInAdditionalFields() {
    val user =
        json.decodeFromString<AuthUser>("""{"id":"u1","email":"a@b.com","role":"Org Admin"}""")
    assertEquals(JsonPrimitive("Org Admin"), user.additionalFields["role"])
    assertNull(user.name)
    assertNull(user.createdAt)
    assertFalse(user.emailVerified)
    val dated =
        json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"a@b.com","createdAt":"2024-05-06T07:08:09.123Z"}"""
        )
    assertEquals(Instant.parse("2024-05-06T07:08:09.123Z"), dated.createdAt)
  }

  @Test
  fun t2RoundTripFlattensExtras() {
    val extras = buildJsonObject {
      put("nested", buildJsonObject { put("a", 1) })
      put("list", JsonArray(listOf(JsonPrimitive(1), JsonPrimitive("x"))))
      put("nothing", JsonNull)
    }
    val user =
        AuthUser(
            id = "u1",
            email = "a@b.com",
            createdAt = Instant.parse("2024-05-06T07:08:09Z"),
            additionalFields = extras,
        )
    val encoded = json.encodeToString(user)
    val obj = json.parseToJsonElement(encoded).jsonObject
    assertFalse("additionalFields" in obj)
    assertEquals(extras["nested"], obj["nested"])
    assertEquals(JsonNull, obj["nothing"])
    assertEquals(JsonPrimitive("2024-05-06T07:08:09Z"), obj["createdAt"])
    assertEquals(user, json.decodeFromString<AuthUser>(encoded))
  }

  @Test
  fun t3EnvelopeKeepsExtrasPerLevel() {
    val envelope =
        json.decodeFromString<SessionEnvelope>(
            """{"session":{"id":"s1","userId":"u1","token":"tok","sx":1},
               "user":{"id":"u1","email":"a@b.com","ux":2},"ex":3}"""
        )
    assertEquals("tok", envelope.session.token)
    assertEquals(JsonPrimitive(1), envelope.session.additionalFields["sx"])
    assertEquals(JsonPrimitive(2), envelope.user.additionalFields["ux"])
    assertEquals(JsonPrimitive(3), envelope.additionalFields["ex"])
    assertEquals(setOf("ex"), envelope.additionalFields.keys)
  }

  @Test
  fun t4Collisions() {
    val user =
        json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"a@b.com","additionalFields":{"x":1}}"""
        )
    assertEquals(setOf("additionalFields"), user.additionalFields.keys)
    assertEquals(
        JsonObject(mapOf("x" to JsonPrimitive(1))),
        user.additionalFields["additionalFields"],
    )

    val clash =
        AuthUser(
            "real",
            "a@b.com",
            additionalFields = JsonObject(mapOf("id" to JsonPrimitive("fake"))),
        )
    val obj = json.parseToJsonElement(json.encodeToString(clash)).jsonObject
    assertEquals(JsonPrimitive("real"), obj["id"])
  }

  @Test
  fun t5ErrorAndAuthResponse() {
    val error = json.decodeFromString<BetterAuthError>("""{"code":"C","message":"m","foo":"bar"}""")
    assertEquals("C", error.code)
    assertEquals(JsonPrimitive("bar"), error.additionalFields["foo"])

    val signUp =
        json.decodeFromString<AuthResponse>("""{"token":null,"user":{"id":"u","email":"e"}}""")
    assertNull(signUp.token)
    assertEquals("u", signUp.user?.id)

    val twoFactor = json.decodeFromString<AuthResponse>("""{"twoFactorRedirect":true}""")
    assertNull(twoFactor.user)
    assertEquals(JsonPrimitive(true), twoFactor.additionalFields["twoFactorRedirect"])
  }

  @Test
  fun t6RedactionAndRequestEncoding() {
    val up = SignUpEmailRequest("N", "a@b.com", "pw-secret")
    val inn = SignInEmailRequest("a@b.com", "pw-secret")
    assertFalse("pw-secret" in up.toString())
    assertFalse("pw-secret" in inn.toString())
    assertFalse("tok-secret" in AuthSession("s", "u", "tok-secret").toString())
    assertFalse("tok-secret" in AuthResponse(token = "tok-secret").toString())

    val upJson = json.encodeToString(up)
    assertTrue("pw-secret" in upJson)
    assertFalse("image" in upJson || "callbackURL" in upJson)
    val inJson = json.encodeToString(inn)
    assertTrue("pw-secret" in inJson)
    assertFalse("rememberMe" in inJson || "callbackURL" in inJson)
  }
}
