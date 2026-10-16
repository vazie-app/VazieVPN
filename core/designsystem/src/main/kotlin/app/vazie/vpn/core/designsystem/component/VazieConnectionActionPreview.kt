package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VaziePreview
@Composable
private fun VazieConnectionActionStatesPreview() {
    VazieTheme {
        Column(
            modifier = Modifier.background(VazieTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VazieConnectionAction(VazieConnectionActionKind.Connect, "Подключиться", onClick = {})
            VazieConnectionAction(VazieConnectionActionKind.Connecting, "Отменить", onClick = {}, stateDescription = "идёт подключение")
            VazieConnectionAction(VazieConnectionActionKind.Disconnect, "Отключиться", onClick = {})
            VazieConnectionAction(VazieConnectionActionKind.Reconnecting, "Отменить", onClick = {})
            VazieConnectionAction(VazieConnectionActionKind.Retry, "Повторить", onClick = {})
            VazieConnectionAction(VazieConnectionActionKind.Connect, "Подключиться", onClick = {}, enabled = false)
        }
    }
}

@Preview(name = "Connection action · large font, long label", widthDp = 320, fontScale = 1.6f)
@Composable
private fun VazieConnectionActionLargeFontPreview() {
    VazieTheme {
        Column(modifier = Modifier.background(VazieTheme.colors.background).padding(16.dp)) {
            VazieConnectionAction(VazieConnectionActionKind.Retry, "Попробовать подключиться ещё раз", onClick = {})
        }
    }
}
