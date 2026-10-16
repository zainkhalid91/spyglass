rootProject.name = "new-uis"

pluginManagement {
    // Uses the Spyglass plugin straight from this repository.
    includeBuild("../..")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
