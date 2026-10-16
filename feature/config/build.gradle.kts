plugins {
    id("vazie.android.feature")
    id("vazie.compose.test")
}

android {
    namespace = "app.vazie.vpn.feature.config"
}

dependencies {
    implementation(project(":core:designsystem"))
    // The one feature that may see the parser.
    implementation(project(":vpn:api"))
    implementation(project(":vpn:config"))
    implementation(libs.androidx.hilt.navigation.compose)
}
