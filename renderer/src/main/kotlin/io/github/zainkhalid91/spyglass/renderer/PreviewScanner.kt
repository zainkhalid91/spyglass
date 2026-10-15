package io.github.zainkhalid91.spyglass.renderer

import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import java.io.File

internal data class PreviewSpec(
    val className: String,
    val methodName: String,
    val name: String,
    val group: String,
    val widthDp: Int,
    val heightDp: Int,
    val showBackground: Boolean,
    val backgroundColor: Long,
    val fontScale: Float,
) {
    val functionName: String get() = "$className.$methodName"

    /** File name without extension, e.g. `CardsKt.WeatherCardPreview_Dark`. */
    val fileStem: String
        get() {
            val base = "${className.substringAfterLast('.')}.$methodName"
            val full = if (name.isEmpty()) base else "${base}_$name"
            return full.replace(Regex("[^A-Za-z0-9._-]"), "_")
        }

    fun matches(filter: String): Boolean =
        functionName.contains(filter, ignoreCase = true) || name.contains(filter, ignoreCase = true)
}

/** Finds `@Preview` functions in compiled classes, without loading them. */
internal object PreviewScanner {
    private val previewAnnotations = setOf(
        "androidx.compose.ui.tooling.preview.Preview",
        "org.jetbrains.compose.ui.tooling.preview.Preview",
        "androidx.compose.desktop.ui.tooling.preview.Preview",
    )

    // @Preview is @Repeatable, so several of them on one function compile into a container annotation.
    private val containerAnnotations = previewAnnotations.map { "$it\$Container" }.toSet()

    fun scan(roots: List<File>): List<PreviewSpec> =
        ClassGraph()
            .overrideClasspath(roots)
            .enableClassInfo()
            .enableMethodInfo()
            .enableAnnotationInfo()
            .ignoreClassVisibility()
            .ignoreMethodVisibility()
            .scan()
            .use { result ->
                result.allClasses
                    .sortedBy { it.name }
                    .flatMap { classInfo ->
                        classInfo.declaredMethodInfo
                            .filter { it.isStatic }
                            .flatMap { method ->
                                method.annotationInfo
                                    .flatMap { expand(it, result, depth = 0) }
                                    .map { toSpec(classInfo.name, method.name, it) }
                            }
                    }
            }

    /** The @Preview annotations that [annotation] stands for: itself, a repeat container, or a multi-preview annotation. */
    private fun expand(annotation: AnnotationInfo, result: ScanResult, depth: Int): List<AnnotationInfo> = when {
        annotation.name in previewAnnotations -> listOf(annotation)
        annotation.name in containerAnnotations ->
            (annotation.parameterValues.getValue("value") as? Array<*>).orEmpty().filterIsInstance<AnnotationInfo>()
        depth < 4 ->
            result.getClassInfo(annotation.name)?.annotationInfo.orEmpty().flatMap { expand(it, result, depth + 1) }
        else -> emptyList()
    }

    private fun toSpec(className: String, methodName: String, annotation: AnnotationInfo): PreviewSpec {
        val values = annotation.parameterValues
        return PreviewSpec(
            className = className,
            methodName = methodName,
            name = values.getValue("name") as? String ?: "",
            group = values.getValue("group") as? String ?: "",
            widthDp = values.getValue("widthDp") as? Int ?: -1,
            heightDp = values.getValue("heightDp") as? Int ?: -1,
            showBackground = values.getValue("showBackground") as? Boolean ?: false,
            backgroundColor = values.getValue("backgroundColor") as? Long ?: 0L,
            fontScale = values.getValue("fontScale") as? Float ?: 1f,
        )
    }
}
