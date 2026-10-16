package app.vazie.vpn.runtime

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The module that holds a decrypted profile must not be able to print one. */
class NoRuntimeLoggingTest {

    @Test
    fun `nothing in the runtime writes to a log`() {
        val offenders = sources(MAIN).filter { file ->
            file.readLines().any { line ->
                val code = line.substringBefore("//")
                FORBIDDEN.any { it.containsMatchIn(code) }
            }
        }.map { it.path }

        assertTrue(
            offenders.isEmpty(),
            "the module that holds decrypted profiles is writing to a log:\n$offenders",
        )
    }

    @Test
    fun `the release build's trace sink writes nothing`() {
        // The carve-out, and its boundary.
        val release = sources(RELEASE)
        assertTrue(release.isNotEmpty(), "the release trace sink is missing entirely")

        val offenders = release.filter { file ->
            file.readLines().any { line ->
                val code = line.substringBefore("//")
                FORBIDDEN.any { it.containsMatchIn(code) }
            }
        }.map { it.path }

        assertTrue(offenders.isEmpty(), "the release build is writing to a log:\n$offenders")
    }

    private fun sources(root: String): List<File> = File(root).walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private companion object {
        const val MAIN = "src/main"
        const val RELEASE = "src/release"

        val FORBIDDEN = listOf(
            Regex("""\bLog\s*\.\s*[dewiv]\s*\("""),
            Regex("""\bprintln\s*\("""),
            Regex("""\bprintStackTrace\s*\("""),
            Regex("""\bSystem\s*\.\s*(out|err)\b"""),
        )
    }
}
