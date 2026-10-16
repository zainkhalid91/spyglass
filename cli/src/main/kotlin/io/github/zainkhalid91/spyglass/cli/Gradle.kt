package io.github.zainkhalid91.spyglass.cli

import java.io.File
import java.util.zip.CRC32

/** Where a Gradle build lives and how to launch its wrapper. */
internal class GradleBuild(val settingsDir: File, private val wrapperDir: File) {

    /**
     * Starts the wrapper the way `gradlew` itself does (java + gradle-wrapper.jar), so no shell is
     * involved: paths containing `&`, `^` or spaces can't be misread by cmd.exe. Falls back to the
     * script when the wrapper jar is missing.
     */
    private fun command(arguments: List<String>, tmpDir: File?): List<String> {
        val wrapperJar = File(wrapperDir, "gradle/wrapper/gradle-wrapper.jar")
        if (!wrapperJar.isFile) {
            if (isWindows) {
                // cmd.exe re-parses its command line, so a value like `x&calc` would run a command.
                (arguments + settingsDir.path).firstOrNull { arg -> arg.any { it in CMD_SPECIAL } }?.let {
                    throw CliError("refusing to pass '$it' through cmd.exe; add the Gradle wrapper jar (gradle/wrapper/gradle-wrapper.jar) or avoid & | < > ^ % ! \" in values")
                }
                return listOf(Executables.cmd, "/c", ".\\gradlew.bat", "-p", settingsDir.path) + arguments
            }
            return listOf("sh", "./gradlew", "-p", settingsDir.path) + arguments
        }
        val java = File(System.getProperty("java.home"), if (isWindows) "bin/java.exe" else "bin/java").path
        return buildList {
            add(java)
            addAll(listOf("-Xmx64m", "-Xms64m")) // gradlew's DEFAULT_JVM_OPTS
            listOf("JAVA_OPTS", "GRADLE_OPTS").forEach { name ->
                System.getenv(name)?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.let(::addAll)
            }
            tmpDir?.let { add("-Djdk.net.unixdomain.tmpdir=${it.path}") }
            add("-Dorg.gradle.appname=gradlew")
            addAll(listOf("-classpath", wrapperJar.path, "org.gradle.wrapper.GradleWrapperMain"))
            addAll(listOf("-p", settingsDir.path))
            addAll(arguments)
        }
    }

    class Result(val exitCode: Int, val lastLines: List<String>)

    /**
     * Runs Gradle, streaming its output to stderr so stdout stays clean for `--json` and MCP.
     * Keeps the last lines so a failure (say, a compile error) can be shown to an agent.
     */
    fun run(arguments: List<String>): Result {
        val tmpDir = windowsTempDir()
        val process = ProcessBuilder(command(arguments, tmpDir))
            .directory(wrapperDir)
            .redirectErrorStream(true)
            .apply { tmpDir?.let { environment()["TEMP"] = it.path; environment()["TMP"] = it.path } }
            .start()
        val tail = ArrayDeque<String>()
        process.inputStream.bufferedReader().forEachLine { line ->
            System.err.println(line)
            tail.addLast(line)
            if (tail.size > TAIL_LINES) tail.removeFirst()
        }
        return Result(process.waitFor(), tail.toList())
    }

    /**
     * On Windows, a TEMP that is an 8.3 short path (C:\Users\JOHNSM~1\...) breaks the Unix-domain
     * sockets Gradle uses ("Unable to establish loopback connection"). Returns a safe directory to use
     * instead, or null when TEMP is fine. Space-free paths are preferred, as some tools split on spaces.
     */
    private fun windowsTempDir(): File? {
        if (!isWindows) return null
        val temp = System.getenv("TEMP") ?: return null
        if ('~' !in temp) return null
        val candidates = listOfNotNull(
            File(settingsDir, ".gradle/spyglass-tmp"),
            System.getenv("PUBLIC")?.let { File(it, "spyglass-tmp") }, // C:\Users\Public, writable by every user
            File(File(temp).canonicalPath),
        ).filter { '~' !in it.path }
        val dir = candidates.firstOrNull { ' ' !in it.path } ?: candidates.firstOrNull() ?: return null
        dir.mkdirs()
        return dir
    }

    companion object {
        private const val TAIL_LINES = 60
        private const val CMD_SPECIAL = "&|<>^%!\"\n\r"
        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

        /** The nearest build (settings file) at or above [start], and the nearest wrapper at or above that. */
        fun find(start: File): GradleBuild {
            val settingsDir = generateSequence(start) { it.parentFile }
                .firstOrNull { File(it, "settings.gradle.kts").isFile || File(it, "settings.gradle").isFile }
                ?: throw CliError("no Gradle build (settings.gradle[.kts]) found in $start or its parents")
            val wrapperName = if (isWindows) "gradlew.bat" else "gradlew"
            val wrapperDir = generateSequence(settingsDir) { it.parentFile }
                .firstOrNull { File(it, "gradle/wrapper/gradle-wrapper.jar").isFile || File(it, wrapperName).isFile }
                ?: throw CliError("no Gradle wrapper found in $settingsDir or its parents")
            return GradleBuild(settingsDir, wrapperDir)
        }
    }
}

/** The plugin jar and init script bundled in the CLI, unpacked once per content version. */
internal object BundledPlugin {
    private const val BASE = "/io/github/zainkhalid91/spyglass/cli/"

    val pluginJar: File by lazy { unpack("spyglass-gradle-plugin.jar") }
    val initScript: File by lazy { unpack("spyglass.init.gradle") }

    private fun unpack(name: String): File {
        val bytes = BundledPlugin::class.java.getResourceAsStream(BASE + name)?.use { it.readBytes() }
            ?: throw CliError("$name is missing from the Spyglass CLI")
        val crc = CRC32().apply { update(bytes) }.value.toString(16)
        val dir = File(System.getProperty("user.home"), ".spyglass/$VERSION/$crc").apply { mkdirs() }
        val file = File(dir, name)
        // Gradle loads this jar as build code, so its content is checked on every run, not just its size.
        if (!file.isFile || !file.readBytes().contentEquals(bytes)) {
            val partial = File(dir, "$name.${ProcessHandle.current().pid()}.part")
            partial.writeBytes(bytes)
            try {
                java.nio.file.Files.move(partial.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (e: java.io.IOException) {
                partial.delete()
                // Another Spyglass process may have written it at the same moment; accept it only if correct.
                if (!file.isFile || !file.readBytes().contentEquals(bytes)) throw CliError("could not write $file: ${e.message}")
            }
        }
        return file
    }
}
