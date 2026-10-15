plugins { kotlin("jvm"); kotlin("plugin.compose"); id("org.jetbrains.compose") }
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.12.1")
}
