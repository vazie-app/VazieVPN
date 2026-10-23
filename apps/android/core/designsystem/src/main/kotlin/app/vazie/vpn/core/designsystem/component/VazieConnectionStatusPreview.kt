package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VaziePreview
@Composable
private fun VazieConnectionStatusStatesPreview() {
    VazieTheme {
        Column(
            modifier = Modifier.background(VazieTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VazieConnectionStatus(VazieRouteState.Disconnected, "Не подключено", subtitle = "Трафик идёт напрямую через вашего провайдера")
            VazieConnectionStatus(VazieRouteState.Connecting, "Подключаемся…", subtitle = "Прокладываем маршрут")
            VazieConnectionStatus(VazieRouteState.Connected, "Подключено", subtitle = "00:42:18 · VLESS Reality", subtitleTechnical = true)
            VazieConnectionStatus(VazieRouteState.Reconnecting, "Переподключаемся…", subtitle = "Связь прервалась, восстанавливаем маршрут")
            VazieConnectionStatus(VazieRouteState.Failed, "Не удалось подключиться", subtitle = "Сервер не ответил за 10 секунд")
        }
    }
}

@VaziePreview
@Composable
private fun VazieTechnicalValuesPreview() {
    VazieTheme {
        Column(
            modifier = Modifier.background(VazieTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VazieConnectionTimer(text = "00:42:18", contentDescription = "42 минуты")
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                VazieTechnicalValue(label = "Протокол", value = "VLESS Reality")
                VazieTechnicalValue(label = "Адрес", value = "203.0.113.10", icon = VazieIcons.Server)
            }
        }
    }
}

@Preview(name = "Status · long Russian error, large font", widthDp = 320, fontScale = 1.5f)
@Composable
private fun VazieConnectionStatusLongPreview() {
    VazieTheme {
        Column(modifier = Modifier.background(VazieTheme.colors.background).padding(16.dp)) {
            VazieConnectionStatus(
                VazieRouteState.Failed,
                "Не удалось подключиться",
                subtitle = "Туннель поднялся, но трафик через него не проходит. Проверьте конфигурацию.",
            )
        }
    }
}
