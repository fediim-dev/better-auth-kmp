# better-auth-kmp
Kotlin Multiplatform client SDK and Compose/FDS UI for Better Auth

## Modules

Group `dev.fediim.auth`, version `0.1.0-SNAPSHOT`; targets jvm, android, iosArm64, iosSimulatorArm64, js, wasmJs.

| Artifact | Depends on |
|---|---|
| `dev.fediim.auth:better-auth-core` | none |
| `dev.fediim.auth:better-auth-passkeys` | core |
| `dev.fediim.auth:better-auth-compose` | core, passkeys |
| `dev.fediim.auth:better-auth-fds` | compose, core, passkeys |

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
- Requests send `Authorization: Bearer <token>`; the token is read from the `set-auth-token` response header (body `token` as fallback for sign-in/sign-up) and is dropped if `signOut()` ran meanwhile. See CONSTRAINTS C5.
- Server fields the models do not declare are kept in `additionalFields` (CONSTRAINTS C4).
- `SettingsAuthTokenStorage` stores the token as plaintext in the platform settings store; implement `AuthTokenStorage` for keystore-backed storage.

### Organizations and API keys

`client.organizations` and `client.apiKeys` cover the `organization()` and `apiKey()` plugins; every call returns a `BetterAuthResult`:

```kotlin
client.organizations.create(CreateOrganizationRequest("Acme", "acme"))
client.organizations.setActive("org-id") // setActive(null) clears it
val created = client.apiKeys.create(CreateApiKeyRequest(name = "ci"))
```

- The client caches the active organization id in `AuthTokenStorage` (cleared with the token by `signOut()`). It is a convenience that mirrors this client's own calls only; the server session is authoritative. After a 403 not-a-member the server clears its active organization while the cache keeps the previous id.
- A custom `AuthTokenStorage` must override `activeOrganizationId()` and `writeActiveOrganizationIdIfEpoch()` to persist it (existing implementations still compile and run on the JVM; they just cache nothing), and its `clear()` must also remove the id.
- The raw API key is returned only in `CreatedApiKey.key`; showing it once is the app's job. Records from `list`/`get`/`update` never carry it.
- Server requirements: `apiKey({ enableMetadata: true })` for metadata, and `enableSessionForAPIKeys: true` to authenticate with `BetterAuthConfig.apiKey` (`X-API-Key`). Rate-limit and permission fields are server-only and read-only here. See CONSTRAINTS C4 and C5.

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
- `jvmTest` (hence `check`) also runs the live contract suite against the Node better-auth server in [tests/contract-server](tests/contract-server); it needs Node 22.13+ and npm on `PATH`, and the `npmCi` task installs the pinned dependencies.
- `./gradlew publishToMavenLocal` installs into `~/.m2` without credentials.
- `./gradlew publishAllPublicationsToGitHubPackagesRepository` publishes; credentials from `gpr.user`/`gpr.key` or `GITHUB_ACTOR`/`GITHUB_TOKEN`.
- CI publishes on push to `main` (snapshots) and on `v*` tags. For a release, drop `-SNAPSHOT` from `version` in `gradle.properties` (and `VERSION` in each module) before tagging.

See [CONSTRAINTS.md](CONSTRAINTS.md) for architecture rules and [ai-context.md](ai-context.md) for the project map.
