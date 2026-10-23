package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

// The three states side by side: planned, live value and switched off must not be confused.
@VaziePreview
@Composable
private fun VazieComingSoonBadgePreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieListCard {
            VazieListItem(
                title = "Plan",
                subtitle = "What this build of Vazie is on",
                trailingContent = { VazieBadge("VAZIE FREE") },
                onClick = {},
            )
            VazieDivider()
            VazieListItem(
                title = "Sign in",
                subtitle = "A planned capability, not a broken one",
                trailingContent = { VazieComingSoonBadge("Coming soon") },
                onClick = {},
            )
            VazieDivider()
            VazieListItem(
                title = "Something switched off",
                subtitle = "The grey this component exists not to look like",
                enabled = false,
            )
        }
    }
}
