package app.vazie.vpn.feature.config.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.feature.config.R

@VaziePreview
@Composable
private fun AddConfigTopBarPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        AddConfigTopBar(title = stringResource(R.string.config_method_title), onClose = {})
    }
}

// The Russian title is longer than the English one and is the first to collide with the close button.
@Preview(widthDp = 320, locale = "ru")
@Composable
private fun AddConfigTopBarRussianPreview() {
    VaziePreviewTheme {
        AddConfigTopBar(title = stringResource(R.string.config_review_title), onClose = {})
    }
}
