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

// Documentation addresses only (RFC 5737): no real server is named or implied here.

@VaziePreview
@Composable
private fun VazieConnectionCardVariantsPreview() {
    VazieTheme {
        Column(
            modifier = Modifier.background(VazieTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VazieConnectionCard(
                title = "Моя Reality",
                subtitle = "VLESS Reality · 203.0.113.10",
                subtitleTechnical = true,
                mark = VazieConnectionMark.Code("VL"),
                actionLabel = "Сменить",
                onClick = {},
            )
            VazieConnectionCard(
                title = "Нет конфигурации",
                subtitle = "Добавьте ссылку VLESS, чтобы подключиться",
                mark = VazieConnectionMark.Empty,
                actionLabel = "Добавить",
                onClick = {},
            )
            VazieConnectionCard(
                title = "Рабочая подписка",
                subtitle = "Недоступна сейчас",
                mark = VazieConnectionMark.Locked,
                locked = true,
                onClick = {},
            )
        }
    }
}

@Preview(name = "Connection card · long Russian, large font", widthDp = 320, fontScale = 1.4f)
@Composable
private fun VazieConnectionCardLongPreview() {
    VazieTheme {
        Column(modifier = Modifier.background(VazieTheme.colors.background).padding(16.dp)) {
            VazieConnectionCard(
                title = "Конфигурация для работы из загородного дома",
                subtitle = "VLESS Reality · vpn.very-long-subdomain.example.org",
                subtitleTechnical = true,
                mark = VazieConnectionMark.Server,
                actionLabel = "Сменить",
                onClick = {},
            )
        }
    }
}
