/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.db.entities.Album
import dev.vxs.frostsoulx.db.entities.Artist
import dev.vxs.frostsoulx.db.entities.LocalItem
import dev.vxs.frostsoulx.db.entities.Playlist
import dev.vxs.frostsoulx.db.entities.Song
import dev.vxs.frostsoulx.extensions.toMediaItem
import dev.vxs.frostsoulx.extensions.togglePlayPause
import dev.vxs.frostsoulx.home.HomeAction
import dev.vxs.frostsoulx.home.HomeUiState
import dev.vxs.frostsoulx.innertube.models.PlaylistItem
import dev.vxs.frostsoulx.innertube.models.AlbumItem
import dev.vxs.frostsoulx.innertube.models.SongItem
import dev.vxs.frostsoulx.innertube.models.WatchEndpoint
import dev.vxs.frostsoulx.innertube.models.YTItem
import dev.vxs.frostsoulx.innertube.pages.HomePage
import dev.vxs.frostsoulx.library.LibraryTopMix
import dev.vxs.frostsoulx.models.MediaMetadata
import dev.vxs.frostsoulx.models.toMediaMetadata
import dev.vxs.frostsoulx.playback.PlayerConnection
import dev.vxs.frostsoulx.playback.queues.ListQueue
import dev.vxs.frostsoulx.playback.queues.YouTubeQueue
import dev.vxs.frostsoulx.ui.component.MenuState
import dev.vxs.frostsoulx.ui.frostsoul.FSEmptyState
import dev.vxs.frostsoulx.ui.frostsoul.FSIcon
import dev.vxs.frostsoulx.ui.frostsoul.FSLoading
import dev.vxs.frostsoulx.ui.frostsoul.FSSectionHeader
import dev.vxs.frostsoulx.ui.frostsoul.FSText
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.premium.PremiumSegmentedTabs
import kotlinx.coroutines.CoroutineScope
import java.util.Calendar

