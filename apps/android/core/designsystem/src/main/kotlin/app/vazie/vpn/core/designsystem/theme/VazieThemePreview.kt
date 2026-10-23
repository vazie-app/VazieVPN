package app.vazie.vpn.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// The "Маршрут" foundation at a glance: the Night Indigo roles and the type scale, drawn with the
// tokens themselves so a change to a token shows up here first.

@Preview(name = "Night Indigo · palette", widthDp = 380, heightDp = 760)
@Composable
private fun NightIndigoPalettePreview() {
    VazieTheme {
        val c = VazieTheme.colors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Night Indigo", style = VazieTheme.typography.headline, color = c.textPrimary)
            Swatch("background", c.background, c.textPrimary)
            Swatch("surface", c.surface, c.textPrimary)
            Swatch("elevated", c.elevated, c.textPrimary)
            Swatch("surfaceMuted · raised", c.surfaceMuted, c.textPrimary)
            Swatch("routeSoft", c.routeSoft, c.onAccentContainer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Brush.horizontalGradient(listOf(c.routeStart, c.routeEnd)), VazieTheme.shapes.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("route", style = VazieTheme.typography.mono, color = c.onPrimary, modifier = Modifier.padding(start = 12.dp))
            }
            Swatch("action", c.action, c.onAction)
            Swatch("successContainer", c.successContainer, c.onSuccessContainer)
            Swatch("warningContainer", c.warningContainer, c.onWarningContainer)
            Swatch("errorContainer", c.errorContainer, c.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("primary", style = VazieTheme.typography.body, color = c.textPrimary)
                Text("secondary", style = VazieTheme.typography.body, color = c.textSecondary)
                Text("muted", style = VazieTheme.typography.body, color = c.textMuted)
                Text("disabled", style = VazieTheme.typography.body, color = c.textDisabled)
            }
        }
    }
}

@Composable
private fun Swatch(name: String, fill: Color, content: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(fill, VazieTheme.shapes.sm)
            .border(VazieTheme.borders.hairline, VazieTheme.colors.dividerSubtle, VazieTheme.shapes.sm)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(name, style = VazieTheme.typography.mono, color = content)
    }
}

@Preview(name = "Маршрут · type scale", widthDp = 380, heightDp = 820)
@Composable
private fun TypeScalePreview() {
    VazieTheme {
        val t = VazieTheme.typography
        val c = VazieTheme.colors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Specimen("display", "Подключено", t.display)
            Specimen("headline", "Подключения", t.headline)
            Specimen("title", "Мои конфигурации", t.title)
            Specimen("titleSmall", "Мой сервер", t.titleSmall)
            Specimen("body", "Трафик идёт через выбранный сервер.", t.body)
            Specimen("bodySecondary", "Прокладываем маршрут до сервера · шаг 2 из 3", t.bodySecondary)
            Specimen("label", "VLESS Reality", t.label)
            Specimen("caption", "Можно переименовать позже", t.caption)
            Specimen("button", "Подключиться", t.button)
            Specimen("sectionLabel", "ПОДКЛЮЧЕНИЕ", t.sectionLabel)
            Specimen("monoDisplay", "00:42:18", t.monoDisplay)
            Specimen("monoTitle", "203.0.113.42", t.monoTitle)
            Specimen("mono", "vless · reality · xtls-rprx-vision", t.mono)
            Specimen("monoMicro", "VPN+", t.monoMicro)
        }
    }
}

@Composable
private fun Specimen(role: String, sample: String, style: TextStyle) {
    Column {
        Text(role, style = VazieTheme.typography.monoSmall, color = VazieTheme.colors.textMuted)
        Text(sample, style = style, color = VazieTheme.colors.textPrimary)
    }
}
