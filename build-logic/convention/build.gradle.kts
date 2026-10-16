plugins {
    `kotlin-dsl`
}

group = "app.vazie.vpn.buildlogic"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "vazie.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "vazie.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "vazie.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "vazie.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "vazie.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("hilt") {
            id = "vazie.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("test") {
            id = "vazie.test"
            implementationClass = "TestConventionPlugin"
        }
        register("lint") {
            id = "vazie.lint"
            implementationClass = "LintConventionPlugin"
        }
        register("composeTest") {
            id = "vazie.compose.test"
            implementationClass = "ComposeTestConventionPlugin"
        }
        register("previewConvention") {
            id = "vazie.preview.convention"
            implementationClass = "PreviewConventionPlugin"
        }
        register("moduleRules") {
            id = "vazie.module.rules"
            implementationClass = "ModuleRulesConventionPlugin"
        }
    }
}
