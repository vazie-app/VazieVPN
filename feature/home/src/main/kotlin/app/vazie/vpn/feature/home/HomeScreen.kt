package app.vazie.vpn.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieConnectionAction
import app.vazie.vpn.core.designsystem.component.VazieConnectionStatus
import app.vazie.vpn.core.designsystem.component.VazieRoute
import app.vazie.vpn.core.designsystem.component.VazieRouteAnimation
import app.vazie.vpn.core.designsystem.component.VazieRouteState
import app.vazie.vpn.core.designsystem.component.rememberVazieRouteAnimation
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieTechnicalValue
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId
import app.vazie.vpn.core.designsystem.tour.vazieTourTarget

/** Home, in the "Маршрут" design: one focused VPN screen, not a dashboard. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    /** What to connect through — the user's configurations. */
    connections: @Composable () -> Unit = {},
) {
    val spacing = VazieTheme.spacing
    val scroll = rememberScrollState()
    // The route plays its connecting story to the end even when the connection is faster; until it
    // has, the status and the button stay on "connecting" too, so all three change together.
    val routeAnimation = rememberVazieRouteAnimation(homeRouteState(state) ?: VazieRouteState.Disconnected)
    val shown = homeShownState(state, routeAnimation)
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = { HomeHeader(onOpenSettings = { onAction(HomeAction.OpenSettings) }) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = spacing.md)
                .vazieScrollEdgePadding(extra = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxxs),
        ) {
            HomeStatus(shown)
            val route = homeRouteState(state)
            if (route != null && state is HomeUiState.Ready && shown is HomeUiState.Ready) {
                HomeRoute(state = shown, route = route, animation = routeAnimation, onAction = onAction)
            }
            // The traffic card comes and goes with the button, gap included.
            Column(modifier = Modifier.fillMaxWidth()) {
                HomePrimaryAction(state = shown, onAction = onAction)
                val session = (shown as? HomeUiState.Ready)?.let { it.connection as? ConnectionUiState.Connected }?.session
                TrafficReveal(session = session, onOpen = { onAction(HomeAction.ShowConnectionDetails) })
            }
            Box(modifier = Modifier.fillMaxWidth().vazieTourTarget(VazieTourTargetId.CONNECTION_CARD)) {
                connections()
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    val colors = VazieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VazieTheme.spacing.listRowHeight)
            .padding(start = VazieTheme.spacing.screenHorizontal, end = VazieTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val brand = stringResource(R.string.home_title)
        val product = stringResource(R.string.home_title_product)
        Text(
            text = buildAnnotatedString {
                append(brand)
                append(' ')
                withStyle(SpanStyle(color = colors.routeStart)) { append(product) }
            },
            style = VazieTheme.typography.title,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(VazieTheme.spacing.minTouchTarget)
                .vazieTourTarget(VazieTourTargetId.SETTINGS_GEAR),
        ) {
            Icon(
                imageVector = VazieIcons.Settings,
                contentDescription = stringResource(R.string.home_a11y_settings),
                tint = colors.textPrimary,
                modifier = Modifier.size(VazieTheme.spacing.xl),
            )
        }
    }
}

@Composable
private fun HomeStatus(state: HomeUiState) {
    val route = homeRouteState(state) ?: VazieRouteState.Disconnected
    when (val line = homeStatusLine(state)) {
        is HomeStatusLine.Words -> VazieConnectionStatus(
            state = route,
            title = stringResource(homeStatusTitle(state)),
            subtitle = if (line.configurationName != null) {
                stringResource(line.res, line.configurationName)
            } else {
                stringResource(line.res)
            },
        )
        is HomeStatusLine.Session -> {
            val minutes = (line.seconds / SECONDS_PER_MINUTE).toInt()
            VazieConnectionStatus(
                state = route,
                title = stringResource(homeStatusTitle(state)),
                subtitle = stringResource(R.string.home_route_session, formatSessionDuration(line.seconds), line.protocol),
                subtitleTechnical = true,
                subtitleContentDescription = pluralStringResource(R.plurals.home_session_a11y, minutes, minutes) +
                    ", " + line.protocol,
            )
        }
    }
}

