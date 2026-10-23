package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@Preview(widthDp = 360, heightDp = 420)
@Composable
private fun VazieScreenScaffoldPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        VazieScreenScaffold(
            topBar = {
                Text(
                    text = "Screen title",
                    style = VazieTheme.typography.headline,
                    color = VazieTheme.colors.textPrimary,
                    modifier = Modifier.padding(
                        horizontal = VazieTheme.spacing.screenHorizontal,
                        vertical = VazieTheme.spacing.md,
                    ),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VazieTheme.spacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm),
            ) {
                VazieCard { Text("Content sits below the top bar.", style = VazieTheme.typography.body) }
                VazieButton(text = "Primary action", onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
