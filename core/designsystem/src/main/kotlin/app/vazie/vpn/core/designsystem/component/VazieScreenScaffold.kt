package app.vazie.vpn.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** How far the fade above a floating bottom bar reaches. */
private val FadeHeight = 28.dp

/** How far the fade under a top bar reaches. */
private val TopFadeHeight = 20.dp

/** How long the top fade takes to arrive. */
private const val HazeFadeMillis = 180

/** The frame every Vazie screen sits in: background, safe insets, and the edges content passes under. */
/** Whether this column has scrolled anything under the toolbar. */
fun ScrollState.scrolledUnderToolbar(): () -> Boolean = { value > 0 }

/** The same question, for a lazy list. */
fun LazyListState.scrolledUnderToolbar(): () -> Boolean = { canScrollBackward }

/** [contentScrolledUnderToolbar] comes from the screen's scroll state (`scrolledUnderToolbar()`); the
 * default means no fade, right for content that does not scroll. */
@Composable
fun VazieScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    contentScrolledUnderToolbar: () -> Boolean = { false },
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = VazieTheme.colors.background
    val scrollEdge = VazieTheme.scrollEdge
    val spacing = VazieTheme.spacing
    val insets = if (scrollEdge > 0.dp) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing
    }

    // Zero at rest, one once something has gone under the bar.
    val under = contentScrolledUnderToolbar()
    val hazeAlpha by animateFloatAsState(
        targetValue = if (under) 1f else 0f,
        animationSpec = if (VazieTheme.reduceMotion) snap() else tween(HazeFadeMillis),
        label = "topHaze",
    )

    Box(modifier = modifier.fillMaxSize().background(background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(insets)) {
            if (topBar != null) {
                // The bar, the room under it and the fade that hangs below both, as one block.
                Box(modifier = Modifier.zIndex(1f)) {
                    Column {
                        topBar()
                        // The top gap for every screen, as a token (`VazieSpacing.screenContentTop`).
                        Spacer(modifier = Modifier.height(spacing.screenContentTop))
                    }
                    Spacer(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = TopFadeHeight)
                            .fillMaxWidth()
                            .height(TopFadeHeight)
                            // Zero until content is actually under the bar.
                            .graphicsLayer { alpha = hazeAlpha }
                            .background(
                                Brush.verticalGradient(listOf(background, Color.Transparent)),
                            ),
                    )
                }
            }
            content()
        }

        if (scrollEdge > 0.dp) {
            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FadeHeight)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, background))),
                )
                // Solid under the bar itself, so nothing shows through the gaps a floating pill
                // leaves at its sides.
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scrollEdge)
                        .background(background),
                )
            }
        }
    }
}

/** The room a scrollable screen leaves at the end of its content so the last row clears the bottom bar. */
@Composable
fun Modifier.vazieScrollEdgePadding(extra: Dp = VazieTheme.spacing.lg): Modifier =
    padding(bottom = VazieTheme.scrollEdge + extra)
