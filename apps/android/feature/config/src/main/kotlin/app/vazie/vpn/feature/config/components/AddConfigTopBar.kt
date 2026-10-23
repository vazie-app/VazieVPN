package app.vazie.vpn.feature.config.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieIconButton
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.feature.config.R

/** The wizard's top bar, which is `VazieToolbar` with the close button on the trailing side. */
@Composable
internal fun AddConfigTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VazieToolbar(title = title, modifier = modifier) {
        VazieIconButton(
            onClick = onClose,
            contentDescription = stringResource(R.string.config_close),
        ) {
            Icon(imageVector = VazieIcons.Close, contentDescription = null)
        }
    }
}
