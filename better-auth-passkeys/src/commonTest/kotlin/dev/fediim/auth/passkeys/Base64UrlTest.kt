package dev.fediim.auth.passkeys

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class Base64UrlTest {
  @Test
  fun roundTripsAndUsesTheUrlAlphabetWithoutPadding() {
    for (length in listOf(0, 1, 2, 3, 4, 32)) {
      val bytes = ByteArray(length) { (it * 37 + 11).toByte() }
      val text = Base64Url.encode(bytes)
      assertFalse(text.any { it == '=' || it == '+' || it == '/' }, text)
      assertContentEquals(bytes, Base64Url.decode(text))
    }
    val edge = byteArrayOf(0xFB.toByte(), 0xFF.toByte())
    assertEquals("-_8", Base64Url.encode(edge))
    assertContentEquals(edge, Base64Url.decode("-_8"))
    val clientData =
        """{"type":"webauthn.get","origin":"https://exämple.test"}""".encodeToByteArray()
    assertContentEquals(clientData, Base64Url.decode(Base64Url.encode(clientData)))
  }

  @Test
  fun paddedInputDecodesAndInvalidInputIsRejected() {
    assertContentEquals("a".encodeToByteArray(), Base64Url.decode("YQ=="))
    assertContentEquals("a".encodeToByteArray(), Base64Url.decode("YQ"))
    assertFailsWith<IllegalArgumentException> { Base64Url.decode("a*b") }
  }
}
