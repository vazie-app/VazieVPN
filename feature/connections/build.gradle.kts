plugins {
    id("vazie.android.feature")
    // Compose tests for the inventory rows.
    id("vazie.compose.test")
}

android {
    namespace = "app.vazie.vpn.feature.connections"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    // The list is stored profiles now. `:vpn:api` is the only VPN module a feature may see, and
    // what arrives through it are summaries with no field capable of holding a secret (R2, R12).
    implementation(project(":vpn:api"))
    // `LastUsed` and the other shared value types.
    implementation(project(":core:model"))
    implementation(libs.androidx.hilt.navigation.compose)
}
