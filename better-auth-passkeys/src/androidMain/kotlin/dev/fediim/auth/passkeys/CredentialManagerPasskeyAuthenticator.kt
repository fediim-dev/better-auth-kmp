package dev.fediim.auth.passkeys

import android.app.Activity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

private val wireJson = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
  encodeDefaults = true
}

/**
 * Passkeys through Android's Credential Manager (`androidx.credentials`). [activity] supplies the
 * Activity that hosts the system sheet on every call. A dismissed sheet is
 * `PasskeyException("cancelled")`, a missing credential `no_credential`, anything else
 * `PASSKEY_FAILED`.
 *
 * The app and server must be linked for the relying party id: Digital Asset Links on the server and
 * an `android:apk-key-hash:` origin accepted by the passkey plugin (see the README).
 */
public class CredentialManagerPasskeyAuthenticator
internal constructor(
    private val createJson: suspend (requestJson: String) -> String,
    private val getJson: suspend (requestJson: String) -> String,
) : PasskeyAuthenticator {
  public constructor(
      activity: () -> Activity
  ) : this(
      createJson = { requestJson ->
        val host = activity()
        val response =
            CredentialManager.create(host)
                .createCredential(host, CreatePublicKeyCredentialRequest(requestJson))
        (response as CreatePublicKeyCredentialResponse).registrationResponseJson
      },
      getJson = { requestJson ->
        val host = activity()
        val response =
            CredentialManager.create(host)
                .getCredential(
                    host,
                    GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson))),
                )
        (response.credential as PublicKeyCredential).authenticationResponseJson
      },
  )

  override val capability: PasskeyCapability = PasskeyCapability.Supported

  override suspend fun create(options: PublicKeyCredentialCreationOptions): RegistrationResponse {
    val request = wireJson.encodeToString(PublicKeyCredentialCreationOptions.serializer(), options)
    return wireJson.decodeFromString(
        RegistrationResponse.serializer(),
        mapErrors { createJson(request) },
    )
  }

  override suspend fun get(options: PublicKeyCredentialRequestOptions): AuthenticationResponse {
    val request = wireJson.encodeToString(PublicKeyCredentialRequestOptions.serializer(), options)
    return wireJson.decodeFromString(
        AuthenticationResponse.serializer(),
        mapErrors { getJson(request) },
    )
  }

  private suspend fun mapErrors(block: suspend () -> String): String =
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: CreateCredentialCancellationException) {
        throw PasskeyException("cancelled", cause = e)
      } catch (e: GetCredentialCancellationException) {
        throw PasskeyException("cancelled", cause = e)
      } catch (e: NoCredentialException) {
        throw PasskeyException("no_credential", cause = e)
      } catch (e: Exception) {
        throw PasskeyException("PASSKEY_FAILED", cause = e)
      }
}
