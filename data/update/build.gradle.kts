plugins {
    id("vazie.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.vazie.vpn.data.update"
}

// The version endpoint on the wire and the update state on disk; implements `:update:api`. Only `:app` sees it
// (R12). Talks to the backend without a session: an update check is never an account's.
dependencies {
    api(project(":update:api"))
    implementation(project(":core:network"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.mock)
}
