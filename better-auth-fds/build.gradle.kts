import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins { id("betterauth.kmp.library") }

// The Compose compiler plugin comes from the build-logic classpath (no version resolvable here).
apply(plugin = "org.jetbrains.kotlin.plugin.compose")

// L30: js/wasm browser tests need the CMP plugin to bundle Skiko (skiko.mjs) for FDS's Compose UI.
apply(plugin = "org.jetbrains.compose")

kotlin {
  // CMP's browser-test check demands an executable binary so webpack bundles Skiko (CMP-4906).
  js { binaries.executable() }
  @OptIn(ExperimentalWasmDsl::class) wasmJs { binaries.executable() }

  sourceSets {
    commonMain.dependencies {
      api(project(":better-auth-compose"))
      api(project(":better-auth-core"))
      api(project(":better-auth-passkeys"))
      api(libs.fds.components)
    }
    jvmTest.dependencies {
      implementation(libs.compose.ui.test.junit4)
      implementation(libs.compose.desktop.macos.x64)
      implementation(libs.compose.desktop.macos.arm64)
      implementation(libs.roborazzi.compose.desktop)
      implementation(libs.ktor.client.mock)
      implementation(libs.multiplatform.settings.test)
    }
  }
}

// Roborazzi runs without its Gradle plugin: record vs verify is a system property
// (-Proborazzi.test.record=true re-records the committed goldens).
tasks.named<Test>("jvmTest") {
  val record = providers.gradleProperty("roborazzi.test.record").orNull == "true"
  systemProperty(if (record) "roborazzi.test.record" else "roborazzi.test.verify", "true")
  inputs
      .dir(layout.projectDirectory.dir("screenshots"))
      .withPropertyName("screenshots")
      .optional()
      .withPathSensitivity(PathSensitivity.RELATIVE)
}
