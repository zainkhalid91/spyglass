@file:OptIn(ExperimentalComposeUiApi::class)

package io.github.zainkhalid91.spyglass.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier as JavaModifier
import kotlin.math.min
import kotlin.math.roundToInt

internal class RenderResult(
    val spec: PreviewSpec,
    val file: File?,
    val widthPx: Int,
    val heightPx: Int,
    val durationMs: Long,
    val error: String?,
    /** The `@PreviewParameter` value this image shows, as text. */
    val parameter: String? = null,
)

/** One image to render: a preview and, for a `@PreviewParameter` preview, the value to pass in. */
internal class RenderJob(val spec: PreviewSpec, val hasArgument: Boolean, val argument: Any?) {
    val argumentLabel: String? get() = if (hasArgument) argument.toString().take(60) else null
}

/** A problem Spyglass found itself; reported as plain text, without a stack location. */
internal class PreviewProblem(message: String) : Exception(message)

/** A preview function and, for previews declared inside an object or class, the instance to call it on. */
private class Target(val method: Method, val receiver: Any?)

internal object PreviewRenderer {
    // Two frames: the first composes, the second picks up state set by effects in the first.
    private const val SECOND_FRAME_NANOS = 16_000_000L

    // Providers can yield endless sequences; Android Studio caps what it shows too.
    private const val MAX_PARAMETER_VALUES = 50

    // Stack frames from these packages are skipped when pointing at the line that failed.
    private val frameworkPrefixes = listOf(
        "java.", "jdk.", "sun.", "kotlin.", "kotlinx.", "androidx.compose.", "org.jetbrains.skia",
        "org.jetbrains.skiko", "io.github.zainkhalid91.spyglass.",
    )

    /** The images a preview produces: one, or one per `@PreviewParameter` value. */
    fun jobs(spec: PreviewSpec): List<RenderJob> {
        val providerName = spec.parameterProvider ?: return listOf(RenderJob(spec, hasArgument = false, argument = null))
        val loader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        val provider = Class.forName(providerName, true, loader).getDeclaredConstructor()
            .apply { isAccessible = true }.newInstance()
        val values = provider.javaClass.getMethod("getValues").invoke(provider) as Sequence<*>
        return values.take(spec.parameterLimit.coerceAtMost(MAX_PARAMETER_VALUES)).mapIndexed { i, value ->
            RenderJob(spec.copy(parameterIndex = i), hasArgument = true, argument = value)
        }.toList()
    }

    /** A failure that happened before any image could be rendered, such as a provider that won't load. */
    fun failure(spec: PreviewSpec, t: Throwable) =
        RenderResult(spec, file = null, widthPx = 0, heightPx = 0, durationMs = 0, error = describe(t))

    fun render(job: RenderJob, options: Options, output: File): RenderResult {
        val spec = job.spec
        // Never leave an image from an earlier run behind if this render fails.
        output.delete()
        // Compose logs composition errors to stderr; Spyglass reports them itself, so only
        // pass the log through when the preview rendered (it may hold useful warnings).
        val stderr = System.err
        val log = ByteArrayOutputStream()
        System.setErr(PrintStream(log, true))
        val result = try {
            renderCaptured(job, options, output)
        } finally {
            System.setErr(stderr)
        }
        if (result.error == null && log.size() > 0) stderr.print(log.toString())
        return result
    }

