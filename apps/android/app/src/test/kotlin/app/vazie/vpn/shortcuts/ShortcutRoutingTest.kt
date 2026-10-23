package app.vazie.vpn.shortcuts

import app.vazie.vpn.feature.config.ADD_CONFIG_DEEP_LINK
import app.vazie.vpn.feature.home.HOME_CONNECT_DEEP_LINK
import app.vazie.vpn.feature.home.HOME_DEEP_LINK
import app.vazie.vpn.feature.home.homeConnectDeepLink
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Where a launcher shortcut goes, and what it is allowed to carry there. */
class ShortcutRoutingTest {

    @Test
    fun `every shortcut lands on a destination the manifest can receive`() {
        val schemes = manifestSchemes()
        deepLinks().forEach { deepLink ->
            val scheme = deepLink.substringBefore("://")
            assertTrue(scheme in schemes, "$deepLink has no matching filter in the manifest")
        }
    }

    @Test
    fun `connecting to a configuration is Home with arguments, not a screen of its own`() {
        val link = homeConnectDeepLink(EXAMPLE_PROFILE_ID)

        assertTrue(link.startsWith("$HOME_DEEP_LINK?"), "the connect link left Home: $link")
        assertTrue(link.contains("connect=true"))
        assertTrue(link.contains("profile=$EXAMPLE_PROFILE_ID"))
    }

    @Test
    fun `a shortcut carries a destination and a profile id, and nothing else`() {
        deepLinks().forEach { deepLink ->
            val parameters = deepLink.substringAfter("?", missingDelimiterValue = "")
                .split("&")
                .filter { it.isNotEmpty() }
                .map { it.substringBefore("=") }
            assertTrue(
                parameters.all { it == "connect" || it == "profile" },
                "$deepLink carries a parameter that is neither a destination nor an id",
            )
        }
    }

    @Test
    fun `nothing resembling a configuration reaches a shortcut`() {
        deepLinks().forEach { deepLink ->
            FORBIDDEN.forEach { pattern ->
                assertTrue(
                    !pattern.containsMatchIn(deepLink),
                    "$deepLink looks like it carries configuration material",
                )
            }
        }
    }

    /** A stand-in id, deliberately not shaped like a real one. */
    private fun deepLinks() = listOf(
        HOME_CONNECT_DEEP_LINK,
        homeConnectDeepLink(EXAMPLE_PROFILE_ID),
        ADD_CONFIG_DEEP_LINK,
    )

    private fun manifestSchemes(): Set<String> {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue(manifest.exists(), "expected the manifest at ${manifest.absolutePath}")
        return SCHEME.findAll(manifest.readText()).map { it.groupValues[1] }.toSet()
    }

    private companion object {
        const val EXAMPLE_PROFILE_ID = "an-example-profile-id"
        val SCHEME = Regex("""android:scheme="([^"]+)"""")
        val FORBIDDEN = listOf(
            Regex("""@"""),
            Regex("""\d{1,3}(\.\d{1,3}){3}"""),
            Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""),
            Regex("""(?<!vazie-vpn)://"""),
            Regex("""(?i)(uuid|secret|token|key=)"""),
        )
    }
}
