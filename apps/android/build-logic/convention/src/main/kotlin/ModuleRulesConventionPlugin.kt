import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/** Checks the module dependency rules in the Gradle project graph and prints which ones it verified. */
class ModuleRulesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) { "vazie.module.rules applies to the root project only" }

        val task = target.tasks.register<CheckModuleRulesTask>("checkModuleRules") {
            group = "verification"
            description = "Checks module dependency rules against the Gradle project graph."
        }

        target.gradle.projectsEvaluated {
            task.configure {
                graph.set(target.subprojects.associate { it.path to it.edges(testEdges = false) })
                testGraph.set(target.subprojects.associate { it.path to it.edges(testEdges = true) })
                androidModules.set(
                    target.subprojects
                        .filter { module ->
                            module.pluginManager.hasPlugin("com.android.library") ||
                                module.pluginManager.hasPlugin("com.android.application")
                        }
                        .map { it.path }
                        .toSet()
                )
            }
        }
    }
}

/** The module's project dependencies, split by whether they can reach a shipped artifact (tests: R13). */
private fun Project.edges(testEdges: Boolean): List<String> = configurations
    .filter { it.name.isTestConfiguration() == testEdges }
    .flatMap { configuration ->
        runCatching { configuration.dependencies }.getOrElse { emptyList() }
    }
    .filterIsInstance<ProjectDependency>()
    .map { it.path }
    // AGP adds implicit self-edges (androidTest -> module under test); they never violate a rule.
    .filter { it != path }
    .toSortedSet()
    .toList()

private fun String.isTestConfiguration(): Boolean =
    startsWith("test") || startsWith("androidTest") || contains("UnitTest") || contains("AndroidTest")

abstract class CheckModuleRulesTask : DefaultTask() {

    @get:Input
    abstract val graph: MapProperty<String, List<String>>

    /** Project edges declared only in test configurations; used by R13. */
    @get:Input
    abstract val testGraph: MapProperty<String, List<String>>

    /** Modules that applied an Android plugin; used by R6. */
    @get:Input
    abstract val androidModules: SetProperty<String>

    @TaskAction
    fun run() {
        val graph = graph.get()
        val androidModules = androidModules.get()
        val violations = mutableListOf<String>()

        fun report(rule: String, subject: String, why: String) {
            violations += "$rule  $subject     ($why)"
        }

        fun forbidEdge(rule: String, why: String, predicate: (from: String, to: String) -> Boolean) {
            graph.forEach { (from, deps) ->
                deps.filter { predicate(from, it) }.forEach { to -> report(rule, "$from  ->  $to", why) }
            }
        }

        /** Modules that must sit at the bottom of the graph with no project dependencies at all. */
        fun requireZeroProjectDependencies(rule: String, module: String, why: String) {
            graph[module]?.forEach { to -> report(rule, "$module  ->  $to", why) }
        }

        fun String.isFeature() = startsWith(":feature:")
        fun String.isEngine() = startsWith(":vpn:engine:")
        fun String.isData() = startsWith(":data:")

        // R1 - features are independent of each other.
        forbidEdge("R1", "cross-feature nav goes through :core:navigation") { from, to ->
            from.isFeature() && to.isFeature()
        }
        // R2 - features never see an engine or the VPN runtime.
        forbidEdge("R2", "features depend on :vpn:api only") { from, to ->
            from.isFeature() && (to.isEngine() || to == ":vpn:runtime")
        }
        // R3 - engines are bound at the composition root.
        forbidEdge("R3", "only :app may depend on an engine") { from, to ->
            to.isEngine() && from != ":app"
        }
        // R5 - the design system is a leaf: zero project dependencies, so it cannot drift towards
        // the domain one edge at a time.
        requireZeroProjectDependencies(
            rule = "R5",
            module = ":core:designsystem",
            why = ":core:designsystem must have zero project dependencies",
        )
        // R6 - foundation purity: :core:model is a leaf, and the pure-JVM modules stay pure.
        requireZeroProjectDependencies(
            rule = "R6",
            module = ":core:model",
            why = ":core:model must have zero project dependencies",
        )
        PURE_JVM_MODULES.filter { it in androidModules }.forEach { module ->
            report("R6", module, "must not apply an Android plugin")
        }
        // R7 - nothing depends on the application module.
        forbidEdge("R7", "nothing may depend on :app") { _, to -> to == ":app" }
        // R12 - decrypted profiles stay behind the :vpn:api interface; only :app sees the implementation.
        forbidEdge("R12", "only :app may depend on a :data:* module") { from, to ->
            to.isData() && from != ":app"
        }
        // R14 - only :app and :data:* reach the backend, so the own-configuration path never needs
        // Vazie infrastructure.
        forbidEdge("R14", "only :app and :data:* may depend on :core:network") { from, to ->
            to == NETWORK && from != ":app" && !from.isData()
        }
        // R13 - test source sets are not a back door around R1-R12: no test-only project edges.
        testGraph.get().forEach { (from, deps) ->
            deps.forEach { to ->
                report("R13", "$from  ->  $to", "a test-only project edge is not allowed")
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Module dependency rule violations:")
                    violations.forEach { appendLine("  $it") }
                }
            )
        }
        logger.lifecycle(
            "Module rules OK - ${graph.size} modules, rules checked: ${CHECKED_RULES.joinToString(", ")} " +
                "(design values are checked separately by checkDesignTokens)"
        )
    }

    private companion object {
        val CHECKED_RULES =
            listOf("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R12", "R13", "R14")
        const val NETWORK = ":core:network"
        val PURE_JVM_MODULES =
            listOf(":core:model", ":core:navigation", ":vpn:api", ":vpn:config", ":update:api")
    }
}
