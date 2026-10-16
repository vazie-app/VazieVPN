package app.vazie.vpn.feature.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.onboarding.OnboardingPage

/** One onboarding page: a heading and a line of explanation. */
@Composable
internal fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    // Scrollable, and centred only while there is room to centre in.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = VazieTheme.spacing.screenHorizontal,
                vertical = VazieTheme.spacing.md,
            )
            .vazieScrollEdgePadding(extra = VazieTheme.spacing.zero),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(page.titleRes),
            style = VazieTheme.typography.display,
            color = VazieTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(page.bodyRes),
            style = VazieTheme.typography.body,
            color = VazieTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = VazieTheme.spacing.sm),
        )
    }
}
