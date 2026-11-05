import org.gradle.process.CommandLineArgumentProvider

plugins { id("betterauth.kmp.library") }

// The serialization plugin comes from the build-logic classpath (no version resolvable here).
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

kotlin {
  sourceSets {
    all { languageSettings.optIn("dev.fediim.auth.core.InternalBetterAuthApi") }
    commonMain.dependencies {
      api(project(":better-auth-core"))
      implementation(libs.kotlinx.coroutines.core)
    }
    androidMain.dependencies {
      implementation(libs.androidx.credentials)
      implementation(libs.androidx.credentials.play.services.auth)
    }
    commonTest.dependencies {
      implementation(libs.ktor.client.mock)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.multiplatform.settings.test)
    }
    jvmTest.dependencies { implementation(libs.ktor.client.okhttp) }
    // The live-contract harness is shared with better-auth-core (plan #6 L12).
    jvmTest { kotlin.srcDir(rootProject.file("tests/contract-harness/kotlin")) }
  }
}

val contractServerDir = rootProject.layout.projectDirectory.dir("tests/contract-server")

// Same wiring as better-auth-core's jvmTest (plan #6 L21): the pinned npm install stays one task,
// owned by core, and this module's jvmTest depends on it.
class ContractServerDirArgument(@get:Internal val dir: File) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> =
      listOf("-Dbetterauth.contractServerDir=${dir.absolutePath}")
}

// Read lazily: only evaluated when jvmTest's inputs are computed.
val nodeVersion = providers.of(NodeVersionSource::class) {}

tasks.named<Test>("jvmTest") {
  dependsOn(":better-auth-core:npmCi")
  jvmArgumentProviders.add(ContractServerDirArgument(contractServerDir.asFile))
  inputs
      .files(contractServerDir.file("server.mjs"), contractServerDir.file("package-lock.json"))
      .withPropertyName("contractServerSources")
      .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs.property("nodeVersion", nodeVersion)
}
