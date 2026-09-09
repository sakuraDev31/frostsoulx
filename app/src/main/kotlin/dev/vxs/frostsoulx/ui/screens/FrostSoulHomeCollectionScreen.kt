package dev.vxs.frostsoulx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.LocalPlayerConnection
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.db.entities.Album
import dev.vxs.frostsoulx.db.entities.Artist
import dev.vxs.frostsoulx.db.entities.LocalItem
import dev.vxs.frostsoulx.db.entities.Playlist
import dev.vxs.frostsoulx.db.entities.Song
import dev.vxs.frostsoulx.extensions.toMediaItem
import dev.vxs.frostsoulx.home.HomeScreenState
import dev.vxs.frostsoulx.playback.queues.ListQueue
import dev.vxs.frostsoulx.ui.frostsoul.FSGlassCard
import dev.vxs.frostsoulx.ui.frostsoul.FSIcon
import dev.vxs.frostsoulx.ui.frostsoul.FSListItem
import dev.vxs.frostsoulx.ui.frostsoul.FSText
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.frostsoul.frostSoulCalmScreenBackground
import dev.vxs.frostsoulx.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostSoulHomeCollectionScreen(
    navController: NavController,
    kind: String,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current ?: return
    val title = if (kind == "moment") "For This Moment" else "Continue Listening"
    val items: List<LocalItem> =
        when (val value = state) {
            is HomeScreenState.Success -> if (kind == "moment") value.uiState.forThisMoment else value.uiState.keepListening
            else -> emptyList()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .frostSoulCalmScreenBackground()
                .padding(innerPadding)
                .padding(horizontal = FrostSoulTheme.spacing.page),
        ) {
            Text(
                text = if (kind == "moment") {
                    "Similar songs from your listening history, tuned for this moment."
                } else {
                    "Pick up where you left off, without losing the thread."
                },
                style = FrostSoulTheme.typography.bodyMuted,
                modifier = Modifier.padding(vertical = FrostSoulTheme.spacing.medium),
            )
            FSGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(FrostSoulTheme.colors.surface.copy(alpha = 0.28f)),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        val isActive = playerConnection.player.currentMediaItem?.mediaId == item.id
                        ContinueListeningRow(
                            item = item,
                            isActive = isActive,
                            progress = itemProgress(item, playerConnection, isActive),
                            onClick = {
                                when (item) {
                                    is Song -> playerConnection.playQueue(ListQueue(items = listOf(item.toMediaItem())))
                                    is Album -> navController.navigate("album/${item.id}")
                                    is Artist -> navController.navigate("artist/${item.id}")
                                    is Playlist -> navController.navigate("local_playlist/${item.id}")
                                }
                            },
                            onPlay = {
                                when (item) {
                                    is Song -> playerConnection.playQueue(ListQueue(items = listOf(item.toMediaItem())))
                                    is Album -> navController.navigate("album/${item.id}")
                                    is Artist -> navController.navigate("artist/${item.id}")
                                    is Playlist -> navController.navigate("local_playlist/${item.id}")
                                }
                            },
                        )
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 78.dp, end = 12.dp),
                                thickness = 0.5.dp,
                                color = FrostSoulTheme.colors.outline.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningRow(
    item: LocalItem,
    isActive: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val colors = FrostSoulTheme.colors
    val typeLabel = itemTypeLabel(item)
    val typeIcon = itemTypeIcon(item)
    FSListItem(
        title = itemTitle(item),
        subtitle = itemSubtitle(item),
        artworkUrl = itemArtwork(item),
        isActive = isActive,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) {
                    Modifier
                        .clip(FrostSoulTheme.shapes.medium)
                        .background(colors.accent.copy(alpha = 0.08f))
                } else {
                    Modifier
                },
            ),
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FSIcon(
                        painter = painterResource(typeIcon),
                        contentDescription = typeLabel,
                        tint = if (isActive) colors.accentBright else colors.onSurfaceMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    FSText(
                        text = typeLabel,
                        color = if (isActive) colors.accentBright else colors.onSurfaceMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onPlay, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(if (isActive) R.drawable.pause else R.drawable.play),
                        contentDescription = if (isActive) "Pause ${item.title}" else "Play ${item.title}",
                        tint = if (isActive) colors.accentBright else colors.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
    if (progress > 0f) {
        Box(
            modifier = Modifier
                .padding(start = 78.dp, end = 18.dp, bottom = 4.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(FrostSoulTheme.shapes.pill)
                .background(colors.outline.copy(alpha = 0.35f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .clip(FrostSoulTheme.shapes.pill)
                    .background(if (isActive) colors.accentBright else colors.accent.copy(alpha = 0.72f)),
            )
        }
    }
}

private fun itemTitle(item: LocalItem): String = item.title

private fun itemArtwork(item: LocalItem): String? = when (item) {
    is Playlist -> item.thumbnails.firstOrNull()
    else -> item.thumbnailUrl
}

private fun itemSubtitle(item: LocalItem): String = when (item) {
    is Song -> item.artists.joinToString(" • ") { it.name }
    is Album -> item.artists.joinToString(" • ") { it.name }.ifBlank { "Album" }
    is Artist -> "Artist"
    is Playlist -> "${item.songCount} tracks"
}

private fun itemTypeLabel(item: LocalItem): String = when (item) {
    is Song -> "TRACK"
    is Album -> "ALBUM"
    is Artist -> "ARTIST"
    is Playlist -> "PLAYLIST"
}

private fun itemTypeIcon(item: LocalItem): Int = when (item) {
    is Song -> R.drawable.music_note
    is Album -> R.drawable.album
    is Artist -> R.drawable.person
    is Playlist -> R.drawable.queue_music
}

private fun itemProgress(
    item: LocalItem,
    playerConnection: dev.vxs.frostsoulx.playback.PlayerConnection,
    isActive: Boolean,
): Float {
    if (item !is Song) return 0f
    val durationMs = item.song.duration.coerceAtLeast(0).toLong() * 1000L
    if (durationMs <= 0L) return 0f
    if (isActive) {
        val currentPosition = playerConnection.player.currentPosition.coerceAtLeast(0L)
        return (currentPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
    return (item.song.totalPlayTime.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.96f)
}
