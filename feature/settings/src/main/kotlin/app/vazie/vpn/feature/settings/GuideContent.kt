package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieGuideAction
import app.vazie.vpn.core.model.VazieGuideId
import app.vazie.vpn.core.model.VazieGuideKind

/** What each guide says, what its button does, and how to do it by hand. */
internal val VazieGuideId.titleRes: Int
    get() = when (this) {
        VazieGuideId.QUICK_SETTINGS -> R.string.guide_quick_settings_title
        VazieGuideId.LAUNCHER_SHORTCUTS -> R.string.guide_shortcuts_title
        VazieGuideId.WIDGETS -> R.string.guide_widgets_title
        VazieGuideId.NOTIFICATION -> R.string.guide_notification_title
    }

/** The body, which for one guide depends on what the system says right now. */
internal fun VazieGuideId.bodyRes(integration: SystemIntegrationUi): Int = when (this) {
    VazieGuideId.QUICK_SETTINGS -> R.string.guide_quick_settings_body
    VazieGuideId.LAUNCHER_SHORTCUTS -> R.string.guide_shortcuts_body
    VazieGuideId.WIDGETS -> R.string.guide_widgets_body
    VazieGuideId.NOTIFICATION -> if (integration.notificationsEnabled) {
        R.string.guide_notification_body_on
    } else {
        R.string.guide_notification_body_off
    }
}

/** The one line a *list* shows, which is not the guide's body. */
internal val VazieGuideId.summaryRes: Int
    get() = when (this) {
        VazieGuideId.QUICK_SETTINGS -> R.string.guide_quick_settings_summary
        VazieGuideId.LAUNCHER_SHORTCUTS -> R.string.guide_shortcuts_summary
        VazieGuideId.WIDGETS -> R.string.guide_widgets_summary
        VazieGuideId.NOTIFICATION -> R.string.guide_notification_summary
    }

/** The heading over the steps, which says what the steps are *for*. */
internal val VazieGuideId.stepsTitleRes: Int
    get() = when (this) {
        VazieGuideId.QUICK_SETTINGS -> R.string.guide_quick_settings_manual
        VazieGuideId.LAUNCHER_SHORTCUTS -> R.string.guide_shortcuts_manual
        VazieGuideId.WIDGETS -> R.string.guide_widgets_manual
        VazieGuideId.NOTIFICATION -> R.string.guide_notification_manual
    }

internal val VazieGuideId.stepsRes: List<Int>
    get() = when (this) {
        VazieGuideId.QUICK_SETTINGS -> listOf(
            R.string.guide_quick_settings_step_1,
            R.string.guide_quick_settings_step_2,
            R.string.guide_quick_settings_step_3,
            R.string.guide_quick_settings_step_4,
        )

        VazieGuideId.LAUNCHER_SHORTCUTS -> listOf(
            R.string.guide_shortcuts_step_1,
            R.string.guide_shortcuts_step_2,
        )

        // The button pins Quick Connect only (`requestPinAppWidget` takes one provider), so the Dashboard
        // gets a step.
        VazieGuideId.WIDGETS -> listOf(
            R.string.guide_widgets_step_1,
            R.string.guide_widgets_step_2,
            R.string.guide_widgets_step_3,
            R.string.guide_widgets_step_4,
            R.string.guide_widgets_step_5,
        )

        VazieGuideId.NOTIFICATION -> listOf(
            R.string.guide_notification_step_1,
            R.string.guide_notification_step_2,
            R.string.guide_notification_step_3,
        )
    }

/** The button a guide offers, or `null` when nothing would happen if it were pressed. */
@Composable
internal fun VazieGuideId.platformAction(
    integration: SystemIntegrationUi,
    onAction: (SettingsAction) -> Unit,
): VazieGuideAction? {
    if (!kind.allowsPlatformAction) return null
    return when (this) {
        VazieGuideId.QUICK_SETTINGS -> if (integration.canAddQuickSettingsTile) {
            VazieGuideAction(
                label = stringResource(R.string.guide_quick_settings_action),
                onClick = { onAction(SettingsAction.AddQuickSettingsTile) },
            )
        } else {
            null
        }

        VazieGuideId.WIDGETS -> if (integration.canAddWidget) {
            VazieGuideAction(
                label = stringResource(R.string.guide_widgets_action),
                onClick = { onAction(SettingsAction.AddWidget) },
            )
        } else {
            null
        }

        // Offered in both states: opening notification settings requests nothing.
        VazieGuideId.NOTIFICATION -> if (integration.canOpenNotificationSettings) {
            VazieGuideAction(
                label = stringResource(R.string.guide_notification_action),
                onClick = { onAction(SettingsAction.OpenNotificationSettings) },
            )
        } else {
            null
        }

        // Written out rather than left to an `else` so a new guide has to state its own answer
        // here.
        VazieGuideId.LAUNCHER_SHORTCUTS -> null
    }
}

/** Whether this guide has been opened. */
internal fun VazieGuideId.isAcknowledged(acknowledged: Set<VazieGuideId>): Boolean =
    this in acknowledged



/** Every guide there is, in declaration order, each told whether to show that it has been read. */
internal fun guideList(
    acknowledged: Set<VazieGuideId>,
    visible: Set<VazieGuideId> = VazieGuideId.entries.toSet(),
): List<GuideListEntry> =
    VazieGuideId.entries.filter { it in visible }.map { guide ->
        GuideListEntry(
            id = guide,
            showViewed = guide.isAcknowledged(acknowledged),
        )
    }
