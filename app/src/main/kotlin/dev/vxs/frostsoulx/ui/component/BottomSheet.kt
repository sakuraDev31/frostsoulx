/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dev.vxs.frostsoulx.LocalAnimationsDisabled
import dev.vxs.frostsoulx.constants.BottomSheetAnimationSpec
import dev.vxs.frostsoulx.constants.BottomSheetSoftAnimationSpec
import kotlin.math.abs

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    onDismiss: (() -> Unit)? = null,
    collapsedContentHeight: Dp? = null,
    sharedArtworkKey: String? = null,
    immersiveArtwork: Boolean = false,
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (sharedArtworkKey != null) {
        PlayerArtworkBottomSheet(
            state, sharedArtworkKey, modifier, backgroundColor, onDismiss,
            collapsedContentHeight, collapsedContent, content,
            immersive = immersiveArtwork,
        )
        return
    }
    val sheetDragModifier =
        if (!state.isCollapsed && !state.isDismissed) {
            Modifier.bottomSheetDraggable(state, onDismiss)
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .offset {
                    val y =
                        (state.expandedBound - state.value)
                            .roundToPx()
                            .coerceAtLeast(0)
                    IntOffset(x = 0, y = y)
                }.then(sheetDragModifier)
                .clip(
                    RoundedCornerShape(
                        topStart = if (!state.isExpanded) 16.dp else 0.dp,
                        topEnd = if (!state.isExpanded) 16.dp else 0.dp,
                    ),
                ).background(
                    backgroundColor.copy(
                        alpha = backgroundColor.alpha * state.progress.coerceIn(0f, 1f),
                    ),
                ),
    ) {
        if (state.isExpandedOrExpanding) {
            BackHandler(onBack = state::collapseSoft)
        }

        if (!state.isCollapsed) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = ((state.progress - 0.25f) * 4).coerceIn(0f, 1f)
                        },
                content = content,
            )
        }

                if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                    Modifier
                        .graphicsLayer {
                            alpha = 1f - (state.progress * 4).coerceAtMost(1f)
                        }
                        .fillMaxWidth()
                        .height(collapsedContentHeight ?: state.collapsedBound),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(collapsedContentHeight ?: state.collapsedBound)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = state::expandSoft,
                        ),
                    content = collapsedContent,
                )
            }
        }

    }
}

