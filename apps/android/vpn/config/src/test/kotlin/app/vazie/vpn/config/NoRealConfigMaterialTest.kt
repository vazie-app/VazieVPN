package app.vazie.vpn.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The module that reads configurations is the module most likely to grow one in a fixture, and a real
 * configuration committed once is a live credential in git history forever. */
class NoRealConfigMaterialTest {

    @Test
    fun `a share-link literal appears only where the format itself is declared`() {
        val scheme = "vless" + "://"
        val offenders = sources()
            .filter { it.name !in SCHEME_HOLDERS && it.readText().contains(scheme) }
            .map { it.path }

        assertTrue(offenders.isEmpty(), "a link literal outside $SCHEME_HOLDERS:\n$offenders")
    }

    @Test
    fun `every UUID-shaped literal is a synthetic one`() {
        val offenders = literals()
            .filter { UUID_SHAPE.containsMatchIn(it) }
            .filterNot { it.startsWith(SYNTHETIC_UUID_PREFIX) }

        assertTrue(offenders.isEmpty(), "a UUID that is not obviously invented:\n$offenders")
    }

    @Test
    fun `every hostname-shaped literal is a documentation domain`() {
        val offenders = literals()
            .mapNotNull { HOST_SHAPE.find(it)?.value }
            .filterNot { host -> RESERVED_DOMAINS.any { host.endsWith(it) } }

        assertTrue(offenders.isEmpty(), "a host that is not reserved for documentation:\n$offenders")
    }

    private fun sources(): List<File> = File("src").walkTopDown()
        .filter { it.isFile && it.extension == "kt" && it.name != javaClass.simpleName + ".kt" }
        .toList()

    private fun literals(): List<String> = sources()
        .flatMap { file -> STRING_LITERAL.findAll(file.readText()).map { it.groupValues[1] } }

    private companion object {
        /** The only files allowed to contain the share-link scheme. */
        val SCHEME_HOLDERS = setOf(
            "VlessLinkParser.kt",
            "VlessLinkSerializer.kt",
            "SyntheticLinks.kt",
        )
        val RESERVED_DOMAINS = listOf(".example.net", ".example.com", ".example.org", ".invalid")
        const val SYNTHETIC_UUID_PREFIX = "00000000-0000-"

        val STRING_LITERAL = Regex("\"([^\"\\\\\\n]*)\"")
        val UUID_SHAPE = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val HOST_SHAPE = Regex("(?<![A-Za-z0-9./-])[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+\\.[A-Za-z]{2,}")
    }
}
