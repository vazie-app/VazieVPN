plugins {
    id("vazie.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.vazie.vpn.data.profiles"
}

// The only module that decrypts stored secrets; only `:app` sees it (R12).
dependencies {
    implementation(project(":vpn:api"))
    implementation(project(":core:model"))
    implementation(project(":core:crypto"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
