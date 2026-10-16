package app.vazie.vpn.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import app.vazie.vpn.core.model.VazieLinks
import kotlinx.collections.immutable.persistentListOf

/** The About screen's links as the app shipped them, in the device's language. Shown until the backend has
 * published its own, and whenever it has published nothing. */
@Composable
internal fun bundledAboutLinks(): AboutLinksUi = AboutLinksUi(
    author = persistentListOf(
        AboutLinkUi(AboutLink.DEVELOPER.url, stringResource(R.string.settings_developer), stringResource(R.string.settings_developer_body)),
        AboutLinkUi(AboutLink.FOLIO.url, stringResource(R.string.settings_folio), stringResource(R.string.settings_folio_body)),
    ),
    project = persistentListOf(
        AboutLinkUi(AboutLink.CHANNEL.url, stringResource(R.string.settings_channel), stringResource(R.string.settings_channel_body)),
        AboutLinkUi(AboutLink.SUPPORT_EMAIL.url, stringResource(R.string.settings_support_email), stringResource(R.string.settings_support_email_body)),
        AboutLinkUi(AboutLink.BEHANCE.url, stringResource(R.string.settings_behance), stringResource(R.string.settings_behance_body)),
    ),
    support = persistentListOf(
        AboutLinkUi(
            url = AboutLink.BOOSTY.url,
            title = stringResource(R.string.settings_boosty_title),
            subtitle = stringResource(R.string.settings_boosty_body),
            action = stringResource(R.string.settings_boosty_action),
        ),
    ),
    feedback = bundledFeedbackLink(),
    security = persistentListOf(
        AboutLinkUi(AboutLink.SOURCE.url, stringResource(R.string.settings_source), stringResource(R.string.settings_source_body)),
    ),
    feedbackLinks = persistentListOf(bundledFeedbackLink()),
)

/** The "report a bug" row as the app shipped it. */
@Composable
internal fun bundledFeedbackLink(): AboutLinkUi = AboutLinkUi(
    url = AboutLink.BUG_REPORT.url,
    title = stringResource(R.string.settings_report_bug),
    subtitle = stringResource(R.string.settings_report_bug_body),
)

/** Settings' «Обратная связь»: the first published feedback link, else the form the app shipped with. */
internal fun feedbackUrl(links: AboutLinksUi?): String = links?.feedback?.url ?: AboutLink.BUG_REPORT.url

/** Settings' «Связаться с поддержкой»: the first published `mailto:` link; the app's own address only while nothing is
 * published at all. `null` - the backend left it out or switched it off - hides the row. */
internal fun supportUrl(links: AboutLinksUi?): String? =
    if (links == null) AboutLink.SUPPORT_EMAIL.url else links.supportEmail?.url

/** Opens [url] if it is an `https` page or a `mailto:` address, and does nothing if there is nothing to open it with. */
internal fun Context.openLink(url: String) {
    if (!VazieLinks.isOpenable(url)) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.onFailure { failure ->
        if (failure !is ActivityNotFoundException) throw failure
    }
}
