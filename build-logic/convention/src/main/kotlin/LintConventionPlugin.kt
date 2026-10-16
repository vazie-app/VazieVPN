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

/** Fails the build on colours, dimensions or font sizes written outside `:core:designsystem`. */
class LintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Captured here because inside a task-configuration block `path` is the task's path.
        val module = path
        val isDesignSystem = path == ":core:designsystem"
        val allowDimensionLiterals = isDesignSystem

        val task = tasks.register<CheckDesignTokensTask>("checkDesignTokens") {
            group = "verification"
            description = "Fails on hardcoded colours, dimensions or font sizes outside :core:designsystem."
            sources.from(layout.projectDirectory.dir("src"))
            allowColorLiterals.set(isDesignSystem)
            allowDimensions.set(allowDimensionLiterals)
            modulePath.set(module)
        }
        val localization = tasks.register<CheckLocalizationTask>("checkLocalization") {
            group = "verification"
            description = "Fails when a string exists in one of values/ and values-ru/ but not the other."
            resources.from(layout.projectDirectory.dir("src/main/res"))
            modulePath.set(module)
        }
        pluginManager.apply("vazie.preview.convention")
        tasks.matching { it.name == "check" }.configureEach { dependsOn(task, localization) }
    }
}

abstract class CheckDesignTokensTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val allowColorLiterals: Property<Boolean>

    @get:Input
    abstract val allowDimensions: Property<Boolean>

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun run() {
        val colorRegex = Regex("""Color\(\s*0x[0-9A-Fa-f]{6,8}""")
        val dimensionRegex = Regex("""(?<![\w.])\d+(\.\d+)?\.(dp|sp)\b""")
        val fontSizeRegex = Regex("""fontSize\s*=""")

        val violations = mutableListOf<String>()
        sources.asFileTree.matching { include("**/*.kt") }.forEach { file ->
            file.readLines().forEachIndexed { index, rawLine ->
                val line = rawLine.substringBefore("//")
                fun report(what: String) {
                    violations += "${file.path}:${index + 1}  $what  ->  ${rawLine.trim()}"
                }
                if (!allowColorLiterals.get() && colorRegex.containsMatchIn(line)) {
                    report("hardcoded colour (use VazieTheme.colors.*)")
                }
                if (!allowDimensions.get()) {
                    if (dimensionRegex.containsMatchIn(line)) report("hardcoded dimension (use VazieTheme.spacing.*)")
                    if (fontSizeRegex.containsMatchIn(line)) report("explicit fontSize (use VazieTheme.typography.*)")
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Design-token violations in ${modulePath.get()} (rule R10):")
                    violations.forEach { appendLine("  $it") }
                    append("Design values belong in :core:designsystem.")
                }
            )
        }
    }
}

/** Fails when `values/strings.xml` and `values-ru/strings.xml` do not declare the same set of names. */
abstract class CheckLocalizationTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resources: ConfigurableFileCollection

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun run() {
        val stringFiles = resources.asFileTree
            .matching { include("values/strings.xml", "values-ru/strings.xml") }
            .files
        val default = stringFiles.firstOrNull { it.parentFile.name == "values" }
        val russian = stringFiles.firstOrNull { it.parentFile.name == "values-ru" }
        if (default == null && russian == null) return
        if (default == null || russian == null) {
            throw GradleException(
                "${modulePath.get()} has only one of values/strings.xml and values-ru/strings.xml; " +
                    "Vazie ships both locales."
            )
        }

        val missingRussian = default.stringNames() - russian.stringNames()
        val orphanRussian = russian.stringNames() - default.stringNames()
        if (missingRussian.isEmpty() && orphanRussian.isEmpty()) return

        throw GradleException(
            buildString {
                appendLine("Localization parity broken in ${modulePath.get()}:")
                missingRussian.sorted().forEach { appendLine("  missing in values-ru/:  $it") }
                orphanRussian.sorted().forEach { appendLine("  missing in values/:     $it") }
            }
        )
    }

    private fun File.stringNames(): Set<String> =
        STRING_NAME.findAll(readText()).map { it.groupValues[1] }.toSet()

    private companion object {
        val STRING_NAME = Regex("""<string\s+name="([^"]+)"""")
    }
}
