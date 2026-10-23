package app.vazie.vpn.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.VazieTheme

// Previews draw with motion off, so every frame is also the reduced-motion Home.

/** Every connection state, one render each — the fastest way to review the whole vocabulary. */
private class HomeStates : PreviewParameterProvider<HomeUiState> {
    override val values = HomeFixtures.allStates.asSequence()
}

@VazieScreenPreview
@Composable
private fun HomeScreenStatesPreview(@PreviewParameter(HomeStates::class) state: HomeUiState) {
    VazieTheme {
        HomeScreen(state = state, onAction = {})
    }
}

@VazieScreenPreview
@Composable
private fun HomeScreenConnectedPreview() {
    VazieTheme {
        HomeScreen(state = HomeFixtures.connected, onAction = {})
    }
}

@VazieScreenPreview
@Composable
private fun HomeScreenEmptyPreview() {
    VazieTheme {
        HomeScreen(state = HomeUiState.Empty, onAction = {})
    }
}

@Preview(name = "Narrow", widthDp = 300, heightDp = 720, locale = "ru")
@Preview(name = "Large font", widthDp = 360, heightDp = 760, fontScale = 1.8f, locale = "ru")
@Composable
private fun HomeScreenRussianPreview() {
    VazieTheme {
        HomeScreen(
            state = HomeUiState.Ready(
                configuration = HomeFixtures.configuration.copy(
                    name = "Конфигурация для работы из загородного дома в Подмосковье",
                ),
                connection = ConnectionUiState.Failed(ConnectionFailureUi.TUNNEL_UNUSABLE),
            ),
            onAction = {},
        )
    }
}
