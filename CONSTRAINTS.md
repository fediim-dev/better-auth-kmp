# Project Constraints & Architecture

> Authoritative and hand-maintained. fediim scaffolded this file once and
> will never overwrite it. Amending any constraint requires updating the
> related spec/RFC and this register in the same PR.

## Architecture overview

Four Kotlin Multiplatform library modules, group `dev.fediim.auth`, configured by the
`betterauth.kmp.library` convention plugin (`build-logic/`).

```
better-auth-core      (no module deps; no Compose, no FDS)
better-auth-passkeys  -> core
better-auth-compose   -> core, passkeys (+ Compose Runtime)
better-auth-fds       -> compose, core, passkeys (+ FDS)
```

Allowed imports mirror these edges and are enforced by `forbiddenImports` in
`.fediim/guards.json` (source imports and `project(...)` edges in module build files):
core never imports `androidx.compose`, `dev.fediim.fds` or other auth modules; passkeys never
imports `androidx.compose`, `dev.fediim.fds`, `dev.fediim.auth.compose` or `.fds`; compose never
imports `dev.fediim.fds` or `dev.fediim.auth.fds`.

## Constraints register

Violating a constraint is a defect, not a style choice. Items marked [irreversible] are locked
intake decisions quoted from their source issue.

### C1 · Targets & Toolchain (#1) [irreversible]
- **Constraint:** Every module compiles and runs tests across `jvm` (JDK 21), `wasmJs`, `js`, `androidTarget`, and `ios` (`iosArm64`, `iosSimulatorArm64`).
- **Rationale:** One API surface for all supported platforms.

### C2 · Maven Coordinates (#1) [irreversible]
- **Constraint:** Group `dev.fediim.auth` with modules `better-auth-core`, `better-auth-passkeys`, `better-auth-compose`, and `better-auth-fds`; published to `maven.pkg.github.com/fediim-dev/better-auth-kmp` and `mavenLocal`.
- **Rationale:** Published coordinates cannot change without breaking consumers.

### C3 · Module Layering & Dependency Policy (#1, #7)
- **Constraint:** `core` imports no Compose/FDS/passkeys; `passkeys` depends only on `core`; `compose` depends on `core` + `passkeys` + Compose Runtime (an allowed future dependency) and never imports `dev.fediim.fds`; `fds` depends on `compose` + `core` + `passkeys`. New runtime dependencies are human-gated.
- **Rationale:** #7 Module boundary [irreversible]: better-auth-compose depends on core, passkeys and Compose Runtime, never imports dev.fediim.fds.

### C4 · Data Model: additionalFields (#2, #5) [irreversible]
- **Constraint:** #2 Data model: AuthUser, AuthSession, AuthResponse, BetterAuthError use kotlinx.serialization and include `additionalFields: JsonObject = JsonObject(emptyMap())` so app-specific server fields round-trip without schema loss. #5 Data model: Organization, OrganizationMember, OrganizationInvitation, ApiKeyRecord include additionalFields / metadata JsonObject.
- **Rationale:** Servers add app-specific fields; the SDK must not drop them.

### C5 · Auth Transport: Bearer (#2, #3) [irreversible]
- **Constraint:** #2 Auth & transport: uniform `Authorization: Bearer <token>` across all targets, token captured from Better Auth's `set-auth-token` response header (fallback to response body `token`), optional `X-API-Key` header. #3 Data model & transport: Bearer via set-auth-token + additionalFields round-tripping, verified against a real Node better-auth server.
- **Rationale:** Cookies are unavailable on most targets; one transport everywhere.

### C6 · Platform OAuth Boundaries (#4) [irreversible]
- **Constraint:** Web (wasmJs, js): popup + window.opener.postMessage listener with strict expected-origin verification plus full-page redirect fallback when popups are blocked. Desktop (jvm): ephemeral loopback listener http://127.0.0.1:<port>/auth/callback with Desktop.browse and configurable timeout. Android: Chrome Custom Tabs (androidx.browser) + deep-link callback dispatcher. iOS: ASWebAuthenticationSession.
- **Rationale:** Each platform's sanctioned OAuth mechanism.

### C7 · Platform Passkey Boundaries (#6) [irreversible]
- **Constraint:** Passkey bridges live in better-auth-passkeys: navigator.credentials.create/get on wasmJs and js, androidx.credentials CredentialManager on Android, ASAuthorizationPlatformPublicKeyCredentialProvider on iOS, explicit PasskeyCapability.Unsupported (or injected authenticator) on JVM.
- **Rationale:** Passkeys stay out of core; unsupported platforms fail explicitly.

### C8 · Two-Factor in Core (#6) [irreversible]
- **Constraint:** 2FA/TOTP lives in better-auth-core on all 5 targets, including twoFactorRedirect handling during signInEmail.
- **Rationale:** 2FA is part of the base sign-in flow, not an optional module.
