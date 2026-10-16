import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("vazie.lint")
        pluginManager.apply("vazie.test")

        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig {
                targetSdk = libs.int("targetSdk")
                versionCode = libs.int("vazieVersionCode")
                versionName = libs.string("vazieVersionName")
            }
            // Off by default in AGP 9; application modules need it for build-derived settings.
            buildFeatures.buildConfig = true
        }
    }
}
