rootProject.name = "desktop-sample"

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
