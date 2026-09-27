/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.AppBarHeight
import dev.vxs.frostsoulx.db.entities.Album
import dev.vxs.frostsoulx.db.entities.Artist
import dev.vxs.frostsoulx.db.entities.LocalItem
import dev.vxs.frostsoulx.db.entities.Playlist
import dev.vxs.frostsoulx.db.entities.Song
import dev.vxs.frostsoulx.extensions.toMediaItem
import dev.vxs.frostsoulx.extensions.togglePlayPause
import dev.vxs.frostsoulx.home.HomeAction
import dev.vxs.frostsoulx.innertube.pages.HomePage
import dev.vxs.frostsoulx.home.HomeUiState
import dev.vxs.frostsoulx.models.MediaMetadata
import dev.vxs.frostsoulx.library.LibraryTopMix
import dev.vxs.frostsoulx.playback.PlayerConnection
import dev.vxs.frostsoulx.playback.queues.ListQueue
import dev.vxs.frostsoulx.ui.component.MenuState
import dev.vxs.frostsoulx.ui.frostsoul.FSAlbumCard
import dev.vxs.frostsoulx.ui.frostsoul.FSIcon
import dev.vxs.frostsoulx.ui.frostsoul.FSText
import dev.vxs.frostsoulx.ui.frostsoul.FSText as Text
import dev.vxs.frostsoulx.ui.frostsoul.FSArtistCard
import dev.vxs.frostsoulx.ui.frostsoul.FSButton
import dev.vxs.frostsoulx.ui.frostsoul.FSEmptyState
import dev.vxs.frostsoulx.ui.frostsoul.FSIconButton
import dev.vxs.frostsoulx.ui.frostsoul.FSLoading
import dev.vxs.frostsoulx.ui.frostsoul.FSSectionHeader
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.premium.PremiumCard
import dev.vxs.frostsoulx.ui.premium.PremiumHeroBanner
import dev.vxs.frostsoulx.ui.premium.PremiumListRow
import dev.vxs.frostsoulx.ui.premium.PremiumSearchBar
import dev.vxs.frostsoulx.ui.premium.PremiumSegmentedTabs
import dev.vxs.frostsoulx.ui.frostsoul.frostSoulCalmScreenBackground
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope

private val FrostSoulShelfItemPadding = PaddingValues(horizontal = 16.dp)
private val FrostSoulShelfSpacing = 16.dp

