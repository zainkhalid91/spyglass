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

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
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

// Apps on macOS (Claude Desktop, Cursor, ...) start MCP servers with a minimal PATH, and /usr/bin/java is
// only a stub that asks to install Java. Without JAVA_HOME, the launcher looks for a JDK the way macOS
// tools do, then in Homebrew's usual places.
val findMacJdk = """
# Spyglass: on macOS without JAVA_HOME, find a JDK (apps start MCP servers with a minimal PATH).
if [ -z "${'$'}JAVA_HOME" ] && [ "${'$'}( uname )" = Darwin ] ; then
    for candidate in "${'$'}( /usr/libexec/java_home 2>/dev/null )" \
        /opt/homebrew/opt/openjdk*/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk*/libexec/openjdk.jdk/Contents/Home ; do
        if [ -n "${'$'}candidate" ] && [ -x "${'$'}candidate/bin/java" ] ; then
            JAVA_HOME=${'$'}candidate
            break
        fi
    done
fi

# Determine the Java command to use to start the JVM.
"""

tasks.startScripts {
    // Only plain values go into the action, so the configuration cache can store it.
    val lookup = findMacJdk.trimStart()
    val script = unixScript
    doLast {
        val anchor = "# Determine the Java command to use to start the JVM.\n"
        val text = script.readText()
        if (anchor !in text) throw GradleException("Gradle's start script changed; update findMacJdk")
        script.writeText(text.replaceFirst(anchor, lookup))
    }
}
