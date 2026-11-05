package dev.fediim.auth.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BetterAuthConfigTest {
  @Test
  fun t9AcceptedUrls() {
    BetterAuthConfig("http://example.com", allowInsecureHttpForTesting = true)
    BetterAuthConfig("https://example.com")
    BetterAuthConfig("HTTPS://Example.com")
    BetterAuthConfig("http://localhost:3000")
    BetterAuthConfig("http://127.0.0.1:8080")
    BetterAuthConfig("http://[::1]:3000")
    assertEquals("https://example.com", BetterAuthConfig("https://example.com/").baseUrl)
  }

  @Test
  fun t9RejectedUrls() {
    for (bad in
        listOf(
            "http://example.com",
            "http://localhost.evil.com",
            "http://localhost@evil.com",
            "https://",
            "example.com",
            "",
            "not a url",
            "https://example.com?x=",
            "https://example.com/#frag",
        )) {
      assertFailsWith<IllegalArgumentException>(bad) { BetterAuthConfig(bad) }
    }
    assertFailsWith<IllegalArgumentException> {
      BetterAuthConfig("ftp://example.com", allowInsecureHttpForTesting = true)
    }
  }

  @Test
  fun t9ToStringOmitsApiKey() {
    assertFalse(
        "key-secret" in BetterAuthConfig("https://example.com", apiKey = "key-secret").toString()
    )
  }

  @Test
  fun originIsNormalized() {
    assertEquals("https://example.com", BetterAuthConfig("HTTPS://Example.com:443/").origin)
    assertEquals("http://127.0.0.1:3000", BetterAuthConfig("http://127.0.0.1:3000").origin)
    assertEquals("http://[::1]:3000", BetterAuthConfig("http://[::1]:3000").origin)
  }
}
