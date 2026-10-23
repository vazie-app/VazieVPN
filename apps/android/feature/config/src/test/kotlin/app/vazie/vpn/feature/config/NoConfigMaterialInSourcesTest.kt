package app.vazie.vpn.feature.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** No real configuration in this module's sources; patterns are split so the file skips
 * itself. */
class NoConfigMaterialInSourcesTest {

    @Test
    fun `no protocol link or key material appears in the sources`() {
        val scheme = "://"
        val forbidden = listOf("vless", "vmess", "trojan", "wireguard").map { it + scheme } +
            listOf("PrivateKey" + " =", "PresharedKey" + " =")

        val offenders = File("src").walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .flatMap { file ->
                val text = file.readText()
                forbidden.filter { text.contains(it, ignoreCase = true) }
                    .map { "${file.path} contains $it" }
            }
            .toList()

        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }
}
