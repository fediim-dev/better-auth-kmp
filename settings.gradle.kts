rootProject.name = "better-auth-kmp"

pluginManagement {
  includeBuild("build-logic")
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    // FDS releases (used from #8 by better-auth-fds only); credentials are optional.
    // exclusiveContent: dev.fediim.fds resolves only from this repo, never from
    // google/mavenCentral.
    exclusiveContent {
      forRepositories(
          maven {
          url = uri("https://maven.pkg.github.com/fediim-dev/fediim-design-system")
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
        },
      )
      filter { includeGroup("dev.fediim.fds") }
    }
  }
}

include(":better-auth-core")

include(":better-auth-passkeys")

include(":better-auth-compose")

include(":better-auth-fds")

include(":sample")
