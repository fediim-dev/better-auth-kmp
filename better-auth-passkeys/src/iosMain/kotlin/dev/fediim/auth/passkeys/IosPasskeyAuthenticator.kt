@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.fediim.auth.passkeys

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttachment
import platform.AuthenticationServices.ASAuthorizationRequest
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Passkeys through `ASAuthorizationPlatformPublicKeyCredentialProvider` (iOS 16+). [anchor] returns
 * the window that hosts the system sheet. A dismissed sheet is `PasskeyException("cancelled")`,
 * anything else `PASSKEY_FAILED`.
 *
 * The app needs the Associated Domains entitlement `webcredentials:<rpID>` and the server must
 * serve the matching `apple-app-site-association` file (see the README). Not yet exercised on a
 * device: runtime confirmation is tracked in #10.
 */
public class IosPasskeyAuthenticator(private val anchor: () -> ASPresentationAnchor) :
    PasskeyAuthenticator {
  // The controller keeps its delegate and presentation provider weakly; hold them for the ceremony.
  private var active: List<Any>? = null

  override val capability: PasskeyCapability = PasskeyCapability.Supported

  override suspend fun create(options: PublicKeyCredentialCreationOptions): RegistrationResponse {
    val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(rpId(options.rp.id))
    val request =
        provider.createCredentialRegistrationRequestWithChallenge(
            challenge = Base64Url.decode(options.challenge).toNSData(),
            name = options.user.name,
            userID = Base64Url.decode(options.user.id).toNSData(),
        )
    val credential = perform(request) as? ASAuthorizationPlatformPublicKeyCredentialRegistration
    credential ?: throw PasskeyException("PASSKEY_FAILED", "unexpected credential type")
    val id = Base64Url.encode(credential.credentialID.toByteArray())
    return RegistrationResponse(
        id = id,
        rawId = id,
        response =
            AttestationResponse(
                clientDataJSON = Base64Url.encode(credential.rawClientDataJSON.toByteArray()),
                attestationObject =
                    Base64Url.encode(
                        (credential.rawAttestationObject
                                ?: throw PasskeyException("PASSKEY_FAILED", "no attestation"))
                            .toByteArray()
                    ),
            ),
        authenticatorAttachment = credential.attachment.wireName(),
    )
  }

  override suspend fun get(options: PublicKeyCredentialRequestOptions): AuthenticationResponse {
    val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(rpId(options.rpId))
    val request =
        provider.createCredentialAssertionRequestWithChallenge(
            Base64Url.decode(options.challenge).toNSData()
        )
    request.setAllowedCredentials(
        options.allowCredentials.map {
          ASAuthorizationPlatformPublicKeyCredentialDescriptor(Base64Url.decode(it.id).toNSData())
        }
    )
    val credential = perform(request) as? ASAuthorizationPlatformPublicKeyCredentialAssertion
    credential ?: throw PasskeyException("PASSKEY_FAILED", "unexpected credential type")
    val id = Base64Url.encode(credential.credentialID.toByteArray())
    return AuthenticationResponse(
        id = id,
        rawId = id,
        response =
            AssertionResponse(
                clientDataJSON = Base64Url.encode(credential.rawClientDataJSON.toByteArray()),
                authenticatorData =
                    Base64Url.encode(
                        (credential.rawAuthenticatorData
                                ?: throw PasskeyException(
                                    "PASSKEY_FAILED",
                                    "no authenticator data",
                                ))
                            .toByteArray()
                    ),
                signature =
                    Base64Url.encode(
                        (credential.signature
                                ?: throw PasskeyException("PASSKEY_FAILED", "no signature"))
                            .toByteArray()
                    ),
                userHandle = credential.userID?.let { Base64Url.encode(it.toByteArray()) },
            ),
        authenticatorAttachment = credential.attachment.wireName(),
    )
  }

  private fun rpId(id: String?): String =
      id ?: throw PasskeyException("PASSKEY_FAILED", "the server sent no relying party id")

  private suspend fun perform(request: ASAuthorizationRequest): Any =
      withContext(Dispatchers.Main) {
        try {
          suspendCancellableCoroutine { continuation ->
            val delegate = CompletionDelegate(continuation)
            val presenter = AnchorProvider(anchor)
            val controller = ASAuthorizationController(listOf(request))
            controller.delegate = delegate
            controller.presentationContextProvider = presenter
            active = listOf(controller, delegate, presenter)
            continuation.invokeOnCancellation { controller.cancel() }
            controller.performRequests()
          }
        } finally {
          active = null
        }
      }
}

private class CompletionDelegate(private val continuation: CancellableContinuation<Any>) :
    NSObject(), ASAuthorizationControllerDelegateProtocol {
  override fun authorizationController(
      controller: ASAuthorizationController,
      didCompleteWithAuthorization: ASAuthorization,
  ) {
    if (continuation.isActive)
        continuation.resumeWith(Result.success(didCompleteWithAuthorization.credential))
  }

  override fun authorizationController(
      controller: ASAuthorizationController,
      didCompleteWithError: NSError,
  ) {
    val error =
        if (didCompleteWithError.code == ASAuthorizationErrorCanceled) PasskeyException("cancelled")
        else PasskeyException("PASSKEY_FAILED", "authorization error ${didCompleteWithError.code}")
    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
  }
}

private class AnchorProvider(private val anchor: () -> ASPresentationAnchor) :
    NSObject(), ASAuthorizationControllerPresentationContextProvidingProtocol {
  override fun presentationAnchorForAuthorizationController(
      controller: ASAuthorizationController
  ): ASPresentationAnchor = anchor()
}

// The shared-metadata compile cannot see that these two entries are exhaustive.
@Suppress("REDUNDANT_ELSE_IN_WHEN")
private fun ASAuthorizationPublicKeyCredentialAttachment.wireName(): String? =
    when (this) {
      ASAuthorizationPublicKeyCredentialAttachment
          .ASAuthorizationPublicKeyCredentialAttachmentPlatform -> "platform"
      ASAuthorizationPublicKeyCredentialAttachment
          .ASAuthorizationPublicKeyCredentialAttachmentCrossPlatform -> "cross-platform"
      else -> null
    }

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData()
    else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray {
  val result = ByteArray(length.toInt())
  if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
  return result
}
