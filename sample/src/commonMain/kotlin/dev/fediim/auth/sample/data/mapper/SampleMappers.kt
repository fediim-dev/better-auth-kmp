package dev.fediim.auth.sample.data.mapper

import dev.fediim.auth.core.model.AuthUser
import dev.fediim.auth.core.model.BetterAuthResult
import dev.fediim.auth.core.model.SessionEnvelope
import dev.fediim.auth.passkeys.Passkey
import dev.fediim.auth.sample.domain.model.AccountProfile
import dev.fediim.auth.sample.domain.model.PasskeyItem
import dev.fediim.auth.sample.domain.model.SessionOverview
import dev.fediim.auth.sample.domain.model.maskSessionToken

/** Mapping functions converting SDK models into presentation-safe domain models. */
public object SampleMappers {
  public fun toAccountProfile(user: AuthUser): AccountProfile =
      AccountProfile(
          id = user.id,
          email = user.email,
          name = user.name,
          emailVerified = user.emailVerified,
          additionalFields = user.additionalFields,
      )

  public fun toSessionOverview(
      envelope: SessionEnvelope,
      activeOrgId: String? = null,
  ): SessionOverview =
      SessionOverview(
          sessionId = envelope.session.id,
          userId = envelope.session.userId,
          maskedToken = maskSessionToken(envelope.session.token),
          expiresAtIso = envelope.session.expiresAt?.toString(),
          activeOrganizationId = activeOrgId,
          user = toAccountProfile(envelope.user),
          sessionAdditionalFields = envelope.session.additionalFields,
      )

  public fun toPasskeyItem(passkey: Passkey): PasskeyItem =
      PasskeyItem(
          id = passkey.id,
          name = passkey.name ?: passkey.id,
          credentialId = passkey.credentialID,
          deviceType = passkey.deviceType,
          backedUp = passkey.backedUp == true,
          transports = passkey.transports,
      )

  public fun <T> toResult(result: BetterAuthResult<T>): Result<T> =
      when (result) {
        is BetterAuthResult.Success -> Result.success(result.value)
        is BetterAuthResult.Failure ->
            Result.failure(Exception(result.error.message ?: result.error.code))
        is BetterAuthResult.RateLimited ->
            Result.failure(Exception(result.error.message ?: result.error.code))
        is BetterAuthResult.NetworkError -> Result.failure(result.cause)
      }
}
