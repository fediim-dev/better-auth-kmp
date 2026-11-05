# better-auth-kmp
Kotlin Multiplatform client SDK and Compose/FDS UI for Better Auth

## Modules

Group `dev.fediim.auth`, version `0.1.0-SNAPSHOT`; targets jvm, android, iosArm64, iosSimulatorArm64, js, wasmJs.

| Artifact | Depends on |
|---|---|
| `dev.fediim.auth:better-auth-core` | none |
| `dev.fediim.auth:better-auth-passkeys` | core |
| `dev.fediim.auth:better-auth-compose` | core, passkeys, Compose Runtime |
| `dev.fediim.auth:better-auth-fds` | compose, core, passkeys, FDS (`dev.fediim.fds` 0.1.4-SNAPSHOT) |

## better-auth-core

```kotlin
val client = BetterAuthClient(
  BetterAuthConfig("https://auth.example.com"),
  SettingsAuthTokenStorage(settings), // a multiplatform-settings `Settings`; one instance per store
)
when (val result = client.signInEmail(SignInEmailRequest("a@b.com", "password"))) {
  is BetterAuthResult.Success -> result.value.user
  is BetterAuthResult.Failure, is BetterAuthResult.RateLimited, is BetterAuthResult.NetworkError -> Unit
}
```

- `baseUrl` must be `https`; `http` is accepted only for `localhost`, `127.0.0.1`, `::1`, or with `allowInsecureHttpForTesting = true`. Anything else throws `IllegalArgumentException`.
- `signOut(onLocalStateCleared)` runs the callback once after the token, cached organization id and challenge cookies are cleared and before the revoke request, also when no token was stored; if it throws, the revoke is still sent and the exception is rethrown afterwards.
- Requests send `Authorization: Bearer <token>`; the token is read from the `set-auth-token` response header (body `token` as fallback for sign-in/sign-up) and is dropped if `signOut()` ran meanwhile. A `set-auth-token` on a failed sign-in is ignored. See CONSTRAINTS C5.
- Server fields the models do not declare are kept in `additionalFields` (CONSTRAINTS C4).
- `SettingsAuthTokenStorage` stores the token as plaintext in the platform settings store; implement `AuthTokenStorage` for keystore-backed storage.

### Organizations and API keys

`client.organizations` and `client.apiKeys` cover the `organization()` and `apiKey()` plugins; every call returns a `BetterAuthResult`:

```kotlin
client.organizations.create(CreateOrganizationRequest("Acme", "acme"))
client.organizations.setActive("org-id") // setActive(null) clears it
val created = client.apiKeys.create(CreateApiKeyRequest(name = "ci"))
```

- The client caches the active organization id in `AuthTokenStorage` (cleared with the token by `signOut()`). It is a convenience that mirrors this client's own calls only; the server session is authoritative. After a 403 not-a-member the server clears its active organization while the cache keeps the previous id. The cache is not updated by signing in as another user, server-side removal or other devices: call `signOut()` before switching accounts (`BetterAuthViewModel` does this before every sign-in) and confirm with `getSession()` before authorization-relevant UI.
- A custom `AuthTokenStorage` must override `activeOrganizationId()` and `writeActiveOrganizationIdIfEpoch()` to persist it (existing implementations still compile and run on the JVM; they just cache nothing), and its `clear()` must also remove the id.
- The raw API key is returned only in `CreatedApiKey.key`; showing it once is the app's job. Records from `list`/`get`/`update` never carry it.
- Server requirements: `apiKey({ enableMetadata: true })` for metadata, and `enableSessionForAPIKeys: true` to authenticate with `BetterAuthConfig.apiKey` (`X-API-Key`). That option makes `X-API-Key` a full session of the key owner on every endpoint, overriding any Bearer token (including creating more keys and managing organizations), and the key's rate limit (default 10 per day) counts every request: do not combine `BetterAuthConfig.apiKey` with user sign-in, and never ship a key in a distributed client. The contract server enables it for tests only. Rate-limit and permission fields are server-only and read-only here. See CONSTRAINTS C4 and C5.

