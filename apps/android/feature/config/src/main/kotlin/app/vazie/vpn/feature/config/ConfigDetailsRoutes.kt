package app.vazie.vpn.feature.config

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.vazie.vpn.core.model.Secret
import kotlinx.serialization.Serializable

/** The configuration detail screen. */
@Serializable
data class ConfigDetailsRoute(val configurationId: String)

fun NavController.navigateToConfigDetails(configurationId: String) =
    navigate(ConfigDetailsRoute(configurationId))

/** Registered by `:app` inside the Connections graph — the screen belongs to `:feature:config`, the tab
 * belongs to `:feature:connections`, and only the composition root sees both (R1). */
fun NavGraphBuilder.configDetailsScreen(navController: NavController) {
    composable<ConfigDetailsRoute> { entry ->
        val viewModel: ConfigDetailsViewModel = hiltViewModel(viewModelStoreOwner = entry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val duplicated = stringResource(R.string.config_duplicated)
        val chooserTitle = stringResource(R.string.config_export_chooser)

        LaunchedEffect(viewModel) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    ConfigDetailsEffect.Close -> navController.popBackStack()
                    ConfigDetailsEffect.Duplicated ->
                        Toast.makeText(context, duplicated, Toast.LENGTH_SHORT).show()

                    is ConfigDetailsEffect.Share -> context.share(effect.link, chooserTitle)
                }
            }
        }

        // Hide a revealed secret before the task-switcher thumbnail is captured.
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            viewModel.onAction(ConfigDetailsAction.HideSecret)
        }

        ConfigDetailsScreen(state = state, onAction = viewModel::onAction)
    }
}

/** Hands the link to Android's share sheet, and nothing else. */
private fun Context.share(link: Secret<String>, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link.expose())
    }
    runCatching { startActivity(Intent.createChooser(send, chooserTitle)) }
        .onFailure { failure -> if (failure !is ActivityNotFoundException) throw failure }
}
