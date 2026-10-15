plugins {
    `java-gradle-plugin`
}

// Written in Java so the plugin does not depend on the Kotlin version embedded in Gradle,
// and so it can be applied from an init script to projects that never declared it.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

val renderer = configurations.create("renderer") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    renderer(project(path = ":renderer", configuration = "rendererJar"))
}

tasks.processResources {
    from(renderer) {
        into("io/github/zainkhalid91/spyglass")
        rename { "renderer.jar" }
    }
}

gradlePlugin {
    plugins {
        create("spyglass") {
            id = "io.github.zainkhalid91.spyglass"
            implementationClass = "io.github.zainkhalid91.spyglass.gradle.SpyglassPlugin"
            displayName = "Spyglass"
            description = "Renders Compose @Preview functions to PNG without an IDE, for humans, CI and AI agents."
        }
    }
}
