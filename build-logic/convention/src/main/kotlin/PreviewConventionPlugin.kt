import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import java.io.File

/** Checks the project's preview convention: every screen and every reusable component has a preview file of
 * its own. */
class PreviewConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Captured here because inside a task-configuration block `path` is the task's path.
        val module = path
        val task = tasks.register<CheckPreviewConventionTask>("checkPreviewConvention") {
            group = "verification"
            description = "Fails when a screen or a reusable component has no preview file beside it."
            sources.from(layout.projectDirectory.dir("src"))
            modulePath.set(module)
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(task) }
    }
}

abstract class CheckPreviewConventionTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun run() {
        val kotlinFiles = sources.asFileTree.matching { include("**/*.kt") }.files
        val violations = mutableListOf<String>()

        val (previewFiles, subjects) = kotlinFiles.partition { it.name.endsWith(PREVIEW_SUFFIX) }
        val previewsByPath = previewFiles.associateBy { it.absolutePath }

        subjects.filter { it.needsPreview() }.forEach { subject ->
            val expected = File(subject.parentFile, subject.nameWithoutExtension + PREVIEW_SUFFIX)
            val preview = previewsByPath[expected.absolutePath]
            when {
                preview == null ->
                    violations += "${subject.path}  ->  expected a preview in ${expected.name}"

                !preview.hasPreviewAnnotation() ->
                    violations += "${preview.path}  ->  no @Preview / @VaziePreview / @VazieScreenPreview in it"
            }
        }

        // The other direction: a preview whose subject was renamed or deleted keeps compiling and
        // keeps rendering, and nothing else would ever point at it.
        previewFiles.forEach { preview ->
            val subject = File(preview.parentFile, preview.name.removeSuffix(PREVIEW_SUFFIX) + ".kt")
            if (!subject.isFile) {
                violations += "${preview.path}  ->  previews ${subject.name}, which does not exist"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Preview convention broken in ${modulePath.get()}:")
                    violations.sorted().forEach { appendLine("  $it") }
                    append("Every screen and every reusable component has a Foo.kt / FooPreview.kt pair.")
                }
            )
        }
        logger.lifecycle(
            "Preview convention OK - ${modulePath.get()}: ${subjects.count { it.needsPreview() }} " +
                "subjects, ${previewFiles.size} previews"
        )
    }

    /** Screens and files in a component package need a preview; routes, state and view models do not. */
    private fun File.needsPreview(): Boolean {
        val parent = parentFile?.name
        val inComponentPackage = parent == "component" || parent == "components"
        return (inComponentPackage || nameWithoutExtension.endsWith("Screen")) && !isGeneratedOrTest()
    }

    private fun File.isGeneratedOrTest(): Boolean =
        path.contains("/src/test/") || path.contains("/src/androidTest/")

    private fun File.hasPreviewAnnotation(): Boolean = PREVIEW_ANNOTATION.containsMatchIn(readText())

    private companion object {
        const val PREVIEW_SUFFIX = "Preview.kt"
        val PREVIEW_ANNOTATION = Regex("""@(Vazie)?(Screen)?Preview\b""")
    }
}
