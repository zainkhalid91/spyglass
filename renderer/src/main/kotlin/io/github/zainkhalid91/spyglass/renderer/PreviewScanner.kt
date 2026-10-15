package io.github.zainkhalid91.spyglass.renderer

import io.github.classgraph.AnnotationClassRef
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
    val uiMode: Int,
    val device: String,
    val showSystemUi: Boolean,
    /** For `@PreviewParameter` previews: the provider class and how many of its values to render. */
    val parameterProvider: String? = null,
    val parameterLimit: Int = Int.MAX_VALUE,
    /** Set when this spec is one value of a `@PreviewParameter` preview. */
    val parameterIndex: Int? = null,
) {
    val functionName: String get() = "$className.$methodName"

    /** `uiMode` carries Android's night bits: UI_MODE_NIGHT_YES (0x20) under UI_MODE_NIGHT_MASK (0x30). */
    val dark: Boolean get() = uiMode and 0x30 == 0x20

    /** File name without extension, e.g. `CardsKt.WeatherCardPreview_Dark`. */
    val fileStem: String
        get() {
            val base = "${className.substringAfterLast('.')}.$methodName"
            val named = if (name.isEmpty()) base else "${base}_$name"
            val full = if (parameterIndex == null) named else "${named}_$parameterIndex"
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

    // @Preview is @Repeatable, so several of them in one place compile into a container annotation.
    private val containerAnnotations = previewAnnotations.map { "$it\$Container" }.toSet()

    // Annotations that can never be multi-preview annotations; skipping them avoids needless library scans.
    private val ignoredPrefixes = listOf(
        "kotlin.", "java.", "javax.", "kotlinx.", "org.jetbrains.annotations.",
        "androidx.annotation.", "androidx.compose.runtime.",
    )

    private const val MAX_DEPTH = 4
    private val previewParameterAnnotations = setOf(
        "androidx.compose.ui.tooling.preview.PreviewParameter",
        "org.jetbrains.compose.ui.tooling.preview.PreviewParameter",
    )

    /**
     * @param roots the module's compiled classes, where previews are looked for.
     * @param libraries the rest of the runtime classpath, searched only for multi-preview annotations
     *   declared outside the module, such as `@PreviewLightDark` or a shared design-system annotation.
     */
    fun scan(roots: List<File>, libraries: List<File>): List<PreviewSpec> =
        ClassGraph()
            .overrideClasspath(roots)
            .enableClassInfo()
            .enableMethodInfo()
            .enableAnnotationInfo()
            .ignoreClassVisibility()
            .ignoreMethodVisibility()
            .scan()
            .use { project ->
                val library = LibraryAnnotations(project, libraries)
                try {
                    val methods = project.allClasses.sortedBy { it.name }
                        .flatMap { c -> c.declaredMethodInfo.map { c.name to it } }
                    library.load(methods.flatMap { (_, m) -> m.annotationInfo.map { it.name } })
                    methods.flatMap { (className, method) ->
                        val parameter = method.parameterInfo.firstNotNullOfOrNull { p ->
                            p.annotationInfo.firstOrNull { it.name in previewParameterAnnotations }
                        }
                        method.annotationInfo
                            .flatMap { expand(it, library, depth = 0) }
                            .map { toSpec(className, method.name, it, parameter) }
                    }
                } finally {
                    library.close()
                }
            }

    /** The @Preview annotations that [annotation] stands for: itself, a repeat container, or a multi-preview annotation. */
    private fun expand(annotation: AnnotationInfo, library: LibraryAnnotations, depth: Int): List<AnnotationInfo> = when {
        annotation.name in previewAnnotations -> listOf(annotation)
        annotation.name in containerAnnotations ->
            (annotation.parameterValues.getValue("value") as? Array<*>).orEmpty().filterIsInstance<AnnotationInfo>()
        depth < MAX_DEPTH && isCandidate(annotation.name) ->
            library.annotationsOf(annotation.name).flatMap { expand(it, library, depth + 1) }
        else -> emptyList()
    }

    private fun isCandidate(name: String) =
        name !in previewAnnotations && name !in containerAnnotations && ignoredPrefixes.none(name::startsWith)

    private fun toSpec(className: String, methodName: String, annotation: AnnotationInfo, parameter: AnnotationInfo?): PreviewSpec {
        val values = annotation.parameterValues
        val parameterValues = parameter?.parameterValues
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
            uiMode = values.getValue("uiMode") as? Int ?: 0,
            device = values.getValue("device") as? String ?: "",
            showSystemUi = values.getValue("showSystemUi") as? Boolean ?: false,
            parameterProvider = (parameterValues?.getValue("provider") as? AnnotationClassRef)?.name,
            parameterLimit = parameterValues?.getValue("limit") as? Int ?: Int.MAX_VALUE,
        )
    }

    /**
     * Class-level annotations of annotation classes, from the module itself or, failing that, from its
     * libraries. Library jars are scanned once per nesting level, only for the names still missing.
     */
    private class LibraryAnnotations(private val project: ScanResult, private val libraries: List<File>) : AutoCloseable {
        private val found = mutableMapOf<String, List<AnnotationInfo>>()
        private val scans = mutableListOf<ScanResult>() // AnnotationInfo stays valid only while its scan is open

        // ClassGraph returns empty placeholders for classes the module only references; those count as missing.
        private fun inProject(name: String) = project.getClassInfo(name)?.takeUnless { it.isExternalClass }

        fun annotationsOf(name: String): List<AnnotationInfo> =
            inProject(name)?.annotationInfo ?: found[name].orEmpty()

        fun load(methodAnnotations: List<String>) {
            var missing = methodAnnotations.filter { isCandidate(it) && inProject(it) == null }.toSet()
            var depth = 0
            while (missing.isNotEmpty() && libraries.isNotEmpty() && depth++ < MAX_DEPTH) {
                val scan = ClassGraph()
                    .overrideClasspath(libraries)
                    .acceptClasses(*missing.toTypedArray())
                    .enableClassInfo()
                    .enableAnnotationInfo()
                    .ignoreClassVisibility()
                    .scan()
                scans += scan
                val next = mutableSetOf<String>()
                for (name in missing) {
                    val annotations = scan.getClassInfo(name)?.takeUnless { it.isExternalClass }?.annotationInfo ?: continue
                    found[name] = annotations
                    annotations.map { it.name }
                        .filter { isCandidate(it) && it !in found && inProject(it) == null }
                        .forEach(next::add)
                }
                missing = next - found.keys
            }
        }

        override fun close() = scans.forEach(ScanResult::close)
    }
}
