package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Opt-in, anonymous usage numbers that help improve Spyglass. Off until the developer agrees.
 *
 * Only counters are kept: how often commands and tools run, how many previews render or fail (by
 * standard exception type), similarity scores in ranges, and token-saving hits. Never code, images,
 * file paths, preview, module or project names. Reports are posted at most weekly as a comment on
 * the repository's "usage-report" issue, through the developer's own GitHub CLI login.
 */
internal object Telemetry {
    const val REPO = "zainkhalid91/spyglass"
    const val LABEL = "usage-report"
    private val SEND_EVERY: Duration = Duration.ofDays(7)

    /** Settings and counters live in the user's home, never in the project. Replaceable for tests. */
    var home = File(System.getProperty("user.home"), ".spyglass")
        set(value) {
            field = value
            cachedEnabled = null
        }
    private val settingsFile get() = File(home, "settings.json")
    private val usageFile get() = File(home, "usage.json")

    private val pending = sortedMapOf<String, Int>()
    private var cachedEnabled: Boolean? = null

    val enabled: Boolean
        get() = cachedEnabled ?: (settings()["enabled"]?.jsonPrimitive?.booleanOrNull == true).also { cachedEnabled = it }

    fun count(event: String, n: Int = 1) {
        if (n == 0 || !enabled) return
        synchronized(pending) { pending[event] = (pending[event] ?: 0) + n }
    }

    fun similarity(value: Double) = count(
        "diff.similarity." + when {
            value >= 0.99 -> "99-100"
            value >= 0.95 -> "95-99"
            value >= 0.90 -> "90-95"
            value >= 0.80 -> "80-90"
            else -> "under-80"
        }
    )

    /**
     * Reduces an error message to a category with no user text in it: a standard JDK or Kotlin
     * exception name, one of Spyglass's own checks, or "other".
     */
    fun errorCategory(error: String): String {
        val head = error.substringBefore(':').trim()
        return when {
            head in standardExceptions -> head
            "drew nothing" in error -> "drew-nothing"
            "has parameters" in error || "must take exactly one parameter" in error -> "unsupported-parameters"
            "neither an object" in error -> "no-instance"
            else -> "other"
        }
    }

    private val standardExceptions = setOf(
        "IllegalStateException", "IllegalArgumentException", "NullPointerException", "ClassNotFoundException",
        "NoClassDefFoundError", "NoSuchMethodError", "NoSuchFieldError", "UnsatisfiedLinkError", "OutOfMemoryError",
        "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException", "ClassCastException", "ArithmeticException",
        "UnsupportedOperationException", "NumberFormatException", "ConcurrentModificationException",
        "UninitializedPropertyAccessException", "KotlinNullPointerException", "StackOverflowError",
    )

    // ------------------------------------------------------------------ consent

    /**
     * Asks once, in an interactive terminal only (never in CI or under an agent), and remembers the
     * answer either way. The default is no.
     */
    fun askOnce() {
        if (settings().containsKey("enabled")) return
        if (System.console() == null || System.getenv("CI") != null || System.getenv("SPYGLASS_TELEMETRY") != null) return
        System.err.println(CONSENT_TEXT)
        System.err.print("Share anonymous usage numbers? [y/N] ")
        val answer = runCatching { readlnOrNull() }.getOrNull()?.trim()?.lowercase()
        setEnabled(answer == "y" || answer == "yes")
        System.err.println(if (enabled) "Thanks! Turn it off any time with `spyglass telemetry off`." else "OK, nothing will be shared. `spyglass telemetry on` changes that.")
    }

    val CONSENT_TEXT = """
        |Spyglass can share anonymous usage numbers to help improve it:
        |  - how often commands and agent tools run, and how many previews render or fail (error types only)
        |  - comparison scores in ranges, and how often unchanged images were skipped to save tokens
        |  - Spyglass, Java and OS versions
        |Never code, images, file names, preview, module or project names.
        |About once a week a report is posted as a comment on a public issue in github.com/$REPO,
        |under your GitHub account, using the GitHub CLI (gh). Without gh, nothing is sent.
        |See exactly what would be sent with `spyglass telemetry show`.
    """.trimMargin()

    @Synchronized
    fun setEnabled(on: Boolean) {
        writeSettings(settings() + mapOf("enabled" to JsonPrimitive(on)))
        cachedEnabled = on
        if (!on) {
            synchronized(pending) { pending.clear() }
            usageFile.delete()
        }
    }

