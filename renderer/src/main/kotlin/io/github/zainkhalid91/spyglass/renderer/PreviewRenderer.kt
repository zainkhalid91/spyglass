@file:OptIn(ExperimentalComposeUiApi::class)

package io.github.zainkhalid91.spyglass.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
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
)

internal object PreviewRenderer {
    // Two frames: the first composes, the second picks up state set by effects in the first.
    private const val SECOND_FRAME_NANOS = 16_000_000L

    fun render(spec: PreviewSpec, options: Options, output: File): RenderResult {
        // Compose logs composition errors to stderr; Spyglass reports them itself, so only
        // pass the log through when the preview rendered (it may hold useful warnings).
        val stderr = System.err
        val log = ByteArrayOutputStream()
        System.setErr(PrintStream(log, true))
        val result = try {
            renderCaptured(spec, options, output)
        } finally {
            System.setErr(stderr)
        }
        if (result.error == null && log.size() > 0) stderr.print(log.toString())
        return result
    }

    private fun renderCaptured(spec: PreviewSpec, options: Options, output: File): RenderResult {
        val started = System.nanoTime()
        fun elapsedMs() = (System.nanoTime() - started) / 1_000_000

        return try {
            val method = findComposable(spec)
            val sceneWidth = toPx(spec.widthDp.takeIf { it > 0 } ?: options.maxWidthDp, options.density)
            val sceneHeight = toPx(spec.heightDp.takeIf { it > 0 } ?: options.maxHeightDp, options.density)
            val density = Density(options.density, spec.fontScale.takeIf { it > 0f } ?: 1f)

            var measured = IntSize.Zero
            val scene = ImageComposeScene(sceneWidth, sceneHeight, density) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    PreviewFrame(spec, onMeasured = { measured = it }) { InvokeStatic(method) }
                }
            }
            try {
                scene.render(0)
                val image = scene.render(SECOND_FRAME_NANOS)
                check(measured.width > 0 && measured.height > 0) {
                    "Preview drew nothing (measured ${measured.width}x${measured.height} px)."
                }
                writePng(image, measured.width, measured.height, output)
            } finally {
                scene.close()
            }
            RenderResult(spec, output, measured.width, measured.height, elapsedMs(), error = null)
        } catch (t: Throwable) {
            RenderResult(spec, file = null, widthPx = 0, heightPx = 0, elapsedMs(), error = describe(t))
        }
    }

    private fun findComposable(spec: PreviewSpec): Method {
        val loader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        val type = Class.forName(spec.className, false, loader)
        // A parameterless @Composable compiles to a static method taking (Composer, changed flags).
        val method = type.declaredMethods.firstOrNull { m ->
            m.name == spec.methodName &&
                JavaModifier.isStatic(m.modifiers) &&
                m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == Composer::class.java &&
                m.parameterTypes[1] == Int::class.javaPrimitiveType
        } ?: error("${spec.functionName} is not a parameterless @Composable function (preview parameters are not supported yet).")
        method.isAccessible = true
        return method
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

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        val frame = root.stackTrace.firstOrNull()?.let { " (at ${it.className}.${it.methodName}:${it.lineNumber})" } ?: ""
        return "${root.javaClass.simpleName}: ${root.message}$frame"
    }
}

/** Lays the preview out at its natural size (or the fixed widthDp/heightDp) and reports that size. */
@Composable
private fun PreviewFrame(spec: PreviewSpec, onMeasured: (IntSize) -> Unit, content: @Composable () -> Unit) {
    val background = when {
        !spec.showBackground -> Modifier
        spec.backgroundColor != 0L -> Modifier.background(Color(spec.backgroundColor))
        else -> Modifier.background(Color.White)
    }
    Layout(
        content = content,
        modifier = Modifier.wrapContentSize(Alignment.TopStart).then(background),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = if (spec.widthDp > 0) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        val height = if (spec.heightDp > 0) constraints.maxHeight else placeables.maxOfOrNull { it.height } ?: 0
        onMeasured(IntSize(width, height))
        layout(width, height) { placeables.forEach { it.place(0, 0) } }
    }
}

@Composable
private fun InvokeStatic(method: Method) {
    invokeComposable(method, currentComposer)
}

// Kept outside the @Composable: the Compose compiler forbids try/catch around composable calls.
private fun invokeComposable(method: Method, composer: Composer) {
    try {
        method.invoke(null, composer, 0)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
