package dev.fediim.auth.sample.data.demo

import dev.fediim.auth.core.apikey.ApiKeyRecord
import dev.fediim.auth.core.model.AuthUser
import dev.fediim.auth.core.model.SessionEnvelope
import dev.fediim.auth.core.organization.FullOrganization
import dev.fediim.auth.core.organization.Organization
import dev.fediim.auth.passkeys.Passkey
import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.content.TextContent
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Creates a Ktor [MockEngine] providing offline demo endpoints backed by [DemoServerStore]. */
public fun createDemoMockEngine(store: DemoServerStore): MockEngine {
  val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  fun MockRequestHandleScope.jsonResponse(
      content: String,
      status: HttpStatusCode = HttpStatusCode.OK,
      extraHeaders: Map<String, List<String>> = emptyMap(),
  ): HttpResponseData =
      respond(
          content = content,
          status = status,
          headers =
              Headers.build {
                append(HttpHeaders.ContentType, "application/json")
                extraHeaders.forEach { (k, v) -> appendAll(k, v) }
              },
      )

  fun parseBody(requestBody: Any?): JsonObject {
    val text =
        when (requestBody) {
          is TextContent -> requestBody.text
          is String -> requestBody
          is ByteArray -> requestBody.decodeToString()
          else -> ""
        }
    return if (text.isNotBlank()) {
      try {
        json.parseToJsonElement(text).jsonObject
      } catch (_: Exception) {
        JsonObject(emptyMap())
      }
    } else {
      JsonObject(emptyMap())
    }
  }

  return MockEngine { request ->
    val path = request.url.encodedPath
    val body = parseBody(request.body)

    if (store.failureScenario == DemoFailureScenario.RATE_LIMITED_429) {
      if (path.endsWith("sign-in/email") || path.endsWith("sign-up/email")) {
        return@MockEngine jsonResponse(
            content = """{"code":"TOO_MANY_REQUESTS","message":"Too many requests"}""",
            status = HttpStatusCode.TooManyRequests,
            extraHeaders = mapOf("Retry-After" to listOf("30")),
        )
      }
    }

    when {
      path.endsWith("sign-in/email") -> {
        if (store.failureScenario == DemoFailureScenario.INVALID_CREDENTIALS) {
          return@MockEngine jsonResponse(
              content = """{"code":"INVALID_EMAIL_OR_PASSWORD","message":"Invalid credentials"}""",
              status = HttpStatusCode.Unauthorized,
          )
        }
        val email = body["email"]?.jsonPrimitive?.content ?: ""
        val pass = body["password"]?.jsonPrimitive?.content ?: ""
        val user = store.findUserByEmail(email)
        if (user == null || user.password != pass) {
          return@MockEngine jsonResponse(
              content = """{"code":"INVALID_EMAIL_OR_PASSWORD","message":"Invalid credentials"}""",
              status = HttpStatusCode.Unauthorized,
          )
        }
        if (user.twoFactorEnabled) {
          return@MockEngine jsonResponse(
              content =
                  """{"token":null,"user":null,"twoFactorRedirect":true,"twoFactorMethods":["totp","backup_code"]}""",
              extraHeaders =
                  mapOf("Set-Cookie" to listOf("sample.two_factor=c-twofactor; Path=/; HttpOnly")),
          )
        }
        val (tok, envelope) = store.createSession(user.id)
        val userJson = json.encodeToString(AuthUser.serializer(), envelope.user)
        jsonResponse(
            content = """{"token":"$tok","user":$userJson}""",
            extraHeaders = mapOf("set-auth-token" to listOf(tok)),
        )
      }

      path.endsWith("sign-up/email") -> {
        val email = body["email"]?.jsonPrimitive?.content ?: ""
        val name = body["name"]?.jsonPrimitive?.content ?: ""
        val pass = body["password"]?.jsonPrimitive?.content ?: ""
        val user = store.createUser(email, name, pass)
        val (tok, envelope) = store.createSession(user.id)
        val userJson = json.encodeToString(AuthUser.serializer(), envelope.user)
        jsonResponse(
            content = """{"token":"$tok","user":$userJson}""",
            extraHeaders = mapOf("set-auth-token" to listOf(tok)),
        )
      }

      path.endsWith("sign-out") -> {
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        if (token != null) store.removeSession(token)
        jsonResponse("""{"success":true}""")
      }

      path.endsWith("get-session") -> {
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        val envelope = token?.let { store.getSessionByToken(it) }
        if (envelope != null) {
          jsonResponse(json.encodeToString(SessionEnvelope.serializer(), envelope))
        } else {
          jsonResponse("null")
        }
      }

      path.endsWith("sign-in/social") -> {
        jsonResponse(
            """{"url":"http://127.0.0.1:3000/oauth-callback?mock=true","redirect":false}"""
        )
      }

      path.endsWith("one-time-token/verify") -> {
        val ott = body["token"]?.jsonPrimitive?.content ?: ""
        val user = store.consumeOneTimeToken(ott) ?: store.findUserById("user-alice")!!
        val (tok, envelope) = store.createSession(user.id)
        jsonResponse(
            content = json.encodeToString(SessionEnvelope.serializer(), envelope),
            extraHeaders = mapOf("set-auth-token" to listOf(tok)),
        )
      }

      path.endsWith("two-factor/enable") -> {
        jsonResponse(
            """{"totpURI":"otpauth://totp/BetterAuth:bob?secret=JBSWY3DPEHPK3PXP&issuer=BetterAuth","backupCodes":["bk-new-1","bk-new-2"]}"""
        )
      }

      path.endsWith("two-factor/get-totp-uri") -> {
        jsonResponse(
            """{"totpURI":"otpauth://totp/BetterAuth:bob?secret=JBSWY3DPEHPK3PXP&issuer=BetterAuth"}"""
        )
      }

      path.endsWith("two-factor/verify-totp") -> {
        val code = body["code"]?.jsonPrimitive?.content ?: ""
        if (
            store.failureScenario == DemoFailureScenario.INVALID_TWO_FACTOR_CODE || code != "123456"
        ) {
          return@MockEngine jsonResponse(
              content = """{"code":"INVALID_TWO_FACTOR_CODE","message":"Invalid TOTP code"}""",
              status = HttpStatusCode.BadRequest,
          )
        }
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        if (token != null) {
          val user = store.getSessionByToken(token)?.user
          val targetUser =
              (user?.let { store.findUserById(it.id) }) ?: store.findUserById("user-alice")
          targetUser?.twoFactorEnabled = true
          val (tok, envelope) = store.createSession(targetUser?.id ?: "user-alice")
          val userJson = json.encodeToString(AuthUser.serializer(), envelope.user)
          jsonResponse(
              content = """{"token":"$tok","user":$userJson}""",
              extraHeaders = mapOf("set-auth-token" to listOf(tok)),
          )
        } else {
          val cookieHeader = request.headers[HttpHeaders.Cookie] ?: ""
          if (!cookieHeader.contains(".two_factor")) {
            return@MockEngine jsonResponse(
                content =
                    """{"code":"MISSING_CHALLENGE","message":"Missing two-factor challenge cookie"}""",
                status = HttpStatusCode.BadRequest,
            )
          }
          val (tok, envelope) = store.createSession("user-bob")
          val userJson = json.encodeToString(AuthUser.serializer(), envelope.user)
          jsonResponse(
              content = """{"token":"$tok","user":$userJson}""",
              extraHeaders = mapOf("set-auth-token" to listOf(tok)),
          )
        }
      }

      path.endsWith("two-factor/verify-backup-code") -> {
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        if (token == null) {
          val cookieHeader = request.headers[HttpHeaders.Cookie] ?: ""
          if (!cookieHeader.contains(".two_factor")) {
            return@MockEngine jsonResponse(
                content =
                    """{"code":"MISSING_CHALLENGE","message":"Missing two-factor challenge cookie"}""",
                status = HttpStatusCode.BadRequest,
            )
          }
        }
        val code = body["code"]?.jsonPrimitive?.content ?: ""
        if (code in listOf("bk-bob-1", "bk-bob-2")) {
          val (tok, envelope) = store.createSession("user-bob")
          val userJson = json.encodeToString(AuthUser.serializer(), envelope.user)
          jsonResponse(
              content = """{"token":"$tok","user":$userJson}""",
              extraHeaders = mapOf("set-auth-token" to listOf(tok)),
          )
        } else {
          jsonResponse(
              content = """{"code":"INVALID_TWO_FACTOR_CODE","message":"Invalid backup code"}""",
              status = HttpStatusCode.BadRequest,
          )
        }
      }

      path.endsWith("two-factor/disable") -> {
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        val userId = (token?.let { store.getSessionByToken(it)?.user?.id }) ?: "user-bob"
        val user = store.findUserById(userId)
        user?.twoFactorEnabled = false
        jsonResponse("""{"status":true}""")
      }

      path.endsWith("passkey/generate-register-options") -> {
        jsonResponse(
            content =
                """{"challenge":"ZGVtby1jaGFsbGVuZ2U","rp":{"name":"Better Auth Sample","id":"localhost"},"user":{"id":"dXNlci1hbGljZQ","name":"alice@example.com","displayName":"Alice"},"pubKeyCredParams":[{"type":"public-key","alg":-7}]}""",
            extraHeaders =
                mapOf(
                    "Set-Cookie" to listOf("sample.better-auth-passkey=c-pk-reg; Path=/; HttpOnly")
                ),
        )
      }

      path.endsWith("passkey/verify-registration") -> {
        val cookieHeader = request.headers[HttpHeaders.Cookie] ?: ""
        if (!cookieHeader.contains(".better-auth-passkey")) {
          return@MockEngine jsonResponse(
              content =
                  """{"code":"MISSING_CHALLENGE","message":"Missing passkey challenge cookie"}""",
              status = HttpStatusCode.BadRequest,
          )
        }
        val name = body["name"]?.jsonPrimitive?.content ?: "My Security Key"
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        val userId = (token?.let { store.getSessionByToken(it)?.user?.id }) ?: "user-alice"
        val pk = store.addPasskey(userId, name)
        jsonResponse(json.encodeToString(Passkey.serializer(), pk))
      }

      path.endsWith("passkey/generate-authenticate-options") -> {
        jsonResponse(
            content = """{"challenge":"ZGVtby1hdXRoLWNoYWxsZW5nZQ","rpId":"localhost"}""",
            extraHeaders =
                mapOf(
                    "Set-Cookie" to listOf("sample.better-auth-passkey=c-pk-auth; Path=/; HttpOnly")
                ),
        )
      }

      path.endsWith("passkey/verify-authentication") -> {
        val cookieHeader = request.headers[HttpHeaders.Cookie] ?: ""
        if (!cookieHeader.contains(".better-auth-passkey")) {
          return@MockEngine jsonResponse(
              content =
                  """{"code":"MISSING_CHALLENGE","message":"Missing passkey challenge cookie"}""",
              status = HttpStatusCode.BadRequest,
          )
        }
        if (store.failureScenario == DemoFailureScenario.PASSKEY_FAILED) {
          return@MockEngine jsonResponse(
              content = """{"code":"PASSKEY_FAILED","message":"Passkey ceremony failed"}""",
              status = HttpStatusCode.BadRequest,
          )
        }
        val (tok, envelope) = store.createSession("user-carol")
        jsonResponse(
            content = json.encodeToString(SessionEnvelope.serializer(), envelope),
            extraHeaders = mapOf("set-auth-token" to listOf(tok)),
        )
      }

      path.endsWith("passkey/list-user-passkeys") -> {
        val list = store.listPasskeys("user-carol") + store.listPasskeys("user-alice")
        jsonResponse(json.encodeToString(ListSerializer(Passkey.serializer()), list))
      }

      path.endsWith("passkey/delete-passkey") -> {
        val id = body["id"]?.jsonPrimitive?.content ?: ""
        store.deletePasskey("user-carol", id)
        store.deletePasskey("user-alice", id)
        jsonResponse("""{"success":true}""")
      }

      path.endsWith("organization/list") -> {
        val orgs = store.listOrganizations()
        jsonResponse(json.encodeToString(ListSerializer(Organization.serializer()), orgs))
      }

      path.endsWith("organization/get-full-organization") -> {
        val orgId = request.url.parameters["organizationId"] ?: "org-acme"
        val full = store.getFullOrganization(orgId) ?: store.getFullOrganization("org-acme")!!
        jsonResponse(json.encodeToString(FullOrganization.serializer(), full))
      }

      path.endsWith("organization/set-active") -> {
        val orgId = body["organizationId"]?.jsonPrimitive?.content
        val org =
            store.setActiveOrganization("user-alice", orgId)
                ?: Organization("org-acme", "Acme Corp", "acme")
        jsonResponse(json.encodeToString(Organization.serializer(), org))
      }

      path.endsWith("api-key/list") -> {
        val keys = store.listApiKeys("user-alice")
        val keysJson = json.encodeToJsonElement(ListSerializer(ApiKeyRecord.serializer()), keys)
        jsonResponse(buildJsonObject { put("apiKeys", keysJson) }.toString())
      }

      path.endsWith("api-key/create") -> {
        val name = body["name"]?.jsonPrimitive?.content ?: "new-key"
        val created = store.createApiKey("user-alice", name)
        val respObj = buildJsonObject {
          put("key", created.key)
          put("id", created.record.id)
          put("name", created.record.name)
          put("start", created.record.start)
        }
        jsonResponse(respObj.toString())
      }

      path.endsWith("api-key/delete") -> {
        val keyId = body["keyId"]?.jsonPrimitive?.content ?: ""
        store.deleteApiKey("user-alice", keyId)
        jsonResponse("""{"success":true}""")
      }

      else -> jsonResponse("""{"error":"not_found"}""", HttpStatusCode.NotFound)
    }
  }
}
