/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.home.HomeScreenState
import dev.vxs.frostsoulx.innertube.models.PlaylistItem
import dev.vxs.frostsoulx.ui.frostsoul.FSEmptyState
import dev.vxs.frostsoulx.ui.frostsoul.FSLoading
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulCalmTheme
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.frostsoul.frostSoulCalmScreenBackground
import dev.vxs.frostsoulx.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePlaylistCollectionScreen(
    navController: NavController,
    kind: String,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val uiState = (state as? HomeScreenState.Success)?.uiState
    val communityPlaylists = remember(uiState?.homePage, uiState?.accountPlaylists) {
        val sections = uiState?.homePage?.sections.orEmpty()
        val fromOnline = sections
            .filter { section ->
                val title = section.title
                val isFeatured = title.contains("featured", ignoreCase = true) ||
                    title.contains("for you", ignoreCase = true) ||
                    title.contains("recommend", ignoreCase = true) ||
                    title.contains("made for you", ignoreCase = true)
                !isFeatured && (
                    title.contains("playlist", ignoreCase = true) ||
                        title.contains("community", ignoreCase = true) ||
                        title.contains("mix", ignoreCase = true) ||
                        title.contains("chart", ignoreCase = true) ||
                        title.contains("trending", ignoreCase = true) ||
                        section.items.any { it is PlaylistItem }
                )
            }
            .flatMap { it.items }
            .filterIsInstance<PlaylistItem>()
        (fromOnline + uiState?.accountPlaylists.orEmpty()).distinctBy { it.id }
    }
    val playlists = remember(kind, uiState?.homePage, communityPlaylists) {
        if (kind == "featured") {
            val communityIds = communityPlaylists.mapTo(HashSet()) { it.id }
            uiState?.homePage?.sections.orEmpty()
                .flatMap { it.items }
                .filterIsInstance<PlaylistItem>()
                .filterNot { it.id in communityIds }
                .distinctBy { it.id }
        } else {
            communityPlaylists
        }
    }
    val title = if (kind == "featured") "Featured playlists" else "Trending community playlists"

    FrostSoulCalmTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize().frostSoulCalmScreenBackground(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title, style = FrostSoulTheme.typography.sectionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.home_navigate_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = FrostSoulTheme.colors.background,
                        titleContentColor = FrostSoulTheme.colors.onSurface,
                        navigationIconContentColor = FrostSoulTheme.colors.onSurface,
                    ),
                )
            },
            contentWindowInsets = LocalPlayerAwareWindowInsets.current,
        ) { innerPadding ->
            when {
                state is HomeScreenState.Loading -> Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { FSLoading() }
                state is HomeScreenState.Error && playlists.isEmpty() -> FSEmptyState(
                    title = title,
                    message = stringResource(R.string.error_unknown),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
                playlists.isEmpty() -> FSEmptyState(
                    title = title,
                    message = "No playlists available yet.",
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(playlists, key = { "${kind}_${it.id}" }, contentType = { "playlist_row" }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { navController.navigate("online_playlist/${playlist.id}") }
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = playlist.thumbnail,
                                contentDescription = playlist.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = playlist.title,
                                    style = FrostSoulTheme.typography.body,
                                    color = FrostSoulTheme.colors.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = playlist.author?.name
                                    ?: playlist.songCountText
                                    ?: "Playlist"
                                Text(
                                    text = subtitle,
                                    style = FrostSoulTheme.typography.bodyMuted,
                                    color = FrostSoulTheme.colors.onSurfaceMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.arrow_forward),
                                contentDescription = null,
                                tint = FrostSoulTheme.colors.onSurfaceMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
