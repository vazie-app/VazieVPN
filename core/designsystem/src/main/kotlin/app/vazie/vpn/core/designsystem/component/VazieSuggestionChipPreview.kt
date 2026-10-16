package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** Completions under an email field, in both palettes. */
@VaziePreview
@Composable
private fun VazieSuggestionChipPreview() {
    for (appearance in listOf(Appearance.NIGHT_INDIGO, Appearance.MILK)) {
        VaziePreviewTheme(appearance) {
            Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
                VazieSuggestionChip(text = "person@gmail.com", onClick = {})
                VazieSuggestionChip(text = "person@yandex.ru", onClick = {})
            }
        }
    }
}
