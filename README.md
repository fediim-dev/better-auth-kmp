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
