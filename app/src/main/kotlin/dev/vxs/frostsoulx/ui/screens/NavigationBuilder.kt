/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens

import dev.vxs.frostsoulx.ui.screens.settings.FrostSoulSettingsPage
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.vxs.frostsoulx.BuildConfig
import dev.vxs.frostsoulx.constants.UpdateChannel
import dev.vxs.frostsoulx.defaultUpdateChannel
import dev.vxs.frostsoulx.musicrecognition.MusicRecognitionRoute
import dev.vxs.frostsoulx.musicrecognition.MusicRecognitionDetailsRoute
import dev.vxs.frostsoulx.ui.screens.BrowseScreen
import dev.vxs.frostsoulx.ui.screens.artist.ArtistAlbumsScreen
import dev.vxs.frostsoulx.ui.screens.artist.ArtistItemsScreen
import dev.vxs.frostsoulx.ui.screens.artist.ArtistScreen
import dev.vxs.frostsoulx.ui.screens.artist.ArtistSongsScreen
import dev.vxs.frostsoulx.ui.screens.library.LibraryScreen
import dev.vxs.frostsoulx.ui.screens.library.LocalSongScreen
import dev.vxs.frostsoulx.ui.screens.musicrecognition.MusicRecognitionScreen
import dev.vxs.frostsoulx.ui.screens.musicrecognition.MusicRecognitionDetailsScreen
import dev.vxs.frostsoulx.ui.screens.playlist.AutoPlaylistScreen
import dev.vxs.frostsoulx.ui.screens.playlist.CachePlaylistScreen
import dev.vxs.frostsoulx.ui.screens.playlist.LocalPlaylistScreen
import dev.vxs.frostsoulx.ui.screens.playlist.OnlinePlaylistScreen
import dev.vxs.frostsoulx.ui.screens.playlist.SpotifyPlaylistScreen
import dev.vxs.frostsoulx.ui.screens.playlist.TopPlaylistScreen
import dev.vxs.frostsoulx.ui.screens.search.OnlineSearchResult
import dev.vxs.frostsoulx.ui.screens.search.OnlineSearchResultArgument
import dev.vxs.frostsoulx.ui.screens.search.OnlineSearchResultRoute
import dev.vxs.frostsoulx.ui.screens.search.OnlineSearchResultRoutePrefix
import dev.vxs.frostsoulx.ui.screens.search.SearchScreen
import dev.vxs.frostsoulx.ui.screens.settings.AboutScreen
import dev.vxs.frostsoulx.ui.screens.settings.AccountSettings
import dev.vxs.frostsoulx.ui.screens.settings.AiIntegrationSettings
import dev.vxs.frostsoulx.ui.screens.settings.AppearanceSettings
import dev.vxs.frostsoulx.ui.screens.settings.BackupAndRestore
import dev.vxs.frostsoulx.ui.screens.settings.ChangelogScreen
import dev.vxs.frostsoulx.ui.screens.settings.ChiperSettings
import dev.vxs.frostsoulx.ui.screens.settings.ContentSettings
import dev.vxs.frostsoulx.ui.screens.settings.CustomizeBackground
import dev.vxs.frostsoulx.ui.screens.settings.DebugSettings
import dev.vxs.frostsoulx.ui.screens.settings.DiscordSettings
import dev.vxs.frostsoulx.ui.screens.settings.HiddenPlaylistsScreen
import dev.vxs.frostsoulx.ui.screens.settings.IconScreen
import dev.vxs.frostsoulx.ui.screens.settings.IntegrationScreen
import dev.vxs.frostsoulx.ui.screens.settings.InternetSettings
import dev.vxs.frostsoulx.ui.screens.settings.LastFMSettings
import dev.vxs.frostsoulx.ui.screens.settings.LogcatScreen
import dev.vxs.frostsoulx.ui.screens.settings.LyricsAnimationSettings
import dev.vxs.frostsoulx.ui.screens.settings.LyricsSettings
import dev.vxs.frostsoulx.ui.screens.settings.MusicTogetherScreen
import dev.vxs.frostsoulx.ui.screens.settings.PO_TOKEN_ROUTE
import dev.vxs.frostsoulx.ui.screens.settings.PlayerSettings
import dev.vxs.frostsoulx.ui.screens.settings.PoTokenScreen
import dev.vxs.frostsoulx.ui.screens.settings.PrivacySettings
import dev.vxs.frostsoulx.ui.screens.settings.SettingsScreen
import dev.vxs.frostsoulx.ui.screens.settings.StorageSettings
import dev.vxs.frostsoulx.ui.screens.settings.StereoSurroundScreen
import dev.vxs.frostsoulx.ui.screens.settings.StereoSurroundTuningScreen
import dev.vxs.frostsoulx.ui.screens.settings.UpdateScreen
import dev.vxs.frostsoulx.viewmodels.OnlineSearchSort

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: () -> String,
    disableAnimations: Boolean = false,
    onClearUpdateBadge: () -> Unit = {},
    homeScrollConnection: NestedScrollConnection? = null,
    searchScrollConnection: NestedScrollConnection? = null,
    onlineSearchSort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
) {
    composable(Screens.Home.route) {
        HomeScreen(navController, headerScrollConnection = homeScrollConnection)
    }
    composable(
        Screens.Library.route,
    ) {
        LibraryScreen(navController)
    }
    composable(Screens.Search.route) {
        SearchScreen(
            navController = navController,
            onSearchClick = {
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.set("openSearch", true)
            },
            headerScrollConnection = searchScrollConnection,
        )
    }
    composable("local_songs") {
        LocalSongScreen(navController)
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable(
        route = "home_collection/{kind}",
        arguments = listOf(navArgument("kind") { type = NavType.StringType }),
    ) { backStackEntry ->
        FrostSoulHomeCollectionScreen(
            navController = navController,
            kind = backStackEntry.arguments?.getString("kind").orEmpty(),
        )
    }
    composable("stats") {
        StatsScreen(navController)
    }
    composable(
        route = "year_in_music?year={year}",
        arguments =
            listOf(
                navArgument("year") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
    ) { backStackEntry ->
        val selectedYear = backStackEntry.arguments?.getInt("year")?.takeIf { it > 0 }
        YearInMusicScreen(
            navController = navController,
            initialYear = selectedYear,
        )
    }
    composable(MusicRecognitionRoute) {
        MusicRecognitionScreen(navController)
    }
    composable(MusicRecognitionDetailsRoute) { backStackEntry ->
        val encodedTrack = backStackEntry.arguments?.getString("encodedTrack").orEmpty()
        MusicRecognitionDetailsScreen(navController, encodedTrack)
    }
    composable(Screens.MoodAndGenres.route) {
        MoodAndGenresScreen(navController)
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("charts_screen") {
        ChartsScreen(navController)
    }
    composable(
        route = "browse/{browseId}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
            ),
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId"),
        )
    }
    composable(
        route = OnlineSearchResultRoute,
        arguments =
            listOf(
                navArgument(OnlineSearchResultArgument) {
                    type = NavType.StringType
                },
            ),
        enterTransition = {
            if (disableAnimations) {
                fadeIn(tween(0))
            } else {
                fadeIn(tween(250))
            }
        },
        exitTransition = {
            if (disableAnimations) {
                fadeOut(tween(0))
            } else if (targetState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (disableAnimations) {
                fadeIn(tween(0))
            } else if (initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            if (disableAnimations) {
                fadeOut(tween(0))
            } else {
                fadeOut(tween(200))
            }
        },
    ) {
        OnlineSearchResult(
            navController = navController,
            searchSort = onlineSearchSort,
        )
    }
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "spotify_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        SpotifyPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "auto_playlist/{playlist}?tab={tab}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "downloaded"
                },
            ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }
    composable("settings") {
        FrostSoulSettingsPage { SettingsScreen(navController, latestVersionName()) }
    }
    composable("settings/account") {
        FrostSoulSettingsPage { AccountSettings(navController, latestVersionName()) }
    }
    composable("settings/hidden_playlists") {
        FrostSoulSettingsPage { HiddenPlaylistsScreen(navController) }
    }
    composable("settings/appearance") {
        FrostSoulSettingsPage { AppearanceSettings(navController) }
    }
    composable("settings/appearance/icon") {
        FrostSoulSettingsPage { IconScreen(navController) }
    }
    composable("settings/appearance/lyrics_animations") {
        FrostSoulSettingsPage { LyricsAnimationSettings(navController) }
    }
    composable("settings/content") {
        FrostSoulSettingsPage { ContentSettings(navController) }
    }
    composable("settings/lyrics") {
        FrostSoulSettingsPage { LyricsSettings(navController) }
    }
    composable("settings/internet") {
        FrostSoulSettingsPage { InternetSettings(navController) }
    }
    composable("settings/player") {
        FrostSoulSettingsPage { PlayerSettings(navController) }
    }
    composable("settings/player/chiper") {
        FrostSoulSettingsPage { ChiperSettings(navController) }
    }
    composable("settings/storage") {
        FrostSoulSettingsPage { StorageSettings(navController) }
    }
    composable("settings/privacy") {
        FrostSoulSettingsPage { PrivacySettings(navController) }
    }
    composable("settings/backup_restore") {
        FrostSoulSettingsPage { BackupAndRestore(navController) }
    }
    composable("settings/discord") {
        FrostSoulSettingsPage { DiscordSettings(navController) }
    }
    composable("settings/integration") {
        FrostSoulSettingsPage { IntegrationScreen(navController) }
    }
    composable("settings/ai_integration") {
        FrostSoulSettingsPage { AiIntegrationSettings(navController) }
    }
    composable("settings/music_together") {
        FrostSoulSettingsPage { MusicTogetherScreen(navController) }
    }
    composable("settings/surround") {
        FrostSoulSettingsPage { StereoSurroundScreen(navController) }
    }
    composable("settings/misc/surround_tuning") {
        FrostSoulSettingsPage { StereoSurroundTuningScreen(navController) }
    }
    composable("settings/lastfm") {
        FrostSoulSettingsPage { LastFMSettings(navController) }
    }
    composable("settings/discord/experimental") {
        FrostSoulSettingsPage {
            dev.vxs.frostsoulx.ui.screens.settings.DiscordExperimental(navController)
        }
    }
    composable("settings/misc") {
        FrostSoulSettingsPage { DebugSettings(navController) }
    }
    composable("settings/logcat") {
        FrostSoulSettingsPage { LogcatScreen(navController) }
    }
    if (BuildConfig.UPDATER_AVAILABLE) {
        composable("settings/update") {
            FrostSoulSettingsPage { UpdateScreen(navController, onUpToDate = onClearUpdateBadge) }
        }
    }
    composable(
        route = "settings/changelog?channel={channel}",
        arguments =
            listOf(
                navArgument("channel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val channelName = backStackEntry.arguments?.getString("channel")
        val channel = UpdateChannel.fromStoredName(channelName, defaultUpdateChannel)
        FrostSoulSettingsPage { ChangelogScreen(navController, channel = channel) }
    }
    composable("settings/about") {
        FrostSoulSettingsPage { AboutScreen(navController) }
    }
    composable(PO_TOKEN_ROUTE) {
        FrostSoulSettingsPage { PoTokenScreen(navController) }
    }
    composable("customize_background") {
        FrostSoulSettingsPage { CustomizeBackground(navController) }
    }
    composable(
        route = "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT={$LOGIN_URL_ARGUMENT}",
        arguments =
            listOf(
                navArgument(LOGIN_URL_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        LoginScreen(
            navController,
            startUrl = backStackEntry.arguments?.getString(LOGIN_URL_ARGUMENT)?.let(Uri::decode),
        )
    }
}
