rootProject.name = "edge-cases"
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
include(":designsystem", ":app", ":jsonly", ":jvmapp")
