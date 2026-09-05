/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vxs.frostsoulx.LocalPlayerConnection
import dev.vxs.frostsoulx.constants.MiniPlayerHeight
import dev.vxs.frostsoulx.extensions.togglePlayPause
import dev.vxs.frostsoulx.ui.player.frostsoul.FSMiniPlayer
import dev.vxs.frostsoulx.ui.player.frostsoul.FrostSoulTrack
import dev.vxs.frostsoulx.ui.player.frostsoul.rememberFrostSoulPalette
import kotlin.math.abs

@Composable
fun MiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    isPairedWithNavigation: Boolean = false,
    onQueueClick: (() -> Unit)? = null,
    onOpenFullPlayer: () -> Unit = {},
    onSmartPeekChanged: (Boolean) -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val metadata = mediaMetadata ?: return
    val palette = rememberFrostSoulPalette(metadata.thumbnailUrl)
    val interactionSource = remember { MutableInteractionSource() }
    val openPlayer by rememberUpdatedState(onOpenFullPlayer)
    val onPeekChanged by rememberUpdatedState(onSmartPeekChanged)
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var quickMenuVisible by remember(metadata.id) { mutableStateOf(false) }
    var dragOffsetX by remember(metadata.id) { mutableFloatStateOf(0f) }
    var dragDistanceX by remember(metadata.id) { mutableFloatStateOf(0f) }
    var dragDistanceY by remember(metadata.id) { mutableFloatStateOf(0f) }
    val settledOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
        label = "mini-player-swipe",
    )

    Box(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = if (isPairedWithNavigation) 8.dp else 12.dp)
            .height(MiniPlayerHeight)
            .graphicsLayer { translationX = settledOffsetX }
            .pointerInput(metadata.id, swipeThreshold) {
                detectDragGestures(
                    onDragStart = {
                        dragDistanceX = 0f
                        dragDistanceY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDistanceX += dragAmount.x
                        dragDistanceY += dragAmount.y
                        if (abs(dragDistanceX) > abs(dragDistanceY)) {
                            dragOffsetX = (dragDistanceX * 0.3f).coerceIn(-swipeThreshold, swipeThreshold)
                        }
                    },
                    onDragEnd = {
                        if (abs(dragDistanceX) > maxOf(abs(dragDistanceY), swipeThreshold)) {
                            if (dragDistanceX < 0f && playerConnection.player.hasNextMediaItem()) {
                                playerConnection.seekToNext()
                            } else if (dragDistanceX > 0f && playerConnection.player.hasPreviousMediaItem()) {
                                playerConnection.seekToPrevious()
                            }
                        } else if (dragDistanceY < -swipeThreshold && abs(dragDistanceY) > abs(dragDistanceX)) {
                            onPeekChanged(false)
                            openPlayer()
                        }
                        // A stray downward swipe must not destroy the listening queue.
                        // Stopping playback is an explicit action in the dismissible menu.
                        dragOffsetX = 0f
                        dragDistanceX = 0f
                        dragDistanceY = 0f
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        dragDistanceX = 0f
                        dragDistanceY = 0f
                    },
                )
            },
    ) {
        FSMiniPlayer(
            track = FrostSoulTrack.from(metadata, currentSong?.song?.liked == true),
            positionMs = position,
            durationMs = duration,
            isPlaying = isPlaying,
            palette = palette,
            height = MiniPlayerHeight,
            artworkSize = 48.dp,
            peeked = false,
            shape = RoundedCornerShape(22.dp),
            interactionSource = interactionSource,
            onCardClick = {
                onPeekChanged(false)
                openPlayer()
            },
            onLongPress = { quickMenuVisible = true },
            onTogglePlayPause = { playerConnection.player.togglePlayPause() },
            onToggleLike = playerConnection::toggleLike,
            onQueueClick = onQueueClick,
        )
        DropdownMenu(expanded = quickMenuVisible, onDismissRequest = { quickMenuVisible = false }) {
            DropdownMenuItem(
                text = { Text(if (currentSong?.song?.liked == true) "Remove favorite" else "Add to favorites") },
                onClick = {
                    playerConnection.toggleLike()
                    quickMenuVisible = false
                },
            )
            onQueueClick?.let { openQueue ->
                DropdownMenuItem(text = { Text("Open queue") }, onClick = {
                    quickMenuVisible = false
                    openQueue()
                })
            }
            DropdownMenuItem(text = { Text("Open full player") }, onClick = {
                quickMenuVisible = false
                onPeekChanged(false)
                openPlayer()
            })
            DropdownMenuItem(text = { Text("Stop playback") }, onClick = {
                quickMenuVisible = false
                onPeekChanged(false)
                playerConnection.service.stopAndClearPlayback(clearPersistentState = true)
            })
        }
    }
}
