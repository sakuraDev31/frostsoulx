package dev.vxs.frostsoulx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import dev.vxs.frostsoulx.extensions.togglePlayPause
import dev.vxs.frostsoulx.home.HomeAction
import dev.vxs.frostsoulx.home.HomeScreenState
import dev.vxs.frostsoulx.playback.queues.ListQueue
import dev.vxs.frostsoulx.ui.frostsoul.FSEmptyState
import dev.vxs.frostsoulx.ui.frostsoul.FSLoading
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulCalmTheme
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.frostsoul.frostSoulCalmScreenBackground
import dev.vxs.frostsoulx.ui.premium.PremiumListRow
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
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val title = stringResource(if (kind == "moment") R.string.home_for_this_moment else R.string.home_continue_listening)
    val uiState = (state as? HomeScreenState.Success)?.uiState
    // Exactly the shelf that was opened, not an independently reranked fallback.
    val collection: List<LocalItem> = if (kind == "moment") uiState?.forThisMoment.orEmpty()
        else uiState?.keepListening.orEmpty()
    val songs = remember(collection) { collection.filterIsInstance<Song>() }

    FrostSoulCalmTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize().frostSoulCalmScreenBackground(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title, style = FrostSoulTheme.typography.sectionTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = FrostSoulTheme.colors.background,
                        titleContentColor = FrostSoulTheme.colors.onSurface,
                        navigationIconContentColor = FrostSoulTheme.colors.onSurface,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.home_navigate_back))
                        }
                    },
                )
            },
            contentWindowInsets = LocalPlayerAwareWindowInsets.current,
        ) { innerPadding ->
            if (state is HomeScreenState.Loading) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    FSLoading()
                }
            } else if (collection.isEmpty()) {
                FSEmptyState(
                    title = title,
                    message = stringResource(if (state is HomeScreenState.Error) R.string.error_unknown else R.string.home_collection_empty),
                    actionLabel = stringResource(R.string.refresh),
                    onAction = { viewModel.onAction(HomeAction.Refresh) },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = FrostSoulTheme.spacing.page, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "description", contentType = "description") {
                        Text(
                            text = stringResource(if (kind == "moment") R.string.home_moment_description else R.string.home_continue_description),
                            style = FrostSoulTheme.typography.bodyMuted,
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    items(collection, key = { "${it::class.simpleName}_${it.id}" }, contentType = { "track" }) { item ->
                        val active = item is Song && item.id == mediaMetadata?.id
                        val play: () -> Unit = {
                            when (item) {
                                is Song -> if (active) playerConnection.player.togglePlayPause() else {
                                    playerConnection.playQueue(ListQueue(
                                        title = title,
                                        items = songs.map { it.toMediaItem() },
                                        startIndex = songs.indexOfFirst { it.id == item.id }.coerceAtLeast(0),
                                    ))
                                }
                                is Album -> navController.navigate("album/${item.id}")
                                is Artist -> navController.navigate("artist/${item.id}")
                                is Playlist -> navController.navigate("local_playlist/${item.id}")
                            }
                        }
                        PremiumListRow(
                            title = item.title,
                            subtitle = itemSubtitle(item),
                            artworkUrl = if (item is Playlist) item.thumbnails.firstOrNull() else item.thumbnailUrl,
                            isActive = active,
                            onClick = play,
                            trailing = {
                                IconButton(onClick = play) {
                                    Icon(
                                        painterResource(if (active && isPlaying) R.drawable.pause else R.drawable.play),
                                        contentDescription = stringResource(if (active && isPlaying) R.string.home_pause_track else R.string.play),
                                        tint = FrostSoulTheme.colors.onSurface,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun itemSubtitle(item: LocalItem): String = when (item) {
    is Song -> item.artists.joinToString(" • ") { it.name }
    is Album -> item.artists.joinToString(" • ") { it.name }
    is Artist -> "Artist"
    is Playlist -> "${item.songCount} tracks"
}
