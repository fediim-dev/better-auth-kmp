plugins { id("betterauth.kmp.library") }

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":better-auth-core"))
      api(project(":better-auth-passkeys"))
      // StateFlow is part of the public API; core only has coroutines as `implementation`.
      api(libs.kotlinx.coroutines.core)
      implementation(libs.compose.runtime)
    }
    commonTest.dependencies {
      implementation(libs.ktor.client.mock)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.multiplatform.settings.test)
    }
  }
}
