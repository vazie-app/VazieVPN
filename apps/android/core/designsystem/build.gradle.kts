plugins {
    id("vazie.android.library.compose")
    id("vazie.compose.test")
}

android {
    namespace = "app.vazie.vpn.core.designsystem"
}

dependencies {
    // Glance, so the widget token layer lives with every other design value.
    api(libs.androidx.glance.appwidget)
}