private val ShelfPadding = PaddingValues(horizontal = 16.dp)
private val ShelfSpacing = 12.dp

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
    val isMoodSelected = uiState.selectedChip != null
    val pageSections = if (uiState.isChipLoading || uiState.chipLoadFailed) {
        emptyList()
    } else {
        uiState.homePage?.sections.orEmpty().filter { it.items.isNotEmpty() }
    }

    // Hero items: 3 to 5 recommendations
    val heroSongs = remember(uiState.featuredForYou, uiState.quickPicks, uiState.recentlyPlayed) {
        val candidates = (uiState.featuredForYou + uiState.quickPicks + uiState.recentlyPlayed).distinctBy { it.id }
        candidates.take(5)
    }

    // Quick picks: 12 distinct tracks
    val quickPicks = remember(uiState.quickPicks, uiState.recentlyPlayed) {
        (uiState.quickPicks + uiState.recentlyPlayed).distinctBy { it.id }.take(12)
    }

    // Keep listening: unfinished / recently played tracks
    val keepListeningItems = remember(uiState.keepListening, uiState.recentlyPlayed) {
        (uiState.keepListening + uiState.recentlyPlayed).distinctBy { it.id }.take(8)
    }

    // Dynamic community playlists from online sections or account
    val communityPlaylists = remember(uiState.homePage, uiState.accountPlaylists) {
        val fromOnline = uiState.homePage?.sections.orEmpty()
            .filter { section ->
                section.title.contains("playlist", ignoreCase = true) ||
                    section.title.contains("community", ignoreCase = true) ||
                    section.title.contains("mix", ignoreCase = true) ||
                    section.title.contains("chart", ignoreCase = true) ||
                    section.items.any { it is PlaylistItem }
            }
            .flatMap { it.items }
            .filterIsInstance<PlaylistItem>()
            .distinctBy { it.id }

        (fromOnline + uiState.accountPlaylists).distinctBy { it.id }.take(4)
    }

    // Use only real album payloads from the Home response. The shelf intentionally
    // stays compact (5–6 cards) so it remains a quick discovery row on phones.
    val newReleases = remember(uiState.homePage) {
        val sections = uiState.homePage?.sections.orEmpty()
        val preferred = sections
            .filter { section ->
                section.title.contains("new release", ignoreCase = true) ||
                    section.title.contains("latest", ignoreCase = true) ||
                    section.title.contains("album", ignoreCase = true) ||
                    section.title.contains("single", ignoreCase = true)
            }
            .flatMap { it.items }
            .filterIsInstance<AlbumItem>()
        val fallback = sections.flatMap { it.items }.filterIsInstance<AlbumItem>()
        (preferred + fallback).distinctBy { it.id }.take(6)
    }

    // Some Home responses include songs alongside a playlist card. Preserve those
    // actual songs for the card preview; never invent track titles or durations.
    val communityPreviewSongs = remember(uiState.homePage) {
        buildMap<String, List<SongItem>> {
            uiState.homePage?.sections.orEmpty()
                .filter { section ->
                    section.title.contains("playlist", ignoreCase = true) ||
                        section.title.contains("community", ignoreCase = true) ||
                        section.items.any { it is PlaylistItem }
                }
                .forEach { section ->
                    val songs = section.items.filterIsInstance<SongItem>().distinctBy { it.id }.take(3)
                    section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                        put(playlist.id, songs)
                    }
                }
        }
    }

    // Similar artists
    val topSimilarRecommendation = remember(uiState.similarRecommendations) {
        uiState.similarRecommendations.firstOrNull()
    }
    val similarArtistName = remember(topSimilarRecommendation, uiState.speedDialItems, uiState.recentlyPlayed) {
        topSimilarRecommendation?.title?.title
            ?: uiState.speedDialItems.filterIsInstance<Artist>().firstOrNull()?.title
            ?: uiState.recentlyPlayed.firstOrNull()?.artists?.firstOrNull()?.name
            ?: "Artists you may like"
    }
    val similarArtistsList = remember(uiState.speedDialItems, topSimilarRecommendation) {
        val localArtists = uiState.speedDialItems.filterIsInstance<Artist>()
        if (localArtists.isNotEmpty()) {
            localArtists.take(8)
        } else {
            emptyList()
        }
    }

    // Forgotten favorites
    val forgottenFavorites = remember(uiState.forgottenFavorites) {
        uiState.forgottenFavorites.distinctBy { it.id }.take(8)
    }

    // Trending songs for you
    val trendingSongs = remember(uiState.forThisMoment, uiState.homePage) {
        if (uiState.forThisMoment.isNotEmpty()) {
            uiState.forThisMoment.distinctBy { it.id }.take(10)
        } else {
            uiState.homePage?.sections.orEmpty()
                .filter { it.title.contains("trending", ignoreCase = true) || it.title.contains("chart", ignoreCase = true) }
                .flatMap { it.items }
                .filterIsInstance<SongItem>()
                .distinctBy { it.id }
                .take(10)
        }
    }

    // Covers and remixes
    val coversAndRemixes = remember(uiState.homePage) {
        uiState.homePage?.sections.orEmpty()
            .filter { section ->
                section.title.contains("remix", ignoreCase = true) ||
                    section.title.contains("cover", ignoreCase = true) ||
                    section.title.contains("acoustic", ignoreCase = true) ||
                    section.title.contains("version", ignoreCase = true)
            }
            .flatMap { it.items }
            .distinctBy { it.id }
            .take(8)
    }

    // Featured discovery playlists
    val featuredPlaylists = remember(uiState.homePage, communityPlaylists) {
        val communityIds = communityPlaylists.mapTo(HashSet()) { it.id }
        uiState.homePage?.sections.orEmpty()
            .flatMap { it.items }
            .filterIsInstance<PlaylistItem>()
            .filterNot { it.id in communityIds }
            .distinctBy { it.id }
            .take(6)
    }

    // MainActivity already exposes the status-bar + shared brand-header inset here.
    // Re-adding WindowInsets.statusBars caused the Home rows to move underneath the
    // header when the system bars were revealed by a pull-down gesture.
    val topPadding = (LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateTopPadding() +
        FrostSoulTheme.spacing.micro).coerceAtLeast(0.dp)
    val bottomPadding = (LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp).coerceAtLeast(0.dp)

    // The feed scrolls over the stationary canvas below
    LazyColumn(
        state = lazyListState,
        contentPadding =
            PaddingValues(
                top = topPadding,
                bottom = bottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.section),
        modifier = modifier.fillMaxSize(),
    ) {
        // Mood / Category Chips
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

        if (!uiState.isChipLoading && !uiState.chipLoadFailed) {
            // 1. TOP HEADER / GREETING
            item(key = "frostsoul_greeting") {
                FrostSoulGreetingHeader(
                    accountName = uiState.accountName,
                    selectedChip = uiState.selectedChip,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                )
            }
        }

        if (!isMoodSelected) {

            // 2. HERO CAROUSEL (3 to 5 items with pagination dots)
            if (heroSongs.isNotEmpty()) {
                item(key = "frostsoul_hero_carousel") {
                    FrostSoulHeroCarousel(
                        songs = heroSongs,
                        mediaMetadata = mediaMetadata,
                        playerConnection = playerConnection,
                        isPlaying = isPlaying,
                    )
                }
            }

            // 3. QUICK PICKS
            if (quickPicks.isNotEmpty()) {
                item(key = "frostsoul_quick_picks_header") {
                    FSSectionHeader(
                        title = "Quick picks",
                        actionLabel = "See all ›",
                        onAction = { navController.navigate("home_collection/quick_picks") },
                    )
                }
                item(key = "frostsoul_quick_picks_shelf") {
                    FrostSoulQuickPicksShelf(
                        songs = quickPicks,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playerConnection = playerConnection,
                    )
                }
            }

            // 4. KEEP LISTENING
            if (keepListeningItems.isNotEmpty()) {
                item(key = "frostsoul_keep_listening_header") {
                    FSSectionHeader(
                        title = "Keep listening",
                        actionLabel = "See all ›",
                        onAction = { navController.navigate("home_collection/continue") },
                    )
                }
                item(key = "frostsoul_keep_listening_shelf") {
                    FrostSoulKeepListeningShelf(
                        items = keepListeningItems,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playerConnection = playerConnection,
                        navController = navController,
                    )
                }
            }

            // 5. MADE FOR YOU
            item(key = "frostsoul_made_for_you_header") {
                FSSectionHeader(
                    title = "Made for you",
                )
            }
            item(key = "frostsoul_made_for_you_shelf") {
                FrostSoulMadeForYouShelf(
                    offlineMixes = uiState.offlineMixes,
                    playerConnection = playerConnection,
                )
            }

            // 6. NEW RELEASES
            if (newReleases.isNotEmpty()) {
                item(key = "frostsoul_new_releases_header") {
                    FSSectionHeader(
                        title = "New releases",
                        actionLabel = "See all ›",
                        onAction = { navController.navigate("new_release") },
                    )
                }
                item(key = "frostsoul_new_releases_shelf") {
                    FrostSoulNewReleasesShelf(
                        releases = newReleases,
                        navController = navController,
                    )
                }
            }

            // 7. TRENDING COMMUNITY PLAYLISTS
            if (communityPlaylists.isNotEmpty()) {
                item(key = "frostsoul_community_playlists_header") {
                    FSSectionHeader(
                        title = "Trending community playlists",
                        actionLabel = "See all ›",
                        onAction = { navController.navigate("browse") },
                    )
                }
                item(key = "frostsoul_community_playlists_shelf") {
                    FrostSoulCommunityPlaylistsShelf(
                        playlists = communityPlaylists,
                        previewSongsByPlaylist = communityPreviewSongs,
                        navController = navController,
                        playerConnection = playerConnection,
                    )
                }
            }

            // 7. SIMILAR ARTISTS
            if (similarArtistsList.isNotEmpty() || topSimilarRecommendation != null) {
                item(key = "frostsoul_similar_artists_header") {
                    FSSectionHeader(
                        title = if (similarArtistName.isNotBlank()) "Because you listen to $similarArtistName" else "Similar artists",
                        actionLabel = "See all ›",
                        onAction = {
                            val firstArtist = similarArtistsList.firstOrNull()
                            if (firstArtist != null) {
                                navController.navigate("artist/${firstArtist.id}")
                            } else {
                                navController.navigate(Screens.Library.route)
                            }
                        },
                    )
                }
                item(key = "frostsoul_similar_artists_shelf") {
                    FrostSoulSimilarArtistsShelf(
                        artists = similarArtistsList,
                        navController = navController,
                    )
                }
            }

            // 8. FORGOTTEN FAVORITES (Only shown when available)
            if (forgottenFavorites.isNotEmpty()) {
                item(key = "frostsoul_forgotten_favorites_header") {
                    FSSectionHeader(
                        title = "Forgotten favorites",
                        actionLabel = "See all ›",
                        onAction = { navController.navigate("home_collection/forgotten") },
                    )
                }
                item(key = "frostsoul_forgotten_favorites_shelf") {
                    FrostSoulForgottenFavoritesShelf(
                        songs = forgottenFavorites,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playerConnection = playerConnection,
                    )
                }
            }

            // 9. TRENDING SONGS FOR YOU
            if (trendingSongs.isNotEmpty()) {
                item(key = "frostsoul_trending_songs_header") {
                    FSSectionHeader(
                        title = "Trending songs for you",
                        actionLabel = "See all ›",
                        onAction = { navController.navigate("charts") },
                    )
                }
                item(key = "frostsoul_trending_songs_shelf") {
                    FrostSoulTrendingSongsShelf(
                        items = trendingSongs,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playerConnection = playerConnection,
                    )
                }
            }

            // 10. COVERS & REMIXES
            if (coversAndRemixes.isNotEmpty()) {
                item(key = "frostsoul_covers_remixes_header") {
                    FSSectionHeader(
                        title = "Covers & remixes",
                        actionLabel = "See all ›",
                        onAction = { navController.navigate("browse") },
                    )
                }
                item(key = "frostsoul_covers_remixes_shelf") {
                    FrostSoulTrendingSongsShelf(
                        items = coversAndRemixes,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playerConnection = playerConnection,
                    )
                }
            }

            // 11. FEATURED PLAYLISTS FOR YOU
            if (featuredPlaylists.isNotEmpty()) {
                item(key = "frostsoul_featured_playlists_header") {
                    FSSectionHeader(
                        title = "Featured playlists for you",
                        actionLabel = "See all ›",
                        onAction = { navController.navigate("browse") },
                    )
                }
                item(key = "frostsoul_featured_playlists_shelf") {
                    FrostSoulCommunityPlaylistsShelf(
                        playlists = featuredPlaylists,
                        navController = navController,
                        playerConnection = playerConnection,
                    )
                }
            }
        }

        // Mood-specific or Remaining Remote Sections
        val renderedTitles = setOf(
            "trending", "community", "playlist", "remix", "cover", "charts", "featured",
        )
        val remainingSections = if (isMoodSelected) {
            pageSections
        } else {
            pageSections.filterNot { section ->
                renderedTitles.any { section.title.contains(it, ignoreCase = true) }
            }
        }

        remainingSections.forEachIndexed { index, section ->
            val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
            item(key = "frostsoul_remote_header_$sectionKey") {
                FSSectionHeader(title = section.title)
            }
            item(key = "frostsoul_remote_$sectionKey") {
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

        if (uiState.isLoadingMore) {
            item(key = "frostsoul_loading_more") {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(FrostSoulTheme.spacing.hero)) {
                    FSLoading()
                }
            }
        }

        val hasAnyContent = heroSongs.isNotEmpty() || quickPicks.isNotEmpty() ||
            keepListeningItems.isNotEmpty() || pageSections.isNotEmpty()
        if (!uiState.isChipLoading && !uiState.chipLoadFailed && !hasAnyContent) {
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

// =======================================================================
// 1. TOP HEADER / GREETING
// =======================================================================

@Composable
private fun FrostSoulGreetingHeader(
    accountName: String,
    selectedChip: HomePage.Chip?,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val (greetingTitle, symbol, defaultOverline) = remember(currentHour) {
        when (currentHour) {
            in 0..4 -> Triple("Good Night", "🌙", "LATE NIGHT")
            in 5..11 -> Triple("Good Morning", "☀️", "MORNING FLOW")
            in 12..16 -> Triple("Good Afternoon", "⛅", "AFTERNOON FLOW")
            in 17..20 -> Triple("Good Evening", "🌅", "EVENING UNWIND")
            else -> Triple("Good Night", "🌙", "NIGHT VIBES")
        }
    }

    val resolvedName = remember(accountName) {
        val raw = accountName.trim()
        if (raw.isNotBlank() && !raw.equals("Shivam", ignoreCase = true)) {
            raw.split("\\s+".toRegex()).firstOrNull()?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }.orEmpty()
        } else {
            ""
        }
    }

    val displayTitle = remember(greetingTitle, resolvedName) {
        if (resolvedName.isNotBlank()) {
            "$greetingTitle, $resolvedName"
        } else {
            greetingTitle
        }
    }

    val overline = remember(selectedChip, isPlaying, defaultOverline) {
        when {
            selectedChip != null -> "${selectedChip.title.uppercase()} SESSION"
            isPlaying -> "NOW LISTENING"
            else -> defaultOverline
        }
    }

    val subtitle = remember(currentHour, selectedChip, isPlaying, mediaMetadata) {
        if (selectedChip != null) {
            val chipName = selectedChip.title
            when {
                chipName.contains("relax", ignoreCase = true) ->
                    if (currentHour in 21..23 || currentHour in 0..4) {
                        "Relaxation session • Calming ambient tones for winding down"
                    } else {
                        "Relaxation session • Peaceful melodies to clear your mind"
                    }
                chipName.contains("workout", ignoreCase = true) ->
                    if (currentHour in 5..11) {
                        "Morning workout • High-energy beats to power your day"
                    } else {
                        "Workout session • Heavy bass & high-tempo momentum"
                    }
                chipName.contains("focus", ignoreCase = true) ->
                    if (currentHour in 0..4) {
                        "Midnight focus • Deep instrumental concentration"
                    } else {
                        "Focus session • Ambient flow & distraction-free listening"
                    }
                chipName.contains("energ", ignoreCase = true) ->
                    "Energy session • Uplifting tracks & fast-paced rhythms"
                chipName.contains("commute", ignoreCase = true) ->
                    if (currentHour in 5..11) {
                        "Morning commute • Fresh soundtracks for your journey"
                    } else if (currentHour in 17..21) {
                        "Evening commute • Unwind on your way home"
                    } else {
                        "On the move • Handpicked tracks for the road"
                    }
                chipName.contains("party", ignoreCase = true) ->
                    "Party session • Crowd favorites & high-voltage jams"
                chipName.contains("sleep", ignoreCase = true) ->
                    "Sleep session • Soothing soundscapes for deep rest"
                chipName.contains("romance", ignoreCase = true) ->
                    "Romance session • Intimate acoustics & soulful ballads"
                chipName.contains("feel good", ignoreCase = true) ->
                    "Feel good session • Bright melodies & mood-lifting vibes"
                else ->
                    "$chipName session • Curated music for your current flow"
            }
        } else if (isPlaying && mediaMetadata != null) {
            val trackTitle = mediaMetadata.title.take(30)
            val artist = mediaMetadata.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }
            val trackInfo = if (!artist.isNullOrBlank()) "$trackTitle • $artist" else trackTitle
            when (currentHour) {
                in 0..4 -> "Late night session • Listening to $trackInfo"
                in 5..11 -> "Morning session • Listening to $trackInfo"
                in 12..16 -> "Afternoon flow • Listening to $trackInfo"
                in 17..20 -> "Evening session • Listening to $trackInfo"
                else -> "Night session • Listening to $trackInfo"
            }
        } else {
            when (currentHour) {
                in 0..4 -> "Late night vibes • Ambient tones & deep listening"
                in 5..8 -> "Early morning vibes • Gentle acoustics & sunrise melodies"
                in 9..11 -> "Morning vibes • Uplifting rhythms tuned for your day"
                in 12..16 -> "Afternoon flow • Soundtracks for focus & daytime momentum"
                in 17..20 -> "Evening vibes • Wind down with smooth melodies & favorites"
                else -> "Night vibes • Chill beats, mellow acoustic & calm listening"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FrostSoulTheme.spacing.page)
            .padding(top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FSText(
            text = overline,
            color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.85f),
            style = FrostSoulTheme.typography.overline.copy(
                fontSize = 11.5.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FSText(
                text = displayTitle,
                color = FrostSoulTheme.colors.onSurface,
                style = FrostSoulTheme.typography.title.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            FSText(
                text = symbol,
                style = FrostSoulTheme.typography.title.copy(
                    fontSize = 24.sp,
                ),
            )
        }
        FSText(
            text = subtitle,
            color = FrostSoulTheme.colors.onSurfaceMuted,
            style = FrostSoulTheme.typography.bodyMuted.copy(
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// =======================================================================
// 2. HERO CAROUSEL
// =======================================================================

@Composable
private fun FrostSoulHeroCarousel(
    songs: List<Song>,
    mediaMetadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
) {
    if (songs.isEmpty()) return

    val pagerState = rememberPagerState { songs.size }
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardHeight = (maxWidth * 0.58f).coerceIn(210.dp, 260.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fill,
                contentPadding = PaddingValues(horizontal = 14.dp),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight),
            ) { pageIndex ->
                val song = songs[pageIndex]
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
                                startIndex = pageIndex,
                            ),
                        )
                    }
                }

                val badgeText = when {
                    active && playing -> "NOW PLAYING"
                    active -> "PAUSED"
                    pageIndex == 0 -> "MADE FOR YOU"
                    pageIndex == 1 -> when (currentHour) {
                        in 5..11 -> "YOUR MORNING FLOW"
                        in 12..16 -> "YOUR AFTERNOON BEATS"
                        in 17..21 -> "YOUR EVENING MIX"
                        else -> "YOUR NIGHT MIX"
                    }
                    pageIndex == 2 -> "BECAUSE YOU PLAYED..."
                    pageIndex == 3 -> "TRENDING FOR YOU"
                    else -> "NEW DISCOVERY"
                }

                val cardShape = RoundedCornerShape(24.dp)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape)
                        .clickable(onClick = playSong),
                ) {
                    // High resolution artwork
                    AsyncImage(
                        model = song.song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Cinematic gradient wash
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.38f),
                                    0.28f to Color.Transparent,
                                    0.52f to Color.Black.copy(alpha = 0.38f),
                                    1f to Color.Black.copy(alpha = 0.94f),
                                ),
                            ),
                    )

                    // Top-left Frosted Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.54f))
                            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
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
                            text = badgeText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                        )
                    }

                    // Bottom-right Floating White Play FAB
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

                    // Bottom metadata column
                    Column(
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 68.dp, bottom = 14.dp),
                    ) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artists.joinToString(" • ") { it.name }.ifBlank { "Featured Artist" },
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = song.album?.title?.takeIf { it.isNotBlank() }?.let { "$it • " }.orEmpty() + "Recommended for you",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            // Centered pagination dots (● ○ ○ ○)
            if (songs.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(songs.size) { dotIndex ->
                        val isSelected = pagerState.currentPage == dotIndex
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 16.dp else 6.dp,
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                            label = "HeroDotWidth",
                        )
                        val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
                        val dotColor = when {
                            isSelected && isLight -> Color.Black
                            isSelected -> Color.White
                            isLight -> Color.Black.copy(alpha = 0.20f)
                            else -> Color.White.copy(alpha = 0.25f)
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(5.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }
            }
        }
    }
}

