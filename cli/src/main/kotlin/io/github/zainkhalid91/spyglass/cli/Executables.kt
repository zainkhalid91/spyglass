package io.github.zainkhalid91.spyglass.cli

import java.io.File

/**
 * Absolute paths for the programs Spyglass starts. On Windows, a bare name like `gh` is looked up in
 * the current directory before PATH, and the current directory is often a cloned project, so a
 * planted `gh.exe` or `cmd.exe` there would run instead.
 */
internal object Executables {
    /** The system cmd.exe, never one from the current directory. */
    val cmd: String
        get() = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\cmd.exe").path

    /** [name] from PATH, using only absolute PATH entries; null when it isn't installed. */
    fun onPath(name: String, path: String? = System.getenv("PATH"), windows: Boolean = GradleBuild.isWindows): String? {
        val names = if (windows) listOf("$name.exe", "$name.cmd", "$name.bat") else listOf(name)
        return path.orEmpty().split(File.pathSeparatorChar)
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() && File(it).isAbsolute }
            .firstNotNullOfOrNull { dir -> names.map { File(dir, it) }.firstOrNull { it.isFile && (windows || it.canExecute()) } }
            ?.path
    }
}
