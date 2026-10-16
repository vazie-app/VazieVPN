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

// Previews run with motion off (see rememberReduceMotion), so every frame here is also the
// reduced-motion rendering: each state must read from its shape alone.

@Composable
private fun RouteColumn(content: @Composable () -> Unit) {
    VazieTheme {
        Column(
            modifier = Modifier
                .background(VazieTheme.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@VaziePreview
@Composable
private fun VazieRouteStatesPreview() {
    RouteColumn {
        VazieRouteState.entries.forEach { state ->
            VazieRoute(
                state = state,
                deviceLabel = "Вы",
                serverLabel = "Мой сервер",
                serverCode = "VL",
                contentDescription = "$state. Маршрут: это устройство — Мой сервер",
                onClick = {},
            )
        }
    }
}

@Preview(name = "Route · own configuration, long Russian labels", widthDp = 360)
@Composable
private fun VazieRouteOwnConfigPreview() {
    RouteColumn {
        VazieRoute(
            state = VazieRouteState.Connected,
            deviceLabel = "Это устройство",
            serverLabel = "Моя конфигурация для дачи в Подмосковье",
            contentDescription = "Подключено. Маршрут: это устройство — Моя конфигурация",
        )
        VazieRoute(
            state = VazieRouteState.Failed,
            deviceLabel = "Это устройство",
            serverLabel = "Санкт-Петербург",
            contentDescription = "Не удалось подключиться",
        )
    }
}

@Preview(name = "Route · narrow and large font", widthDp = 300, fontScale = 1.5f)
@Composable
private fun VazieRouteNarrowPreview() {
    RouteColumn {
        VazieRoute(
            state = VazieRouteState.Reconnecting,
            deviceLabel = "Вы",
            serverLabel = "Хельсинки",
            serverCode = "FI",
            contentDescription = "Переподключаемся",
        )
    }
}
