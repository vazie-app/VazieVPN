package app.vazie.vpn.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import app.vazie.vpn.feature.home.HomeGraphRoute
import app.vazie.vpn.feature.home.navigateToHome
import app.vazie.vpn.feature.settings.GuidesRoute
import app.vazie.vpn.feature.settings.SettingsRoute
import app.vazie.vpn.feature.settings.navigateToSettings

@Composable
fun rememberVazieAppState(
    navController: NavHostController = rememberNavController(),
): VazieAppState = remember(navController) { VazieAppState(navController) }

/** Navigation state, and nothing else. */
@Stable
class VazieAppState(val navController: NavHostController) {

    val currentDestination: NavDestination?
        @Composable get() {
            val backStackEntry by navController.currentBackStackEntryAsState()
            return backStackEntry?.destination
        }

    /** Null outside the tabs — during onboarding and inside the add-configuration wizard. */
    /** On the Settings screen itself, not one of its sub-pages. */
    val isOnSettingsRoot: Boolean
        @Composable get() = currentDestination?.hasRoute(SettingsRoute::class) == true

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() {
            val destination = currentDestination ?: return null
            return TopLevelDestination.entries.firstOrNull { topLevel ->
                destination.hierarchy.any { it.hasRoute(topLevel.graphRoute) }
            }
        }

    /** Open the guides, arriving through the Settings tab rather than beside it. */
    fun navigateToGuides() {
        openSection(TopLevelDestination.SETTINGS)
        navController.navigate(GuidesRoute)
    }

    /** Opens a section over Home, which stays on the back stack; opening the current section does nothing. */
    fun openSection(destination: TopLevelDestination) {
        val options = navOptions { launchSingleTop = true }
        when (destination) {
            TopLevelDestination.HOME -> navigateToTopLevelDestination(TopLevelDestination.HOME)
            TopLevelDestination.SETTINGS -> navController.navigateToSettings(options)
        }
    }

    /** `saveState`/`restoreState` is what gives each tab its own back stack: leaving a tab remembers where
     * the user was in it, and coming back puts them there rather than at its start destination. */
    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        val options = navOptions {
            popUpTo<HomeGraphRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.HOME -> returnHome()
            TopLevelDestination.SETTINGS -> navController.navigateToSettings(options)
        }
    }

    /** Back to Home by taking everything above it off the stack. */
    private fun returnHome() {
        if (navController.popBackStack<HomeGraphRoute>(inclusive = false)) return
        navController.navigateToHome(
            navOptions {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            },
        )
    }

}
