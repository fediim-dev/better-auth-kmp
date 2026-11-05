package dev.fediim.auth.passkeys

import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.KSerializer

private fun pageCredentials(): JsAny? =
    js("typeof navigator !== 'undefined' && navigator.credentials ? navigator.credentials : null")

// Resolves with the JSON envelope read by runBrowserCeremony; never rejects.
private fun callCredentials(
    credentials: JsAny?,
    kind: String,
    optionsJson: String,
): Promise<String> =
    js(
        """(function () {
  function decode(s) {
    var p = s.replace(/-/g, "+").replace(/_/g, "/");
    var bin = atob(p + "=".repeat((4 - (p.length % 4)) % 4));
    var u = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
    return u.buffer;
  }
  function encode(b) {
    var u = new Uint8Array(b);
    var s = "";
    for (var i = 0; i < u.length; i++) s += String.fromCharCode(u[i]);
    return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+/g, "");
  }
  function fail(e) {
    return JSON.stringify({ error: e && e.name === "NotAllowedError" ? "cancelled" : "PASSKEY_FAILED" });
  }
  function ids(list) {
    return list.map(function (c) { return Object.assign({}, c, { id: decode(c.id) }); });
  }
  function answer(c) {
    var r = c.response;
    var response = kind === "create"
      ? { clientDataJSON: encode(r.clientDataJSON), attestationObject: encode(r.attestationObject),
          transports: typeof r.getTransports === "function" ? r.getTransports() : undefined }
      : { clientDataJSON: encode(r.clientDataJSON), authenticatorData: encode(r.authenticatorData),
          signature: encode(r.signature), userHandle: r.userHandle ? encode(r.userHandle) : undefined };
    return JSON.stringify({ ok: {
      id: c.id, rawId: encode(c.rawId), type: c.type, response: response,
      authenticatorAttachment: c.authenticatorAttachment || undefined,
      clientExtensionResults: typeof c.getClientExtensionResults === "function" ? c.getClientExtensionResults() : {}
    } });
  }
  try {
    var o = JSON.parse(optionsJson);
    o.challenge = decode(o.challenge);
    if (o.user) o.user.id = decode(o.user.id);
    if (o.excludeCredentials) o.excludeCredentials = ids(o.excludeCredentials);
    if (o.allowCredentials) o.allowCredentials = ids(o.allowCredentials);
    var call = kind === "create" ? credentials.create({ publicKey: o }) : credentials.get({ publicKey: o });
    return Promise.resolve(call).then(answer).catch(fail);
  } catch (e) {
    return Promise.resolve(fail(e));
  }
})()"""
    )

/**
 * Passkeys through `navigator.credentials` (WebAuthn) in the browser. `NotAllowedError` (dismissed
 * prompt or timeout) becomes `PasskeyException("cancelled")`, any other failure `PASSKEY_FAILED`.
 * [capability] is [PasskeyCapability.Supported] when `navigator.credentials` and
 * `PublicKeyCredential` exist; WebAuthn needs a secure context (https or localhost).
 *
 * Challenge cookies are carried by the browser's own cookie jar, so the auth server must be
 * same-origin with the page (or CORS with credentials, SameSite=None): see the README.
 */
public class BrowserPasskeyAuthenticator internal constructor(private val credentials: JsAny?) :
    PasskeyAuthenticator {
  /** Uses the page's `navigator.credentials`. */
  public constructor() : this(pageCredentials())

  override val capability: PasskeyCapability
    get() =
        if (credentials != null && publicKeyCredentialDefined()) PasskeyCapability.Supported
        else PasskeyCapability.Unsupported

  override suspend fun create(options: PublicKeyCredentialCreationOptions): RegistrationResponse =
      ceremony(
          "create",
          options,
          PublicKeyCredentialCreationOptions.serializer(),
          RegistrationResponse.serializer(),
      )

  override suspend fun get(options: PublicKeyCredentialRequestOptions): AuthenticationResponse =
      ceremony(
          "get",
          options,
          PublicKeyCredentialRequestOptions.serializer(),
          AuthenticationResponse.serializer(),
      )

  private suspend fun <O, R> ceremony(
      kind: String,
      options: O,
      optionsSerializer: KSerializer<O>,
      responseSerializer: KSerializer<R>,
  ): R {
    if (capability == PasskeyCapability.Unsupported) throw PasskeyException("PASSKEY_UNSUPPORTED")
    return runBrowserCeremony(kind, options, optionsSerializer, responseSerializer) { k, json ->
      callCredentials(credentials, k, json).await()
    }
  }
}

private fun publicKeyCredentialDefined(): Boolean = js("typeof PublicKeyCredential !== 'undefined'")
