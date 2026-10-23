package app.vazie.vpn.feature.config

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import app.vazie.vpn.core.model.Secret
import kotlinx.serialization.Serializable

/** The add-configuration wizard. */
/** The import wizard, opened from outside the app. */
const val ADD_CONFIG_DEEP_LINK: String = "vazie-vpn://add-config"

@Serializable
data object AddConfigGraphRoute

@Serializable
data object AddConfigMethodRoute

@Serializable
data object ImportPreviewRoute

@Serializable
data object ImportReviewRoute

@Serializable
data object ImportDoneRoute

fun NavController.navigateToAddConfig(navOptions: NavOptions? = null) =
    navigate(AddConfigGraphRoute, navOptions)

fun NavGraphBuilder.addConfigGraph(navController: NavController) {
    navigation<AddConfigGraphRoute>(startDestination = AddConfigMethodRoute) {
        composable<AddConfigMethodRoute>(
            deepLinks = listOf(navDeepLink<AddConfigMethodRoute>(basePath = ADD_CONFIG_DEEP_LINK)),
        ) { entry ->
            val viewModel = addConfigViewModel(entry, navController)
            AddConfigEffects(viewModel, navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            // The link is a credential: in memory only, never in saved state or the state object.
            var link by remember { mutableStateOf("") }
            AddConfigMethodScreen(
                state = state,
                link = link,
                onLinkChange = { link = it },
                onPaste = { context.readClipboardText().trim().takeIf { it.isNotEmpty() }?.let { link = it } },
                onSubmit = { viewModel.onLinkSubmitted(Secret.of(link.trim())) },
                onAction = viewModel::onAction,
            )
        }
        composable<ImportPreviewRoute> { entry ->
            val viewModel = addConfigViewModel(entry, navController)
            AddConfigEffects(viewModel, navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            ImportPreviewScreen(state = state, onAction = viewModel::onAction)
        }
        composable<ImportReviewRoute> { entry ->
            val viewModel = addConfigViewModel(entry, navController)
            AddConfigEffects(viewModel, navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            ImportReviewScreen(state = state, onAction = viewModel::onAction)
        }
        composable<ImportDoneRoute> { entry ->
            val viewModel = addConfigViewModel(entry, navController)
            AddConfigEffects(viewModel, navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            ImportDoneScreen(state = state, onAction = viewModel::onAction)
        }
    }
}

/** One ViewModel for the whole wizard: it is scoped to the graph's entry, so every step shares the draft. */
@Composable
private fun addConfigViewModel(
    entry: NavBackStackEntry,
    navController: NavController,
): AddConfigViewModel {
    val graphEntry = remember(entry) { navController.getBackStackEntry(AddConfigGraphRoute) }
    return hiltViewModel(viewModelStoreOwner = graphEntry)
}

@Composable
private fun AddConfigEffects(
    viewModel: AddConfigViewModel,
    navController: NavController,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel, navController, context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AddConfigEffect.OpenPreview -> navController.navigate(ImportPreviewRoute)
                AddConfigEffect.OpenReview -> navController.navigate(ImportReviewRoute)
                AddConfigEffect.OpenDone -> navController.navigate(ImportDoneRoute)
                AddConfigEffect.Close ->
                    navController.popBackStack(AddConfigGraphRoute, inclusive = true)

                AddConfigEffect.ReadClipboard ->
                    viewModel.onPasted(Secret.of(context.readClipboardText()))
            }
        }
    }
}

/** The clipboard, read once, at the moment the user asked for it. */
private fun Context.readClipboardText(): String {
    val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return ""
    return (0 until clip.itemCount)
        .firstNotNullOfOrNull { index -> clip.getItemAt(index).coerceToText(this)?.toString() }
        .orEmpty()
}
