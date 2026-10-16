package app.vazie.vpn.engine.xray

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The module that builds the engine's configuration must not be able to print it. */
class NoEngineLoggingTest {

    @Test
    fun `nothing in the engine binding writes to a log`() {
        val offenders = sources().filter { file ->
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

    private fun sources(): List<File> = File("src/main").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private companion object {
        val FORBIDDEN = listOf(
            Regex("""\bLog\s*\.\s*[dewiv]\s*\("""),
            Regex("""\bprintln\s*\("""),
            Regex("""\bprintStackTrace\s*\("""),
            Regex("""\bSystem\s*\.\s*(out|err)\b"""),
        )
    }
}
