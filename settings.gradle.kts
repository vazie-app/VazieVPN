pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The verified libXray AAR as module "vendored:libXray": AGP will not bundle a raw local .aar, and
        // FAIL_ON_PROJECT_REPOS puts the repository here. See `fetchAndVerifyLibXray()`.
        flatDir { dirs(File(rootDir, "vpn/engine/xray/build/libxray-repo")) }
    }
}

rootProject.name = "Vazie"

// Modules are created when they get real content, never as placeholders.
include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:navigation")
// The backend client: the public version and links endpoints, never a session.
include(":core:network")
// Encryption at rest, for the profile store.
include(":core:crypto")
// The VPN contracts and the link parser.
include(":vpn:api")
include(":vpn:config")
// The tunnel and VpnService, and the Xray engine binding (visible to `:app` only, R3).
include(":vpn:runtime")
include(":vpn:engine:xray")
include(":data:profiles")
// Downloads a subscription link's list from its own server. Never the Vazie backend, never with a session.
include(":data:subscriptions")
include(":feature:home")
include(":feature:connections")
include(":feature:config")
include(":feature:settings")
include(":feature:onboarding")
// Which build is current and which must update, from the backend's public version endpoint. An update check
// needs no session, and never stands in the way of connecting.
include(":update:api")
include(":data:update")
// The About screen's links and the words under them, from the backend's public links endpoint, cached on disk.
include(":data:links")
