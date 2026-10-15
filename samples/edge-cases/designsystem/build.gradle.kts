plugins { kotlin("multiplatform"); kotlin("plugin.compose"); id("org.jetbrains.compose") }
kotlin {
    jvm("desktop")
    sourceSets.commonMain.dependencies {
        api("org.jetbrains.compose.foundation:foundation:1.12.1")
        api("org.jetbrains.compose.ui:ui-tooling-preview:1.12.1")
    }
}
