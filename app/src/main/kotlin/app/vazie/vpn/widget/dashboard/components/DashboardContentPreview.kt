package app.vazie.vpn.widget.dashboard.components

import androidx.compose.runtime.Composable
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import app.vazie.vpn.core.designsystem.glance.VazieGlanceDimens
import app.vazie.vpn.core.designsystem.glance.VazieGlanceTheme
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import app.vazie.vpn.core.model.LastUsed
import app.vazie.vpn.widget.WidgetConnectionUi
import app.vazie.vpn.widget.WidgetFixtures
import app.vazie.vpn.widget.tunnelIsUp
import app.vazie.vpn.widget.presentation.WidgetVariant
import app.vazie.vpn.widget.presentation.widgetLayout

/** The dashboard in the four states the design has to be judged on, at its design size, plus the two cells
 * that change its shape. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardDisconnected() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.idle,
            layout = dashboardLayout(VazieWidgetSizes.wideTarget),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardConnecting() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.configured(WidgetConnectionUi.Connecting),
            layout = dashboardLayout(VazieWidgetSizes.wideTarget),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardConnected() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.connected,
            layout = dashboardLayout(VazieWidgetSizes.wideTarget),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardFailed() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.configured(WidgetConnectionUi.Failed),
            layout = dashboardLayout(VazieWidgetSizes.wideTarget),
        )
    }
}

/** The dashboard with Vazie's other locations on it - connected, and chosen but not connected. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardConnectedWithLocations() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.configured(
                connection = WidgetConnectionUi.Connected,
            ),
            layout = dashboardLayout(VazieWidgetSizes.wideTarget),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardDisconnectedWithLocations() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.configured(
                connection = WidgetConnectionUi.Idle,
            ),
            layout = dashboardLayout(VazieWidgetSizes.wideTarget),
        )
    }
}

/** A device that has never been online, or one that only uses its own configurations: no catalogue, and the
 * line falls back to what it always said. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardWithoutCatalogue() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.configured(
                connection = WidgetConnectionUi.Idle,
            ),
            layout = dashboardLayout(VazieWidgetSizes.wideTarget),
        )
    }
}

/** Squeezed onto a narrow grid, where the control goes back under the text. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 110)
@Composable
private fun DashboardSqueezed() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.connected,
            layout = dashboardLayout(VazieWidgetSizes.wideMin),
        )
    }
}

/** Stretched down a row: the one cell tall enough for a row of configurations to cost the text nothing. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 180)
@Composable
private fun DashboardTall() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.connected,
            layout = dashboardLayout(VazieWidgetSizes.wideTall),
        )
    }
}

/** Nothing used yet — the state a widget is in on a device that has just had Vazie installed. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 180)
@Composable
private fun DashboardFresh() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.configured(
                connection = WidgetConnectionUi.Idle,
                lastUsed = LastUsed.Never,
            ),
            layout = dashboardLayout(VazieWidgetSizes.wideTall),
        )
    }
}

/** The dashboard, one frame per state, at the cell it is designed for. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardSplitDisconnected() = dashboard(WidgetConnectionUi.Idle)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardSplitConnecting() = dashboard(WidgetConnectionUi.Connecting)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardSplitConnected() = dashboard(WidgetConnectionUi.Connected)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun DashboardSplitFailed() = dashboard(WidgetConnectionUi.Failed)

/** The cell tall enough to offer its locations rather than only name them, and the same cell with a tunnel
 * up, where they are named and not offered. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 180)
@Composable
private fun DashboardLocationsOffered() =
    dashboard(WidgetConnectionUi.Idle, VazieWidgetSizes.wideTall)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 180)
@Composable
private fun DashboardLocationsNamedOnly() =
    dashboard(WidgetConnectionUi.Connected, VazieWidgetSizes.wideTall)

/** A saved configuration keeps its own main block and still gets the discovery row. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 180)
@Composable
private fun DashboardCustomWithLocations() {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.configured(
                connection = WidgetConnectionUi.Idle,
            ),
            layout = dashboardLayout(VazieWidgetSizes.wideTall),
        )
    }
}

/** Squeezed onto a narrow grid: no column for a figure, so the rail stays rather than the widget losing its
 * only non-colour state channel. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 110)
@Composable
private fun DashboardSqueezedKeepsRail() =
    dashboard(WidgetConnectionUi.Connected, VazieWidgetSizes.wideMin)

@Composable
private fun dashboard(
    connection: WidgetConnectionUi,
    size: androidx.compose.ui.unit.DpSize = VazieWidgetSizes.wideTarget,
) {
    VazieGlanceTheme {
        DashboardContent(
            state = WidgetFixtures.configured(
                connection = connection,
            ),
            layout = widgetLayout(
                size = size,
                variant = WidgetVariant.DASHBOARD,
                dimens = VazieGlanceDimens(),
                tunnelIsUp = connection.tunnelIsUp,
            ),
        )
    }
}

@Composable
private fun dashboardLayout(size: androidx.compose.ui.unit.DpSize) = widgetLayout(
    size = size,
    variant = WidgetVariant.DASHBOARD,
    dimens = VazieGlanceDimens(),
)
