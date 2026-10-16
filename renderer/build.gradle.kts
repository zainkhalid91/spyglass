import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Compose and the stdlib come from the project being rendered, so they are compile-only here.
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.compose.runtime)
    compileOnly(libs.compose.foundation)
    compileOnly(libs.compose.ui)

    implementation(libs.classgraph)

    // The Compose compiler plugin refuses to compile any source set without the runtime present.
    testImplementation(libs.compose.runtime) // also brings kotlinx-coroutines for VirtualTimeTest
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// A single jar holding the renderer plus ClassGraph, embedded inside the Gradle plugin.
val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.map { it.output })
    val runtime = configurations.runtimeClasspath
    dependsOn(runtime)
    from({ runtime.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "**/module-info.class")
}

val rendererJar = configurations.create("rendererJar") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(rendererJar.name, fatJar)
}
