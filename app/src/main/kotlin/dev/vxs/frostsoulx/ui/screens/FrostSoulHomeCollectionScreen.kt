package dev.vxs.frostsoulx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
import dev.vxs.frostsoulx.ui.frostsoul.FSListItem
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
            is HomeScreenState.Success -> {
                if (kind == "moment") value.uiState.forThisMoment else value.uiState.keepListening
            }
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
                text = if (kind == "moment") "Similar songs from your listening history, tuned for this moment." else "Pick up where you left off, without losing the thread.",
                style = FrostSoulTheme.typography.bodyMuted,
                modifier = Modifier.padding(vertical = FrostSoulTheme.spacing.medium),
            )
            FSGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        FSListItem(
                            title = itemTitle(item),
                            subtitle = itemSubtitle(item),
                            artworkUrl = itemArtwork(item),
                            onClick = {
                                when (item) {
                                    is Song -> playerConnection.playQueue(ListQueue(items = listOf(item.toMediaItem())))
                                    is Album -> navController.navigate("album/${item.id}")
                                    is Artist -> navController.navigate("artist/${item.id}")
                                    is Playlist -> navController.navigate("local_playlist/${item.id}")
                                }
                            },
                        )
                    }
                }
            }
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
