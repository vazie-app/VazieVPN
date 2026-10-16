package app.vazie.vpn.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.R
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieDialog
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.update.api.AppVersionPolicy
import app.vazie.vpn.update.api.UpdateStatus
import app.vazie.vpn.update.api.UpdateUrl

/**
 * The app, or — when this build is below the minimum the backend supports — the update screen and nothing else.
 *
 * [content] stays composed under the screen, so nothing is lost when the requirement is lifted, but it is hidden
 * from accessibility and every touch is taken by the screen above it. Back does nothing while it is up. The
 * screen is drawn from a status the checker holds on disk, so an offline start of a blocked build is blocked
 * from its first frame.
 */
@Composable
fun UpdateGate(
    state: AppUpdateUiState,
    onOpenUpdate: (AppVersionPolicy) -> Unit,
    onCheckAgain: () -> Unit,
    onPostpone: () -> Unit,
    onDismissPrompt: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val required = state.status as? UpdateStatus.Required
    Box(modifier = modifier.fillMaxSize()) {
        content(if (required != null) Modifier.semantics { invisibleToUser() } else Modifier)
        if (required != null) {
            // Not a destination, so nothing to pop to: the system Back is taken here and does nothing.
            BackHandler(enabled = true) {}
            UpdateRequiredScreen(
                policy = required.policy,
                checking = state.checking,
                couldNotVerify = state.couldNotVerify,
                onUpdate = { onOpenUpdate(required.policy) },
                onCheckAgain = onCheckAgain,
                // A screen of its own that takes every touch: what is underneath cannot be reached.
                modifier = Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            )
        } else {
            when (val prompt = state.prompt) {
                is UpdatePrompt.Available -> OptionalUpdateDialog(
                    policy = prompt.policy,
                    onUpdate = { onOpenUpdate(prompt.policy) },
                    onLater = onPostpone,
                )
                UpdatePrompt.UpToDate -> InfoDialog(
                    title = stringResource(R.string.update_up_to_date_title),
                    text = stringResource(R.string.update_up_to_date_body),
                    onClose = onDismissPrompt,
                )
                UpdatePrompt.CheckFailed -> InfoDialog(
                    title = stringResource(R.string.update_check_failed_title),
                    text = stringResource(R.string.update_check_failed_body),
                    onClose = onDismissPrompt,
                    retry = onCheckAgain,
                )
                null -> Unit
            }
        }
    }
}

/** The full-screen block. The primary button opens the update page; "Check again" asks the backend once more. */
@Composable
fun UpdateRequiredScreen(
    policy: AppVersionPolicy,
    checking: Boolean,
    couldNotVerify: Boolean,
    onUpdate: () -> Unit,
    onCheckAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VazieTheme.colors.background)
            .testTag(TAG_REQUIRED),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.update_required_title),
                style = VazieTheme.typography.headline,
                color = VazieTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = policy.localMessage() ?: stringResource(R.string.update_required_body),
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.update_latest_version, policy.latestVersionName),
                style = VazieTheme.typography.caption,
                color = VazieTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (couldNotVerify) {
                Text(
                    text = stringResource(R.string.update_required_offline),
                    style = VazieTheme.typography.caption,
                    color = VazieTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            VazieButton(
                text = stringResource(R.string.update_action),
                onClick = onUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_UPDATE),
            )
            VazieButton(
                text = stringResource(R.string.update_check_again),
                onClick = onCheckAgain,
                variant = VazieButtonVariant.Secondary,
                loading = checking,
                enabled = !checking,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CHECK_AGAIN),
            )
        }
    }
}

@Composable
private fun OptionalUpdateDialog(policy: AppVersionPolicy, onUpdate: () -> Unit, onLater: () -> Unit) {
    VazieDialog(
        title = stringResource(R.string.update_available_title, policy.latestVersionName),
        text = policy.localMessage() ?: stringResource(R.string.update_available_body),
        onDismissRequest = onLater,
        confirmButton = { VazieButton(text = stringResource(R.string.update_action), onClick = onUpdate) },
        dismissButton = {
            VazieButton(text = stringResource(R.string.update_later), onClick = onLater, variant = VazieButtonVariant.Text)
        },
    )
}

@Composable
private fun InfoDialog(title: String, text: String, onClose: () -> Unit, retry: (() -> Unit)? = null) {
    VazieDialog(
        title = title,
        text = text,
        onDismissRequest = onClose,
        confirmButton = {
            if (retry != null) {
                VazieButton(text = stringResource(R.string.update_retry), onClick = { onClose(); retry() })
            } else {
                VazieButton(text = stringResource(R.string.update_ok), onClick = onClose)
            }
        },
        dismissButton = retry?.let {
            { VazieButton(text = stringResource(R.string.update_close), onClick = onClose, variant = VazieButtonVariant.Text) }
        },
    )
}

/** The backend's wording in the phone's language, when it gave one. */
@Composable
private fun AppVersionPolicy.localMessage(): String? =
    message(LocalConfiguration.current.locales[0].language)

/**
 * Opens the update page, and only a page: an `https` URL with a host is handed to the browser, and anything else
 * the backend could have sent — `http`, a deep link, an `intent:` or `market:` URI — is refused before an intent is
 * built. Returns whether anything was opened.
 */
fun openUpdatePage(context: Context, url: String): Boolean {
    if (!UpdateUrl.isSafe(url)) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Composable
fun rememberOpenUpdate(): (AppVersionPolicy) -> Unit {
    val context = LocalContext.current
    return { policy -> openUpdatePage(context, policy.updateUrl) }
}

const val TAG_REQUIRED = "update-required"
const val TAG_UPDATE = "update-action"
const val TAG_CHECK_AGAIN = "update-check-again"
