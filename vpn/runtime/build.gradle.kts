plugins {
    id("vazie.android.library")
    id("vazie.hilt")
}

android {
    namespace = "app.vazie.vpn.runtime"

    // JVM tests hit `VpnTrace`'s `android.util.Log`; defaults are fine. `NetworkMonitorTest` uses
    // Robolectric.
    testOptions.unitTests.isReturnDefaultValues = true
}

// Owns the connection; engines are bound by `:app` (R3), so no feature, screen or engine is here.
dependencies {
    api(project(":vpn:api"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // `VpnTraceVocabularyTest` checks by reflection that no trace type has a text field.
    testImplementation(libs.kotlin.reflect)
}
