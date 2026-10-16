package app.vazie.vpn.network

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The HTTP inspector stays next to the HTTP client, and nowhere else. */
class ChuckerDependencyTest {

    @Test
    fun `only core network declares the inspector`() {
        val declaring = buildFiles()
            .filter { it.readText().contains(DEPENDENCY) }
            .map { it.moduleRelativePath() }
            .sorted()

        assertEquals(
            listOf("core/network/build.gradle.kts"),
            declaring,
            "the inspector is declared outside :core:network",
        )
    }

    @Test
    fun `the real library is debug-only and the no-op covers everything else`() {
        val build = File(root(), "core/network/build.gradle.kts").readText()

        assertTrue(
            build.contains("debugImplementation(libs.chucker)"),
            "the real inspector is no longer debug-only",
        )
        assertTrue(
            build.contains("releaseImplementation(libs.chucker.no.op)"),
            "the release variant no longer links the no-op",
        )
        assertTrue(
            !build.contains("implementation(libs.chucker)"),
            "the inspector is declared for every variant, which would ship it in release",
        )
    }

    @Test
    fun `no feature or vpn module can reach the inspector`() {
        val offenders = offendersUnder(root())

        assertTrue(offenders.isEmpty(), "a feature or vpn module declares the inspector:\n$offenders")
    }

    @Test
    fun moduleClassificationIsLayoutIndependent() {
        // Regression: an ancestor directory named "vpn" must not make core/network look like a vpn module.
        val tempBase = createTempDirectory(prefix = "chucker-layout-").toFile()
        val tempRoot = File(tempBase, "apps/vpn/android").apply { mkdirs() }
        try {
            File(tempRoot, "settings.gradle.kts").writeText("")
            File(tempRoot, "core/network").mkdirs()
            File(tempRoot, "core/network/build.gradle.kts").writeText("dependencies { debugImplementation($DEPENDENCY) }")

            val offenders = offendersUnder(tempRoot)

            assertTrue(
                offenders.isEmpty(),
                "an ancestor directory named \"vpn\" must not make core/network an offender:\n$offenders",
            )
        } finally {
            tempBase.deleteRecursively()
        }
    }

    @Test
    fun `a genuinely forbidden module declaring the inspector is caught`() {
        val tempRoot = createTempDirectory(prefix = "chucker-forbidden-").toFile()
        try {
            File(tempRoot, "settings.gradle.kts").writeText("")
            File(tempRoot, "vpn/runtime").mkdirs()
            File(tempRoot, "vpn/runtime/build.gradle.kts")
                .writeText("dependencies { debugImplementation($DEPENDENCY) }")
            File(tempRoot, "feature/home").mkdirs()
            File(tempRoot, "feature/home/build.gradle.kts")
                .writeText("dependencies { debugImplementation($DEPENDENCY) }")
            File(tempRoot, "core/network").mkdirs()
            File(tempRoot, "core/network/build.gradle.kts")
                .writeText("dependencies { debugImplementation($DEPENDENCY) }")

            val offenders = offendersUnder(tempRoot)

            assertEquals(
                listOf("feature/home/build.gradle.kts", "vpn/runtime/build.gradle.kts"),
                offenders.sorted(),
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun root(): File {
        // Tests run with `:app` as the working directory; the module tree is one level up.
        var dir = File(".").absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return dir
    }

    private fun File.moduleRelativePath(): String = relativeTo(root()).invariantSeparatorsPath

    /** Every `build.gradle.kts` under [root] whose module group is [FORBIDDEN_GROUPS] and which declares [DEPENDENCY]. */
    private fun offendersUnder(root: File): List<String> = root.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
        .filter { it.isFile && it.name == "build.gradle.kts" }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .filter { it.substringBefore('/') in FORBIDDEN_GROUPS }
        .filter { File(root, it).readText().contains(DEPENDENCY) }
        .toList()

    private companion object {
        /** The version-catalog accessor, which is how a dependency on it can be written at all. */
        const val DEPENDENCY = "libs.chucker"

        /** Top-level Gradle module groups (`:feature:*`, `:vpn:*`) the inspector must never reach. */
        val FORBIDDEN_GROUPS = setOf("feature", "vpn")
    }

    private fun buildFiles(): List<File> = root().walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
        .filter { it.isFile && it.name == "build.gradle.kts" }
        .toList()
}
