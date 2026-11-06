package dev.fediim.auth.sample.domain.model

import kotlinx.serialization.json.JsonObject

/** Execution backend for the sample application, selecting offline demo or live server. */
public enum class BackendMode {
  OFFLINE_DEMO,
  LIVE_SERVER,
}

/** Active environment configuration holding backend mode and live server URL. */
public data class EnvironmentConfig(
    val mode: BackendMode = BackendMode.OFFLINE_DEMO,
    val liveServerUrl: String = "http://127.0.0.1:3000",
)

/** Simplified account profile details for presentation in the sample workspace. */
public data class AccountProfile(
    val id: String,
    val email: String,
    val name: String?,
    val emailVerified: Boolean,
    val additionalFields: JsonObject = JsonObject(emptyMap()),
)

/** Aggregated session overview containing user profile and masked authentication token. */
public data class SessionOverview(
    val sessionId: String,
    val userId: String,
    val maskedToken: String,
    val expiresAtIso: String?,
    val activeOrganizationId: String?,
    val user: AccountProfile,
    val sessionAdditionalFields: JsonObject = JsonObject(emptyMap()),
)

/** Registered passkey credential representation for listing and deletion in the sample UI. */
public data class PasskeyItem(
    val id: String,
    val name: String,
    val credentialId: String,
    val deviceType: String?,
    val backedUp: Boolean,
    val transports: String?,
)

/** Two-factor authentication status and enabled verification methods. */
public data class TwoFactorStatus(
    val isEnabled: Boolean,
    val supportedMethods: List<String> = listOf("totp", "backup_code"),
)

/** Pre-configured user persona presets available in the offline demo. */
public enum class DemoPreset {
  STANDARD,
  TWO_FACTOR,
  PASSKEY,
}

/** Simulated error scenarios for testing client resilience in offline demo mode. */
public enum class DemoFailureScenario {
  NONE,
  RATE_LIMITED_429,
  PASSKEY_FAILED,
  INVALID_CREDENTIALS,
  INVALID_TWO_FACTOR_CODE,
}

/** Returns `<first 4>••••<last 4>` for tokens > 8 chars, never exposing the full raw token. */
public fun maskSessionToken(rawToken: String?): String {
  if (rawToken.isNullOrEmpty()) return ""
  if (rawToken.length <= 8) return "••••"
  return "${rawToken.take(4)}••••${rawToken.takeLast(4)}"
}
