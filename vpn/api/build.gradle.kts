plugins {
    id("vazie.jvm.library")
}

// Pure Kotlin (R6). This is the only VPN module a feature may import, so anything added here is added to
// every feature at once — and an Android type here would put `:vpn:api` out of reach of a plain JVM test.
dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    // `connectionTicks` is the one thing here with a cadence, and a cadence is only testable
    // against a scheduler that can be moved by hand.
    testImplementation(libs.kotlinx.coroutines.test)
}
