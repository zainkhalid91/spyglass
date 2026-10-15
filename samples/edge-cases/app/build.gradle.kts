plugins { kotlin("multiplatform"); kotlin("plugin.compose"); id("org.jetbrains.compose") }
kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies { implementation(project(":designsystem")) }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
    }
}
