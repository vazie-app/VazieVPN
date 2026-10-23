plugins {
    id("vazie.android.library")
}

android {
    namespace = "app.vazie.vpn.core.crypto"
}

// One implementation of encryption at rest, shared rather than copied.

// Robolectric only, and only for the test that binds to the platform: an Android Keystore exists
// nowhere else, and the class under test is exactly the binding.
dependencies {
    testImplementation(libs.robolectric)
}
