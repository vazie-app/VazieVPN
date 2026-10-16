plugins {
    id("vazie.jvm.library")
}

// Pure Kotlin: when an update is required, available or not needed, and what is remembered between checks.
// `:data:update` fetches and stores; `:app` shows. No Android, no network, no account.
dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
