# Better Auth KMP Sample Application

Reference Clean Architecture Compose Multiplatform application demonstrating the `better-auth-kmp` SDK suite (`better-auth-core`, `better-auth-passkeys`, `better-auth-compose`, and `better-auth-fds`).

The sample is an internal reference module and is unpublished (`AbstractPublishToMaven` tasks disabled).

## Architecture Layers

The `:sample` module adheres to Clean Architecture with strict layering boundaries enforced by `.fediim/guards.json`:

1. **`domain`** (`dev.fediim.auth.sample.domain`):
   - Pure domain models (`AccountProfile`, `SessionOverview`, `PasskeyItem`, `TwoFactorStatus`, `DemoPreset`, `DemoFailureScenario`).
   - Repository contract (`SampleAuthRepository`).
   - Use cases (`SampleUseCases` aggregating `SwitchEnvironmentUseCase`, `ApplyDemoPresetUseCase`, `SetDemoFailureScenarioUseCase`, `ResetDemoStateUseCase`, `InspectSessionUseCase`, `ManagePasskeysUseCase`, `ManageTwoFactorUseCase`).
   - Helper functions like `maskSessionToken(rawToken)`.
   - Forbidden imports: `data`, `presentation`, `di`, `io.ktor`, `androidx.compose.ui`.

2. **`data`** (`dev.fediim.auth.sample.data`):
   - Repository implementation (`DefaultSampleAuthRepository`).
   - Domain entity mappers (`SampleMappers`).
   - Pre-seeded in-memory store (`DemoServerStore`) and mock Ktor engine (`DemoMockEngine`).
   - Platform authenticator and social auth bridges (`PlatformAuthBridges`, `DemoAuthBridges`).
   - Forbidden imports: `presentation`, `di`.

3. **`presentation`** (`dev.fediim.auth.sample.presentation`):
   - Headless workspace state coordinator (`SampleWorkspaceViewModel`).
   - Compose Multiplatform UI components (`SampleApp`, `UnauthenticatedScreen`, `AuthenticatedWorkspaceScreen`).
   - Integration with FDS design tokens, typography, and theme (`FediimTheme`).
   - Forbidden imports: `data`.

4. **`di`** (`dev.fediim.auth.sample.di`):
   - Application dependency injection container (`SampleAppContainer`).
   - Lifecycle-aware Compose hook (`rememberSampleAppContainer`).

## Backend Environments

### Offline Demo

Runs entirely in-memory with zero external network dependencies via Ktor `MockEngine`.
- **Pre-seeded accounts**:
  - `alice@example.com`: Standard email/password user, active in Acme Corp and Beta Labs with pre-configured API keys.
  - `bob-2fa@example.com`: Two-factor user requiring TOTP code (`123456`) or backup codes (`bk-bob-1`, `bk-bob-2`).
  - `carol-passkey@example.com`: Passkey user with pre-enrolled passkey credentials.
- **Interactive Failure Scenarios**:
  - `NONE`: Normal successful operations.
  - `INVALID_CREDENTIALS`: Simulates incorrect password on sign-in.
  - `INVALID_TWO_FACTOR_CODE`: Simulates invalid TOTP or backup code.
  - `PASSKEY_FAILED`: Simulates WebAuthn ceremony rejection or failure.
  - `RATE_LIMITED_429`: Simulates server rate-limiting returning 429 with `Retry-After: 30`.
- **Reset Demo**: Restores all users, organizations, API keys, and passkeys to baseline pre-seeded state and clears tokens.

### Live Server

Connects to a live Better Auth server deployment using platform network and credential bridges:
- URLs must use `https://`, except for loopback addresses (`http://127.0.0.1`, `http://localhost`, `http://[::1]`) per `BetterAuthConfig`.
- Arbitrary HTTP URLs (such as `http://evil.example.com`) are rejected before attempting connection, keeping Offline Demo active.

## Platform Entry Points

- **Desktop (JVM)**: `sample/src/jvmMain/kotlin/dev/fediim/auth/sample/Main.kt`
- **Android**: `sample/src/androidMain/kotlin/dev/fediim/auth/sample/SampleActivityContent.kt`
- **iOS**: `sample/src/iosMain/kotlin/dev/fediim/auth/sample/MainViewController.kt`
- **Web (JS & Wasm)**: `sample/src/webMain/kotlin/dev/fediim/auth/sample/Main.kt`

## Verification & Tests

```bash
# Run unit, data, interactive UI, and screenshot verification tests
./gradlew :sample:jvmTest

# Re-record Roborazzi screenshot goldens (12 light/dark states)
./gradlew :sample:jvmTest -Proborazzi.test.record=true

# Run Android host tests
./gradlew :sample:testAndroidHostTest

# Compile across all targets (JVM, Android, iOS, JS, Wasm)
./gradlew :sample:compileAllTargets -q
```
