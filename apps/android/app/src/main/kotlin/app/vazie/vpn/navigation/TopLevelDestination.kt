package app.vazie.vpn.navigation

import app.vazie.vpn.feature.home.HomeGraphRoute
import app.vazie.vpn.feature.settings.SettingsGraphRoute
import kotlin.reflect.KClass

/** The top-level destinations — the whole information architecture. The configurations live inside Home, not in a
 * destination of their own. */
enum class TopLevelDestination(val graphRoute: KClass<*>) {
    HOME(HomeGraphRoute::class),
    SETTINGS(SettingsGraphRoute::class),
}
