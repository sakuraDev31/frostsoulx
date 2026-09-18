package dev.vxs.frostsoulx.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.GenericShape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Two retained render layers, not bitmaps or additional artwork/image requests. */
internal class PlayerArtworkAnchor(val layer: GraphicsLayer) {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    var recorded by mutableStateOf(false)
    var round: Boolean = false
    var rotation: () -> Float = { 0f }
}

internal class PlayerArtworkTransition(val sheet: BottomSheetState) {
    var source by mutableStateOf<PlayerArtworkAnchor?>(null)
    var target by mutableStateOf<PlayerArtworkAnchor?>(null)
    var root by mutableStateOf<LayoutCoordinates?>(null)
    var enabled by mutableStateOf(true)
    val active: Boolean
        get() = enabled && sheet.progress > 0f && sheet.progress < 1f &&
            root?.isAttached == true && source?.coordinates?.isAttached == true &&
            target?.coordinates?.isAttached == true &&
            source?.recorded == true && target?.recorded == true
}

internal val LocalPlayerArtworkTransition = staticCompositionLocalOf<PlayerArtworkTransition?> { null }

/** Measure the actual artwork node, including insets, scrolling, and compact/landscape layouts. */
@Composable
internal fun Modifier.playerArtwork(
    expanded: Boolean,
    round: Boolean = false,
    rotation: () -> Float = { 0f },
): Modifier {
    val transition = LocalPlayerArtworkTransition.current ?: return this
    val layer = rememberGraphicsLayer()
    val anchor = remember(transition, layer) { PlayerArtworkAnchor(layer) }
    val currentRotation = rememberUpdatedState(rotation)
    anchor.round = round
    anchor.rotation = { currentRotation.value() }
    DisposableEffect(transition, anchor, expanded) {
        if (expanded) transition.source = anchor else transition.target = anchor
        onDispose {
            if (transition.source === anchor) transition.source = null
            if (transition.target === anchor) transition.target = null
        }
    }
    return onGloballyPositioned { anchor.coordinates = it }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            anchor.recorded = true
            if (!transition.active) {
                layer.alpha = 1f
                drawLayer(layer)
            }
        }
}

/** Opt-in player sheet: the mini player stays docked while its artwork travels above the page. */
@Composable
internal fun PlayerArtworkBottomSheet(
    state: BottomSheetState,
    artworkKey: String,
    modifier: Modifier,
    backgroundColor: Color,
    onDismiss: (() -> Unit)?,
    collapsedContentHeight: Dp?,
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = remember(state, artworkKey) { PlayerArtworkTransition(state) }
    if (state.isExpandedOrExpanding) BackHandler(onBack = state::collapseSoft)
    CompositionLocalProvider(LocalPlayerArtworkTransition provides transition) {
        Box(modifier.fillMaxSize().onGloballyPositioned { transition.root = it }) {
            // Keep the small destination measured and its render layer warm. The full sheet
            // is above it for hit testing; hidden semantics must not expose duplicate controls.
            if (onDismiss == null || !state.isDismissed) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(collapsedContentHeight ?: state.collapsedBound)
                        .offset {
                            IntOffset(0, (state.expandedBound - minOf(state.value, state.collapsedBound)).roundToPx())
                        }
                        .graphicsLayer { alpha = (1f - state.progress / 0.6f).coerceIn(0f, 1f) }
                        .then(if (!state.isCollapsed) Modifier.clearAndSetSemantics {} else Modifier)
                        .clickable(onClick = state::expandSoft),
                    content = collapsedContent,
                )
            }
            if (!state.isCollapsed && !state.isDismissed) {
                Box(
                    Modifier.fillMaxSize()
                        .offset { IntOffset(0, (state.expandedBound - state.value).roundToPx().coerceAtLeast(0)) }
                        .bottomSheetDraggable(state, onDismiss)
                        .graphicsLayer {
                            // Clip the visible page, not its full off-screen height: all four
                            // corners become rounded during dismissal without relaying out content.
                            val radius = 28.dp.toPx() * ((1f - state.progress) * 6f).coerceIn(0f, 1f)
                            val visibleHeight = state.value.toPx().coerceIn(0f, size.height)
                            shape = playerPageShape(visibleHeight, radius)
                            clip = true
                        }
                        .background(backgroundColor.copy(alpha = backgroundColor.alpha * state.progress.coerceIn(0f, 1f))),
                ) {
                    BoxWithConstraints(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = ((state.progress - 0.12f) / 0.88f).coerceIn(0f, 1f)
                        },
                        content = content,
                    )
                }
            }
            Box(
                Modifier.matchParentSize().drawWithCache {
                    val clip = Path()
                    onDrawBehind {
                        if (!transition.active) return@onDrawBehind
                        val source = transition.source ?: return@onDrawBehind
                        val target = transition.target ?: return@onDrawBehind
                        val root = transition.root ?: return@onDrawBehind
                        val from = source.coordinates ?: return@onDrawBehind
                        val to = target.coordinates ?: return@onDrawBehind
                        val p = state.progress.coerceIn(0f, 1f)
                        // Subtract the sheet's translation: artwork follows one continuous
                        // trajectory instead of inheriting the page's downward slide as well.
                        val sourceCenter = root.localPositionOf(from, Offset(from.size.width / 2f, from.size.height / 2f)) -
                            Offset(0f, (state.expandedBound - state.value).roundToPx().toFloat())
                        val targetCenter = root.localPositionOf(to, Offset(to.size.width / 2f, to.size.height / 2f))
                        val center = targetCenter + (sourceCenter - targetCenter) * p
                        val width = to.size.width + (from.size.width - to.size.width) * p
                        val height = to.size.height + (from.size.height - to.size.height) * p
                        if (width <= 0f || height <= 0f) return@onDrawBehind
                        val sourceRadius = if (source.round) minOf(width, height) / 2f else 0f
                        val radius = 8.dp.toPx() * (1f - p) + sourceRadius * p
                        clip.reset()
                        clip.addRoundRect(RoundRect(0f, 0f, width, height, CornerRadius(radius)))
                        val rotation = ((source.rotation() % 360f + 540f) % 360f - 180f) * p
                        withTransform({ translate(center.x - width / 2f, center.y - height / 2f) }) {
                            clipPath(clip) {
                                // Blend into the mini layer to handle canvas covers and the
                                // immersive lower-edge mask without a last-frame artwork swap.
                                val targetAlpha = ((1f - p) * 2f).coerceIn(0f, 1f)
                                source.layer.alpha = 1f - targetAlpha
                                target.layer.alpha = targetAlpha
                                withTransform({
                                    rotate(rotation, Offset(width / 2f, height / 2f))
                                    scale(width / from.size.width, height / from.size.height, Offset.Zero)
                                }) { drawLayer(source.layer) }
                                withTransform({
                                    scale(width / to.size.width, height / to.size.height, Offset.Zero)
                                }) { drawLayer(target.layer) }
                            }
                        }
                    }
                },
            )
        }
    }
}

private fun playerPageShape(height: Float, radius: Float): Shape = GenericShape { size, _ ->
    addRoundRect(RoundRect(0f, 0f, size.width, height, CornerRadius(radius)))
}
