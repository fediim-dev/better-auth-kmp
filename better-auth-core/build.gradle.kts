import org.gradle.process.CommandLineArgumentProvider

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
    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
      implementation(libs.androidx.browser)
    }
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

val contractServerDir = rootProject.layout.projectDirectory.dir("tests/contract-server")

// Passes the contract server location to jvmTest (I3). The absolute path is @Internal so it does
// not enter the build-cache key; server.mjs and the lockfile are declared as inputs instead.
class ContractServerDirArgument(@get:Internal val dir: File) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> =
      listOf("-Dbetterauth.contractServerDir=${dir.absolutePath}")
}

val npmCi by
    tasks.registering(Exec::class) {
      description = "Installs the contract server's pinned npm dependencies (no install scripts)."
      workingDir(contractServerDir.asFile)
      commandLine("npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund")
      inputs.files(
          contractServerDir.file("package.json"),
          contractServerDir.file("package-lock.json"),
      )
      outputs.dir(contractServerDir.dir("node_modules"))
    }

// Read lazily: only evaluated when jvmTest's inputs are computed.
val nodeVersion = providers.exec { commandLine("node", "--version") }.standardOutput.asText

tasks.named<Test>("jvmTest") {
  dependsOn(npmCi)
  jvmArgumentProviders.add(ContractServerDirArgument(contractServerDir.asFile))
  inputs
      .files(contractServerDir.file("server.mjs"), contractServerDir.file("package-lock.json"))
      .withPropertyName("contractServerSources")
      .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs.property("nodeVersion", nodeVersion)
}
