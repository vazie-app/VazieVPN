package app.vazie.vpn.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** A widget shows the tunnel, and a tap cannot tell it otherwise. */
class NoWidgetStateInjectionTest {

    @Test
    fun `no widget renders a state switcher`() {
        val offenders = sources()
            .filter { it.readText().contains("DebugStateSwitcher") }
            .map { it.path }

        assertTrue(offenders.isEmpty(), "a widget state switcher is back:\n$offenders")
    }

    @Test
    fun `the only thing a widget draws is what the publisher wrote`() {
        // `WidgetFixtures` stays for previews. What it must not do is appear in a Glance composition
        // or in the state store — the two places a launcher's process actually reads.
        val offenders = sources()
            .filterNot { it.name.endsWith("Preview.kt") }
            .filterNot { it.name == "WidgetFixtures.kt" }
            .filter { it.readText().contains("WidgetFixtures") }
            .map { it.path }

        assertTrue(offenders.isEmpty(), "a fixture reached a widget the launcher draws:\n$offenders")
    }

    private fun sources(): List<File> = File("src/main").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
}
