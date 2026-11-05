plugins { id("betterauth.kmp.library") }

// The serialization plugin comes from the build-logic classpath (no version resolvable here).
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(libs.ktor.client.core)
      api(libs.kotlinx.serialization.json)
      api(libs.multiplatform.settings)
      implementation(libs.kotlinx.coroutines.core)
    }
    jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
    androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    jsMain.dependencies { implementation(libs.ktor.client.js) }
    wasmJsMain.dependencies { implementation(libs.ktor.client.js) }
    commonTest.dependencies {
      implementation(libs.ktor.client.mock)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.multiplatform.settings.test)
    }
  }
}
