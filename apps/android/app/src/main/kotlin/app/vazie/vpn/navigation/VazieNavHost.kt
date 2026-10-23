package app.vazie.vpn.navigation

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import app.vazie.vpn.BuildConfig
import app.vazie.vpn.R
import app.vazie.vpn.config.ConfigParserRegistry
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieDialog
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.SplitTunnel
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.core.model.VazieGuideId
import app.vazie.vpn.core.navigation.HomeNavigator
import app.vazie.vpn.core.navigation.OnboardingNavigator
import app.vazie.vpn.feature.config.addConfigGraph
import app.vazie.vpn.feature.config.configDetailsScreen
import app.vazie.vpn.feature.config.navigateToAddConfig
import app.vazie.vpn.feature.config.navigateToConfigDetails
import app.vazie.vpn.feature.connections.ConnectionsSection
import app.vazie.vpn.feature.home.HomeGraphRoute
import app.vazie.vpn.feature.home.NotificationReadiness
import app.vazie.vpn.feature.home.VpnConsentPrompt
import app.vazie.vpn.feature.home.homeGraph
import app.vazie.vpn.feature.onboarding.OnboardingGraphRoute
import app.vazie.vpn.feature.onboarding.onboardingGraph
import app.vazie.vpn.core.model.VazieLinks
import app.vazie.vpn.feature.settings.AboutUi
import app.vazie.vpn.feature.settings.FaqUiState
import app.vazie.vpn.feature.settings.SystemIntegrationUi
import app.vazie.vpn.feature.settings.settingsGraph
import app.vazie.vpn.icon.LauncherIconArt
import app.vazie.vpn.icon.LauncherPlateColours
import app.vazie.vpn.runtime.VpnPermission
import app.vazie.vpn.system.SystemIntegration
import kotlinx.serialization.Serializable

/** The tabbed scaffold: everything the bottom bar can reach lives under it. */
@Serializable
data object MainGraphRoute

