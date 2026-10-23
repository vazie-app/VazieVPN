package app.vazie.vpn.core.network

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The module that carries the session token and the provisioned credential must not be able to print
 * either. */
class NoNetworkLoggingTest {

    @Test
    fun `nothing in the api client writes to a log`() {
        val offenders = File(MAIN).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readLines().any { line ->
                    val code = line.substringBefore("//")
                    FORBIDDEN.any { it.containsMatchIn(code) }
                }
            }
            .map { it.path }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "the module that carries session tokens and credentials is writing to a log:\n$offenders",
        )
    }

    @Test
    fun `the Ktor logging plugin is not installed`() {
        // Named separately from the scan above because it fails differently: `install(Logging)` is a
        // single legitimate-looking line that logs every request and response body in the app.
        val offenders = File(MAIN).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("io.ktor.client.plugins.logging") }
            .map { it.path }
            .toList()

        assertTrue(offenders.isEmpty(), "a Ktor logging plugin reached the API client:\n$offenders")
    }

    private companion object {
        const val MAIN = "src/main"

        val FORBIDDEN = listOf(
            Regex("""\bLog\s*\.\s*[dewiv]\s*\("""),
            Regex("""\bprintln\s*\("""),
            Regex("""\bprintStackTrace\s*\("""),
            Regex("""\bSystem\s*\.\s*(out|err)\b"""),
        )
    }
}
