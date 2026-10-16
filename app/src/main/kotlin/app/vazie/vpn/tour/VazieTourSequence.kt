package app.vazie.vpn.tour

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.vazie.vpn.R
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId

/** One step: what to point at, and the two sentences said about it. */
@Immutable
data class VazieTourStep(
    val target: VazieTourTargetId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

/** The first-run tour: three steps, no navigation, and a deliberate end. */
internal fun vazieTourSequence(): List<VazieTourStep> = listOf(
    VazieTourStep(
        target = VazieTourTargetId.CONNECT_CONTROL,
        titleRes = R.string.tour_connect_title,
        bodyRes = R.string.tour_connect_body,
    ),
    VazieTourStep(
        target = VazieTourTargetId.CONNECTION_CARD,
        titleRes = R.string.tour_configuration_title,
        bodyRes = R.string.tour_configuration_body,
    ),
    VazieTourStep(
        target = VazieTourTargetId.SETTINGS_GEAR,
        titleRes = R.string.tour_settings_title,
        bodyRes = R.string.tour_settings_body,
    ),
)

/** The one-step coach mark that teaches what a configuration row can do. */
internal fun configurationGuidanceSequence(): List<VazieTourStep> = listOf(
    VazieTourStep(
        target = VazieTourTargetId.CONFIGURATION_ROW,
        titleRes = R.string.guidance_swipe_title,
        bodyRes = R.string.guidance_swipe_body,
    ),
)

/** The Settings tour, the first time Settings is opened: the rows worth knowing, down to where a bug is reported.
 * A step whose row is absent in this build is skipped. */
internal fun settingsGuidanceSequence(): List<VazieTourStep> = listOf(
    VazieTourStep(VazieTourTargetId.SETTINGS_APPEARANCE, R.string.guidance_appearance_title, R.string.guidance_appearance_body),
    VazieTourStep(VazieTourTargetId.SETTINGS_SPLIT_TUNNEL, R.string.guidance_split_title, R.string.guidance_split_body),
    VazieTourStep(VazieTourTargetId.SETTINGS_GUIDES, R.string.guidance_guides_title, R.string.guidance_guides_body),
    VazieTourStep(VazieTourTargetId.SETTINGS_REPORT_BUG, R.string.guidance_bug_title, R.string.guidance_bug_body),
)