@Stable
class BottomSheetState(
    private val coroutineScope: CoroutineScope,
    private var density: Density,
    private var animationsDisabled: Boolean,
    private val onAnchorChanged: (Int) -> Unit,
    initialAnchor: Int = DISMISSED_ANCHOR,
    initialDismissedBound: Dp,
    initialExpandedBound: Dp,
    initialCollapsedBound: Dp,
) : DraggableState {
    var dismissedBound by mutableStateOf(initialDismissedBound)
        private set

    var expandedBound by mutableStateOf(initialExpandedBound)
        private set

    var collapsedBound by mutableStateOf(initialCollapsedBound)
        private set

    private val animatable =
        Animatable(
            initialValue =
                when (initialAnchor) {
                    EXPANDED_ANCHOR -> initialExpandedBound
                    COLLAPSED_ANCHOR -> initialCollapsedBound
                    DISMISSED_ANCHOR -> initialDismissedBound
                    else -> initialCollapsedBound
                },
            typeConverter = Dp.VectorConverter,
        )

    init {
        animatable.updateBounds(
            lowerBound = initialDismissedBound.coerceAtMost(initialExpandedBound),
            upperBound = initialExpandedBound,
        )
    }

    private var animationJob: Job? = null

    val value: Dp by animatable.asState()

    var targetAnchor by mutableIntStateOf(initialAnchor)
        private set

    val isDismissed by derivedStateOf {
        value <= dismissedBound || abs(value.value - dismissedBound.value) <= 1f
    }

    val isCollapsed by derivedStateOf {
        value <= collapsedBound || abs(value.value - collapsedBound.value) <= 1f
    }

    val isExpanded by derivedStateOf {
        value >= expandedBound || abs(value.value - expandedBound.value) <= 1f
    }

    val isExpandedOrExpanding: Boolean
        get() = targetAnchor == EXPANDED_ANCHOR

    val progress by derivedStateOf {
        val span = expandedBound - collapsedBound
        if (span <= 0.dp) 0f
        else ((value - collapsedBound) / span).coerceIn(0f, 1f)
    }

    fun updateDensity(newDensity: Density) {
        density = newDensity
    }

    fun updateAnimationsDisabled(disabled: Boolean) {
        animationsDisabled = disabled
    }

    fun updateBounds(
        newDismissed: Dp,
        newExpanded: Dp,
        newCollapsed: Dp,
    ) {
        val oldExpanded = expandedBound
        val oldCollapsed = collapsedBound

        dismissedBound = newDismissed
        expandedBound = newExpanded
        collapsedBound = newCollapsed

        animatable.updateBounds(
            lowerBound = newDismissed.coerceAtMost(newExpanded),
            upperBound = newExpanded,
        )

        if (targetAnchor == EXPANDED_ANCHOR && (value == oldExpanded || isExpanded)) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                animatable.snapTo(newExpanded)
            }
        } else if (targetAnchor == COLLAPSED_ANCHOR && (value == oldCollapsed || isCollapsed)) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                animatable.snapTo(newCollapsed)
            }
        }
    }

    private fun updateAnchor(anchor: Int) {
        targetAnchor = anchor
        onAnchorChanged(anchor)
    }

    fun collapse(
        animationSpec: AnimationSpec<Dp> = if (animationsDisabled) snap() else BottomSheetAnimationSpec,
        initialVelocity: Dp = 0.dp,
    ) {
        updateAnchor(COLLAPSED_ANCHOR)
        animationJob?.cancel()
        animationJob =
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                animatable.animateTo(
                    targetValue = collapsedBound,
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity,
                )
            }
    }

    fun expand(
        animationSpec: AnimationSpec<Dp> = if (animationsDisabled) snap() else BottomSheetAnimationSpec,
        initialVelocity: Dp = 0.dp,
    ) {
        updateAnchor(EXPANDED_ANCHOR)
        animationJob?.cancel()
        animationJob =
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                animatable.animateTo(
                    targetValue = expandedBound,
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity,
                )
            }
    }

    fun collapseSoft() {
        collapse(if (animationsDisabled) snap() else BottomSheetSoftAnimationSpec)
    }

    fun expandSoft() {
        expand(if (animationsDisabled) snap() else BottomSheetSoftAnimationSpec)
    }

    fun dismiss(
        animationSpec: AnimationSpec<Dp> = if (animationsDisabled) snap() else BottomSheetAnimationSpec,
        initialVelocity: Dp = 0.dp,
    ) {
        updateAnchor(DISMISSED_ANCHOR)
        animationJob?.cancel()
        animationJob =
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                animatable.animateTo(
                    targetValue = dismissedBound,
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity,
                )
            }
    }

    fun snapTo(target: Dp) {
        updateAnchor(
            when {
                abs(target.value - expandedBound.value) <= 1f -> EXPANDED_ANCHOR
                abs(target.value - collapsedBound.value) <= 1f -> COLLAPSED_ANCHOR
                abs(target.value - dismissedBound.value) <= 1f -> DISMISSED_ANCHOR
                else -> COLLAPSED_ANCHOR
            },
        )
        animationJob?.cancel()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(target)
        }
    }

    override fun dispatchRawDelta(delta: Float) {
        if (delta == 0f) return
        animationJob?.cancel()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val deltaDp = with(density) { delta.toDp() }
            val newDp = (animatable.value - deltaDp).coerceIn(dismissedBound, expandedBound)
            animatable.snapTo(newDp)
        }
    }

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend DragScope.() -> Unit,
    ) {
        animationJob?.cancel()
        val dragScope =
            object : DragScope {
                override fun dragBy(pixels: Float) {
                    dispatchRawDelta(pixels)
                }
            }
        dragScope.block()
    }

    fun performFling(
        velocity: Float,
        onDismiss: (() -> Unit)?,
    ) {
        val velocityDp = with(density) { velocity.toDp() }.coerceIn(-4000.dp, 4000.dp)

        if (velocity > 400f) {
            expand(initialVelocity = velocityDp)
        } else if (velocity < -400f) {
            if (value <= collapsedBound && onDismiss != null) {
                dismiss(initialVelocity = velocityDp)
                onDismiss.invoke()
            } else {
                collapse(initialVelocity = velocityDp)
            }
        } else {
            val dismissThreshold = (dismissedBound + collapsedBound) / 2
            val expandThreshold = (collapsedBound + expandedBound) / 2

            when {
                value < dismissThreshold -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse()
                    }
                }
                value < expandThreshold -> {
                    collapse()
                }
                else -> {
                    expand()
                }
            }
        }
    }

    val preUpPostDownNestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            private var isTopReached = false

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (isExpanded && available.y < 0) {
                    isTopReached = false
                }

                return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!isTopReached) {
                    isTopReached = consumed.y == 0f && available.y > 0
                }

                return if (isTopReached && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isTopReached) {
                    isTopReached = false
                    val velocity = -available.y
                    performFling(velocity, null)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (isTopReached) {
                    isTopReached = false
                    val velocity = -available.y
                    performFling(velocity, null)
                    return available
                }
                return Velocity.Zero
            }
        }
}

const val EXPANDED_ANCHOR = 2
const val COLLAPSED_ANCHOR = 1
const val DISMISSED_ANCHOR = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = DISMISSED_ANCHOR,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val animationsDisabled = LocalAnimationsDisabled.current

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }

    val state =
        remember(coroutineScope) {
            BottomSheetState(
                coroutineScope = coroutineScope,
                density = density,
                animationsDisabled = animationsDisabled,
                onAnchorChanged = { previousAnchor = it },
                initialAnchor = previousAnchor,
                initialDismissedBound = dismissedBound,
                initialExpandedBound = expandedBound,
                initialCollapsedBound = collapsedBound,
            )
        }

    state.updateDensity(density)
    state.updateAnimationsDisabled(animationsDisabled)
    state.updateBounds(dismissedBound, expandedBound, collapsedBound)

    return state
}

@Composable
fun Modifier.bottomSheetDraggable(
    state: BottomSheetState,
    onDismiss: (() -> Unit)? = null,
): Modifier =
    this.pointerInput(state) {
        val velocityTracker = VelocityTracker()

        detectVerticalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPointerInputChange(change)
                state.dispatchRawDelta(dragAmount)
            },
            onDragCancel = {
                val velocity = -velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                state.performFling(velocity, onDismiss)
            },
            onDragEnd = {
                val velocity = -velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                state.performFling(velocity, onDismiss)
            },
        )
    }