### Two-factor

`client.twoFactor` covers the `twoFactor()` plugin. A sign-in that needs a second factor is a `Success` whose body carries `twoFactorRedirect`; read it with `signInResult`:

```kotlin
when (val outcome = client.signInEmail(request)) {
  is BetterAuthResult.Success ->
    when (val step = outcome.value.signInResult) {
      is SignInResult.SignedIn -> step.response.user
      is SignInResult.TwoFactorRequired -> client.twoFactor.verifyTotp(codeFromUser) // or verifyBackupCode
    }
  else -> Unit
}
```

- Enrol with `enable(password)`; it only prepares the secret. Two-factor becomes active after a `verifyTotp` with a code from it, called while signed in (this also rotates the session). `TotpEnrollment.totpUri` and the backup codes are credentials and never appear in `toString()`.
- **Challenge mode.** After a `TwoFactorRequired` sign-in, `verifyTotp`/`verifyBackupCode` send no `Authorization` header (a stored token of another user must not take over) and replay the signed `two_factor` challenge cookie the sign-in captured. The new session token is stored from `set-auth-token`. A wrong code keeps the challenge (the server allows 5 attempts within 10 minutes); success, the next `signInEmail` or `signOut()` end it. The challenge lives in memory only: if a sign-in is abandoned it stays pending until the next `signInEmail`/`signOut()`, and a signed-in `verifyTotp` (enrolment) fails in that time.
- The sign-in response of a challenged account carries a `set-auth-token` for a session the server deletes; the SDK ignores it and stores no token until the second factor succeeds.
- Not exposed: `trustDevice`, OTP send/verify, `disableSession`.
- Browsers (js, wasmJs) cannot read `Set-Cookie` or set `Cookie`; the browser's own cookie jar carries the challenge. That works when the auth server is same-origin with the page, or with CORS credentials, `SameSite=None` and a `fetch` that includes credentials. Cross-origin setups are documented, not covered by the test suite.
- The SDK adds `Origin: <baseUrl origin>` to the one request that replays a challenge cookie, because Better Auth rejects a cookie-bearing POST without a trusted origin; it is accepted when it equals the origin of the server's own `baseURL` or is listed in its `trustedOrigins`. Behind a reverse proxy or another public host, add the client's `baseUrl` origin to `trustedOrigins`, or the 2FA and passkey verify calls fail with 403 `INVALID_ORIGIN`.
- On iOS the Darwin engine disables cookie storage by default, so the SDK's replay is the only cookie sent; runtime confirmation is tracked in #10.

### Social sign-in

Pass a launcher to the client and call `signInSocial`; every outcome is a `BetterAuthResult` (user denial and timeouts are `Failure(0, error.code)`, see the KDoc of `signInSocial`):

```kotlin
val client = BetterAuthClient(config, storage, socialAuthLauncher = JvmLoopbackSocialAuthLauncher())
client.signInSocial("github", scopes = listOf("read:user"))
```

