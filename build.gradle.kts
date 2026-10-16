plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    id("vazie.module.rules")
}

// `./gradlew check` at the root also verifies the module graph.
tasks.register("check") {
    group = "verification"
    dependsOn("checkModuleRules")
}
