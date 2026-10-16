package app.vazie.vpn.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** Preview support: theme, background and axis values only; previews render the real components. */
@Composable
fun VaziePreviewTheme(
    appearance: Appearance = Appearance.Default,
    content: @Composable ColumnScope.() -> Unit,
) {
    VazieTheme(appearance = appearance) {
        Column(
            modifier = Modifier
                .background(VazieTheme.colors.background)
                .padding(VazieTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm),
            content = content,
        )
    }
}

/** Renders one function once per palette — Night Indigo and Milk — instead of a copy for each. */
class Appearances : PreviewParameterProvider<Appearance> {
    override val values = Appearance.entries.asSequence()
}

/** One place to change the width every component preview renders at. */
@Preview(widthDp = 360)
annotation class VaziePreview

/** The same, at phone height, for previews of a whole screen. */
@Preview(widthDp = 360, heightDp = 760)
annotation class VazieScreenPreview
