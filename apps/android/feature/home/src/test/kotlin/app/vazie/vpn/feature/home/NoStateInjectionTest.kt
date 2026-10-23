package app.vazie.vpn.feature.home

import java.io.File
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.test.Test
import kotlin.test.assertTrue

/** Home shows the runtime, and there is no other way to put a state on it. */
class NoStateInjectionTest {

    @Test
    fun `nothing outside the state holder can hand it a state`() {
        // Is there any non-private way in that takes a `HomeUiState`?
        val injectors = HomeViewModel::class.declaredMemberFunctions
            .filter { it.visibility == KVisibility.PUBLIC || it.visibility == KVisibility.INTERNAL }
            .filter { function ->
                function.parameters.any { parameter ->
                    parameter.type.toString().contains(HomeUiState::class.simpleName!!) ||
                        parameter.type.toString().contains(ConnectionUiState::class.simpleName!!)
                }
            }
            .map { it.name }

        assertTrue(
            injectors.isEmpty(),
            "HomeViewModel grew a way to be handed a state: $injectors",
        )
    }

    @Test
    fun `no source set renders a state switcher`() {
        val offenders = File("src").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("${File.separator}test${File.separator}") }
            .filter { file ->
                val text = file.readText()
                text.contains("DebugStateSwitcher") || text.contains("DebugSwitcher")
            }
            .map { it.path }
            .toList()

        assertTrue(offenders.isEmpty(), "a state switcher is back in the app:\n$offenders")
    }

    @Test
    fun `fixtures are reachable only from previews and tests`() {
        // `HomeFixtures` still exists and should: previews and tests need every state drawn. What
        // it must not do is appear in a file that runs in the app.
        val offenders = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name.endsWith("Preview.kt") }
            .filterNot { it.name == "HomeFixtures.kt" }
            .filter { it.readText().contains("HomeFixtures") }
            .map { it.path }
            .toList()

        assertTrue(offenders.isEmpty(), "a fixture reached the running app:\n$offenders")
    }
}