    private fun renderCaptured(job: RenderJob, options: Options, output: File): RenderResult {
        val spec = job.spec
        val started = System.nanoTime()
        fun elapsedMs() = (System.nanoTime() - started) / 1_000_000

        return try {
            val target = findComposable(spec, job.hasArgument)
            // A device spec gives the screen size: the exact canvas with showSystemUi, otherwise the limit.
            val device = DeviceSpec.parse(spec.device)
            val fixedWidthDp = spec.widthDp.takeIf { it > 0 } ?: device?.widthDp?.takeIf { spec.showSystemUi }
            val fixedHeightDp = spec.heightDp.takeIf { it > 0 } ?: device?.heightDp?.takeIf { spec.showSystemUi }
            val sceneWidth = toPx(fixedWidthDp ?: device?.widthDp ?: options.maxWidthDp, options.density)
            val sceneHeight = toPx(fixedHeightDp ?: device?.heightDp ?: options.maxHeightDp, options.density)
            val density = Density(options.density, spec.fontScale.takeIf { it > 0f } ?: 1f)
            val locals = arrayOf<ProvidedValue<*>>(LocalInspectionMode provides true) + SystemThemeLocal.provide(spec.dark)

            var measured = IntSize.Zero
            val scene = ImageComposeScene(sceneWidth, sceneHeight, density) {
                CompositionLocalProvider(*locals) {
                    PreviewFrame(
                        spec = spec,
                        fixedWidth = fixedWidthDp != null,
                        fixedHeight = fixedHeightDp != null,
                        onMeasured = { measured = it },
                    ) { InvokePreview(target, job) }
                }
            }
            try {
                scene.render(0)
                val image = scene.render(SECOND_FRAME_NANOS)
                if (measured.width <= 0 || measured.height <= 0) {
                    throw PreviewProblem("${spec.functionName} drew nothing (measured ${measured.width}x${measured.height} px). Give it content or a size.")
                }
                writePng(image, measured.width, measured.height, output)
            } finally {
                scene.close()
            }
            RenderResult(spec, output, measured.width, measured.height, elapsedMs(), error = null, parameter = job.argumentLabel)
        } catch (t: Throwable) {
            RenderResult(spec, file = null, widthPx = 0, heightPx = 0, elapsedMs(), error = describe(t), parameter = job.argumentLabel)
        }
    }

    private fun findComposable(spec: PreviewSpec, hasArgument: Boolean): Target {
        val loader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        val type = Class.forName(spec.className, false, loader)
        // A @Composable compiles to a method taking its parameters, then (Composer, changed flags).
        val arity = if (hasArgument) 3 else 2
        val method = type.declaredMethods.firstOrNull { m ->
            val p = m.parameterTypes
            m.name == spec.methodName && p.size == arity &&
                p[arity - 2] == Composer::class.java && p[arity - 1] == Int::class.javaPrimitiveType
        } ?: throw PreviewProblem(
            if (hasArgument) "${spec.functionName} must take exactly one parameter, the @PreviewParameter one."
            else "${spec.functionName} has parameters. Annotate one with @PreviewParameter, or preview a wrapper without parameters."
        )
        method.isAccessible = true
        if (JavaModifier.isStatic(method.modifiers)) return Target(method, receiver = null)

        // Declared in a Kotlin object (INSTANCE) or a class with a no-argument constructor.
        val receiver = type.declaredFields.firstOrNull { it.name == "INSTANCE" && JavaModifier.isStatic(it.modifiers) }
            ?.apply { isAccessible = true }?.get(null)
            ?: runCatching { type.getDeclaredConstructor().apply { isAccessible = true }.newInstance() }.getOrNull()
            ?: throw PreviewProblem("${spec.functionName} is declared in ${type.simpleName}, which is neither an object nor has a no-argument constructor.")
        return Target(method, receiver)
    }

    private fun writePng(image: Image, width: Int, height: Int, file: File) {
        val surface = Surface.makeRasterN32Premul(min(width, image.width), min(height, image.height))
        try {
            surface.canvas.drawImage(image, 0f, 0f)
            val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG encoding failed.")
            file.parentFile?.mkdirs()
            file.writeBytes(data.bytes)
        } finally {
            surface.close()
        }
    }

    private fun toPx(dp: Int, density: Float) = (dp * density).roundToInt()

    /** One line an agent can act on: the root exception and the first line of the project's own code. */
    private fun describe(t: Throwable): String {
        if (t is PreviewProblem) return t.message.orEmpty()
        val root = generateSequence(t) { it.cause }.last()
        val frame = root.stackTrace.firstOrNull { f -> frameworkPrefixes.none(f.className::startsWith) }
            ?: root.stackTrace.firstOrNull()
        val where = frame?.let { " (at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber}))" } ?: ""
        return "${root.javaClass.simpleName}: ${root.message}$where"
    }
}