@Composable
internal fun FrostSoulHomeFeed(
    uiState: HomeUiState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    lazyListState: LazyListState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val albums = remember(uiState.speedDialItems) { uiState.speedDialItems.filterIsInstance<Album>() }
    val artists = remember(uiState.speedDialItems) { uiState.speedDialItems.filterIsInstance<Artist>() }
    val recentItems = remember(uiState.recentlyPlayed) { uiState.recentlyPlayed.take(6) }
    val quickGridItems = remember(uiState.speedDialItems, uiState.recentlyPlayed, uiState.keepListening) {
        (uiState.speedDialItems + uiState.recentlyPlayed + uiState.keepListening)
            .distinctBy { it.id }
            .take(6)
    }
    val isMoodSelected = uiState.selectedChip != null
    val pageSections = if (uiState.isChipLoading || uiState.chipLoadFailed) emptyList()
        else uiState.homePage?.sections.orEmpty().filter { it.items.isNotEmpty() }

    LazyColumn(
        state = lazyListState,
        contentPadding =
            PaddingValues(
                // Match Library: status-bar inset + shared brand header + micro spacing.
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                    AppBarHeight + FrostSoulTheme.spacing.micro,
                bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.section),
        modifier = modifier.fillMaxSize().frostSoulCalmScreenBackground(),
    ) {
        if (uiState.showCategoryChips) {
            uiState.homePage?.chips.orEmpty().takeIf { it.isNotEmpty() }?.let { chips ->
                item(key = "frostsoul_home_tabs", contentType = "chips") {
                    FrostSoulHomeTabs(
                        chips = chips,
                        selectedChip = uiState.selectedChip,
                        onChipSelected = { onAction(HomeAction.SelectChip(it)) },
                    )
                }
            }
        }

        if (uiState.isChipLoading) {
            item(key = "frostsoul_mood_loading", contentType = "status") {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    FSLoading()
                }
            }
        } else if (uiState.chipLoadFailed) {
            item(key = "frostsoul_mood_error", contentType = "status") {
                FSEmptyState(
                    title = stringResource(R.string.home_mood_error),
                    message = stringResource(R.string.home_mood_retry),
                    actionLabel = stringResource(R.string.retry),
                    onAction = { onAction(HomeAction.Refresh) },
                    modifier = Modifier.height(280.dp),
                )
            }
        }

        // Mood endpoints supply their own shelves. Do not leave unrelated local
        // recommendations above them, which makes a successful selection look inert.
        if (!isMoodSelected) {
            item(key = "frostsoul_greeting") {
                FrostSoulGreetingHeader()
            }

            if (quickGridItems.isNotEmpty()) {
                item(key = "frostsoul_spotify_quick_grid") {
                    FrostSoulSpotifyQuickGrid(
                        items = quickGridItems,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playerConnection = playerConnection,
                        navController = navController,
                    )
                }
            }

            if (uiState.featuredForYou.isNotEmpty()) {
                item(key = "frostsoul_featured_for_you_top") {
                    FrostSoulBannerCarousel(
                        songs = uiState.featuredForYou.take(6),
                        mediaMetadata = mediaMetadata,
                        playerConnection = playerConnection,
                        isPlaying = isPlaying,
                    )
                }
            }

            if (uiState.keepListening.isNotEmpty()) {
                item(key = "frostsoul_continue_listening_header") {
                    FSSectionHeader(
                        title = stringResource(R.string.home_continue_listening),
                        actionLabel = stringResource(R.string.see_all),
                        onAction = { navController.navigate("home_collection/continue") },
                    )
                }
                item(key = "frostsoul_continue_listening") {
                    FrostSoulLocalShelf(
                        items = uiState.keepListening,
                        mediaMetadata = mediaMetadata,
                        playerConnection = playerConnection,
                        navController = navController,
                    )
                }
            }

            if (uiState.forThisMoment.isNotEmpty()) {
                item(key = "frostsoul_for_this_moment_header") {
                    FSSectionHeader(title = stringResource(R.string.home_for_this_moment), actionLabel = stringResource(R.string.see_all), onAction = { navController.navigate("home_collection/moment") })
                }
                item(key = "frostsoul_for_this_moment") {
                    FrostSoulSongShelf(
                        songs = uiState.forThisMoment,
                        mediaMetadata = mediaMetadata,
                        playerConnection = playerConnection,
                        badge = "PLAY",
                        spotlight = false,
                    )
                }

            }

            if (uiState.offlineMixes.isNotEmpty()) {
                item(key = "frostsoul_daily_mix_header") {
                    FSSectionHeader(title = "Daily Mix", actionLabel = stringResource(R.string.see_all), onAction = { navController.navigate(Screens.Library.route) })
                }
                item(key = "frostsoul_daily_mix") {
                    FrostSoulOfflineMixShelf(
                        mixes = uiState.offlineMixes,
                        mediaMetadata = mediaMetadata,
                        playerConnection = playerConnection,
                    )
                }
            }

            if (uiState.forgottenFavorites.isNotEmpty()) {
                item(key = "frostsoul_recently_added_header") {
                    FSSectionHeader(title = "Rediscover",
                        actionLabel = stringResource(R.string.see_all), onAction = { navController.navigate(Screens.Library.route) })
                }
                item(key = "frostsoul_recently_added") {
                    FrostSoulSongShelf(
                        songs = uiState.forgottenFavorites,
                        mediaMetadata = mediaMetadata,
                        playerConnection = playerConnection,
                        badge = "NEW",
                        spotlight = false,
                    )
                }
            }

            if (recentItems.isNotEmpty()) {
                item(key = "frostsoul_recently_played_header") {
                    FSSectionHeader(
                        title = "Recently Played",
                        eyebrow = "YOUR HISTORY",
                        actionLabel = stringResource(R.string.see_all),
                        onAction = { navController.navigate("history") },
                    )
                }
                item(key = "frostsoul_recently_played") {
                    PremiumCard(
                        modifier = Modifier.padding(horizontal = FrostSoulTheme.spacing.page),
                        contentPadding = PaddingValues(vertical = FrostSoulTheme.spacing.small),
                    ) {
                        recentItems.forEach { item ->
                            PremiumListRow(
                                title = item.title,
                                subtitle = item.frostSoulSubtitle(),
                                artworkUrl = item.frostSoulArtwork(),
                                isActive = item is Song && item.id == mediaMetadata?.id && isPlaying,
                                onClick = { item.openFromFrostSoul(playerConnection, navController) },
                            )
                        }
                    }
                }
            }

            if (albums.isNotEmpty()) {
                item(key = "frostsoul_albums_header") {
                    FSSectionHeader(title = "Albums", eyebrow = "COLLECTION")
                }
                item(key = "frostsoul_albums") {
                    FrostSoulLocalShelf(
                        items = albums,
                        mediaMetadata = mediaMetadata,
                        playerConnection = playerConnection,
                        navController = navController,
                    )
                }
            }

            if (artists.isNotEmpty()) {
                item(key = "frostsoul_artists_header") {
                    FSSectionHeader(title = "Artists", eyebrow = "FOLLOW THE VOICE")
                }
                item(key = "frostsoul_artists") {
                    LazyRow(
                        contentPadding = FrostSoulShelfItemPadding,
                        horizontalArrangement = Arrangement.spacedBy(FrostSoulShelfSpacing),
                    ) {
                        items(artists, key = { it.id }, contentType = { "artist_card" }) { artist ->
                            FSArtistCard(
                                name = artist.title,
                                artworkUrl = artist.artist.thumbnailUrl,
                                subtitle = "Artist",
                                onClick = { navController.navigate("artist/${artist.id}") },
                            )
                        }
                    }
                }
            }

            uiState.similarRecommendations.forEachIndexed { index, recommendation ->
                item(key = "frostsoul_recommendation_header_${recommendation.title.id}") {
                    FSSectionHeader(
                        title = if (index == 0) "Recommended For You" else recommendation.title.title,
                        eyebrow = if (index == 0) "DISCOVER" else "BASED ON ${recommendation.title.title}",
                    )
                }
                item(key = "frostsoul_recommendation_${recommendation.title.id}") {
                    PremiumCard(
                        modifier = Modifier.padding(horizontal = FrostSoulTheme.spacing.page),
                        contentPadding = PaddingValues(vertical = FrostSoulTheme.spacing.small),
                    ) {
                        SimilarRecommendationsSection(
                            recommendation = recommendation,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                        )
                    }
                }
            }

            if (uiState.quickPicks.isNotEmpty()) {
                item(key = "frostsoul_quick_picks_header", contentType = "header") {
                    FSSectionHeader(title = stringResource(R.string.quick_picks))
                }
                item(key = "frostsoul_quick_picks", contentType = "shelf") {
                    FrostSoulRecommendationList(uiState.quickPicks, mediaMetadata, playerConnection)
                }
            }
        }

        pageSections.forEachIndexed { index, section ->
            val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
            item(key = "frostsoul_remote_header_$sectionKey") {
                FSSectionHeader(title = section.title, eyebrow = "EXPLORE")
            }
            item(key = "frostsoul_remote_$sectionKey") {
                PremiumCard(
                    modifier = Modifier.padding(horizontal = FrostSoulTheme.spacing.page),
                    contentPadding = PaddingValues(vertical = FrostSoulTheme.spacing.small),
                ) {
                    HomePageSectionContent(
                        section = section,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                    )
                }
            }
        }

        if (uiState.isLoadingMore) {
            item(key = "frostsoul_loading_more") {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(FrostSoulTheme.spacing.hero)) {
                    FSLoading()
                }
            }
        }

        val hasLocalShelves = uiState.keepListening.isNotEmpty() || recentItems.isNotEmpty() ||
            uiState.featuredForYou.isNotEmpty() || uiState.forThisMoment.isNotEmpty() ||
            uiState.quickPicks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty() ||
            uiState.offlineMixes.isNotEmpty() || uiState.forgottenFavorites.isNotEmpty() ||
            uiState.similarRecommendations.isNotEmpty()
        if (!uiState.isChipLoading && !uiState.chipLoadFailed && pageSections.isEmpty() &&
            (isMoodSelected || !hasLocalShelves)) {
            item(key = "frostsoul_home_empty", contentType = "status") {
                FSEmptyState(
                    title = stringResource(R.string.no_results_found),
                    message = stringResource(R.string.home_mood_retry),
                    modifier = Modifier.height(280.dp),
                    actionLabel = stringResource(R.string.refresh),
                    onAction = { onAction(HomeAction.Refresh) },
                )
            }
        }
    }
}

