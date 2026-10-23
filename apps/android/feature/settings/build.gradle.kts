plugins {
    id("vazie.android.feature")
    id("vazie.compose.test")
}

android {
    namespace = "app.vazie.vpn.feature.settings"
}

// No :core:navigation: every destination Settings opens is its own screen or goes out through `:app`.
dependencies {
    implementation(project(":core:designsystem"))
    // The app-icon choice is drawn here and applied in `:app`; `VazieAppIcon` is the shared word
    // for it, and `:core:model` is the only module both are allowed to look at.
    implementation(project(":core:model"))
}
