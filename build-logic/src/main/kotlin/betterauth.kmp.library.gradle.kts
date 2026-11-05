// Shared setup for every better-auth-* module: targets, Android library config, ktfmt, publishing.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("maven-publish")
  id("com.ncorti.ktfmt.gradle")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "dev.fediim.auth"

kotlin {
  jvmToolchain(21)
  explicitApi()

  androidLibrary {
    namespace = "dev.fediim.auth." + project.name.removePrefix("better-auth-")
    compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
    minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    withHostTest {}
  }

  iosArm64()
  iosSimulatorArm64()
  jvm()

  js { browser { testTask { useKarma { useChromeHeadless() } } } }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs { browser { testTask { useKarma { useChromeHeadless() } } } }

  sourceSets.commonTest.dependencies { implementation(libs.findLibrary("kotlin-test").get()) }
}

ktfmt {}

// ktfmt*Scripts walks the whole module directory, build/ included, and races tasks that write
// there. The plugin only runs JVM KotlinCompile after it; every other module task waits too.
val ktfmtScriptsTasks = tasks.matching {
  it.name.startsWith("ktfmt") && it.name.endsWith("Scripts")
}

tasks
    .matching { !it.name.startsWith("ktfmt") && it.name != "check" && it.name != "build" }
    .configureEach { mustRunAfter(ktfmtScriptsTasks) }

// check compiles every target's main and test code (iosArm64 has no test task) without packaging.
val compileAllTargets by tasks.registering {
  description = "Compiles every target's main and test code (debug only, no packaging)."
  // Wasm executable links (...ProductionExecutable...) are compilation tasks too; keep them out.
  dependsOn(
      tasks.withType<KotlinCompilationTask<*>>().matching {
        !it.name.contains("Production") && !it.name.contains("Release")
      }
  )
}

tasks.named("check") { dependsOn(compileAllTargets) }

publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/fediim-dev/better-auth-kmp")
      credentials {
        username =
            providers
                .gradleProperty("gpr.user")
                .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                .orNull
        password =
            providers
                .gradleProperty("gpr.key")
                .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                .orNull
      }
    }
  }
}
