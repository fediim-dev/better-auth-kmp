plugins { `kotlin-dsl` }

dependencies {
  implementation(libs.plugin.kotlin.multiplatform)
  implementation(libs.plugin.android.kmp.library)
  implementation(libs.plugin.ktfmt)
  implementation(libs.plugin.kotlin.serialization)
  implementation(libs.plugin.compose.compiler)
  implementation(libs.plugin.compose.multiplatform)
}
