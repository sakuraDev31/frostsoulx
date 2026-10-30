/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.LocalPlayerConnection
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.AppBarHeight
import dev.vxs.frostsoulx.extensions.togglePlayPause
import dev.vxs.frostsoulx.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import dev.vxs.frostsoulx.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import dev.vxs.frostsoulx.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import dev.vxs.frostsoulx.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import dev.vxs.frostsoulx.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import dev.vxs.frostsoulx.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import dev.vxs.frostsoulx.innertube.models.AlbumItem
import dev.vxs.frostsoulx.innertube.models.ArtistItem
import dev.vxs.frostsoulx.innertube.models.PlaylistItem
import dev.vxs.frostsoulx.innertube.models.SongItem
import dev.vxs.frostsoulx.innertube.models.WatchEndpoint
import dev.vxs.frostsoulx.innertube.models.YTItem
import dev.vxs.frostsoulx.models.toMediaMetadata
import dev.vxs.frostsoulx.playback.queues.YouTubeQueue
import dev.vxs.frostsoulx.ui.component.ChipsRow
import dev.vxs.frostsoulx.ui.component.EmptyPlaceholder
import dev.vxs.frostsoulx.ui.component.ItemThumbnail
import dev.vxs.frostsoulx.ui.component.LocalMenuState
import dev.vxs.frostsoulx.ui.component.YouTubeListItem
import dev.vxs.frostsoulx.ui.component.shimmer.ListItemPlaceHolder
import dev.vxs.frostsoulx.ui.component.shimmer.ShimmerHost
import dev.vxs.frostsoulx.ui.menu.YouTubeAlbumMenu
import dev.vxs.frostsoulx.ui.menu.YouTubeArtistMenu
import dev.vxs.frostsoulx.ui.menu.YouTubePlaylistMenu
import dev.vxs.frostsoulx.ui.menu.YouTubeSongMenu
import dev.vxs.frostsoulx.utils.joinByBullet
import dev.vxs.frostsoulx.utils.makeTimeString
import dev.vxs.frostsoulx.viewmodels.OnlineSearchSort
import dev.vxs.frostsoulx.viewmodels.OnlineSearchViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    searchSort: OnlineSearchSort,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val searchFilter by viewModel.filter.collectAsStateWithLifecycle()
    val searchSummary = viewModel.summaryPage

    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }

    val isAllModeLoaded =
        searchSummary != null ||
            listOf(
                FILTER_SONG,
                FILTER_VIDEO,
                FILTER_ALBUM,
                FILTER_ARTIST,
                FILTER_COMMUNITY_PLAYLIST,
                FILTER_FEATURED_PLAYLIST,
            ).all { viewModel.viewStateMap.containsKey(it.value) }

    // Extract items for "All" overview mode
    val songItems = remember(viewModel.viewStateMap[FILTER_SONG.value], viewModel.viewStateMap[FILTER_VIDEO.value]) {
        val songs = viewModel.viewStateMap[FILTER_SONG.value]?.items?.filterIsInstance<SongItem>().orEmpty()
        if (songs.isNotEmpty()) songs else viewModel.viewStateMap[FILTER_VIDEO.value]?.items?.filterIsInstance<SongItem>().orEmpty()
    }
    val sortedSongs = remember(songItems, searchSort) {
        viewModel.sortedItems(songItems, searchSort).filterIsInstance<SongItem>()
    }

    val artistItems = remember(viewModel.viewStateMap[FILTER_ARTIST.value]) {
        viewModel.viewStateMap[FILTER_ARTIST.value]?.items?.filterIsInstance<ArtistItem>().orEmpty()
    }
    val sortedArtists = remember(artistItems, searchSort) {
        viewModel.sortedItems(artistItems, searchSort).filterIsInstance<ArtistItem>()
    }

    val albumItems = remember(viewModel.viewStateMap[FILTER_ALBUM.value]) {
        viewModel.viewStateMap[FILTER_ALBUM.value]?.items?.filterIsInstance<AlbumItem>().orEmpty()
    }
    val playlistItems = remember(viewModel.viewStateMap[FILTER_COMMUNITY_PLAYLIST.value], viewModel.viewStateMap[FILTER_FEATURED_PLAYLIST.value]) {
        (viewModel.viewStateMap[FILTER_COMMUNITY_PLAYLIST.value]?.items.orEmpty() +
            viewModel.viewStateMap[FILTER_FEATURED_PLAYLIST.value]?.items.orEmpty()).filterIsInstance<PlaylistItem>()
    }
    val albumsAndPlaylists = remember(albumItems, playlistItems, searchSort) {
        viewModel.sortedItems((albumItems + playlistItems).distinctBy { it.id }, searchSort)
    }

    val topResultItem = remember(searchSummary, sortedSongs, sortedArtists) {
        searchSummary?.summaries?.firstOrNull()?.items?.firstOrNull()
            ?: sortedSongs.firstOrNull()
            ?: sortedArtists.firstOrNull()
    }

    val topArtists = remember(topResultItem, sortedArtists) {
        val list = mutableListOf<ArtistItem>()
        if (topResultItem is SongItem) {
            topResultItem.artists.forEach { art ->
                val artId = art.id
                val match = sortedArtists.find { it.title.equals(art.name, ignoreCase = true) }
                if (match != null && list.none { it.id == match.id }) {
                    list.add(match)
                } else if (artId != null && list.none { it.id == artId }) {
                    list.add(
                        ArtistItem(
                            id = artId,
                            title = art.name,
                            thumbnail = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        ),
                    )
                }
            }
        }
        sortedArtists.forEach { art ->
            if (list.size < 2 && list.none { it.id == art.id }) {
                list.add(art)
            }
        }
        list.take(2)
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val playSong: (SongItem) -> Unit = { song ->
        if (song.id == mediaMetadata?.id) {
            playerConnection.player.togglePlayPause()
        } else {
            playerConnection.playQueue(
                YouTubeQueue(
                    WatchEndpoint(videoId = song.id),
                    song.toMediaMetadata(),
                ),
            )
        }
    }

    val openSongMenu: (SongItem) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            YouTubeSongMenu(
                song = song,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val openArtistMenu: (ArtistItem) -> Unit = { artist ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            YouTubeArtistMenu(
                artist = artist,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val openAlbumMenu: (AlbumItem) -> Unit = { album ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            YouTubeAlbumMenu(
                albumItem = album,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val openPlaylistMenu: (PlaylistItem) -> Unit = { playlist ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            YouTubePlaylistMenu(
                playlist = playlist,
                coroutineScope = coroutineScope,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        val longClick = {
            when (item) {
                is SongItem -> openSongMenu(item)
                is AlbumItem -> openAlbumMenu(item)
                is ArtistItem -> openArtistMenu(item)
                is PlaylistItem -> openPlaylistMenu(item)
            }
        }
        YouTubeListItem(
            item = item,
            viewCountText = (item as? SongItem)?.viewCountText,
            isActive =
                when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
            isPlaying = isPlaying,
            badges = {
                Text(
                    text = "ONLINE",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            trailingContent = {
                IconButton(onClick = longClick) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            },
            modifier =
                Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> playSong(item)
                                is AlbumItem -> navController.navigate("album/${item.id}")
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                            }
                        },
                        onLongClick = longClick,
                    ).animateItem(),
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Surface(
            color = Color.Transparent,
            tonalElevation = 0.dp,
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top).add(WindowInsets(top = AppBarHeight)))
                    .fillMaxWidth(),
        ) {
            ChipsRow(
                chips =
                    listOf(
                        null to stringResource(R.string.filter_all),
                        FILTER_SONG to stringResource(R.string.filter_songs),
                        FILTER_ARTIST to stringResource(R.string.filter_artists),
                        FILTER_ALBUM to stringResource(R.string.filter_albums),
                        FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                    ),
                currentValue = searchFilter,
                onValueUpdate = {
                    if (viewModel.filter.value != it) {
                        viewModel.filter.value = it
                    }
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                icons =
                    mapOf(
                        FILTER_SONG to R.drawable.music_note,
                        FILTER_ARTIST to R.drawable.person,
                        FILTER_ALBUM to R.drawable.album,
                        FILTER_COMMUNITY_PLAYLIST to R.drawable.playlist_play,
                    ),
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .add(WindowInsets(top = 8.dp))
                    .asPaddingValues(),
            modifier = Modifier.weight(1f),
        ) {
            if (searchFilter == null) {
                // 1. Top Results Section
                if (topResultItem != null) {
                    item(key = "section_header_top_results", contentType = "section_header") {
                        SearchSectionHeader(
                            title = "Top results",
                            onSeeAllClick = {
                                viewModel.filter.value = FILTER_SONG
                                coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                            },
                        )
                    }

                    item(key = "spotlight_top_result", contentType = "spotlight_card") {
                        TopResultSpotlightCard(
                            item = topResultItem,
                            isPlaying = isPlaying,
                            isCurrentItem = (topResultItem as? SongItem)?.id == mediaMetadata?.id,
                            onPlayClick = {
                                if (topResultItem is SongItem) playSong(topResultItem)
                            },
                            onMenuClick = {
                                when (topResultItem) {
                                    is SongItem -> openSongMenu(topResultItem)
                                    is AlbumItem -> openAlbumMenu(topResultItem)
                                    is ArtistItem -> openArtistMenu(topResultItem)
                                    is PlaylistItem -> openPlaylistMenu(topResultItem)
                                }
                            },
                            onClick = {
                                when (topResultItem) {
                                    is SongItem -> playSong(topResultItem)
                                    is AlbumItem -> navController.navigate("album/${topResultItem.id}")
                                    is ArtistItem -> navController.navigate("artist/${topResultItem.id}")
                                    is PlaylistItem -> navController.navigate("online_playlist/${topResultItem.id}")
                                }
                            },
                        )
                    }

                    if (topArtists.isNotEmpty()) {
                        item(key = "dual_artist_cards", contentType = "dual_artist_cards") {
                            TopResultDualArtistCards(
                                artists = topArtists,
                                navController = navController,
                                onMenuClick = openArtistMenu,
                            )
                        }
                    }
                }

                // 2. Songs Section
                if (sortedSongs.isNotEmpty()) {
                    item(key = "section_header_songs", contentType = "section_header") {
                        SearchSectionHeader(
                            title = stringResource(R.string.filter_songs),
                            hasChevron = true,
                            onSeeAllClick = {
                                viewModel.filter.value = FILTER_SONG
                                coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                            },
                        )
                    }

                    itemsIndexed(
                        items = sortedSongs.take(4),
                        key = { idx, song -> "overview_song_${song.id}_$idx" },
                        contentType = { _, _ -> "song_row" },
                    ) { _, song ->
                        SearchSongRow(
                            song = song,
                            isPlaying = isPlaying,
                            isCurrentItem = song.id == mediaMetadata?.id,
                            onPlayClick = { playSong(song) },
                            onMenuClick = { openSongMenu(song) },
                            onClick = { playSong(song) },
                        )
                    }
                }

                // 3. Artists Section
                if (sortedArtists.isNotEmpty()) {
                    item(key = "section_header_artists", contentType = "section_header") {
                        SearchSectionHeader(
                            title = stringResource(R.string.filter_artists),
                            onSeeAllClick = {
                                viewModel.filter.value = FILTER_ARTIST
                                coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                            },
                        )
                    }

                    item(key = "overview_artists_carousel", contentType = "artists_carousel") {
                        SearchArtistsCarousel(
                            artists = sortedArtists.take(10),
                            navController = navController,
                            onMenuClick = openArtistMenu,
                        )
                    }
                }

                // 4. Albums & Playlists Section
                if (albumsAndPlaylists.isNotEmpty()) {
                    item(key = "section_header_albums_playlists", contentType = "section_header") {
                        SearchSectionHeader(
                            title = "Albums & Playlists",
                            onSeeAllClick = {
                                viewModel.filter.value = FILTER_ALBUM
                                coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                            },
                        )
                    }

                    item(key = "overview_albums_playlists_carousel", contentType = "albums_playlists_carousel") {
                        SearchAlbumsAndPlaylistsCarousel(
                            items = albumsAndPlaylists.take(10),
                            navController = navController,
                            onMenuClick = { item ->
                                when (item) {
                                    is AlbumItem -> openAlbumMenu(item)
                                    is PlaylistItem -> openPlaylistMenu(item)
                                    else -> {}
                                }
                            },
                        )
                    }
                }

                // Empty state for All mode
                if (topResultItem == null && sortedSongs.isEmpty() && isAllModeLoaded) {
                    item(key = "empty_all", contentType = "empty") {
                        EmptyPlaceholder(
                            icon = R.drawable.search,
                            text = stringResource(R.string.no_results_found),
                        )
                    }
                }

                // Initial loading shimmer for All mode
                if (topResultItem == null && sortedSongs.isEmpty() && !isAllModeLoaded) {
                    item(key = "initial_loading_all", contentType = "loading") {
                        ShimmerHost {
                            repeat(8) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }
            } else {
                // Filtered Mode (Songs, Artists, Albums, Playlists)
                val filteredItems = viewModel.sortedItems(itemsPage?.items.orEmpty().distinctBy { it.id }, searchSort)

                if (searchFilter == FILTER_SONG) {
                    itemsIndexed(
                        items = filteredItems.filterIsInstance<SongItem>(),
                        key = { idx, song -> "filtered_song_${song.id}_$idx" },
                        contentType = { _, _ -> "song_row" },
                    ) { _, song ->
                        SearchSongRow(
                            song = song,
                            isPlaying = isPlaying,
                            isCurrentItem = song.id == mediaMetadata?.id,
                            onPlayClick = { playSong(song) },
                            onMenuClick = { openSongMenu(song) },
                            onClick = { playSong(song) },
                        )
                    }
                } else {
                    items(
                        items = filteredItems,
                        key = { "filtered_${it.id}" },
                        contentType = { "search_result" },
                        itemContent = ytItemContent,
                    )
                }

                if (itemsPage?.continuation != null) {
                    item(key = "loading", contentType = "loading") {
                        ShimmerHost {
                            repeat(3) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }

                if (itemsPage?.items?.isEmpty() == true) {
                    item(key = "empty_filtered", contentType = "empty") {
                        EmptyPlaceholder(
                            icon = R.drawable.search,
                            text = stringResource(R.string.no_results_found),
                        )
                    }
                }

                if (itemsPage == null) {
                    item(key = "initial_loading_filtered", contentType = "loading") {
                        ShimmerHost {
                            repeat(8) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    hasChevron: Boolean = false,
    onSeeAllClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (onSeeAllClick != null && hasChevron) Modifier.clickable(onClick = onSeeAllClick) else Modifier,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = Color.White,
            )
            if (hasChevron) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (onSeeAllClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onSeeAllClick),
            ) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                    ),
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultSpotlightCard(
    item: YTItem,
    isPlaying: Boolean,
    isCurrentItem: Boolean,
    onPlayClick: () -> Unit,
    onMenuClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161616),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onMenuClick,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp)),
            ) {
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail,
                    isActive = isCurrentItem,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    placeholderIconRes =
                        when (item) {
                            is ArtistItem -> R.drawable.person
                            is AlbumItem -> R.drawable.album
                            is PlaylistItem -> R.drawable.playlist_play
                            else -> R.drawable.music_note
                        },
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable(onClick = onPlayClick),
                ) {
                    Icon(
                        painter = painterResource(if (isCurrentItem && isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp, end = 8.dp),
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val subtitle1 =
                    when (item) {
                        is SongItem -> item.artists.joinToString(" • ") { it.name }
                        is AlbumItem -> item.artists?.joinToString(" • ") { it.name } ?: "Album"
                        is ArtistItem -> "Artist"
                        is PlaylistItem -> item.author?.name ?: "Playlist"
                        else -> ""
                    }
                if (subtitle1.isNotEmpty()) {
                    Text(
                        text = subtitle1,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                val subtitle2 =
                    when (item) {
                        is SongItem -> "Song" + (item.album?.name?.let { " • $it" } ?: "")
                        is AlbumItem -> "Album" + (item.year?.let { " • $it" } ?: "")
                        is PlaylistItem -> "Playlist" + (item.songCountText?.let { " • $it" } ?: "")
                        is ArtistItem -> "Artist"
                        else -> ""
                    }
                if (subtitle2.isNotEmpty()) {
                    Text(
                        text = subtitle2,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = "Menu",
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultDualArtistCards(
    artists: List<ArtistItem>,
    navController: NavController,
    onMenuClick: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        artists.take(2).forEach { artist ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161616),
                modifier =
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .combinedClickable(
                            onClick = { navController.navigate("artist/${artist.id}") },
                            onLongClick = { onMenuClick(artist) },
                        ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                ) {
                    ItemThumbnail(
                        thumbnailUrl = artist.thumbnail,
                        isActive = false,
                        isPlaying = false,
                        shape = CircleShape,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                        placeholderIconRes = R.drawable.person,
                    )
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(start = 10.dp, end = 4.dp),
                    ) {
                        Text(
                            text = artist.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Artist",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.navigate_next),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchSongRow(
    song: SongItem,
    isPlaying: Boolean,
    isCurrentItem: Boolean,
    onPlayClick: () -> Unit,
    onMenuClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onMenuClick,
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        ItemThumbnail(
            thumbnailUrl = song.thumbnail,
            isActive = isCurrentItem,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(8.dp),
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
            placeholderIconRes = R.drawable.music_note,
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
        ) {
            Text(
                text = song.title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = if (isCurrentItem) MaterialTheme.colorScheme.primary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artists.joinToString(" • ") { it.name },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.explicit) {
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Text(
                            text = "HD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                val releaseInfo =
                    joinByBullet(
                        song.album?.name ?: "Single",
                        makeTimeString(song.duration?.times(1000L)),
                    )
                Text(
                    text = releaseInfo,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onPlayClick),
        ) {
            Icon(
                painter = painterResource(if (isCurrentItem && isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onMenuClick) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = "Menu",
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchArtistsCarousel(
    artists: List<ArtistItem>,
    navController: NavController,
    onMenuClick: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            items = artists,
            key = { "artist_carousel_${it.id}" },
        ) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .width(76.dp)
                        .combinedClickable(
                            onClick = { navController.navigate("artist/${artist.id}") },
                            onLongClick = { onMenuClick(artist) },
                        ),
            ) {
                ItemThumbnail(
                    thumbnailUrl = artist.thumbnail,
                    isActive = false,
                    isPlaying = false,
                    shape = CircleShape,
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                    placeholderIconRes = R.drawable.person,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = artist.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Artist",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchAlbumsAndPlaylistsCarousel(
    items: List<YTItem>,
    navController: NavController,
    onMenuClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            items = items,
            key = { "album_playlist_carousel_${it.id}" },
        ) { item ->
            Column(
                modifier =
                    Modifier
                        .width(120.dp)
                        .combinedClickable(
                            onClick = {
                                if (item is AlbumItem) {
                                    navController.navigate("album/${item.id}")
                                } else {
                                    navController.navigate("online_playlist/${item.id}")
                                }
                            },
                            onLongClick = { onMenuClick(item) },
                        ),
            ) {
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail,
                    isActive = false,
                    isPlaying = false,
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    placeholderIconRes =
                        if (item is AlbumItem) R.drawable.album else R.drawable.playlist_play,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subText =
                    if (item is AlbumItem) {
                        "Album" + (item.artists?.firstOrNull()?.name?.let { " • $it" } ?: "")
                    } else {
                        "Playlist"
                    }
                Text(
                    text = subText,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
