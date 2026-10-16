package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieFaqItem
import app.vazie.vpn.core.model.VazieFaqLink
import app.vazie.vpn.core.model.VazieFaqText

/** Placeholder questions for previews only; the app shows what the backend publishes. */
private val PreviewItems = listOf(
    VazieFaqItem(
        key = "what",
        question = VazieFaqText("Где хранятся конфигурации?", "Where are configurations stored?"),
        answer = VazieFaqText("Платный доступ к серверам Vazie.\nСвои конфигурации — бесплатно.", "Paid access to Vazie's servers.\nYour own configurations stay free."),
    ),
    VazieFaqItem(
        key = "refund",
        question = VazieFaqText("Как вернуть деньги?", "How do I get a refund?"),
        answer = VazieFaqText("Напишите нам на почту.", "Write to us."),
        link = VazieFaqLink("/legal/refunds", VazieFaqText("Возврат и отказ от покупки", "Refunds and cancellation")),
    ),
)

@VazieScreenPreview
@Composable
private fun FaqScreenPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        FaqScreen(onBack = {}, state = FaqUiState.Ready(PreviewItems), onRetry = {})
    }
}

@Preview(name = "ru · large font", widthDp = 320, heightDp = 900, fontScale = 1.5f, locale = "ru")
@Composable
private fun FaqScreenRussianPreview() {
    VazieTheme {
        FaqScreen(onBack = {}, state = FaqUiState.Ready(PreviewItems), onRetry = {})
    }
}

@Preview(name = "unavailable", widthDp = 360, heightDp = 400)
@Composable
private fun FaqScreenUnavailablePreview() {
    VazieTheme {
        FaqScreen(onBack = {}, state = FaqUiState.Unavailable, onRetry = {})
    }
}
