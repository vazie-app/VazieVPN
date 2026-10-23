plugins {
    id("vazie.android.library")
}

android {
    namespace = "app.vazie.vpn.data.subscriptions"
}

// Downloads a subscription's list from the address the user gave; implements `:vpn:config`'s
// `SubscriptionSource`. Only `:app` sees it (R12). Deliberately without `:core:network`: that client speaks
// to the Vazie backend and carries the session, and neither has any business in a request to someone else's
// server.
dependencies {
    api(project(":vpn:config"))
    implementation(project(":core:model"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
