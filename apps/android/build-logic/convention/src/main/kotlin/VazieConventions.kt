import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.int(alias: String): Int = string(alias).toInt()

internal fun VersionCatalog.string(alias: String): String =
    findVersion(alias).get().requiredVersion

/** Android configuration shared by every Vazie Android module. */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.compileSdk = libs.int("compileSdk")
    extension.defaultConfig.minSdk = libs.int("minSdk")
    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    extensions.getByType<KotlinAndroidProjectExtension>().compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Off so an AGP/Kotlin deprecation cannot block a build while AGP 9 is new; revisit once it settles.
        allWarningsAsErrors.set(false)
    }
}
