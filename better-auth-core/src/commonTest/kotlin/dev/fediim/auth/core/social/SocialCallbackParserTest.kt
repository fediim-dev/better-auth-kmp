package dev.fediim.auth.core.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SocialCallbackParserTest {
  private val base = "http://127.0.0.1:5555/auth/callback"

  @Test
  fun t5RedirectStateAndToken() {
    assertEquals(
        SocialCallback.OneTimeToken("o"),
        parseRedirectCallback("$base?state=abc&ott=o", "abc"),
    )
    assertNull(parseRedirectCallback("$base?state=zzz&ott=o", "abc"))
    assertNull(parseRedirectCallback("$base?ott=o", "abc"))
    assertNull(parseRedirectCallback("$base?state=&ott=o", ""))
    assertNull(parseRedirectCallback("$base?state=abc&ott=", "abc"))
    assertNull(parseRedirectCallback("$base?state=abc", "abc"))
    assertNull(parseRedirectCallback("not a url", "abc"))
    assertEquals(
        SocialCallback.Error("access_denied", "no way"),
        parseRedirectCallback(
            "$base?state=abc&error=access_denied&error_description=no%20way",
            "abc",
        ),
    )
    assertEquals(
        SocialCallback.Error("access_denied", null),
        parseRedirectCallback("$base?state=abc&error=access_denied", "abc"),
    )
    assertEquals(
        SocialCallback.OneTimeToken("a b+c"),
        parseRedirectCallback("myapp://cb?state=abc&ott=a%20b%2Bc", "abc"),
    )
  }

  private fun message(type: String = OAUTH_POPUP_MESSAGE_TYPE, nonce: String = "n", rest: String) =
      """{"type":"$type","nonce":"$nonce",$rest}"""

  @Test
  fun t5PopupMessage() {
    val origin = "https://example.com"
    val ok = message(rest = """"token":"t.s"""")
    assertEquals(SocialCallback.SessionToken("t.s"), parsePopupMessage(origin, ok, origin, "n"))
    assertEquals(
        SocialCallback.Error("access_denied", "d"),
        parsePopupMessage(
            origin,
            message(rest = """"error":{"code":"access_denied","description":"d"}"""),
            origin,
            "n",
        ),
    )
    for (bad in
        listOf(
            "https://example.com.evil.com",
            "http://example.com",
            "https://example.com:8443",
            "https://example.com/",
            "null",
            "",
        )) {
      assertNull(parsePopupMessage(bad, ok, origin, "n"), bad)
    }
    assertNull(
        parsePopupMessage(origin, message(type = "other", rest = """"token":"t""""), origin, "n")
    )
    assertNull(parsePopupMessage(origin, ok, origin, "other"))
    assertNull(parsePopupMessage(origin, "not json", origin, "n"))
    assertNull(parsePopupMessage(origin, null, origin, "n"))
    assertNull(parsePopupMessage(origin, message(rest = """"token":" """"), origin, "n"))
  }

  @Test
  fun t5ToStringOmitsTokens() {
    assertFalse("secret" in SocialCallback.SessionToken("secret").toString())
    assertFalse("secret" in SocialCallback.OneTimeToken("secret").toString())
  }

  @Test
  fun helpers() {
    assertEquals("$base?x=1&state=s", withState("$base?x=1", "s"))
    assertEquals("$base?state=s#f", withState("$base#f", "s"))
    assertEquals("myapp:", originOf("myapp://cb/path?q=1"))
    assertEquals("https://a.b", originOf("HTTPS://A.b:443/x"))
    assertNull(originOf("::bad"))
  }
}
