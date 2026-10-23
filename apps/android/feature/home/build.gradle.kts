plugins {
    id("vazie.android.feature")
    id("vazie.compose.test")
}

android {
    namespace = "app.vazie.vpn.feature.home"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    // Home reads the controller and the selection through `:vpn:api` only (R2).
    implementation(project(":vpn:api"))
    implementation(libs.androidx.hilt.navigation.compose)
    // For `NoStateInjectionTest`, which inspects the state holder's shape.
    testImplementation(libs.kotlin.reflect)
}
