import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("vazie.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.vazie.vpn.engine.xray"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

// libXray: a GitHub-release AAR pinned by version and SHA-256, fetched at configuration time and consumed
// through a flatDir repository (see settings.gradle.kts). Licences in NOTICE.md.
val libXrayVersion = "v26.7.28"
val libXraySha256 = "28b7dc9d6cc8455fcca5cbd56e387003a7bfb558128651a64899dc3a8ccff666"
val libXrayUrl =
    "https://github.com/XTLS/libXray/releases/download/$libXrayVersion/libxray-android.zip"
val libXrayEntry = "libxray-android/libXray.aar"

val libXrayCache = layout.projectDirectory
    .dir("../../../third-party/libxray")
    .file("libxray-android-$libXrayVersion.zip")
    .asFile
val libXrayRepoDir = layout.buildDirectory.dir("libxray-repo").get().asFile
val libXrayAarFile = File(libXrayRepoDir, "libXray-$libXrayVersion.aar")

fun fetchAndVerifyLibXray() {
    if (libXrayAarFile.exists()) return

    if (!libXrayCache.exists()) {
        libXrayCache.parentFile.mkdirs()
        val partial = File(libXrayCache.parentFile, libXrayCache.name + ".part")
        URI(libXrayUrl).toURL().openStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        check(partial.renameTo(libXrayCache)) { "could not move the downloaded libXray archive" }
    }

    val actual = MessageDigest.getInstance("SHA-256").let { digest ->
        libXrayCache.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    if (actual != libXraySha256) {
        libXrayCache.delete()
        throw GradleException(
            "libXray $libXrayVersion checksum mismatch.\n" +
                "  expected $libXraySha256\n" +
                "  actual   $actual\n" +
                "The downloaded archive has been deleted. Do not bypass this check.",
        )
    }

    libXrayRepoDir.mkdirs()
    ZipFile(libXrayCache).use { zip ->
        val source = requireNotNull(zip.getEntry(libXrayEntry)) {
            "libXray archive does not contain $libXrayEntry"
        }
        zip.getInputStream(source).use { input ->
            libXrayAarFile.outputStream().use { stream -> input.copyTo(stream) }
        }
    }
}

fetchAndVerifyLibXray()

tasks.register("fetchLibXray") {
    group = "build setup"
    description = "Downloads and verifies the pinned libXray Android AAR."
    inputs.property("url", libXrayUrl)
    inputs.property("sha256", libXraySha256)
    outputs.file(libXrayAarFile)
    doLast { fetchAndVerifyLibXray() }
}

dependencies {
    // R3 keeps this module out of reach of everything except `:app`, so `:vpn:api` is the only
    // contract it needs and the only one it may have.
    implementation(project(":vpn:api"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    // JSON is the engine's wire format, and no other module builds Xray JSON.
    implementation(libs.kotlinx.serialization.json)

    // The group is arbitrary and ignored by `flatDir` resolution; only the name, version, and
    // `@aar` extension have to match the file `fetchAndVerifyLibXray()` writes.
    implementation("vendored:libXray:$libXrayVersion@aar")

    testImplementation(libs.kotlinx.coroutines.test)
}
