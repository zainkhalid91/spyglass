package io.github.zainkhalid91.spyglass.cli

import java.io.File
import java.util.zip.CRC32

/** Where a Gradle build lives and how to launch its wrapper. */
internal class GradleBuild(val settingsDir: File, private val wrapperDir: File) {

    fun command(arguments: List<String>): List<String> {
        // Launched from the wrapper's directory by a relative path: cmd.exe mangles a quoted first
        // token, and with NoDefaultCurrentDirectoryInExePath set it won't find a bare "gradlew.bat".
        val launcher = if (isWindows) listOf("cmd.exe", "/c", ".\\gradlew.bat") else listOf("sh", "./gradlew")
        return launcher + listOf("-p", settingsDir.path) + arguments
    }

    class Result(val exitCode: Int, val lastLines: List<String>)

    /**
     * Runs Gradle, streaming its output to stderr so stdout stays clean for `--json` and MCP.
     * Keeps the last lines so a failure (say, a compile error) can be shown to an agent.
     */
    fun run(arguments: List<String>): Result {
        val process = ProcessBuilder(command(arguments))
            .directory(wrapperDir)
            .redirectErrorStream(true)
            .apply { environment().putAll(windowsTempFix()) }
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
     * On Windows, a TEMP that is an 8.3 short path (C:\Users\USERNA~1\...) breaks the Unix-domain
     * sockets Gradle uses ("Unable to establish loopback connection"). Point it somewhere safe.
     */
    private fun windowsTempFix(): Map<String, String> {
        if (!isWindows) return emptyMap()
        val temp = System.getenv("TEMP") ?: return emptyMap()
        if ('~' !in temp) return emptyMap()
        // GRADLE_OPTS is split on spaces by gradlew.bat, so the directory must not contain any.
        val dir = listOf(File(settingsDir, ".gradle/spyglass-tmp"), File(File(temp).canonicalPath))
            .firstOrNull { ' ' !in it.path && '~' !in it.path }
            ?: return emptyMap()
        dir.mkdirs()
        val opts = listOfNotNull(System.getenv("GRADLE_OPTS"), "-Djdk.net.unixdomain.tmpdir=${dir.path}")
        return mapOf("TEMP" to dir.path, "TMP" to dir.path, "GRADLE_OPTS" to opts.joinToString(" "))
    }

    companion object {
        private const val TAIL_LINES = 60
        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

        /** The nearest build (settings file) at or above [start], and the nearest wrapper at or above that. */
        fun find(start: File): GradleBuild {
            val settingsDir = generateSequence(start) { it.parentFile }
                .firstOrNull { File(it, "settings.gradle.kts").isFile || File(it, "settings.gradle").isFile }
                ?: throw CliError("no Gradle build (settings.gradle[.kts]) found in $start or its parents")
            val wrapperName = if (isWindows) "gradlew.bat" else "gradlew"
            val wrapperDir = generateSequence(settingsDir) { it.parentFile }
                .firstOrNull { File(it, wrapperName).isFile }
                ?: throw CliError("no Gradle wrapper ($wrapperName) found in $settingsDir or its parents")
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
        if (!file.isFile || file.length() != bytes.size.toLong()) {
            val partial = File(dir, "$name.part")
            partial.writeBytes(bytes)
            if (!partial.renameTo(file) && !file.isFile) throw CliError("could not write $file")
            partial.delete()
        }
        return file
    }
}
