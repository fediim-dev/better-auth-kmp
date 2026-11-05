package dev.fediim.auth.passkeys

import kotlin.io.encoding.Base64

/** Unpadded base64url (RFC 4648 section 5), the encoding of every binary WebAuthn JSON field. */
internal object Base64Url {
  private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

  fun encode(bytes: ByteArray): String = codec.encode(bytes)

  /** Accepts padded and unpadded input; anything else throws [IllegalArgumentException]. */
  fun decode(text: String): ByteArray = codec.decode(text)
}
