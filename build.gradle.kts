plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "io.github.zainkhalid91.spyglass"
    version = "0.1.0-SNAPSHOT"
}
