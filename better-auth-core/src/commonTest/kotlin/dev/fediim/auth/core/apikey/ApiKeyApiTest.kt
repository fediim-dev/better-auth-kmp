package dev.fediim.auth.core.apikey

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ApiKeyApiTest {
  private val raw = "raw-" + "x".repeat(8) + "-AAAA"
  private val hash = "hash-" + "y".repeat(8) + "-ZZZ"

  private class Rig(handler: suspend (HttpRequestData) -> Reply) {
    val requests = mutableListOf<HttpRequestData>()
    val client =
        BetterAuthClient(
            BetterAuthConfig("https://example.com"),
            SettingsAuthTokenStorage(MapSettings()),
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
  }

  private class Reply(val body: String, val status: HttpStatusCode)

  private fun ok(body: String, status: HttpStatusCode = HttpStatusCode.OK) = Reply(body, status)

  private fun <T> BetterAuthResult<T>.value(): T = (this as BetterAuthResult.Success).value

  @Test
  fun t8CreateReturnsRawKeyOnceAndRedacts() = runTest {
    var next =
        ok(
            """{"id":"k1","name":"ci","key":"$raw","permissions":{"files":["read"]},
            "rateLimitMax":10,"metadata":{"projectId":"p1"},"createdAt":"2030-01-01T00:00:00.000Z"}"""
        )
    val rig = Rig { next }
    val request =
        CreateApiKeyRequest("ci", 86400, metadata = buildJsonObject { put("projectId", "p1") })
    val created = rig.client.apiKeys.create(request).value()
    assertEquals(HttpMethod.Post, rig.requests[0].method)
    assertEquals("https://example.com/api/auth/api-key/create", rig.requests[0].url.toString())
    assertEquals("""{"name":"ci","expiresIn":86400,"metadata":{"projectId":"p1"}}""", rig.bodyOf(0))
    assertEquals(raw, created.key)
    assertEquals(mapOf("files" to listOf("read")), created.record.permissions)
    assertEquals(10L, created.record.rateLimitMax)
    assertEquals(JsonPrimitive("p1"), created.record.metadata?.get("projectId"))
    assertFalse(created.toString().contains(raw))
    assertFalse(created.record.toString().contains(raw))
    assertFalse("key" in created.record.additionalFields)

    next = ok("""{"id":"k2","key":"$raw","metadata":null}""")
    assertNull(rig.client.apiKeys.create().value().record.metadata)
    assertEquals("{}", rig.bodyOf(1))

    next = ok("""{"id":"k3"}""")
    assertEquals(200, (rig.client.apiKeys.create() as BetterAuthResult.Failure).status)
  }

  @Test
  fun t9ListGetUpdateDelete() = runTest {
    var next =
        ok(
            """{"apiKeys":[{"id":"k1","key":"$hash","extra":1},{"id":"k2","metadata":[1]},{"id":"k3","metadata":"{\"projectId\":\"p3\"}"}],
            "total":2,"limit":null,"offset":null}"""
        )
    val rig = Rig { next }
    val api = rig.client.apiKeys
    val list = api.list().value()
    assertEquals("https://example.com/api/auth/api-key/list", rig.requests[0].url.toString())
    assertEquals(listOf("k1", "k2", "k3"), list.map { it.id })
    assertFalse("key" in list[0].additionalFields)
    assertFalse(list[0].toString().contains(hash))
    assertEquals(JsonPrimitive(1), list[0].additionalFields["extra"])
    assertNull(list[1].metadata)
    assertEquals(JsonPrimitive("p3"), list[2].metadata?.get("projectId"))

    next = ok("""{"id":"k1","name":"n"}""")
    assertEquals("k1", api.get("k1").value().id)
    assertEquals("k1", rig.requests[1].url.parameters["id"])
    assertEquals(HttpMethod.Get, rig.requests[1].method)

    api.update(UpdateApiKeyRequest("k1", enabled = false)).value()
    assertEquals("""{"keyId":"k1","enabled":false}""", rig.bodyOf(2))
    assertEquals("https://example.com/api/auth/api-key/update", rig.requests[2].url.toString())

    next = ok("""{"success":true}""")
    assertEquals(BetterAuthResult.Success(Unit), api.delete("k1"))
    assertEquals("""{"keyId":"k1"}""", rig.bodyOf(3))
    assertEquals("https://example.com/api/auth/api-key/delete", rig.requests[3].url.toString())

    next = ok("""{"code":"KEY_NOT_FOUND"}""", HttpStatusCode.NotFound)
    val failure = api.get("zz")
    assertIs<BetterAuthResult.Failure>(failure)
    assertEquals(404, failure.status)
    assertEquals("KEY_NOT_FOUND", failure.error.code)
    assertTrue(failure.error.additionalFields.isEmpty())
  }
}