/** A `@Preview(device = "spec:width=411dp,height=891dp,...")` screen size, in dp. */
internal class DeviceSpec(val widthDp: Int, val heightDp: Int) {
    companion object {
        fun parse(device: String): DeviceSpec? {
            if (!device.startsWith("spec:")) return null // "id:pixel_5" style ids need a device table; not supported
            val fields = device.removePrefix("spec:").split(',')
                .associate { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
            val dpi = fields["dpi"]?.toFloatOrNull() ?: 160f
            val px = fields["unit"] == "px"
            fun dp(value: String?): Int? {
                value ?: return null
                return when {
                    value.endsWith("dp") -> value.removeSuffix("dp").toFloatOrNull()
                    value.endsWith("px") -> value.removeSuffix("px").toFloatOrNull()?.times(160f / dpi)
                    else -> value.toFloatOrNull()?.let { if (px) it * 160f / dpi else it }
                }?.roundToInt()?.takeIf { it > 0 }
            }
            var width = dp(fields["width"]) ?: return null
            var height = dp(fields["height"]) ?: return null
            // Only an explicit orientation turns the screen; without one the spec is taken as written.
            val orientation = fields["orientation"]
            val turn = (orientation == "landscape" && width < height) || (orientation == "portrait" && width > height)
            if (turn) width = height.also { height = width }
            return DeviceSpec(width, height)
        }
    }
}

/**
 * Compose desktop decides `isSystemInDarkTheme()` from `LocalSystemTheme`, which otherwise follows the
 * machine running Spyglass. Providing it makes renders identical everywhere and lets `uiMode` select
 * dark previews. The local is deprecated in Compose, so it is looked up reflectively and skipped if gone.
 */
private object SystemThemeLocal {
    private val provider: ((Boolean) -> ProvidedValue<*>)? = runCatching {
        val local = Class.forName("androidx.compose.ui.SystemThemeKt").getMethod("getLocalSystemTheme").invoke(null)
        @Suppress("UNCHECKED_CAST")
        local as ProvidableCompositionLocal<Any?>
        val themes = Class.forName("androidx.compose.ui.SystemTheme").enumConstants.associateBy { (it as Enum<*>).name }
        val dark = themes.getValue("Dark")
        val light = themes.getValue("Light")
        val provide: (Boolean) -> ProvidedValue<*> = { isDark -> local provides (if (isDark) dark else light) }
        provide
    }.getOrNull()

    fun provide(dark: Boolean): Array<ProvidedValue<*>> = provider?.let { arrayOf(it(dark)) } ?: emptyArray()
}

/** Lays the preview out at its natural size (or the fixed size) and reports that size. */
@Composable
private fun PreviewFrame(
    spec: PreviewSpec,
    fixedWidth: Boolean,
    fixedHeight: Boolean,
    onMeasured: (IntSize) -> Unit,
    content: @Composable () -> Unit,
) {
    val background = when {
        !spec.showBackground -> Modifier
        spec.backgroundColor != 0L -> Modifier.background(Color(spec.backgroundColor))
        spec.dark -> Modifier.background(Color.Black)
        else -> Modifier.background(Color.White)
    }
    Layout(
        content = content,
        modifier = Modifier.wrapContentSize(Alignment.TopStart).then(background),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = if (fixedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        val height = if (fixedHeight) constraints.maxHeight else placeables.maxOfOrNull { it.height } ?: 0
        onMeasured(IntSize(width, height))
        layout(width, height) { placeables.forEach { it.place(0, 0) } }
    }
}

@Composable
private fun InvokePreview(target: Target, job: RenderJob) {
    invokeComposable(target, job, currentComposer)
}

// Kept outside the @Composable: the Compose compiler forbids try/catch around composable calls.
private fun invokeComposable(target: Target, job: RenderJob, composer: Composer) {
    try {
        if (job.hasArgument) {
            target.method.invoke(target.receiver, job.argument, composer, 0)
        } else {
            target.method.invoke(target.receiver, composer, 0)
        }
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
