plugins {
    id("vazie.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.vazie.vpn.data.links"
}

// The About screen's links on the wire and on disk. Only `:app` sees it (R12). Talks to the backend without a
// session, and only when Settings is open: the links are the same for everyone.
dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.mock)
}
