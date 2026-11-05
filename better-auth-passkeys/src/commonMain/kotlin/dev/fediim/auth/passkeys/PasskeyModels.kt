package dev.fediim.auth.passkeys

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// WebAuthn JSON shapes as produced by simplewebauthn 13 (better-auth 1.7.17). Every binary field is
// an unpadded base64url string; unknown keys sent by the server are ignored.

/** Relying party of a registration; [id] is the server's `rpID`. */
@Serializable public data class RelyingParty(val id: String? = null, val name: String)

/** The account a passkey is registered for; [id] is base64url of the opaque user handle. */
@Serializable
public data class PublicKeyCredentialUser(val id: String, val name: String, val displayName: String)

/** An accepted public key algorithm, [alg] being a COSE identifier such as -7 (ES256). */
@Serializable public data class CredentialParameter(val type: String, val alg: Int)

/** An existing credential to exclude or allow; [id] is base64url. */
@Serializable
public data class CredentialDescriptor(
    val id: String,
    val type: String = "public-key",
    val transports: List<String>? = null,
)

/** Authenticator requirements of a registration (WebAuthn `AuthenticatorSelectionCriteria`). */
@Serializable
public data class AuthenticatorSelection(
    val authenticatorAttachment: String? = null,
    val residentKey: String? = null,
    val requireResidentKey: Boolean? = null,
    val userVerification: String? = null,
)

/**
 * `PublicKeyCredentialCreationOptionsJSON` of `passkey/generate-register-options`. [extensions]
 * pass through untouched.
 */
@Serializable
public data class PublicKeyCredentialCreationOptions(
    val challenge: String,
    val rp: RelyingParty,
    val user: PublicKeyCredentialUser,
    val pubKeyCredParams: List<CredentialParameter>,
    val timeout: Long? = null,
    val excludeCredentials: List<CredentialDescriptor> = emptyList(),
    val authenticatorSelection: AuthenticatorSelection? = null,
    val attestation: String? = null,
    val extensions: JsonObject? = null,
)

/** `PublicKeyCredentialRequestOptionsJSON` of `passkey/generate-authenticate-options`. */
@Serializable
public data class PublicKeyCredentialRequestOptions(
    val challenge: String,
    val rpId: String? = null,
    val timeout: Long? = null,
    val allowCredentials: List<CredentialDescriptor> = emptyList(),
    val userVerification: String? = null,
    val extensions: JsonObject? = null,
)

/** Authenticator data of a registration; [transports] feeds the server's `transports` column. */
@Serializable
public data class AttestationResponse(
    val clientDataJSON: String,
    val attestationObject: String,
    val transports: List<String>? = null,
)

/** `RegistrationResponseJSON` sent to `passkey/verify-registration`. */
@Serializable
public data class RegistrationResponse(
    val id: String,
    val rawId: String,
    val response: AttestationResponse,
    val authenticatorAttachment: String? = null,
    val clientExtensionResults: JsonObject = JsonObject(emptyMap()),
    val type: String = "public-key",
)

/** Signed assertion of an authentication. */
@Serializable
public data class AssertionResponse(
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String,
    val userHandle: String? = null,
)

/** `AuthenticationResponseJSON` sent to `passkey/verify-authentication`. */
@Serializable
public data class AuthenticationResponse(
    val id: String,
    val rawId: String,
    val response: AssertionResponse,
    val authenticatorAttachment: String? = null,
    val clientExtensionResults: JsonObject = JsonObject(emptyMap()),
    val type: String = "public-key",
)

/**
 * A passkey row of the server. [transports] is the server's comma-joined column (for example
 * `internal,hybrid`), not a list.
 */
@Serializable
public data class Passkey(
    val id: String,
    val name: String? = null,
    val userId: String,
    val credentialID: String,
    val deviceType: String? = null,
    val backedUp: Boolean? = null,
    val transports: String? = null,
    val createdAt: Instant? = null,
    val aaguid: String? = null,
)
