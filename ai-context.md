# better-auth-kmp (L0)

Kotlin Multiplatform client SDK and Compose/FDS UI for Better Auth. Group `dev.fediim.auth`;
targets jvm (JDK 21), android, iosArm64, iosSimulatorArm64, js, wasmJs.

| Module | Package root | Purpose |
|---|---|---|
| `better-auth-core` | `dev.fediim.auth.core` | Client, config, data model, storage, social/org/apikey/2FA |
| `better-auth-passkeys` | `dev.fediim.auth.passkeys` | Passkey authenticator bridges |
| `better-auth-compose` | `dev.fediim.auth.compose` | Compose Runtime view models, UI contracts |
| `better-auth-fds` | `dev.fediim.auth.fds` | FDS-styled Compose UI |

Edges: passkeys -> core; compose -> core + passkeys; fds -> compose + core + passkeys.
Layering is guard-enforced (`.fediim/guards.json`); rules and locked decisions: `CONSTRAINTS.md`.
Build: `./gradlew check`; shared build config: `build-logic/` (`betterauth.kmp.library`).
