package app.vazie.vpn.feature.settings

/** The things Vazie explains about *itself*, as opposed to about Android. */
enum class GettingStartedTopic {

    /** What a configuration is, where one comes from, and what Vazie can read today. */
    FIRST_CONFIGURATION,

    /** What a person can do with a configuration once they have one. */
    MANAGE_CONFIGURATIONS,
}

internal val GettingStartedTopic.titleRes: Int
    get() = when (this) {
        GettingStartedTopic.FIRST_CONFIGURATION -> R.string.getting_started_first_config_title
        GettingStartedTopic.MANAGE_CONFIGURATIONS -> R.string.getting_started_manage_title
    }

/** The name in a toolbar, which has one line and a back arrow eating into it. */
internal val GettingStartedTopic.toolbarTitleRes: Int
    get() = when (this) {
        GettingStartedTopic.FIRST_CONFIGURATION -> R.string.getting_started_first_config_short
        GettingStartedTopic.MANAGE_CONFIGURATIONS -> R.string.getting_started_manage_short
    }

internal val GettingStartedTopic.summaryRes: Int
    get() = when (this) {
        GettingStartedTopic.FIRST_CONFIGURATION -> R.string.getting_started_first_config_summary
        GettingStartedTopic.MANAGE_CONFIGURATIONS -> R.string.getting_started_manage_summary
    }

/** One headed paragraph of a topic, and whether the example belongs under it. */
internal data class GettingStartedSection(
    @androidx.annotation.StringRes val headingRes: Int,
    val body: @androidx.compose.runtime.Composable () -> String,
    val showsExample: Boolean = false,
)

internal val GettingStartedTopic.introRes: Int
    get() = when (this) {
        GettingStartedTopic.FIRST_CONFIGURATION -> R.string.getting_started_first_config_intro
        GettingStartedTopic.MANAGE_CONFIGURATIONS -> R.string.getting_started_manage_intro
    }

/** The action a topic ends in, or `null` where it ends in understanding. */
internal val GettingStartedTopic.actionRes: Int?
    get() = when (this) {
        GettingStartedTopic.FIRST_CONFIGURATION -> R.string.getting_started_add_action
        GettingStartedTopic.MANAGE_CONFIGURATIONS -> null
    }

/** What a topic actually says, as headed paragraphs. */
@androidx.compose.runtime.Composable
internal fun GettingStartedTopic.sections(supportedFormats: String): List<GettingStartedSection> =
    when (this) {
        GettingStartedTopic.FIRST_CONFIGURATION -> listOf(
            GettingStartedSection(
                headingRes = R.string.getting_started_shape_heading,
                body = { androidx.compose.ui.res.stringResource(R.string.getting_started_shape_body) },
                showsExample = true,
            ),
            GettingStartedSection(
                headingRes = R.string.getting_started_reads_heading,
                body = {
                    androidx.compose.ui.res.stringResource(
                        R.string.getting_started_reads_body,
                        supportedFormats,
                    )
                },
            ),
        )

        GettingStartedTopic.MANAGE_CONFIGURATIONS -> listOf(
            GettingStartedSection(
                headingRes = R.string.getting_started_manage_list_heading,
                body = {
                    androidx.compose.ui.res.stringResource(R.string.getting_started_manage_list_body)
                },
            ),
            GettingStartedSection(
                headingRes = R.string.getting_started_manage_details_heading,
                body = {
                    androidx.compose.ui.res.stringResource(
                        R.string.getting_started_manage_details_body,
                    )
                },
            ),
            // Last and never softened: an exported configuration carries the keys.
            GettingStartedSection(
                headingRes = R.string.getting_started_manage_export_heading,
                body = {
                    androidx.compose.ui.res.stringResource(
                        R.string.getting_started_manage_export_body,
                    )
                },
            ),
        )
    }
