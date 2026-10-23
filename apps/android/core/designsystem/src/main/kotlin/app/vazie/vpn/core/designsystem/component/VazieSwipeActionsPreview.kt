package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

/** A swipeable row at rest, which is the state it is in almost all of the time. */
@VaziePreview
@Composable
private fun VazieSwipeActionsPreview() {
    VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
        VazieListCard {
            VazieSwipeableRow(
                start = null,
                end = VazieSwipeAction(VazieIcons.Delete, "Delete") {},
            ) { rowModifier ->
                VazieListItem(
                    title = "Home relay",
                    subtitle = "VLESS",
                    onClick = {},
                    modifier = rowModifier,
                )
            }
        }
    }
    VaziePreviewTheme(Appearance.MILK) {
        VazieSwipeableRow(
            start = null,
            end = VazieSwipeAction(VazieIcons.Delete, "Delete") {},
        ) { rowModifier ->
            VazieListItem(
                title = "Home relay",
                subtitle = "VLESS",
                onClick = {},
                modifier = rowModifier,
            )
        }
    }
}
