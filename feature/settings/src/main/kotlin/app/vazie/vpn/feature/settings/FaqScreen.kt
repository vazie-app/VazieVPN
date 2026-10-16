package app.vazie.vpn.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.site.LocalVazieSite
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieFaqItem

/**
 * Questions and answers — only what the backend published (edited in the admin panel), as last cached on the device.
 * The app ships no text of its own: with nothing cached yet, the screen says it is loading, or that it could not load
 * and offers to try again. A link opens a page on the site (a path) or an `https` page.
 */
@Composable
fun FaqScreen(
    onBack: () -> Unit,
    state: FaqUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    val scroll = rememberScrollState()
    val uri = LocalUriHandler.current
    val site = LocalVazieSite.current
    val entries = rememberFaqEntries((state as? FaqUiState.Ready)?.items)
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(R.string.faq_title),
                onBack = onBack,
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
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            when {
                state is FaqUiState.Loading -> FaqMessage(stringResource(R.string.faq_loading))
                state is FaqUiState.Unavailable -> FaqMessage(stringResource(R.string.faq_unavailable)) {
                    VazieButton(text = stringResource(R.string.faq_retry), onClick = onRetry, variant = VazieButtonVariant.Text)
                }
                entries.isEmpty() -> FaqMessage(stringResource(R.string.faq_empty))
                else -> entries.forEach { entry ->
                    VazieCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(
                                text = entry.question,
                                style = VazieTheme.typography.titleSmall,
                                color = VazieTheme.colors.textPrimary,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = entry.answer,
                                style = VazieTheme.typography.body,
                                color = VazieTheme.colors.textSecondary,
                            )
                            if (entry.link != null) {
                                // The admin panel's «Кнопка под ответом»: a button like a link's, not a bare text link.
                                VazieButton(
                                    text = entry.link.label,
                                    onClick = { uri.openUri(if (entry.link.isSitePath) site.page(entry.link.url) else entry.link.url) },
                                    variant = VazieButtonVariant.Secondary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the screen has to show. */
sealed interface FaqUiState {
    /** Nothing cached yet, and the backend is being asked. */
    data object Loading : FaqUiState

    /** Nothing cached, and the backend could not be read. */
    data object Unavailable : FaqUiState

    /** The questions as last published; empty when the operator switched every one off. */
    data class Ready(val items: List<VazieFaqItem>) : FaqUiState
}

@Composable
private fun FaqMessage(text: String, action: @Composable () -> Unit = {}) {
    VazieCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            Text(text = text, style = VazieTheme.typography.body, color = VazieTheme.colors.textSecondary)
            action()
        }
    }
}

/** One card, in the device's language: a site path in [FaqEntryLink.url] starts with `/`. */
internal data class FaqEntry(val question: String, val answer: String, val link: FaqEntryLink? = null)

internal data class FaqEntryLink(val label: String, val url: String) {
    val isSitePath: Boolean get() = url.startsWith("/")
}

@Composable
private fun rememberFaqEntries(items: List<VazieFaqItem>?): List<FaqEntry> {
    val language = LocalConfiguration.current.locales[0].language
    return remember(items, language) {
        items.orEmpty().map { item ->
            FaqEntry(
                question = item.question.resolve(language),
                answer = item.answer.resolve(language),
                link = item.link?.let { FaqEntryLink(it.label.resolve(language), it.url) },
            )
        }
    }
}
