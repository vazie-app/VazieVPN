package app.vazie.vpn.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieGuide
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieGuideId

/** One guide, alone, because somebody asked for it. */
@Composable
fun GuideDetailScreen(
    state: GuideDetailUiState,
    onAction: (GuideDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    val scroll = rememberScrollState()

    // Opening it is the acknowledgement, and it is the only thing Vazie is entitled to record.
    LaunchedEffect(state.id) { onAction(GuideDetailAction.MarkViewed(state.id)) }
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(state.id.titleRes),
                onBack = { onAction(GuideDetailAction.Back) },
                backContentDescription = stringResource(R.string.settings_back),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenHorizontal)
                .vazieScrollEdgePadding(),
        ) {
            VazieGuide(
                title = stringResource(state.id.titleRes),
                body = stringResource(state.id.bodyRes(state.systemIntegration)),
                stepsTitle = stringResource(state.id.stepsTitleRes),
                steps = state.id.stepsRes.map { stringResource(it) },
                action = state.id.platformAction(state.systemIntegration) { settings ->
                    onAction(GuideDetailAction.Settings(settings))
                },
            )
        }
    }
}

@Immutable
data class GuideDetailUiState(
    val id: VazieGuideId,
    val systemIntegration: SystemIntegrationUi = SystemIntegrationUi(),
)

sealed interface GuideDetailAction {

    /** A `SettingsAction` raised from here, so the platform requests have one implementation. */
    data class Settings(val action: SettingsAction) : GuideDetailAction

    /** This guide has been opened, so it has been met. */
    data class MarkViewed(val id: VazieGuideId) : GuideDetailAction
    data object Back : GuideDetailAction
}