@Composable
private fun HomeRoute(
    state: HomeUiState.Ready,
    route: VazieRouteState,
    animation: VazieRouteAnimation,
    onAction: (HomeAction) -> Unit,
) {
    val presentation = connectionPresentation(state)
    val kind = connectionActionKind(state, presentation.action)
    val status = stringResource(homeStatusTitle(state))
    VazieRoute(
        state = route,
        deviceLabel = stringResource(R.string.home_route_device),
        serverLabel = state.configuration.name,
        serverCode = state.configuration.mark,
        contentDescription = stringResource(R.string.home_route_a11y, status, state.configuration.name),
        // The whole route is pressable, as the design draws it: it does exactly what the button
        // under it does, and only when that button would.
        onClick = if (kind != null && presentation.actionEnabled) {
            { onAction(presentation.action) }
        } else {
            null
        },
        animation = animation,
    )
}

@Composable
private fun HomePrimaryAction(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    val presentation = connectionPresentation(state)
    val kind = connectionActionKind(state, presentation.action)
    val modifier = Modifier
        .fillMaxWidth()
        // The first thing the tour points at, and the only control that changes what the tunnel
        // does.
        .vazieTourTarget(VazieTourTargetId.CONNECT_CONTROL)
    if (kind != null) {
        VazieConnectionAction(
            kind = kind,
            text = stringResource(presentation.actionRes),
            onClick = { onAction(presentation.action) },
            enabled = presentation.actionEnabled,
            modifier = modifier,
        )
    } else {
        VazieButton(
            text = stringResource(presentation.actionRes),
            onClick = { onAction(presentation.action) },
            variant = presentation.actionVariant,
            enabled = presentation.actionEnabled,
            modifier = modifier,
        )
    }
}

/** The traffic card, arriving when the tunnel is up and leaving when it is not. */
@Composable
private fun TrafficReveal(session: SessionUi?, onOpen: () -> Unit) {
    val last = remember { LastSession() }
    if (session != null) last.value = session
    val still = VazieTheme.reduceMotion
    AnimatedVisibility(
        visible = session != null,
        enter = if (still) {
            EnterTransition.None
        } else {
            expandVertically(tween(RevealMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
                fadeIn(tween(RevealMillis - RevealDelayMillis, delayMillis = RevealDelayMillis)) +
                scaleIn(tween(RevealMillis, easing = FastOutSlowInEasing), initialScale = RevealScale) +
                slideInVertically(tween(RevealMillis, easing = FastOutSlowInEasing)) { -it / RevealSlideFraction }
        },
        exit = if (still) {
            ExitTransition.None
        } else {
            fadeOut(tween(HideMillis / 2)) +
                scaleOut(tween(HideMillis, easing = FastOutSlowInEasing), targetScale = RevealScale) +
                shrinkVertically(tween(HideMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
        },
    ) {
        last.value?.let { reading ->
            Box(modifier = Modifier.padding(top = VazieTheme.spacing.sm + VazieTheme.spacing.xxxs)) {
                TrafficLine(session = reading, onOpen = onOpen)
            }
        }
    }
}

/** Not state: it only remembers what the exit animation should keep showing. */
private class LastSession {
    var value: SessionUi? = null
}

private const val RevealMillis = 420
private const val RevealDelayMillis = 120
private const val HideMillis = 320
private const val RevealScale = 0.96f
private const val RevealSlideFraction = 4

@Composable
private fun TrafficLine(session: SessionUi, onOpen: () -> Unit) {
    val description = stringResource(R.string.home_traffic_a11y)
    VazieCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VazieTheme.shapes.lg)
            // The readings stay readable to TalkBack; the tap is announced as opening the details.
            .clickable(onClickLabel = description, role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.md),
        ) {
            VazieTechnicalValue(
                label = stringResource(R.string.home_traffic_downloaded),
                value = trafficText(session.rxBytes),
                modifier = Modifier.weight(1f),
            )
            VazieTechnicalValue(
                label = stringResource(R.string.home_traffic_uploaded),
                value = trafficText(session.txBytes),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = VazieIcons.ChevronRight,
                contentDescription = null,
                tint = VazieTheme.colors.textSecondary,
                modifier = Modifier.size(VazieTheme.spacing.lg),
            )
        }
    }
}

@Composable
private fun trafficText(bytes: Long): String {
    val reading = trafficReading(bytes) ?: return stringResource(R.string.home_value_unknown)
    return stringResource(R.string.home_value_amount, reading.amount, stringResource(reading.unitRes))
}

private const val SECONDS_PER_MINUTE = 60L
