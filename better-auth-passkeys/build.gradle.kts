plugins { id("betterauth.kmp.library") }

kotlin { sourceSets.commonMain.dependencies { api(project(":better-auth-core")) } }
