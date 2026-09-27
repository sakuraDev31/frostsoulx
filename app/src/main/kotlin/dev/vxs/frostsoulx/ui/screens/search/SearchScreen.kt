/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.LocalPlayerConnection
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.DisableBlurKey
import dev.vxs.frostsoulx.extensions.togglePlayPause
import dev.vxs.frostsoulx.innertube.models.AlbumItem
import dev.vxs.frostsoulx.innertube.models.ArtistItem
import dev.vxs.frostsoulx.innertube.models.SongItem
import dev.vxs.frostsoulx.innertube.models.WatchEndpoint
import dev.vxs.frostsoulx.models.toMediaMetadata
import dev.vxs.frostsoulx.playback.queues.YouTubeQueue
import dev.vxs.frostsoulx.search.SearchDiscoveryUiModel
import dev.vxs.frostsoulx.ui.component.LocalMenuState
import dev.vxs.frostsoulx.ui.frostsoul.FSIcon as FrostSoulIcon
import dev.vxs.frostsoulx.ui.frostsoul.FSText as FrostSoulText
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.frostsoul.SearchTheme
import dev.vxs.frostsoulx.ui.component.NavigationTitle
import dev.vxs.frostsoulx.ui.component.YouTubeGridItem
import dev.vxs.frostsoulx.ui.component.YouTubeListItem
import dev.vxs.frostsoulx.ui.component.shimmer.ShimmerHost
import dev.vxs.frostsoulx.ui.component.shimmer.TextPlaceholder
import dev.vxs.frostsoulx.ui.menu.YouTubeAlbumMenu
import dev.vxs.frostsoulx.ui.menu.YouTubeArtistMenu
import dev.vxs.frostsoulx.ui.menu.YouTubeSongMenu
import dev.vxs.frostsoulx.ui.screens.MoodAndGenresButton
import dev.vxs.frostsoulx.ui.screens.MoodAndGenresButtonHeight
import dev.vxs.frostsoulx.ui.screens.search.onlineSearchResultRoute
import dev.vxs.frostsoulx.utils.rememberPreference
import dev.vxs.frostsoulx.viewmodels.SearchDiscoveryScreenState
import dev.vxs.frostsoulx.viewmodels.SearchDiscoveryTab
import dev.vxs.frostsoulx.viewmodels.SearchDiscoveryViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    onSearchClick: () -> Unit,
    headerScrollConnection: NestedScrollConnection? = null,
    viewModel: SearchDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val pureBlack = FrostSoulTheme.colors.background.luminance() <= 0.5f
    val tonalStart = MaterialTheme.colorScheme.primaryContainer
    val tonalMiddle = MaterialTheme.colorScheme.secondaryContainer
    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry
            ?.savedStateHandle
            ?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) SearchTheme.SearchBarBackground else MaterialTheme.colorScheme.background)
                .then(
                    // Step 2b: attach the shell's floating-header connection here so Search's
                    // scroll/fling writes Search's own header state and can't leak elsewhere.
                    if (headerScrollConnection != null) {
                        Modifier.nestedScroll(headerScrollConnection)
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (!disableBlur) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .align(Alignment.TopCenter)
                        .drawWithCache {
                            val brush =
                                Brush.verticalGradient(
                                    0f to tonalStart.copy(alpha = 0.30f),
                                    0.42f to tonalMiddle.copy(alpha = 0.14f),
                                    1f to Color.Transparent,
                                )
                            onDrawBehind { drawRect(brush) }
                        },
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(
                key = "search_field",
                contentType = "search_field",
            ) {
                SearchEntryField(
                    onClick = onSearchClick,
                    pureBlack = pureBlack,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateItem(),
                )
            }

            item(
                key = "search_tabs",
                contentType = "search_tabs",
            ) {
                SearchDiscoveryTabs(
                    selectedTab = selectedTab,
                    onTabSelected = viewModel::selectTab,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateItem(),
                )
            }

            when (val currentState = state) {
                SearchDiscoveryScreenState.Loading -> {
                    item(
                        key = "search_loading",
                        contentType = "search_loading",
                    ) {
                        SearchDiscoveryLoading(modifier = Modifier.animateItem())
                    }
                }

                SearchDiscoveryScreenState.Empty -> {
                    item(
                        key = "search_empty",
                        contentType = "search_empty",
                    ) {
                        SearchStateMessage(
                            message = stringResource(R.string.no_results_found),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                is SearchDiscoveryScreenState.Error -> {
                    item(
                        key = "search_error",
                        contentType = "search_error",
                    ) {
                        SearchStateMessage(
                            message = stringResource(currentState.messageResId),
                            action = {
                                Button(onClick = viewModel::retry) {
                                    Text(stringResource(R.string.retry_button))
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                is SearchDiscoveryScreenState.Success -> {
                    when (selectedTab) {
                        SearchDiscoveryTab.EXPLORE -> {
                            val hotSearches =
                                currentState.data.suggestedSongs
                                    .map { it.title }
                                    .distinct()
                                    .take(6)
                            val recommendedMusic =
                                (
                                    currentState.data.trendingAlbums.map { it.title } +
                                        currentState.data.suggestedArtists.map { it.title }
                                ).distinct().take(6)
                            item(
                                key = "search_explore_trending_matrix",
                                contentType = "trending_matrix",
                            ) {
                                QQStyleTrendingMatrix(
                                    hotSearches = hotSearches,
                                    recommendedMusic = recommendedMusic,
                                    onItemClick = { keyword ->
                                        navController.navigate(onlineSearchResultRoute(keyword))
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "search_explore_moods_title",
                                contentType = "section_title",
                            ) {
                                NavigationTitle(
                                    title = stringResource(R.string.mood_and_genres),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "search_explore_moods",
                                contentType = "mood_genres_grid",
                            ) {
                                SearchMoodAndGenresGrid(
                                    data = currentState.data,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        SearchDiscoveryTab.SUGGESTIONS -> {
                            item(
                                key = "search_suggestions_songs",
                                contentType = "suggestion_songs",
                            ) {
                                SuggestedSongsSection(
                                    songs = currentState.data.suggestedSongs,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            item(
                                key = "search_suggestions_artists",
                                contentType = "suggestion_artists",
                            ) {
                                SuggestedArtistsSection(
                                    artists = currentState.data.suggestedArtists,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            item(
                                key = "search_suggestions_albums",
                                contentType = "suggestion_albums",
                            ) {
                                TrendingAlbumsSection(
                                    albums = currentState.data.trendingAlbums,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEntryField(
    onClick: () -> Unit,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val searchSurface = if (pureBlack) SearchTheme.SearchBarBackground else FrostSoulTheme.colors.surfaceRaised
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(48.dp)
                .clip(shape)
                .background(searchSurface)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
    ) {
        FrostSoulIcon(
            painter = painterResource(R.drawable.search),
            contentDescription = "Search Icon",
            tint = if (pureBlack) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        FrostSoulText(
            text = stringResource(R.string.search_yt_music),
            style =
                SearchTheme.InputTextStyle.copy(
                    color = if (pureBlack) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                ),
            modifier = Modifier.padding(start = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDiscoveryTabs(
    selectedTab: SearchDiscoveryTab,
    onTabSelected: (SearchDiscoveryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = remember { SearchDiscoveryTab.entries }
    PrimaryTabRow(
        selectedTabIndex = tabs.indexOf(selectedTab),
        modifier = modifier,
        containerColor = Color.Transparent,
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        painter =
                            painterResource(
                                when (tab) {
                                    SearchDiscoveryTab.EXPLORE -> R.drawable.explore_outlined
                                    SearchDiscoveryTab.SUGGESTIONS -> R.drawable.auto_awesome
                                },
                            ),
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text =
                            stringResource(
                                when (tab) {
                                    SearchDiscoveryTab.EXPLORE -> R.string.explore
                                    SearchDiscoveryTab.SUGGESTIONS -> R.string.suggestions
                                },
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun QQStyleTrendingMatrix(
    hotSearches: List<String>,
    recommendedMusic: List<String>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hotSearches.isEmpty() && recommendedMusic.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TrendingColumn(
            title = "Trending Search",
            entries = hotSearches,
            onItemClick = onItemClick,
            modifier = Modifier.weight(1f),
        )
        TrendingColumn(
            title = "Recommended Music",
            entries = recommendedMusic,
            onItemClick = onItemClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrendingColumn(
    title: String,
    entries: List<String>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FrostSoulText(
            text = title,
            color = FrostSoulTheme.colors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        entries.take(6).forEachIndexed { index, keyword ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(keyword) }
                        .padding(vertical = 10.dp),
            ) {
                FrostSoulText(
                    text = (index + 1).toString(),
                    color =
                        if (index < 3) {
                            FrostSoulTheme.colors.accentBright
                        } else {
                            FrostSoulTheme.colors.onSurfaceMuted
                        },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp),
                )
                FrostSoulText(
                    text = keyword,
                    color = FrostSoulTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchMoodAndGenresGrid(
    data: SearchDiscoveryUiModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        val columnCount = (maxWidth.value / MoodAndGenresMinCellWidth.value).toInt().coerceAtLeast(1)
        val rowCount = ((data.moodAndGenres.size + columnCount - 1) / columnCount).coerceAtLeast(1)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = MoodAndGenresMinCellWidth),
            contentPadding = PaddingValues(6.dp),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height((MoodAndGenresButtonHeight + 12.dp) * rowCount + 12.dp),
        ) {
            items(
                items = data.moodAndGenres,
                key = { item -> "${item.title}:${item.endpoint.browseId}:${item.endpoint.params}" },
                contentType = { "mood_genres_item" },
            ) { item ->
                MoodAndGenresButton(
                    title = item.title,
                    stripeColor = item.stripeColor,
                    endpoint = item.endpoint,
                    onClick = {
                        navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                )
            }
        }
    }
}

private val MoodAndGenresMinCellWidth = 180.dp

private val SuggestedSongGroupHorizontalPadding = 12.dp
private val SuggestedSongGroupVerticalPadding = 2.dp
private val SuggestedSongGroupItemSpacing = 2.dp
private val SuggestedSongGroupLargeCorner = 28.dp
private val SuggestedSongGroupSmallCorner = 6.dp

private fun segmentedSuggestedSongShape(
    index: Int,
    count: Int,
): Shape {
    val large = SuggestedSongGroupLargeCorner
    val small = SuggestedSongGroupSmallCorner
    return when {
        count <= 1 -> {
            RoundedCornerShape(large)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = large,
                topEnd = large,
                bottomEnd = small,
                bottomStart = small,
            )
        }

        index == count - 1 -> {
            RoundedCornerShape(
                topStart = small,
                topEnd = small,
                bottomEnd = large,
                bottomStart = large,
            )
        }

        else -> {
            RoundedCornerShape(small)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedSongsSection(
    songs: List<SongItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return

    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    SectionContainer(
        title = stringResource(R.string.stats_unique_songs),
        modifier = modifier,
    ) {
        val visibleSongs = remember(songs) { songs.take(6) }

        Column(
            verticalArrangement = Arrangement.spacedBy(SuggestedSongGroupItemSpacing),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SuggestedSongGroupHorizontalPadding,
                        vertical = SuggestedSongGroupVerticalPadding,
                    ),
        ) {
            visibleSongs.forEachIndexed { index, song ->
                val isActive = song.id == mediaMetadata?.id
                Card(
                    shape = segmentedSuggestedSongShape(index = index, count = visibleSongs.size),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isActive) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isActive) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                preloadItem = song.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                ) {
                    YouTubeListItem(
                        item = song,
                        albumIndex = index + 1,
                        viewCountText = song.viewCountText,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        isSwipeable = false,
                        showActiveContainer = false,
                        trailingContent = {
                            YouTubeSongMenuButton(song = song, navController = navController)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrendingAlbumsSection(
    albums: List<AlbumItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) return

    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    NavigationTitle(
        title = stringResource(R.string.top_albums),
        modifier = modifier,
    )
    LazyRow(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues(),
    ) {
        items(
            items = albums,
            key = { album -> album.id },
            contentType = { "trending_album" },
        ) { album ->
            YouTubeGridItem(
                item = album,
                isActive = mediaMetadata?.album?.id == album.id,
                isPlaying = isPlaying,
                coroutineScope = coroutineScope,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                navController.navigate("album/${album.id}")
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeAlbumMenu(
                                        albumItem = album,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ).animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedArtistsSection(
    artists: List<ArtistItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) return

    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    NavigationTitle(
        title = stringResource(R.string.stats_unique_artists),
        modifier = modifier,
    )
    LazyRow(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues(),
    ) {
        items(
            items = artists,
            key = { artist -> artist.id },
            contentType = { "trending_artist" },
        ) { artist ->
            YouTubeGridItem(
                item = artist,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                navController.navigate("artist/${artist.id}")
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeArtistMenu(
                                        artist = artist,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ).animateItem(),
            )
        }
    }
}

@Composable
private fun SectionContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NavigationTitle(
        title = title,
        modifier = modifier,
    )
    content()
}

@Composable
private fun YouTubeSongMenuButton(
    song: SongItem,
    navController: NavController,
) {
    val menuState = LocalMenuState.current
    IconButton(
        onClick = {
            menuState.show {
                YouTubeSongMenu(
                    song = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
        )
    }
}

@Composable
private fun SearchDiscoveryLoading(modifier: Modifier = Modifier) {
    ShimmerHost(
        modifier = modifier.fillMaxWidth(),
    ) {
        TextPlaceholder(
            height = 56.dp,
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
        )
        TextPlaceholder(
            height = 28.dp,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .width(180.dp),
        )
        repeat(6) {
            TextPlaceholder(
                height = 84.dp,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.search_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout
                .Row(content = action)
        }
    }
}