/** The root graph, assembled here because `:app` is the only module allowed to see every feature (R1). */
@Composable
fun VazieNavHost(
    appState: VazieAppState,
    startWithOnboarding: Boolean,
    appearance: () -> Appearance,
    appIcon: () -> VazieAppIcon,
    appIconPlate: () -> VazieAppIconPlate,
    acknowledgedGuides: () -> Set<VazieGuideId>,
    systemIntegration: SystemIntegration,
    onAppearanceChange: (Appearance) -> Unit,
    onAppIconChange: (VazieAppIcon) -> Unit,
    onAppIconPlateChange: (VazieAppIconPlate) -> Unit,
    splitTunnel: () -> SplitTunnel,
    onSplitTunnelChange: (SplitTunnel) -> Unit,
    onAcknowledgeGuide: (VazieGuideId) -> Unit,
    onOnboardingFinished: () -> Unit,
    onReplayTour: () -> Unit,
    notificationExplained: () -> Boolean,
    onNotificationExplained: () -> Unit,
    modifier: Modifier = Modifier,
    onCheckForUpdates: () -> Unit = {},
    checkingForUpdates: () -> Boolean = { false },
    links: () -> VazieLinks? = { null },
    onRefreshLinks: () -> Unit = {},
    faq: () -> FaqUiState = { FaqUiState.Loading },
    onRefreshFaq: () -> Unit = {},
) {
    val navController = appState.navController
    val consent = rememberVpnConsentPrompt()
    val notifications = rememberNotificationReadiness(
        systemIntegration = systemIntegration,
        explained = notificationExplained,
        onExplained = onNotificationExplained,
    )
    // What About lists as supported comes from `:app`'s parser registry, read once.
    val supportedFormats = remember { ConfigParserRegistry.default().advertisedFormats() }
    val about = remember {
        AboutUi(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
        )
    }
    // The launcher artwork and the plate rule, resolved once and handed on as plain functions.
    val resolvePlate = remember {
        { icon: VazieAppIcon, plate: VazieAppIconPlate -> LauncherIconArt.resolvePlate(plate, icon) }
    }
    val plateColours = LauncherPlateColours.read()
    val appIconArt = remember(plateColours) {
        { icon: VazieAppIcon, plate: VazieAppIconPlate ->
            LauncherIconArt.art(icon, plate, plateColours)
        }
    }
    // Asked once per composition of the graph rather than on every recomposition: a launcher does not
    // gain or lose the pin-widget API while Settings is open, and both calls are binder round trips.
    val integration = remember(systemIntegration) {
        val platformSurfacesEnabled = BuildConfig.SYSTEM_INTEGRATIONS_ENABLED
        SystemIntegrationUi(
            visibleGuides = if (platformSurfacesEnabled) {
                VazieGuideId.entries.toSet()
            } else {
                VazieGuideId.entries.toSet() - setOf(
                    VazieGuideId.QUICK_SETTINGS,
                    VazieGuideId.WIDGETS,
                )
            },
            canAddWidget = platformSurfacesEnabled && systemIntegration.canAddWidget(),
            canAddQuickSettingsTile =
                platformSurfacesEnabled && systemIntegration.canAddQuickSettingsTile(),
            canOpenNotificationSettings = systemIntegration.canOpenNotificationSettings(),
            // Read once per graph composition; it may change while the guide is open and only picks a
            // sentence.
            notificationsEnabled = systemIntegration.notificationsEnabled(),
        )
    }
    val homeNavigator = remember(navController) {
        object : HomeNavigator {
            override fun toAddConfig() = navController.navigateToAddConfig()
            override fun toSettings() = appState.openSection(TopLevelDestination.SETTINGS)
        }
    }
    // Both exits from onboarding, Skip and "Add a configuration", mark it done.
    val onboardingNavigator = remember(navController, onOnboardingFinished) {
        object : OnboardingNavigator {
            override fun toMain() {
                onOnboardingFinished()
                navController.replaceOnboardingWithMain()
            }

            override fun toAddConfig() {
                onOnboardingFinished()
                navController.replaceOnboardingWithMain()
                navController.navigateToAddConfig()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) OnboardingGraphRoute else MainGraphRoute,
        modifier = modifier,
    ) {
        onboardingGraph(navigator = onboardingNavigator)

        navigation<MainGraphRoute>(startDestination = HomeGraphRoute) {
            homeGraph(
                navigator = homeNavigator,
                navController = navController,
                consent = consent,
                notifications = notifications.gate,
                connections = {
                    ConnectionsSection(
                        onOpenConfiguration = navController::navigateToConfigDetails,
                        onAddConfiguration = navController::navigateToAddConfig,
                    )
                },
            )
            configDetailsScreen(navController = navController)
            settingsGraph(
                navController = navController,
                appearance = appearance,
                appIcon = appIcon,
                appIconPlate = appIconPlate,
                acknowledgedGuides = acknowledgedGuides,
                appIconArt = appIconArt,
                resolvePlate = resolvePlate,
                systemIntegration = { integration },
                onAppearanceChange = onAppearanceChange,
                onAppIconChange = onAppIconChange,
                onAppIconPlateChange = onAppIconPlateChange,
                splitTunnel = splitTunnel,
                onSplitTunnelChange = onSplitTunnelChange,
                onAcknowledgeGuide = onAcknowledgeGuide,
                onAddWidget = { systemIntegration.requestAddWidget() },
                onAddQuickSettingsTile = { systemIntegration.requestAddQuickSettingsTile() },
                onOpenNotificationSettings = { systemIntegration.openNotificationSettings() },
                onReplayTour = onReplayTour,
                onAddConfiguration = navController::navigateToAddConfig,
                // The guide names what the parser registry can read, so it cannot outlive a parser.
                supportedFormats = { supportedFormats },
                about = about,
                onCheckForUpdates = onCheckForUpdates,
                checkingForUpdates = checkingForUpdates,
                links = links,
                onRefreshLinks = onRefreshLinks,
                faq = faq,
                onRefreshFaq = onRefreshFaq,
            )
            addConfigGraph(navController = navController)
        }
    }

    // Outside the graph: a prompt is part of a gesture, not a destination "back" could land on.
    notifications.prompt?.let { prompt ->
        NotificationExplanationDialog(
            onAllow = prompt.onAllow,
            onNotNow = prompt.onNotNow,
        )
    }
}

/** Vazie's own words, before Android's dialog rather than instead of it. */
@Composable
private fun NotificationExplanationDialog(onAllow: () -> Unit, onNotNow: () -> Unit) {
    VazieDialog(
        title = stringResource(R.string.notification_prompt_title),
        text = stringResource(R.string.notification_prompt_body),
        onDismissRequest = onNotNow,
        confirmButton = {
            VazieButton(text = stringResource(R.string.notification_prompt_allow), onClick = onAllow)
        },
        dismissButton = {
            VazieButton(
                text = stringResource(R.string.notification_prompt_not_now),
                onClick = onNotNow,
                variant = VazieButtonVariant.Text,
            )
        },
    )
}

private fun NavController.replaceOnboardingWithMain() {
    navigate(MainGraphRoute) {
        popUpTo(OnboardingGraphRoute) { inclusive = true }
    }
}

/** The gate in front of Home's connect controls, and the sheet it may raise. */
@Composable
private fun rememberNotificationReadiness(
    systemIntegration: SystemIntegration,
    explained: () -> Boolean,
    onExplained: () -> Unit,
): NotificationReadinessHost {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Nothing to do with the answer — see this function's documentation. */ }

    val gate = remember(systemIntegration, explained, onExplained) {
        NotificationReadiness { onReady ->
            val alreadySettled = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                systemIntegration.notificationsEnabled() ||
                explained()
            if (alreadySettled) onReady() else pending = onReady
        }
    }

    return NotificationReadinessHost(
        gate = gate,
        prompt = pending?.let { onReady ->
            NotificationPrompt(
                onAllow = {
                    pending = null
                    onExplained()
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onReady()
                },
                onNotNow = {
                    pending = null
                    onExplained()
                    onReady()
                },
            )
        },
    )
}

/** The gate, and the sheet if one is currently owed. */
private data class NotificationReadinessHost(
    val gate: NotificationReadiness,
    val prompt: NotificationPrompt?,
)

private data class NotificationPrompt(val onAllow: () -> Unit, val onNotNow: () -> Unit)

@Composable
private fun rememberVpnConsentPrompt(): VpnConsentPrompt {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val granted = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK) granted?.invoke()
    }
    return remember(launcher, context) {
        VpnConsentPrompt { onGranted ->
            val intent = VpnPermission.consentIntent(context)
            if (intent == null) {
                onGranted()
            } else {
                pending = onGranted
                launcher.launch(intent)
            }
        }
    }
}
