plugins { `kotlin-dsl` }

dependencies {
  implementation(libs.plugin.kotlin.multiplatform)
  implementation(libs.plugin.android.kmp.library)
  implementation(libs.plugin.ktfmt)
}
