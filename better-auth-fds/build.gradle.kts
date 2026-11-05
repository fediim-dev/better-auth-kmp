plugins { id("betterauth.kmp.library") }

kotlin {
  sourceSets.commonMain.dependencies {
    api(project(":better-auth-compose"))
    api(project(":better-auth-core"))
    api(project(":better-auth-passkeys"))
  }
}
