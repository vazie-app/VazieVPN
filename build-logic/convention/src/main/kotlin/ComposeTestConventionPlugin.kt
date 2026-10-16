import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Lets a module test its Compose UI on the JVM, under Robolectric. */
class ComposeTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Robolectric needs the merged resources and the module's assets — the bundled fonts among them — or
        // Compose would silently fall back to the platform font.
        extensions.findByType(LibraryExtension::class.java)
            ?.testOptions?.unitTests?.isIncludeAndroidResources = true
        extensions.findByType(ApplicationExtension::class.java)
            ?.testOptions?.unitTests?.isIncludeAndroidResources = true

        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
            // The consumer's own debug manifest is what supplies the activity `createComposeRule`
            // launches, so this one cannot come transitively.
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        }
    }
}
