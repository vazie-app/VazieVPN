plugins {
    id("vazie.jvm.library")
}

// Pure Kotlin (R6), no engine: parsing works even when an engine is off.
dependencies {
    api(project(":vpn:api"))
    implementation(project(":core:model"))

    // Test-only, and only so `SharedVectorCorpusTest` can read the cross-platform vectors in
    // `/product`. Nothing that ships parses JSON here: a share link is text, not a document.
    testImplementation(libs.kotlinx.serialization.json)
}

// The shared corpus lives outside this build, so Gradle cannot see it as an input on its own - which would
// let a vector change while the test still reported itself up to date. Declaring it keeps the caching honest.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("product/vless-vectors"))
        .withPropertyName("sharedVlessVectors")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
