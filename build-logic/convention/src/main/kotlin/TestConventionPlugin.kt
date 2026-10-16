import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class TestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("kotlin-test").get())
            // Android unit tests do not get the JUnit binding of kotlin-test implicitly under AGP 9
            // built-in Kotlin, so bind it explicitly for every module.
            add("testImplementation", libs.findLibrary("kotlin-test-junit").get())
        }
    }
}
