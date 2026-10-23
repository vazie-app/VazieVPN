package app.vazie.vpn.ui

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.app.OnNewIntentProvider
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.tour.rememberVazieTourTargets
import app.vazie.vpn.core.designsystem.tour.LocalVazieTourTargets
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.data.TourState
import app.vazie.vpn.navigation.TopLevelDestination
import app.vazie.vpn.tour.VazieTourHost
import app.vazie.vpn.tour.settingsGuidanceSequence
import app.vazie.vpn.tour.configurationGuidanceSequence
import app.vazie.vpn.navigation.VazieAppState
import app.vazie.vpn.navigation.VazieNavHost
import app.vazie.vpn.system.SystemIntegration
import app.vazie.vpn.navigation.rememberVazieAppState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.vazie.vpn.links.AppLinksViewModel
import app.vazie.vpn.update.AppUpdateViewModel
import app.vazie.vpn.update.UpdateGate
import app.vazie.vpn.update.rememberOpenUpdate

/** The application shell: the theme, the navigation host and the bottom bar around it. */
@Composable
fun VazieApp(
    systemIntegration: SystemIntegration,
    viewModel: VazieAppViewModel = viewModel(),
    updates: AppUpdateViewModel = viewModel(),
    links: AppLinksViewModel = viewModel(),
) {
    val appState = rememberVazieAppState()
    val startup by viewModel.state.collectAsStateWithLifecycle()
    val update by updates.state.collectAsStateWithLifecycle()
    val aboutLinks by links.links.collectAsStateWithLifecycle()
    val faq by links.faq.collectAsStateWithLifecycle()
    val openUpdate = rememberOpenUpdate()

    // The cold start and every return to the foreground; the checker decides whether one is due (at most hourly),
    // and none of it waits on, or is waited on by, the tunnel.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { updates.onForeground() }

    HandleNewDeepLinkIntents(appState)

    VazieTheme(appearance = startup.appearance) {
        SystemBarsFollowPalette()
        // Read once; `rememberSaveable` keeps the decision across configuration changes and process death.
        val startWithOnboarding = rememberSaveable { !startup.onboardingCompleted }
        val topLevelDestination = appState.currentTopLevelDestination
        // Held here so every tour anchor reports coordinates in the same space.
        val tourTargets = rememberVazieTourTargets()

        // The tour runs when it is owed and when Home is the screen underneath it.
        var replaying by remember { mutableStateOf(false) }
        val tourRunning = topLevelDestination == TopLevelDestination.HOME &&
            (replaying || startup.tourState == TourState.PENDING)

        // The configuration coach mark: on Connections, once, and only when there is a configuration.
        val guidanceRunning = !tourRunning &&
            topLevelDestination == TopLevelDestination.HOME &&
            startup.tourState == TourState.DONE &&
            !startup.configurationGuidanceSeen

        // The Settings tour: the first time Settings itself is opened, once. Never beside another guide.
        val settingsGuidanceRunning = !tourRunning && !guidanceRunning &&
            appState.isOnSettingsRoot &&
            !startup.settingsGuidanceSeen

        // A Box, not a Scaffold slot, so screens can run behind the floating bar (see VazieScreenScaffold);
        // the tour registry covers the whole Box so all anchors share one coordinate space.
        CompositionLocalProvider(LocalVazieTourTargets provides tourTargets) {
          // A build below the backend's minimum sees the update screen and nothing else.
          UpdateGate(
            state = update,
            onOpenUpdate = openUpdate,
            onCheckAgain = updates::onCheckNow,
            onPostpone = updates::onPostpone,
            onDismissPrompt = updates::onDismissPrompt,
          ) { gateModifier ->
            Box(
                modifier = gateModifier
                    .fillMaxSize()
                    .background(VazieTheme.colors.background),
            ) {
                // No bottom bar since the "Маршрут" redesign: Home reaches Connections through its connection
                // card and Settings through its gear, so nothing reserves room at the bottom.
                run {
                    VazieNavHost(
                        modifier = Modifier.tourModalScrim(tourRunning || guidanceRunning || settingsGuidanceRunning),
                        appState = appState,
                        startWithOnboarding = startWithOnboarding,
                        appearance = { startup.appearance },
                        appIcon = { startup.appIcon },
                        appIconPlate = { startup.appIconPlate },
                        acknowledgedGuides = { startup.acknowledgedGuides },
                        systemIntegration = systemIntegration,
                        onAppearanceChange = viewModel::onAppearanceSelected,
                        onAppIconChange = viewModel::onAppIconSelected,
                        onAppIconPlateChange = viewModel::onAppIconPlateSelected,
                        splitTunnel = { startup.splitTunnel },
                        onSplitTunnelChange = viewModel::onSplitTunnelChanged,
                        onAcknowledgeGuide = viewModel::onGuideAcknowledged,
                        onOnboardingFinished = viewModel::onOnboardingFinished,
                        notificationExplained = { startup.notificationExplained },
                        onNotificationExplained = viewModel::onNotificationExplained,
                        onReplayTour = {
                            appState.navigateToTopLevelDestination(TopLevelDestination.HOME)
                            replaying = true
                        },
                        onCheckForUpdates = updates::onCheckNow,
                        checkingForUpdates = { update.checking },
                        links = { aboutLinks },
                        onRefreshLinks = links::refresh,
                        faq = { faq },
                        onRefreshFaq = links::refreshFaq,
                    )
                }
                if (guidanceRunning) {
                    VazieTourHost(
                        targets = tourTargets,
                        onFinished = viewModel::onConfigurationGuidanceSeen,
                        onOpenGuides = appState::navigateToGuides,
                        steps = configurationGuidanceSequence(),
                        completion = false,
                    )
                }
                if (settingsGuidanceRunning) {
                    VazieTourHost(
                        targets = tourTargets,
                        onFinished = viewModel::onSettingsGuidanceSeen,
                        onOpenGuides = appState::navigateToGuides,
                        steps = settingsGuidanceSequence(),
                        completion = false,
                    )
                }

                if (tourRunning) {
                    VazieTourHost(
                        targets = tourTargets,
                        onFinished = {
                            replaying = false
                            // Only the automatic run records completion; a replay does not.
                            if (startup.tourState == TourState.PENDING) viewModel.onTourFinished()
                        },
                        onOpenGuides = appState::navigateToGuides,
                    )
                }
            }
          }
        }
    }
}

@Composable
private fun HandleNewDeepLinkIntents(appState: VazieAppState) {
    val activity = LocalActivity.current as? OnNewIntentProvider ?: return
    DisposableEffect(activity, appState) {
        val listener = Consumer<Intent> { intent -> appState.navController.handleDeepLink(intent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

/** Hides a subtree from accessibility while the tour is over it. */
private fun Modifier.tourModalScrim(active: Boolean): Modifier =
    if (active) semantics { invisibleToUser() } else this

/** Dark status and navigation bar icons on Milk, light ones on Night Indigo. */
@Composable
private fun SystemBarsFollowPalette() {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    val light = !VazieTheme.colors.isDark
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}
