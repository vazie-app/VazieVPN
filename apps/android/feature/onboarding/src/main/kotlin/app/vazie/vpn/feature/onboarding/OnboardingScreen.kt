package app.vazie.vpn.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.onboarding.components.OnboardingPageContent
import kotlinx.coroutines.launch

/** The four-step first run. Nothing in it opens a socket: it ends at adding a configuration of one's own. */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onAddConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { OnboardingPage.entries.size }
    val scope = rememberCoroutineScope()
    val reduceMotion = VazieTheme.reduceMotion
    val onLastPage = pagerState.currentPage == OnboardingPage.entries.lastIndex

    VazieScreenScaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VazieTheme.spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                VazieButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = onFinish,
                    variant = VazieButtonVariant.Text,
                )
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            OnboardingPageContent(page = OnboardingPage.entries[page])
        }

        PageIndicator(
            currentPage = pagerState.currentPage,
            pageCount = OnboardingPage.entries.size,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = VazieTheme.spacing.md),
        )

        // The last page offers two ways out, and neither is the only one.
        Column(
            modifier = Modifier.padding(
                horizontal = VazieTheme.spacing.screenHorizontal,
                vertical = VazieTheme.spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
        ) {
            if (onLastPage) {
                VazieButton(
                    text = stringResource(R.string.onboarding_add_config),
                    onClick = onAddConfig,
                    modifier = Modifier.fillMaxWidth(),
                )
                VazieButton(
                    text = stringResource(R.string.onboarding_start),
                    onClick = onFinish,
                    variant = VazieButtonVariant.Text,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                VazieButton(
                    text = stringResource(R.string.onboarding_next),
                    onClick = {
                        scope.launch {
                            val next = pagerState.currentPage + 1
                            if (reduceMotion) {
                                pagerState.scrollToPage(next)
                            } else {
                                pagerState.animateScrollToPage(next)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The four pages, and the argument each one is making. */
internal enum class OnboardingPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
) {
    WELCOME(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
    WHAT(R.string.onboarding_what_title, R.string.onboarding_what_body),
    OWN(R.string.onboarding_own_title, R.string.onboarding_own_body),
    REACH(R.string.onboarding_reach_title, R.string.onboarding_reach_body),
}

/** The dots are decoration; the position is announced as a single label, so a screen reader says "Page 2 of
 * 4" instead of reading four unlabelled shapes. */
@Composable
private fun PageIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.onboarding_page_indicator, currentPage + 1, pageCount)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                Modifier
                    .size(
                        width = if (selected) VazieTheme.spacing.lg else VazieTheme.spacing.xs,
                        height = VazieTheme.spacing.xs,
                    )
                    .clip(VazieTheme.shapes.pill)
                    .background(if (selected) VazieTheme.colors.primary else VazieTheme.colors.border),
            )
        }
    }
}