// =======================================================================
// 3. QUICK PICKS SHELF
// =======================================================================

@Composable
private fun FrostSoulQuickPicksShelf(
    songs: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
) {
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val cardWidth = 148.dp

    LazyRow(
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
    ) {
        items(songs, key = { "quick_${it.id}" }, contentType = { "quick_pick" }) { song ->
            val isCurrent = song.id == mediaMetadata?.id
            val activePlaying = isCurrent && isPlaying
            val cardShape = RoundedCornerShape(16.dp)

            Column(
                modifier = Modifier
                    .width(cardWidth)
                    .clickable {
                        if (isCurrent) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = "Quick picks",
                                    items = songs.map { it.toMediaItem() },
                                    startIndex = songs.indexOf(song),
                                ),
                            )
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .size(cardWidth)
                        .shadow(
                            elevation = if (isLight) 3.dp else 0.dp,
                            shape = cardShape,
                            clip = false,
                            spotColor = Color(0x18000000),
                            ambientColor = Color(0x0C000000),
                        )
                        .clip(cardShape)
                        .background(FrostSoulTheme.colors.surfaceRaised),
                ) {
                    AsyncImage(
                        model = song.song.thumbnailUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                FSIcon(
                                    painter = painterResource(if (activePlaying) R.drawable.pause else R.drawable.play),
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = song.title,
                    color = FrostSoulTheme.colors.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = song.artists.joinToString(", ") { it.name }.ifBlank { "Various" },
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// =======================================================================
// 4. KEEP LISTENING SHELF
// =======================================================================

@Composable
private fun FrostSoulKeepListeningShelf(
    items: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    navController: NavController,
) {
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val cardShape = RoundedCornerShape(16.dp)

    LazyRow(
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
    ) {
        items(items, key = { "keep_${it.id}" }, contentType = { "keep_listening" }) { item ->
            val isCurrent = (item is Song && item.id == mediaMetadata?.id) ||
                (mediaMetadata != null && item.title == mediaMetadata.title)
            val activePlaying = isCurrent && isPlaying

            val playItem = {
                if (item is Song && item.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else {
                    item.openFromFrostSoul(playerConnection, navController)
                }
            }

            val durationSec = (item as? Song)?.song?.duration ?: 0
            val progress = if (durationSec > 0) 0.42f else 0.35f
            val elapsedSec = (durationSec * progress).toInt()
            val elapsedStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
            val totalStr = if (durationSec > 0) "%d:%02d".format(durationSec / 60, durationSec % 60) else "3:45"

            Box(
                modifier = Modifier
                    .width(270.dp)
                    .height(80.dp)
                    .shadow(
                        elevation = if (isLight) 3.dp else 0.dp,
                        shape = cardShape,
                        clip = false,
                        spotColor = Color(0x18000000),
                        ambientColor = Color(0x0C000000),
                    )
                    .clip(cardShape)
                    .background(FrostSoulTheme.colors.surfaceRaised)
                    .border(
                        1.dp,
                        if (isCurrent) FrostSoulTheme.colors.accent.copy(alpha = 0.40f)
                        else if (isLight) FrostSoulTheme.colors.outline.copy(alpha = 0.12f)
                        else FrostSoulTheme.colors.outline.copy(alpha = 0.08f),
                        cardShape,
                    )
                    .clickable(onClick = playItem)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Artwork
                    val artwork = item.frostSoulArtwork()
                    if (!artwork.isNullOrBlank()) {
                        AsyncImage(
                            model = artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(FrostSoulTheme.colors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            FSIcon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                tint = FrostSoulTheme.colors.accent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    // Title, artist, progress bar & time
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = item.title,
                            color = FrostSoulTheme.colors.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.frostSoulSubtitle(),
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(FrostSoulTheme.colors.onSurface.copy(alpha = 0.14f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(FrostSoulTheme.colors.accent),
                            )
                        }

                        // Elapsed / Total
                        Text(
                            text = "$elapsedStr / $totalStr",
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 10.5.sp,
                        )
                    }

                    // Circular Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FrostSoulTheme.colors.surfaceGlassStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        FSIcon(
                            painter = painterResource(if (activePlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            tint = FrostSoulTheme.colors.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

// =======================================================================
// 5. MADE FOR YOU SHELF
// =======================================================================

private data class MadeForYouMix(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val gradientColors: List<Color>,
)

@Composable
private fun FrostSoulMadeForYouShelf(
    offlineMixes: List<LibraryTopMix>,
    playerConnection: PlayerConnection,
) {
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val cardShape = RoundedCornerShape(18.dp)

    val defaultMixes = listOf(
        MadeForYouMix("Daily Mix", "A mix shaped by your listening", R.drawable.album, listOf(Color(0xFF2C3E50), Color(0xFF000000))),
        MadeForYouMix("Night Drive", "For your late night sessions", R.drawable.bedtime, listOf(Color(0xFF3A1C71), Color(0xFF0F0C29))),
        MadeForYouMix("Indie Blend", "Artists you like", R.drawable.library_music, listOf(Color(0xFF134E5E), Color(0xFF0B192C))),
        MadeForYouMix("Desi Vibes", "Sufi • Bollywood • Acoustic", R.drawable.auto_awesome, listOf(Color(0xFF4A00E0), Color(0xFF1A1A2E))),
        MadeForYouMix("Focus", "Music to keep you in flow", R.drawable.equalizer, listOf(Color(0xFF0F2027), Color(0xFF203A43))),
    )

    LazyRow(
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
    ) {
        if (offlineMixes.isNotEmpty()) {
            items(offlineMixes, key = { "topmix_${it.id}" }) { mix ->
                Box(
                    modifier = Modifier
                        .width(164.dp)
                        .height(204.dp)
                        .shadow(
                            elevation = if (isLight) 3.dp else 0.dp,
                            shape = cardShape,
                            clip = false,
                        )
                        .clip(cardShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF232526), Color(0xFF0B0B0B)),
                            ),
                        )
                        .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.12f), cardShape)
                        .clickable {
                            playerConnection.playQueue(
                                ListQueue(title = mix.title, items = mix.tracks.map { it.toMediaItem() }),
                            )
                        }
                        .padding(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        FSIcon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = mix.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mix.description.ifBlank { "Personalized mix" },
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            items(defaultMixes, key = { it.title }) { mix ->
                Box(
                    modifier = Modifier
                        .width(164.dp)
                        .height(204.dp)
                        .shadow(
                            elevation = if (isLight) 3.dp else 0.dp,
                            shape = cardShape,
                            clip = false,
                        )
                        .clip(cardShape)
                        .background(Brush.verticalGradient(mix.gradientColors))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), cardShape)
                        .clickable {
                            // Focus or mix action
                        }
                        .padding(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        FSIcon(
                            painter = painterResource(mix.iconRes),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = mix.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mix.subtitle,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// =======================================================================
// 6. NEW RELEASES SHELF
// =======================================================================

@Composable
private fun FrostSoulNewReleasesShelf(
    releases: List<AlbumItem>,
    navController: NavController,
) {
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val cardShape = RoundedCornerShape(16.dp)
    val cardWidth = 156.dp

    LazyRow(
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
    ) {
        items(releases, key = { "release_${it.id}" }, contentType = { "new_release" }) { release ->
            Column(
                modifier = Modifier
                    .width(cardWidth)
                    .clickable { navController.navigate("album/${release.id}") },
            ) {
                Box(
                    modifier = Modifier
                        .size(cardWidth)
                        .shadow(
                            elevation = if (isLight) 3.dp else 0.dp,
                            shape = cardShape,
                            clip = false,
                            spotColor = Color(0x18000000),
                            ambientColor = Color(0x0C000000),
                        )
                        .clip(cardShape)
                        .background(FrostSoulTheme.colors.surfaceRaised),
                ) {
                    AsyncImage(
                        model = release.thumbnail,
                        contentDescription = release.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        FSIcon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = "Open ${release.title}",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = release.title,
                    color = FrostSoulTheme.colors.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = release.artists.orEmpty().joinToString(", ") { it.name }
                        .ifBlank { release.year?.toString() ?: "New release" },
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// =======================================================================
// 7. TRENDING COMMUNITY PLAYLISTS SHELF
// =======================================================================

@Composable
private fun FrostSoulCommunityPlaylistsShelf(
    playlists: List<PlaylistItem>,
    previewSongsByPlaylist: Map<String, List<SongItem>> = emptyMap(),
    navController: NavController,
    playerConnection: PlayerConnection,
) {
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val cardShape = RoundedCornerShape(20.dp)

    LazyRow(
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
    ) {
        items(playlists, key = { "comm_${it.id}" }, contentType = { "community_playlist" }) { playlist ->
            val playPlaylist = {
                val endpoint = playlist.playEndpoint
                if (endpoint != null) {
                    playerConnection.playQueue(YouTubeQueue(endpoint))
                } else {
                    navController.navigate("online_playlist/${playlist.id}")
                }
            }

            Box(
                modifier = Modifier
                    .width(310.dp)
                    .height(176.dp)
                    .shadow(
                        elevation = if (isLight) 3.dp else 0.dp,
                        shape = cardShape,
                        clip = false,
                        spotColor = Color(0x18000000),
                        ambientColor = Color(0x0C000000),
                    )
                    .clip(cardShape)
                    .background(FrostSoulTheme.colors.surfaceRaised)
                    .border(
                        1.dp,
                        if (isLight) FrostSoulTheme.colors.outline.copy(alpha = 0.12f)
                        else FrostSoulTheme.colors.outline.copy(alpha = 0.08f),
                        cardShape,
                    )
                    .clickable { navController.navigate("online_playlist/${playlist.id}") }
                    .padding(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Artwork with floating play button
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    ) {
                        AsyncImage(
                            model = playlist.thumbnail,
                            contentDescription = playlist.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = playPlaylist),
                            contentAlignment = Alignment.Center,
                        ) {
                            FSIcon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = "Play ${playlist.title}",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Title, saves & numbered tracks
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = playlist.title,
                            color = FrostSoulTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = playlist.songCountText ?: "Community • Popular",
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        previewSongsByPlaylist[playlist.id].orEmpty().forEachIndexed { index, track ->
                            Text(
                                text = "${index + 1}  ${track.title}",
                                color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// =======================================================================
// 7. SIMILAR ARTISTS SHELF
// =======================================================================

@Composable
private fun FrostSoulSimilarArtistsShelf(
    artists: List<Artist>,
    navController: NavController,
) {
    LazyRow(
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(artists, key = { "artist_${it.id}" }, contentType = { "artist" }) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clickable { navController.navigate("artist/${artist.id}") },
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(FrostSoulTheme.colors.surfaceRaised)
                        .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val avatarUrl = artist.artist.thumbnailUrl
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = artist.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        FSIcon(
                            painter = painterResource(R.drawable.artist),
                            contentDescription = null,
                            tint = FrostSoulTheme.colors.onSurfaceMuted,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Text(
                    text = artist.title,
                    color = FrostSoulTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// =======================================================================
// 8. FORGOTTEN FAVORITES SHELF
// =======================================================================

@Composable
private fun FrostSoulForgottenFavoritesShelf(
    songs: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
) {
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val cardShape = RoundedCornerShape(14.dp)

    LazyRow(
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
    ) {
        items(songs, key = { "forgotten_${it.id}" }, contentType = { "forgotten" }) { song ->
            val isCurrent = song.id == mediaMetadata?.id
            val activePlaying = isCurrent && isPlaying

            val playSong = {
                if (isCurrent) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        ListQueue(
                            title = "Forgotten favorites",
                            items = songs.map { it.toMediaItem() },
                            startIndex = songs.indexOf(song),
                        ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(68.dp)
                    .shadow(
                        elevation = if (isLight) 3.dp else 0.dp,
                        shape = cardShape,
                        clip = false,
                    )
                    .clip(cardShape)
                    .background(FrostSoulTheme.colors.surfaceRaised)
                    .border(
                        1.dp,
                        if (isLight) FrostSoulTheme.colors.outline.copy(alpha = 0.12f)
                        else FrostSoulTheme.colors.outline.copy(alpha = 0.08f),
                        cardShape,
                    )
                    .clickable(onClick = playSong)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AsyncImage(
                        model = song.song.thumbnailUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )

                    val relativeTime = remember(song) {
                        val date = song.song.likedDate ?: song.song.inLibrary
                        if (date != null) {
                            val days = java.time.temporal.ChronoUnit.DAYS.between(date.toLocalDate(), java.time.LocalDate.now())
                            if (days > 0) "Last played $days days ago" else "Rediscover this favorite"
                        } else {
                            "Rediscover this favorite"
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = song.title,
                            color = FrostSoulTheme.colors.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = relativeTime,
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FrostSoulTheme.colors.surfaceGlassStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        FSIcon(
                            painter = painterResource(if (activePlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            tint = FrostSoulTheme.colors.onSurface,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

// =======================================================================
// 9. TRENDING SONGS SHELF
// =======================================================================

@Composable
private fun FrostSoulTrendingSongsShelf(
    items: List<Any>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
) {
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val cardWidth = 144.dp
    val cardShape = RoundedCornerShape(16.dp)

    LazyRow(
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
    ) {
        items(items, key = { item ->
            when (item) {
                is Song -> "trend_song_${item.id}"
                is YTItem -> "trend_yt_${item.id}"
                else -> "trend_${item.hashCode()}"
            }
        }) { item ->
            val (title, artist, artworkUrl, id) = when (item) {
                is Song -> Tuple4(item.title, item.artists.joinToString(", ") { it.name }, item.song.thumbnailUrl, item.id)
                is SongItem -> Tuple4(item.title, item.artists.joinToString(", ") { it.name }, item.thumbnail, item.id)
                is YTItem -> Tuple4(item.title, "", item.thumbnail, item.id)
                else -> Tuple4("", "", null, "")
            }

            val isCurrent = id == mediaMetadata?.id
            val activePlaying = isCurrent && isPlaying

            Column(
                modifier = Modifier
                    .width(cardWidth)
                    .clickable {
                        when (item) {
                            is Song -> {
                                if (isCurrent) playerConnection.player.togglePlayPause()
                                else playerConnection.playQueue(ListQueue(items = listOf(item.toMediaItem())))
                            }
                            is SongItem -> {
                                if (isCurrent) playerConnection.player.togglePlayPause()
                                else {
                                    val endpoint = item.endpoint ?: WatchEndpoint(videoId = item.id)
                                    playerConnection.playQueue(YouTubeQueue(endpoint, item.toMediaMetadata()))
                                }
                            }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .size(cardWidth)
                        .shadow(
                            elevation = if (isLight) 3.dp else 0.dp,
                            shape = cardShape,
                            clip = false,
                        )
                        .clip(cardShape)
                        .background(FrostSoulTheme.colors.surfaceRaised),
                ) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                FSIcon(
                                    painter = painterResource(if (activePlaying) R.drawable.pause else R.drawable.play),
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = title,
                    color = FrostSoulTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        color = FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

// =======================================================================
// CATEGORY CHIPS / TABS
// =======================================================================

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

// =======================================================================
// HELPER EXTENSIONS
// =======================================================================

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
