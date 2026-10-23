package app.vazie.vpn.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.vazie.vpn.feature.home.HomeGraphRoute
import app.vazie.vpn.feature.home.HomeRoute
import app.vazie.vpn.feature.settings.AppearanceRoute
import app.vazie.vpn.feature.settings.SettingsGraphRoute
import app.vazie.vpn.feature.settings.SettingsRoute
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Navigation without a bottom bar: sections open over Home via `openSection`, so "back" returns to Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TopLevelNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Settings opens over Home and back returns to Home`() {
        assertRoundTrip(TopLevelDestination.SETTINGS, SettingsRoute::class.simpleName!!)
    }

    @Test
    fun `a Settings sub-page is still in Settings`() {
        var state: VazieAppState? = null
        var section: TopLevelDestination? = null
        compose.setContent {
            val appState = rememberVazieAppState().also { state = it }
            Graph(appState)
            LaunchedEffect(Unit) {
                appState.openSection(TopLevelDestination.SETTINGS)
                appState.navController.navigate(AppearanceRoute)
            }
            section = appState.currentTopLevelDestination
        }
        compose.waitForIdle()
        assertEquals(TopLevelDestination.SETTINGS, section)
        assertTrue(state!!.navController.currentDestination?.route?.contains("AppearanceRoute") == true)
    }

    /** Going Home from a Settings sub-page after a tour replay lands on Home with nothing over it. */
    @Test
    fun `going Home from a Settings sub-page lands on Home`() {
        var state: VazieAppState? = null
        compose.setContent {
            val appState = rememberVazieAppState().also { state = it }
            Graph(appState)
            LaunchedEffect(Unit) {
                appState.openSection(TopLevelDestination.SETTINGS)
                appState.navController.navigate(AppearanceRoute)
            }
        }
        compose.waitForIdle()
        val controller = state!!.navController
        compose.runOnIdle { state!!.navigateToTopLevelDestination(TopLevelDestination.HOME) }
        compose.waitForIdle()
        assertTrue(
            controller.currentDestination?.route?.contains(HomeRoute::class.simpleName!!) == true,
            "going Home from Settings did not land on Home: ${controller.currentDestination?.route}",
        )
        compose.runOnIdle { controller.popBackStack() }
        compose.waitForIdle()
        assertTrue(controller.currentDestination == null, "Settings stayed under Home after going Home")
    }

    @Test
    fun `the app shell draws no bottom bar`() {
        val shell = File(root(), "app/src/main/kotlin/app/vazie/vpn/ui/VazieApp.kt").readText()
        assertFalse(shell.contains("VazieBottomBar"), "the bottom bar came back to the app shell")
    }

    private fun assertRoundTrip(destination: TopLevelDestination, expected: String) {
        var state: VazieAppState? = null
        compose.setContent {
            val appState = rememberVazieAppState().also { state = it }
            Graph(appState)
            LaunchedEffect(Unit) { appState.openSection(destination) }
        }
        compose.waitForIdle()
        val controller = state!!.navController
        assertTrue(controller.currentDestination?.route?.contains(expected) == true, "did not arrive at $expected")
        compose.runOnIdle { controller.popBackStack() }
        compose.waitForIdle()
        assertTrue(
            controller.currentDestination?.route?.contains(HomeRoute::class.simpleName!!) == true,
            "back from $expected did not return to Home: ${controller.currentDestination?.route}",
        )
    }

    @androidx.compose.runtime.Composable
    private fun Graph(state: VazieAppState) {
        NavHost(navController = state.navController, startDestination = HomeGraphRoute) {
            navigation<HomeGraphRoute>(startDestination = HomeRoute()) { composable<HomeRoute> { } }
            navigation<SettingsGraphRoute>(startDestination = SettingsRoute) {
                composable<SettingsRoute> { }
                composable<AppearanceRoute> { }
            }
        }
    }

    private fun root(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return dir
    }
}
