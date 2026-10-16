package app.vazie.vpn.feature.connections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Everything a person connects through, on Home: their own configurations. */
@Composable
fun ConnectionsSection(
    onOpenConfiguration: (String) -> Unit,
    onAddConfiguration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ConnectionsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ConnectionsEffect.OpenAddConfiguration -> onAddConfiguration()
                is ConnectionsEffect.OpenConfiguration -> onOpenConfiguration(effect.id)
            }
        }
    }

    ConnectionsList(state = state, onAction = viewModel::onAction, modifier = modifier)
}