    // ------------------------------------------------------------------ storage and sending

    /** Adds this process's counters to the stored ones. */
    @Synchronized
    fun flush() {
        val batch = synchronized(pending) { pending.toMap().also { pending.clear() } }
        if (batch.isEmpty() || !enabled) return
        val stored = counters().toMutableMap()
        batch.forEach { (k, v) -> stored[k] = (stored[k] ?: 0) + v }
        writeCounters(stored)
    }

    private fun writeCounters(counts: Map<String, Int>) {
        home.mkdirs()
        usageFile.writeText(Json.encodeToString(JsonElement.serializer(), JsonObject(counts.mapValues { JsonPrimitive(it.value) })))
    }

    @Synchronized
    fun counters(): Map<String, Int> = runCatching {
        Json.parseToJsonElement(usageFile.readText()).jsonObject.mapValues { it.value.jsonPrimitive.intOrNull ?: 0 }
    }.getOrDefault(emptyMap())

    /** The exact text that would be posted. */
    fun report(counts: Map<String, Int> = counters()): String {
        return buildString {
            appendLine("<!-- spyglass usage report: anonymous counters, opt-in -->")
            appendLine("```json")
            appendLine(
                Json.encodeToString(
                    JsonElement.serializer(),
                    buildJsonObject {
                        put("spyglass", VERSION)
                        put("os", System.getProperty("os.name").substringBefore(' ').lowercase())
                        put("java", Runtime.version().feature())
                        put("since", settings()["lastSent"]?.jsonPrimitive?.contentOrNull ?: "first report")
                        put("counts", JsonObject(counts.toSortedMap().mapValues { JsonPrimitive(it.value) }))
                    },
                )
            )
            append("```")
        }
    }

    /** Sends when enabled, due and there is something to send. Failures are silent and retried later. */
    fun sendIfDue(): Boolean {
        val due = synchronized(this) {
            val last = settings()["lastSent"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() }
            enabled && counters().isNotEmpty() && (last == null || Duration.between(last, Instant.now()) >= SEND_EVERY)
        }
        return due && send()
    }

    /**
     * Posts the report with `gh`. The lock is not held while gh runs, so tool calls never wait on the
     * network; only the counters that were sent are removed afterwards. Returns false (keeping the
     * counters) if anything goes wrong.
     */
    fun send(): Boolean {
        val snapshot = synchronized(this) { if (enabled) counters() else emptyMap() }
        if (snapshot.isEmpty()) return false
        val issue = gh("issue", "list", "-R", REPO, "--label", LABEL, "--state", "open", "--limit", "1", "--json", "number", "-q", ".[0].number")
            ?.trim()?.toIntOrNull() ?: return false
        val body = File.createTempFile("spyglass-usage", ".md").apply { writeText(report(snapshot)) }
        try {
            gh("issue", "comment", issue.toString(), "-R", REPO, "--body-file", body.path) ?: return false
        } finally {
            body.delete()
        }
        synchronized(this) {
            val remaining = counters().mapValues { (k, v) -> v - (snapshot[k] ?: 0) }.filterValues { it > 0 }
            if (remaining.isEmpty()) usageFile.delete() else writeCounters(remaining)
            writeSettings(settings() + mapOf("lastSent" to JsonPrimitive(Instant.now().toString())))
        }
        return true
    }

    /** Runs gh with a hard timeout; output goes to a file so a hung gh can't block on a full pipe. */
    private fun gh(vararg args: String): String? = runCatching {
        val gh = Executables.onPath("gh") ?: return null
        val out = File.createTempFile("spyglass-gh", ".txt")
        try {
            val process = ProcessBuilder(listOf(gh) + args).redirectErrorStream(true).redirectOutput(out).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0) out.readText() else null
        } finally {
            out.delete()
        }
    }.getOrNull()

    private fun settings(): Map<String, JsonElement> =
        runCatching { Json.parseToJsonElement(settingsFile.readText()).jsonObject.toMap() }.getOrDefault(emptyMap())

    private fun writeSettings(values: Map<String, JsonElement>) {
        home.mkdirs()
        settingsFile.writeText(Json.encodeToString(JsonElement.serializer(), JsonObject(values)))
    }

    fun lastSent(): String? = settings()["lastSent"]?.jsonPrimitive?.contentOrNull
}
