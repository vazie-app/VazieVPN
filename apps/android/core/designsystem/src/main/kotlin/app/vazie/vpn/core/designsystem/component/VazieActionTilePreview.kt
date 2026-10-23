package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** The group as it is actually used, plus the destructive tone beside it. */
@VaziePreview
@Composable
private fun VazieActionTilePreview() {
    VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            VazieActionTile(VazieIcons.Rename, "Rename", {}, Modifier.weight(1f))
            VazieActionTile(VazieIcons.Duplicate, "Duplicate", {}, Modifier.weight(1f))
            VazieActionTile(VazieIcons.Export, "Export", {}, Modifier.weight(1f))
        }
        VazieActionTile(
            icon = VazieIcons.Delete,
            label = "Delete",
            onClick = {},
            tone = VazieActionTone.Destructive,
        )
    }
    VaziePreviewTheme(Appearance.MILK) {
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            VazieActionTile(VazieIcons.Rename, "Rename", {}, Modifier.weight(1f))
            VazieActionTile(VazieIcons.Duplicate, "Duplicate", {}, Modifier.weight(1f))
            VazieActionTile(VazieIcons.Export, "Export", {}, Modifier.weight(1f))
        }
        VazieActionTile(
            icon = VazieIcons.Delete,
            label = "Delete",
            onClick = {},
            tone = VazieActionTone.Destructive,
        )
    }
}