@Composable
private fun FrostSoulAstraQuickAccess(
    hasQuickPicks: Boolean,
    onSurprise: () -> Unit,
    onLiked: () -> Unit,
    onOffline: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.small)) {
        FSSectionHeader(title = "Quick access")
        LazyRow(
            contentPadding = PaddingValues(horizontal = FrostSoulTheme.spacing.page),
            horizontalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.small),
        ) {
            if (hasQuickPicks) {
                item(key = "astra_quick_surprise") {
                    FrostSoulAstraQuickAction(
                        icon = R.drawable.shuffle,
                        title = "Surprise me",
                        subtitle = "Fresh picks",
                        onClick = onSurprise,
                        emphasized = true,
                    )
                }
            }
            item(key = "astra_quick_liked") {
                FrostSoulAstraQuickAction(
                    icon = R.drawable.favorite,
                    title = "Liked songs",
                    subtitle = "Your favorites",
                    onClick = onLiked,
                )
            }
            item(key = "astra_quick_offline") {
                FrostSoulAstraQuickAction(
                    icon = R.drawable.download,
                    title = "Downloads",
                    subtitle = "Offline music",
                    onClick = onOffline,
                )
            }
        }
    }
}

@Composable
private fun FrostSoulAstraQuickAction(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val colors = FrostSoulTheme.colors
    PremiumCard(
        modifier = Modifier.width(204.dp),
        shape = FrostSoulTheme.shapes.large,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(colors.accent.copy(alpha = if (emphasized) 0.18f else 0.08f)),
            ) {
                FSIcon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accent,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = colors.onSurface, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = FrostSoulTheme.typography.bodyMuted,
                    color = colors.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FrostSoulGreetingHeader(
    modifier: Modifier = Modifier,
) {
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val (greeting, subtitle) = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Good morning" to "Music tuned for your morning flow"
            in 12..16 -> "Good afternoon" to "Soundtracks for your afternoon"
            in 17..21 -> "Good evening" to "Wind down with your favorites"
            else -> "Good night" to "Late night vibes & deep listening"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FrostSoulTheme.spacing.page)
            .padding(top = 4.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FSText(
            text = "LISTEN NOW",
            color = FrostSoulTheme.colors.accent,
            style = FrostSoulTheme.typography.overline,
        )
        FSText(
            text = greeting,
            color = FrostSoulTheme.colors.onSurface,
            style = FrostSoulTheme.typography.title.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        FSText(
            text = subtitle,
            color = FrostSoulTheme.colors.onSurfaceMuted,
            style = FrostSoulTheme.typography.bodyMuted,
        )
    }
}

@Composable
private fun FrostSoulSpotifyQuickGrid(
    items: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val displayItems = remember(items) { items.take(6) }
    val itemPairs = remember(displayItems) { displayItems.chunked(2) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FrostSoulTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemPairs.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { item ->
                    val isCurrent = (item is Song && item.id == mediaMetadata?.id) ||
                        (mediaMetadata != null && item.title == mediaMetadata.title)
                    val activePlaying = isCurrent && isPlaying

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FrostSoulTheme.colors.surfaceRaised)
                            .border(
                                width = 1.dp,
                                color = if (isCurrent) FrostSoulTheme.colors.accent.copy(alpha = 0.40f)
                                else FrostSoulTheme.colors.outline.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                if (item is Song && item.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    item.openFromFrostSoul(playerConnection, navController)
                                }
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val artwork = item.frostSoulArtwork()
                            if (!artwork.isNullOrBlank()) {
                                AsyncImage(
                                    model = artwork,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                        .background(FrostSoulTheme.colors.accent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FSIcon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        tint = FrostSoulTheme.colors.accent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            Text(
                                text = item.title,
                                color = FrostSoulTheme.colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            )

                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(FrostSoulTheme.colors.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FSIcon(
                                        painter = painterResource(if (activePlaying) R.drawable.pause else R.drawable.play),
                                        contentDescription = null,
                                        tint = FrostSoulTheme.colors.accent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FrostSoulBannerCarousel(
    songs: List<Song>,
    mediaMetadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth * 0.82f).coerceIn(280.dp, 420.dp)
        val cardHeight = (cardWidth * 0.65f).coerceIn(200.dp, 260.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FSSectionHeader(
                title = "Featured for you",
                eyebrow = "MADE FOR YOU",
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = FrostSoulTheme.spacing.page),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(cardHeight),
            ) {
                items(songs, key = { "banner_${it.id}" }, contentType = { "featured_banner" }) { song ->
                    val active = song.id == mediaMetadata?.id
                    val playing = active && isPlaying
                    val playSong = {
                        if (active) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = "Featured for you",
                                    items = songs.map { it.toMediaItem() },
                                    startIndex = songs.indexOf(song),
                                ),
                            )
                        }
                    }
                    PremiumCard(
                        modifier = Modifier.width(cardWidth).fillMaxHeight(),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = playSong,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = song.song.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.45f),
                                        0.28f to Color.Transparent,
                                        0.50f to Color.Black.copy(alpha = 0.25f),
                                        1f to Color.Black.copy(alpha = 0.94f),
                                    ),
                                ),
                            )
                            // Apple Music style frosted badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.52f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                if (active) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (playing) FrostSoulTheme.colors.accentBright else Color.White),
                                    )
                                }
                                Text(
                                    text = when {
                                        playing -> "NOW PLAYING"
                                        active -> "PAUSED"
                                        else -> "MADE FOR YOU"
                                    },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                )
                            }

                            // Circular Play FAB
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable(onClick = playSong),
                                contentAlignment = Alignment.Center,
                            ) {
                                FSIcon(
                                    painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                                    contentDescription = if (playing) "Pause ${song.title}" else "Play ${song.title}",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Song title and artist
                            Column(
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 68.dp, bottom = 14.dp),
                            ) {
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artists.joinToString(" • ") { it.name }.ifBlank { "Featured" },
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
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
private fun FrostSoulEveryoneListening(
    songs: List<Song>,
    mediaMetadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = FrostSoulTheme.spacing.page),
    ) {
        FSSectionHeader(title = "Everyone is listening", actionLabel = "Play all", onAction = {
            playerConnection.playQueue(ListQueue(items = songs.map { it.toMediaItem() }))
        })
        songs.forEach { song ->
            PremiumListRow(
                title = song.title,
                subtitle = song.artists.joinToString(" • ") { it.name },
                artworkUrl = song.song.thumbnailUrl,
                isActive = song.id == mediaMetadata?.id && isPlaying,
                onClick = {
                    if (song.id == mediaMetadata?.id) playerConnection.player.togglePlayPause()
                    else playerConnection.playQueue(ListQueue(items = listOf(song.toMediaItem())))
                },
            )
        }
    }
}

@Composable
private fun FrostSoulPreferencePrompt(onClick: () -> Unit) {
    PremiumCard(
        modifier = Modifier.padding(horizontal = FrostSoulTheme.spacing.page).fillMaxWidth(),
        shape = FrostSoulTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FSText("Tell us your music taste", color = FrostSoulTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            FSText("Get recommendations tuned to your listening.", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 13.sp)
            FSButton(label = "Set preferences", onClick = onClick, modifier = Modifier.padding(top = 8.dp), emphasized = true)
        }
    }
}

@Composable
private fun FrostSoulRecommendationList(
    songs: List<Song>,
    mediaMetadata: MediaMetadata?,
    playerConnection: PlayerConnection,
) {
    val carouselState = rememberLazyListState()

    LazyRow(
        state = carouselState,
        contentPadding = FrostSoulShelfItemPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(songs, key = { "quick_card_${it.id}" }, contentType = { "quick_card" }) { song ->
            val isCurrent = song.id == mediaMetadata?.id
            PremiumCard(
                modifier = Modifier
                    .width(160.dp)
                    .heightIn(min = 204.dp),
                shape = FrostSoulTheme.shapes.medium,
                contentPadding = PaddingValues(FrostSoulTheme.spacing.medium),
                onClick = {
                    if (isCurrent) playerConnection.player.togglePlayPause()
                    else playerConnection.playQueue(ListQueue(items = listOf(song.toMediaItem())))
                },
            ) {
                AsyncImage(
                    model = song.song.thumbnailUrl,
                    contentDescription = "Artwork for ${song.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(108.dp).clip(FrostSoulTheme.shapes.small),
                )
                Text(
                    text = song.title,
                    color = FrostSoulTheme.colors.onSurface,
                    style = FrostSoulTheme.typography.label,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = song.artists.firstOrNull()?.name.orEmpty(),
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                    style = FrostSoulTheme.typography.bodyMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = FrostSoulTheme.spacing.micro),
                )
                FSIcon(
                    painter = painterResource(if (isCurrent && playerConnection.player.isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = if (isCurrent) "Pause ${song.title}" else "Play ${song.title}",
                    tint = FrostSoulTheme.colors.onSurface,
                    modifier = Modifier.size(18.dp).padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun FrostSoulQuickSearch(onOpenSearch: () -> Unit) {
    PremiumSearchBar(
        onClick = onOpenSearch,
        modifier = Modifier.padding(horizontal = FrostSoulTheme.spacing.page),
    )
}

@Composable
private fun FrostSoulHomeTabs(
    chips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onChipSelected: (HomePage.Chip?) -> Unit,
) {
    if (chips.isEmpty()) return

    val selectedEndpoint = selectedChip?.endpoint
    val selectedIndex = if (selectedEndpoint == null) 0
        else chips.indexOfFirst { it.endpoint == selectedEndpoint } + 1
    PremiumSegmentedTabs(
        labels = listOf(stringResource(R.string.home_for_you)) + chips.map { it.title },
        selectedIndex = selectedIndex,
        onSelected = { index -> onChipSelected(chips.getOrNull(index - 1)) },
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

@Composable
private fun FrostSoulHomeHero(
    track: MediaMetadata?,
    isPlaying: Boolean,
    onQuickSearch: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    canSkipNext: Boolean,
) {
    PremiumHeroBanner(
        artworkUrl = track?.thumbnailUrl,
        title = track?.title ?: "Make room for music",
        subtitle = track?.artists?.joinToString(separator = " • ") { it.name } ?: "Discover something that feels like you",
        modifier = Modifier.padding(horizontal = FrostSoulTheme.spacing.page),
        isPlaying = isPlaying,
        hasTrack = track != null,
        onPlayPause = if (track != null) onPlayPause else onQuickSearch,
        onNext = onNext,
        canSkipNext = canSkipNext,
    )
}

@Composable
private fun FrostSoulSongShelf(
    songs: List<Song>,
    mediaMetadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    badge: String? = null,
    spotlight: Boolean = false,
) {
    LazyRow(
        contentPadding = FrostSoulShelfItemPadding,
        horizontalArrangement = Arrangement.spacedBy(FrostSoulShelfSpacing),
    ) {
        items(songs, key = { it.id }, contentType = { "song_card" }) { song ->
            FSAlbumCard(
                title = song.title,
                subtitle = song.artists.joinToString(separator = " • ") { it.name },
                artworkUrl = song.song.thumbnailUrl,
                badge = badge,
                width = if (spotlight) 256.dp else 154.dp,
                artworkAspectRatio = if (spotlight) 1.28f else 1f,
                showPlayOverlay = true,
                onClick = {
                    if (song.id == mediaMetadata?.id) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        playerConnection.playQueue(ListQueue(items = listOf(song.toMediaItem())))
                    }
                },
            )
        }
    }
}

@Composable
private fun FrostSoulOfflineMixShelf(
    mixes: List<LibraryTopMix>,
    mediaMetadata: MediaMetadata?,
    playerConnection: PlayerConnection,
) {
    LazyRow(
        contentPadding = FrostSoulShelfItemPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(mixes, key = { it.id }, contentType = { "offline_mix" }) { mix ->
            PremiumCard(
                modifier = Modifier.width(196.dp).height(86.dp),
                shape = FrostSoulTheme.shapes.large,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(title = mix.title, items = mix.tracks.map { it.toMediaItem() }),
                    )
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        FSText(
                            text = mix.title,
                            color = FrostSoulTheme.colors.onSurface,
                            style = FrostSoulTheme.typography.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        FSText(
                            text = mix.description.ifBlank { "For today" },
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(0.72f).padding(top = 10.dp).height(2.dp)
                                .background(FrostSoulTheme.colors.onSurface.copy(alpha = 0.72f), FrostSoulTheme.shapes.pill),
                        )
                    }
                    FSIconButton(
                        onClick = {
                            playerConnection.playQueue(
                                ListQueue(title = mix.title, items = mix.tracks.map { it.toMediaItem() }),
                            )
                        },
                        highlighted = false,
                        modifier = Modifier.size(38.dp),
                    ) {
                        FSIcon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = "Play ${mix.title}",
                            tint = FrostSoulTheme.colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostSoulLocalShelf(
    items: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    navController: NavController,
    badge: String? = null,
) {
    LazyRow(
        contentPadding = FrostSoulShelfItemPadding,
        horizontalArrangement = Arrangement.spacedBy(FrostSoulShelfSpacing),
    ) {
        items(
            items,
            key = { item -> "${item::class.simpleName}_${item.id}" },
            contentType = { item -> item::class.simpleName ?: "local_item" },
        ) { item ->
            FSAlbumCard(
                title = item.title,
                subtitle = item.frostSoulSubtitle(),
                artworkUrl = item.frostSoulArtwork(),
                badge = badge,
                showPlayOverlay = true,
                onClick = {
                    if (item is Song && item.id == mediaMetadata?.id) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        item.openFromFrostSoul(playerConnection, navController)
                    }
                },
            )
        }
    }
}

private fun LocalItem.openFromFrostSoul(
    playerConnection: PlayerConnection,
    navController: NavController,
) {
    when (this) {
        is Song -> playerConnection.playQueue(ListQueue(items = listOf(toMediaItem())))
        is Album -> navController.navigate("album/$id")
        is Artist -> navController.navigate("artist/$id")
        is Playlist -> navController.navigate("local_playlist/$id")
    }
}

private fun LocalItem.frostSoulArtwork(): String? =
    when (this) {
        is Playlist -> thumbnails.firstOrNull()
        else -> thumbnailUrl
    }

private fun LocalItem.frostSoulSubtitle(): String =
    when (this) {
        is Song -> artists.joinToString(separator = " • ") { it.name }
        is Album -> artists.joinToString(separator = " • ") { it.name }.ifBlank { "Album" }
        is Artist -> "Artist"
        is Playlist -> "$songCount tracks"
    }
