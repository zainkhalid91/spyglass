import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val gradlePluginJar = configurations.create("gradlePluginJar") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)
    gradlePluginJar(project(":gradle-plugin"))
}

// The CLI carries the Gradle plugin (which carries the renderer), so one install is all a user needs.
tasks.processResources {
    from(gradlePluginJar) {
        into("io/github/zainkhalid91/spyglass/cli")
        rename { "spyglass-gradle-plugin.jar" }
    }
}

application {
    mainClass.set("io.github.zainkhalid91.spyglass.cli.MainKt")
    applicationName = "spyglass"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}
