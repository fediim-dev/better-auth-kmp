import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins { id("betterauth.kmp.library") }

// The Compose compiler plugin comes from the build-logic classpath (no version resolvable here).
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

apply(plugin = "org.jetbrains.kotlin.plugin.compose")

apply(plugin = "org.jetbrains.compose")

kotlin {
  // CMP's browser-test check demands an executable binary so webpack bundles Skiko (CMP-4906).
  js { binaries.executable() }
  @OptIn(ExperimentalWasmDsl::class) wasmJs { binaries.executable() }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":better-auth-fds"))
      implementation(project(":better-auth-compose"))
      implementation(project(":better-auth-core"))
      implementation(project(":better-auth-passkeys"))
      implementation(libs.ktor.client.mock)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.multiplatform.settings)
    }
    jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
    androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    jsMain.dependencies { implementation(libs.ktor.client.js) }
    wasmJsMain.dependencies { implementation(libs.ktor.client.js) }
    commonTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.multiplatform.settings.test)
    }
    jvmTest.dependencies {
      implementation(libs.compose.ui.test.junit4)
      implementation(libs.compose.desktop.macos.x64)
      implementation(libs.compose.desktop.macos.arm64)
      implementation(libs.roborazzi.compose.desktop)
    }
  }
}

// Never publish the sample app to Maven.
tasks.withType<AbstractPublishToMaven>().configureEach { enabled = false }

// better-auth-core and better-auth-passkeys declare web bridges in jsMain/wasmJsMain without
// webMain metadata (L3).
tasks.matching { it.name == "compileWebMainKotlinMetadata" }.configureEach { enabled = false }

// CMP 1.12.2 device test compose resources workaround (same as better-auth-fds).
tasks
    .matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }
    .configureEach { enabled = false }

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
