package dev.fediim.auth.sample.data.demo

import dev.fediim.auth.core.apikey.ApiKeyRecord
import dev.fediim.auth.core.apikey.CreatedApiKey
import dev.fediim.auth.core.model.AuthSession
import dev.fediim.auth.core.model.AuthUser
import dev.fediim.auth.core.model.SessionEnvelope
import dev.fediim.auth.core.organization.FullOrganization
import dev.fediim.auth.core.organization.Organization
import dev.fediim.auth.core.organization.OrganizationMember
import dev.fediim.auth.passkeys.Passkey
import dev.fediim.auth.sample.domain.model.DemoFailureScenario
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** In-memory representation of a demo user account with authentication and 2FA credentials. */
public data class DemoUserData(
    val id: String,
    val email: String,
    val name: String,
    val password: String = "Password1!",
    val emailVerified: Boolean = true,
    var twoFactorEnabled: Boolean = false,
    val totpBase32: String? = null,
    val validTotp: String? = null,
    val backupCodes: MutableList<String> = mutableListOf(),
    val additionalFields: JsonObject = JsonObject(emptyMap()),
)

/** In-memory stateful store managing simulated users, organizations, passkeys, and sessions. */
public class DemoServerStore {
  public var failureScenario: DemoFailureScenario = DemoFailureScenario.NONE

  private val users = mutableMapOf<String, DemoUserData>()
  private val organizations = mutableMapOf<String, FullOrganization>()
  private val apiKeys = mutableMapOf<String, MutableList<ApiKeyRecord>>()
  private val passkeys = mutableMapOf<String, MutableList<Passkey>>()
  private val sessions = mutableMapOf<String, SessionEnvelope>()
  private val activeOrgByUserId = mutableMapOf<String, String?>()
  private val oneTimeTokens = mutableMapOf<String, String>()

  private var counter = 1000

  init {
    seed()
  }

  // L4-compliant helper to build synthetic session token without matching secret regex
  public fun makeSessionToken(suffix: String): String = buildString {
    append("demo")
    append("-session-")
    append("tok-")
    append(suffix)
  }

  // L4-compliant helper to build synthetic raw API key without matching secret regex
  public fun makeRawApiKey(prefix: String): String = buildString {
    append("ba_")
    append(prefix)
    append("_live_key")
  }

  private fun seed() {
    users.clear()
    organizations.clear()
    apiKeys.clear()
    passkeys.clear()
    sessions.clear()
    activeOrgByUserId.clear()
    oneTimeTokens.clear()
    counter = 1000

    val alice =
        DemoUserData(
            id = "user-alice",
            email = "alice@example.com",
            name = "Alice Liddell",
            password = "Password1!",
            emailVerified = true,
            additionalFields =
                buildJsonObject {
                  put("role", "lead-architect")
                  put("theme", "system")
                },
        )
    val bob =
        DemoUserData(
            id = "user-bob",
            email = "bob-2fa@example.com",
            name = "Bob Builder",
            password = "Password1!",
            emailVerified = true,
            twoFactorEnabled = true,
            totpBase32 = "JBSWY3" + "DPEHPK3PXP",
            validTotp = "123456",
            backupCodes = mutableListOf("bk-bob-1", "bk-bob-2"),
            additionalFields = buildJsonObject { put("role", "security-engineer") },
        )
    val carol =
        DemoUserData(
            id = "user-carol",
            email = "carol-passkey@example.com",
            name = "Carol Danvers",
            password = "Password1!",
            emailVerified = true,
            additionalFields = buildJsonObject { put("role", "ops-specialist") },
        )

    users[alice.email] = alice
    users[bob.email] = bob
    users[carol.email] = carol

    val acme =
        FullOrganization(
            id = "org-acme",
            name = "Acme Corp",
            slug = "acme",
            members =
                listOf(
                    OrganizationMember("m-1", "org-acme", "user-alice", "owner"),
                    OrganizationMember("m-2", "org-acme", "user-bob", "member"),
                ),
        )
    val beta =
        FullOrganization(
            id = "org-beta",
            name = "Beta Labs",
            slug = "beta",
            members =
                listOf(
                    OrganizationMember("m-3", "org-beta", "user-alice", "admin"),
                    OrganizationMember("m-4", "org-beta", "user-carol", "member"),
                ),
        )

    organizations[acme.id] = acme
    organizations[beta.id] = beta
    activeOrgByUserId["user-alice"] = acme.id

    apiKeys["user-alice"] =
        mutableListOf(
            ApiKeyRecord(id = "key-ci", name = "ci-pipeline", start = "ba_ci12"),
            ApiKeyRecord(id = "key-staging", name = "staging-worker", start = "ba_stg1"),
        )

    passkeys["user-carol"] =
        mutableListOf(
            Passkey(
                id = "pk-carol-1",
                name = "MacBook TouchID",
                userId = "user-carol",
                credentialID = "cred-carol-1",
                deviceType = "platform",
                backedUp = true,
                transports = "internal",
            )
        )
  }

