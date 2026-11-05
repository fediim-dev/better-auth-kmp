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
- `./gradlew publishToMavenLocal` installs into `~/.m2` without credentials.
- `./gradlew publishAllPublicationsToGitHubPackagesRepository` publishes; credentials from `gpr.user`/`gpr.key` or `GITHUB_ACTOR`/`GITHUB_TOKEN`.
- CI publishes on push to `main` (snapshots) and on `v*` tags. For a release, drop `-SNAPSHOT` from `version` in `gradle.properties` (and `VERSION` in each module) before tagging.

See [CONSTRAINTS.md](CONSTRAINTS.md) for architecture rules and [ai-context.md](ai-context.md) for the project map.
