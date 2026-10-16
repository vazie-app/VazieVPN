package app.vazie.vpn.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import app.vazie.vpn.core.designsystem.feedback.rememberVazieHaptics
import app.vazie.vpn.core.navigation.HomeNavigator
import kotlinx.serialization.Serializable

const val HOME_DEEP_LINK: String = "vazie-vpn://home"

/** Home, with the connect flow already asked for. */
const val HOME_CONNECT_DEEP_LINK: String = "vazie-vpn://home?connect=true"

private const val CONNECTIONS_LINK: String = "vazie-vpn://connections"

/** The same link, aimed at one configuration. */
fun homeConnectDeepLink(profileId: String): String =
    "$HOME_CONNECT_DEEP_LINK&profile=$profileId"

@Serializable
data object HomeGraphRoute

@Serializable
data class HomeRoute(val connect: Boolean = false, val profile: String? = null)

@Serializable
data object ConnectionDetailsRoute

fun NavController.navigateToHome(navOptions: NavOptions? = null) = navigate(HomeGraphRoute, navOptions)

/** Asks Android for VPN permission on Home's behalf. */
fun interface VpnConsentPrompt {
    fun request(onGranted: () -> Unit)
}

fun NavGraphBuilder.homeGraph(
    navigator: HomeNavigator,
    navController: NavController,
    consent: VpnConsentPrompt,
    notifications: NotificationReadiness,
    /** The user's configurations, drawn inside Home. A slot rather than a dependency: features do
     * not see each other, so `:app` hands the list in. */
    connections: @Composable () -> Unit = {},
) {
    navigation<HomeGraphRoute>(startDestination = HomeRoute()) {
        composable<HomeRoute>(
            // Two links: the generated one with `connect`, and plain `vazie-vpn://home` used by existing
            // callers.
            deepLinks = listOf(
                navDeepLink<HomeRoute>(basePath = HOME_DEEP_LINK),
                navDeepLink { uriPattern = HOME_DEEP_LINK },
                // The list that used to be its own screen now lives here.
                navDeepLink { uriPattern = CONNECTIONS_LINK },
            ),
        ) { entry ->
            val viewModel: HomeViewModel = hiltViewModel(viewModelStoreOwner = entry)
            HomeRoute(
                viewModel = viewModel,
                navigator = navigator,
                consent = consent,
                notifications = notifications,
                arrival = entry.toRoute<HomeRoute>(),
                entryId = entry.id,
                onShowDetails = { navController.navigate(ConnectionDetailsRoute) },
                connections = connections,
            )
        }
        composable<ConnectionDetailsRoute> { entry ->
            // Home's own ViewModel: the details describe the same session, and a second ViewModel
            // would sooner or later start showing a different one.
            val parent = remember(entry) { navController.getBackStackEntry<HomeRoute>() }
            val viewModel: HomeViewModel = hiltViewModel(viewModelStoreOwner = parent)
            val state by viewModel.state.collectAsStateWithLifecycle()
            ConnectionDetailsScreen(
                session = (state as? HomeUiState.Ready)
                    ?.let { it.connection as? ConnectionUiState.Connected }
                    ?.session,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Holds the ViewModel and turns effects into navigation, so [HomeScreen] stays a function of state. */
@Composable
private fun HomeRoute(
    viewModel: HomeViewModel,
    navigator: HomeNavigator,
    consent: VpnConsentPrompt,
    notifications: NotificationReadiness,
    arrival: HomeRoute,
    entryId: String,
    onShowDetails: () -> Unit,
    connections: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keyed on the back-stack entry, so the connect deep link asks once per arrival.
    LaunchedEffect(entryId, arrival) {
        if (!arrival.connect) return@LaunchedEffect
        val profile = arrival.profile
        if (profile == null) {
            viewModel.onAction(HomeAction.Connect)
        } else {
            viewModel.onAction(HomeAction.ConnectTo(profile))
        }
    }

    LaunchedEffect(viewModel, navigator) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeEffect.OpenAddConfiguration -> navigator.toAddConfig()
                // The connection list explains Vazie's servers and plans; it is the only destination for this
                // today.
                HomeEffect.OpenConnectionDetails -> onShowDetails()
                HomeEffect.OpenSettings -> navigator.toSettings()
                HomeEffect.RequestVpnPermission ->
                    consent.request(viewModel::onVpnPermissionGranted)
            }
        }
    }

    ConnectionHaptics(connection = (state as? HomeUiState.Ready)?.connection)

    // The gate wraps the screen's own connect controls and nothing else.
    HomeScreen(
        connections = connections,
        state = state,
        onAction = { action ->
            if (action.startsAConnection) {
                notifications.ensure { viewModel.onAction(action) }
            } else {
                viewModel.onAction(action)
            }
        },
    )
}

/** Turns a connection result into something the hand can feel. */
@Composable
private fun ConnectionHaptics(connection: ConnectionUiState?) {
    val haptics = rememberVazieHaptics()
    val previous = remember { mutableStateOf<ConnectionUiState?>(null) }

    LaunchedEffect(connection) {
        if (connection == null) return@LaunchedEffect
        when (connectionFeedback(previous.value, connection)) {
            ConnectionFeedback.Success -> haptics.confirm()
            ConnectionFeedback.Failure -> haptics.reject()
            null -> Unit
        }
        previous.value = connection
    }
}