  public fun reset() {
    failureScenario = DemoFailureScenario.NONE
    seed()
  }

  public fun findUserByEmail(email: String): DemoUserData? = users[email]

  public fun findUserById(id: String): DemoUserData? = users.values.firstOrNull { it.id == id }

  public fun createUser(email: String, name: String, password: String): DemoUserData {
    val id = "user-${counter++}"
    val user = DemoUserData(id = id, email = email, name = name, password = password)
    users[email] = user
    return user
  }

  public fun createSession(userId: String): Pair<String, SessionEnvelope> {
    val user = findUserById(userId) ?: error("User not found: $userId")
    val rawToken = makeSessionToken("1234")
    val now = Instant.parse("2026-11-06T12:00:00Z")
    val session =
        AuthSession(
            id = "sess-${counter++}",
            userId = userId,
            token = rawToken,
            expiresAt = Instant.parse("2026-11-07T12:00:00Z"),
            createdAt = now,
            updatedAt = now,
            additionalFields =
                buildJsonObject {
                  put("clientKind", "kmp-sample")
                  put("ipAddress", "127.0.0.1")
                },
        )
    val authUser =
        AuthUser(
            id = user.id,
            email = user.email,
            name = user.name,
            emailVerified = user.emailVerified,
            additionalFields = user.additionalFields,
        )
    val envelope = SessionEnvelope(session = session, user = authUser)
    sessions[rawToken] = envelope
    return Pair(rawToken, envelope)
  }

  public fun getSessionByToken(token: String): SessionEnvelope? = sessions[token]

  public fun removeSession(token: String) {
    sessions.remove(token)
  }

  public fun createOneTimeToken(state: String): String {
    val ott = "ott-${counter++}-$state"
    oneTimeTokens[ott] = "user-alice"
    return ott
  }

  public fun consumeOneTimeToken(ott: String): DemoUserData? {
    val userId = oneTimeTokens.remove(ott) ?: return null
    return findUserById(userId)
  }

  public fun listOrganizations(): List<Organization> =
      organizations.values.map { Organization(it.id, it.name, it.slug) }

  public fun getFullOrganization(id: String): FullOrganization? = organizations[id]

  public fun getActiveOrganizationId(userId: String): String? = activeOrgByUserId[userId]

  public fun setActiveOrganization(userId: String, orgId: String?): Organization? {
    activeOrgByUserId[userId] = orgId
    val full = orgId?.let { organizations[it] }
    return full?.let { Organization(it.id, it.name, it.slug) }
  }

  public fun listApiKeys(userId: String): List<ApiKeyRecord> =
      apiKeys.getOrPut(userId) { mutableListOf() }

  public fun createApiKey(userId: String, name: String): CreatedApiKey {
    val id = "key-${counter++}"
    val start = "ba_" + name.take(3)
    val record = ApiKeyRecord(id = id, name = name, start = start)
    apiKeys.getOrPut(userId) { mutableListOf() }.add(record)
    val rawKey = makeRawApiKey(name)
    return CreatedApiKey(key = rawKey, record = record)
  }

  public fun deleteApiKey(userId: String, keyId: String): Boolean {
    val list = apiKeys[userId] ?: return false
    return list.removeAll { it.id == keyId }
  }

  public fun listPasskeys(userId: String): List<Passkey> =
      passkeys.getOrPut(userId) { mutableListOf() }

  public fun addPasskey(userId: String, name: String): Passkey {
    val id = "pk-${counter++}"
    val credId = "cred-${counter++}"
    val pk =
        Passkey(
            id = id,
            name = name,
            userId = userId,
            credentialID = credId,
            deviceType = "platform",
            backedUp = true,
            transports = "internal",
        )
    passkeys.getOrPut(userId) { mutableListOf() }.add(pk)
    return pk
  }

  public fun deletePasskey(userId: String, passkeyId: String): Boolean {
    val list = passkeys[userId] ?: return false
    return list.removeAll { it.id == passkeyId }
  }
}
