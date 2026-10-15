package io.github.zainkhalid91.spyglass.cli

/** Minimal parser: `--option value`, `--switch`, and positional arguments. */
internal class Args private constructor(
    val positionals: List<String>,
    private val values: Map<String, String>,
    private val switches: Set<String>,
) {
    operator fun get(option: String): String? = values[option]

    fun has(switch: String): Boolean = switch in switches

    fun double(option: String): Double? = values[option]?.let {
        it.toDoubleOrNull() ?: throw CliError("$option expects a number, got '$it'")
    }

    companion object {
        fun parse(raw: List<String>, options: Set<String>, switches: Set<String>): Args {
            val positionals = mutableListOf<String>()
            val values = mutableMapOf<String, String>()
            val seen = mutableSetOf<String>()
            var i = 0
            while (i < raw.size) {
                val arg = raw[i]
                when {
                    arg in options -> values[arg] = raw.getOrNull(++i) ?: throw CliError("$arg needs a value")
                    arg in switches -> seen += arg
                    arg.startsWith("--") -> throw CliError("unknown option $arg. Run 'spyglass help'.")
                    else -> positionals += arg
                }
                i++
            }
            return Args(positionals, values, seen)
        }
    }
}