| Platform | Launcher | Notes |
|---|---|---|
| JVM | `JvmLoopbackSocialAuthLauncher()` | one-shot server on `127.0.0.1:<random port>/auth/callback`, closed when the flow ends |
| Android | `AndroidCustomTabsSocialAuthLauncher(context, "myapp://auth/callback")` | the Activity owning that intent filter calls `AndroidSocialAuthCallbacks.handle(intent.data?.toString())` from `onCreate`/`onNewIntent` |
| iOS | `IosWebAuthSocialAuthLauncher("myapp", anchor = { window })` | `ASWebAuthenticationSession`, iOS 17.4+, receiver `myapp://auth/callback`; runtime check pending (#10) |
| js / wasmJs | `WebPopupSocialAuthLauncher()` | popup + `postMessage`; if the popup is blocked the page redirects, then call `consumeRedirectCallback()` on load, pass the result to `client.completeSocialSignIn(...)` and remove `ott`/`state` with `history.replaceState` |

Server requirements (better-auth 1.7.17, see `tests/contract-server/server.mjs` for a working setup):

- Plugins `bearer()`, `oneTimeToken({ setOttHeaderOnNewSession: true })`, `oauthPopup()` (web), and a small plugin that appends `ott=<set-ott header>` to the `/callback/*` redirect. `set-ott` is lost on a browser redirect, which is why the plugin is needed. The plugin must come after `oneTimeToken(...)` in `plugins` because after-hooks run in array order (`api/dispatch.mjs:151-168`). It also strips `set-ott` from every response. It must append `ott` only for receivers on its own allowlist, never for every trusted origin: for http(s) match the parsed origin (loopback: host `127.0.0.1`, any port) and the exact normalized path, for custom schemes the scheme. A raw prefix check lets `/landingX` or `/landing/../x` through. Never accept `http`/`https` as a scheme entry.
- The web redirect fallback's receiver (the current page without its query, or a same-origin `callbackUrl`) must be on that allowlist too. Pass your SDK landing page (for example `${baseURL}/sdk-landing`) as `callbackUrl`, and put only that exact path on the allowlist.
- `account: { skipStateCookieCheck: true }` with the default database state strategy. The browser that finishes the flow is not the HTTP client that started it, so the state cookie cannot be checked; the SDK compares its own `state` instead. See Security below.
- `trustedOrigins` must contain every receiver origin and, for the popup flow, the page origin (`popupOrigin`).

Security notes ([CONSTRAINTS.md C6](CONSTRAINTS.md)):

- `skipStateCookieCheck` is server-wide. It removes login-CSRF protection for any cookie-session web frontend on the same instance and enables account-linking CSRF through POST `/link-social`. Set `disabledPaths: ["/link-social"]` unless linking is needed, and then only with `allowDifferentEmails` false and email verification required.
- The `http://127.0.0.1:*` entry in `trustedOrigins` makes every local port a trusted `popupOrigin` (`oauth-popup/index.mjs:112-113`). Add a before-hook rejecting loopback `popupOrigin` on `/oauth-popup/start`, or add the wildcard only on deployments that ship the desktop client.
- Call `history.replaceState` synchronously right after `consumeRedirectCallback()` returns, before any `await`, whether or not it returned null. The landing page needs `Referrer-Policy: no-referrer` and no third-party scripts.
- With custom-scheme receivers, the app must not be claimable by other apps; prefer Android App Links / iOS universal links. iOS `ASWebAuthenticationSession` delivers the callback to the session only and is unaffected.

## better-auth-passkeys

```kotlin
val passkeys = BetterAuthPasskeyClient(client, CredentialManagerPasskeyAuthenticator { activity })
passkeys.registerPasskey(name = "Pixel")   // signed in, fresh session
passkeys.signInWithPasskey()               // stores the session token like signInEmail
passkeys.listPasskeys()
passkeys.deletePasskey(id)
```

A local failure is `BetterAuthResult.Failure(0, error.code)` with code `PASSKEY_UNSUPPORTED` (nothing is sent), `cancelled`, `no_credential` or `PASSKEY_FAILED`.

| Platform | Authenticator | Notes |
|---|---|---|
| js / wasmJs | `BrowserPasskeyAuthenticator()` | `navigator.credentials`, needs https or localhost; challenge cookies use the browser jar (same-origin, see Two-factor above) |
| Android | `CredentialManagerPasskeyAuthenticator { activity }` | `androidx.credentials` 1.6.0; Digital Asset Links for the server's `rpID`, and the passkey plugin must accept the app's `android:apk-key-hash:<hash>` origin |
| iOS | `IosPasskeyAuthenticator(anchor = { window })` | `ASAuthorizationPlatformPublicKeyCredentialProvider`, iOS 16+; Associated Domains `webcredentials:<rpID>` and an `apple-app-site-association` file on the server; runtime check pending (#10) |
| JVM | `JvmPasskeyAuthenticator(hook = null)` | unsupported without a `hook` authenticator |

Server: `passkey({ rpID, origin })` with `rpID` the domain the app is linked to and `origin` (a string or list) containing every origin a ceremony runs from. Better Auth keeps the challenge in a signed cookie (`<prefix>.better-auth-passkey`, 5 minutes); the SDK keeps it in memory and replays it once on the verify call (CONSTRAINTS C5). The live test suite covers the options call and negative verifies; a full ceremony needs a real authenticator.

## better-auth-compose

Headless state holders (no `@Composable`, no Compose compiler plugin; `@Immutable` from Compose Runtime marks the state classes). Create them with the scope of your screen or app and call `close()` when it ends:

```kotlin
val auth = BetterAuthViewModel(client, scope, passkeys = passkeyClient, onClearUserCache = { cache.clear() })
val organizations = OrganizationViewModel(client, scope)
val apiKeys = ApiKeyViewModel(client, scope)

auth.dispatch(AuthUiIntent.EmailChanged("a@b.com"))
auth.dispatch(AuthUiIntent.PasswordChanged("password"))
auth.dispatch(AuthUiIntent.SubmitAuth)
// in a composable: val state by auth.state.collectAsState()
```

- `state` is a `StateFlow` and `effects` a `Flow`; turning them into Compose state with `collectAsState()` is up to the app. `effects` supports one collector, buffers 64 and drops the oldest; state is the source of truth.
- `dispatch` is not thread-safe: call it on `scope`'s single thread (for example `Dispatchers.Main`). Clearing local state inside `Logout` before `dispatch` returns holds with `SettingsAuthTokenStorage`; a custom storage that suspends in `token()`/`clear()` delays it.
- **Re-login.** `SubmitAuth`, `SignInSocial` and `SignInPasskey` first sign out the previous session: its token is revoked with its own Bearer, local state is cleared and `onClearUserCache` runs, on every attempt and also for the same user. A failed re-login therefore leaves the user signed out. A failed revoke is reported as the `SessionRevocationFailed` effect and the sign-in continues. `onClearUserCache` runs after the token is cleared and before the revoke request; if it throws, `UserCacheClearFailed` is emitted and the sign-out goes on.
- **Logout** cancels the running attempt, resets the state at once and is ignored while another Logout runs; auth intents are ignored while it runs. A revoke is never cancelled by Logout or `close()`, so a hung revoke outlives `close()` until the HTTP client times out.
- **Passwords** are never part of the state: the view model keeps the last `PasswordChanged` privately and clears it after sign-in and on Logout. Registration is blocked (`PASSWORD_TOO_SHORT`) below 8 characters; `passwordStrength` is 0..4.
- **Lockout.** A 429 with a positive `Retry-After` sets `isLockedOut` and counts `lockoutSecondsRemaining` down on `scope`'s clock. It blocks `SubmitAuth`, `SubmitTwoFactor`, `SignInSocial` and `SignInPasskey` (not `RestoreSession` or `Logout`) and survives Logout.
- `OrganizationViewModel` and `ApiKeyViewModel` have no single-flight guard (the last response wins); send `Clear` from `onClearUserCache` so a new user never sees the old user's lists or the one-time `createdKey`.
- iOS compiles but the module's tests run on jvm, js, wasmJs and the Android host only; the iOS runtime check is tracked in #10.

## better-auth-fds

Compose UI built only from [FDS](https://github.com/fediim-dev/fediim-design-system) components and tokens over the compose view models. Everything must sit inside `FediimTheme { }` (the components read its tokens and never wrap their own theme).

```kotlin
FediimTheme {
  val scope = rememberCoroutineScope()
  val client = remember { BetterAuthClient(config, storage, socialAuthLauncher = launcher) }
  val auth = remember { BetterAuthViewModel(client, scope, passkeyClient) }
  val organizations = remember { OrganizationViewModel(client, scope) }
  DisposableEffect(auth, organizations) {
    onDispose {
      auth.close()
      organizations.close()
    }
  }

  val state by auth.state.collectAsState()
  if (!state.isSignedIn) {
    BetterAuthDialog(
      auth,
      onDismissRequest = { /* hide the dialog */ },
      passkeyCapability = authenticator.capability,
      // default providers need the `socialAuthLauncher` above; pass `socialProviders = emptyList()` if the client has none
    )
  } else {
    OrganizationSwitcher(organizations, currentUserId = state.user?.id)
  }
}
```

- Components: `BetterAuthDialog` (modal) and `BetterAuthPanel` (embeddable form: sign in / register, password checklist and strength meter, social, passkey, the two-factor code step); `TwoFactorDialog` (the second sign-in step as its own modal, for apps with their own sign-in form; do not compose it next to `BetterAuthDialog`); `TwoFactorSetupDialog` (enrolment over `client.twoFactor`: URI and backup codes in copyable code blocks, no QR code); `PasskeySignInButton`; `OrganizationSwitcher`; `ApiKeyManagerDialog`.
- Dialogs never close themselves: stop composing them (for example on `state.isSignedIn`) and handle `onDismissRequest` (Esc). Components never collect `BetterAuthViewModel.effects` (one collector only), so you can.
- Social buttons call `SignInSocial`, which fails with `UNEXPECTED` when the client has no `socialAuthLauncher`: pass `socialProviders = emptyList()` then.
- Labels are English only. FDS 0.1.4 has no icon set, so buttons and banners are text-only.
- Consumers also need the FDS repository `https://maven.pkg.github.com/fediim-dev/fediim-design-system` (same `gpr.user`/`gpr.key` credentials) and the Compose compiler plugin; until FDS 0.1.4-SNAPSHOT is republished to GitHub Packages (it returns 404 today, plan #8 L29), consumers can only resolve it from `mavenLocal()`.
- This module's own build resolves FDS from `mavenLocal()` first (`settings.gradle.kts`); remove that before any release.
- Secrets are never in saved state and are dropped when the composition is disposed, including on an Android configuration change, by design: the password and the TOTP URI and backup codes live in composition memory; a new API key lives in `ApiKeyUiState.createdKey` (the view model's state) and `ApiKeyManagerDialog` dispatches `DismissCreatedKey` when it leaves composition. Set `FLAG_SECURE` or the platform equivalent in the host app if you need screenshot protection.
- Screenshot tests (`FdsScreenshotTest`, Roborazzi) verify 12 goldens in `better-auth-fds/screenshots/` on macOS only; re-record with `./gradlew :better-auth-fds:jvmTest -Proborazzi.test.record=true`.

## Consuming from GitHub Packages

```kotlin
repositories {
  maven {
    url = uri("https://maven.pkg.github.com/fediim-dev/better-auth-kmp")
    credentials {
      username = providers.gradleProperty("gpr.user").orNull
      password = providers.gradleProperty("gpr.key").orNull
    }
  }
}
```

`gpr.key` is a GitHub token with `read:packages` (in `~/.gradle/gradle.properties`).

## Build

- `./gradlew check` builds and tests every target (iOS tests need macOS; browser tests need Chrome).
- `jvmTest` (hence `check`) also runs the live contract suite against the Node better-auth server in [tests/contract-server](tests/contract-server); it needs Node 22.13+ and npm on `PATH`, and the `npmCi` task installs the pinned dependencies. better-auth-passkeys' `jvmTest` runs against the same server through the shared harness in [tests/contract-harness](tests/contract-harness). The harness starts Node with only `PATH`, `HOME`, the temp-dir variables, `LANG` and `SystemRoot` from the environment, plus `BETTER_AUTH_TELEMETRY=false`.
- `./gradlew publishToMavenLocal` installs into `~/.m2` without credentials.
- `./gradlew publishAllPublicationsToGitHubPackagesRepository` publishes; credentials from `gpr.user`/`gpr.key` or `GITHUB_ACTOR`/`GITHUB_TOKEN`.
- CI publishes on push to `main` (snapshots) and on `v*` tags. For a release, drop `-SNAPSHOT` from `version` in `gradle.properties` (and `VERSION` in each module) before tagging.

See [CONSTRAINTS.md](CONSTRAINTS.md) for architecture rules and [ai-context.md](ai-context.md) for the project map.
