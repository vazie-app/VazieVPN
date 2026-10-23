package app.vazie.vpn.feature.onboarding.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.feature.onboarding.OnboardingPage
import app.vazie.vpn.feature.onboarding.R

private class OnboardingPages : PreviewParameterProvider<OnboardingPage> {
    override val values = OnboardingPage.entries.asSequence()
}

// Inside the pager a preview only ever shows the first page, so each page gets its own.
@VazieScreenPreview
@Composable
private fun OnboardingPageContentPreview(
    @PreviewParameter(OnboardingPages::class) page: OnboardingPage,
) {
    VaziePreviewTheme {
        OnboardingPageContent(page = page)
    }
}

// The longest body in the set, in Russian, at 150%. Onboarding bodies grew from one line to a
// paragraph in this pass, and this is where a paragraph that no longer fits shows up.
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "ru")
@Composable
private fun OnboardingPageContentRussianPreview() {
    VaziePreviewTheme {
        OnboardingPageContent(page = OnboardingPage.WHAT)
    }
}
