plugins {
    id("vazie.android.feature")
    id("vazie.compose.test")
}

android {
    namespace = "app.vazie.vpn.feature.onboarding"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
}
