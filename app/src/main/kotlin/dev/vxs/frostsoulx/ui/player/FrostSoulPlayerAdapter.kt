/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.player

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import dev.vxs.frostsoulx.LocalDownloadUtil
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vxs.frostsoulx.utils.oem.SystemMediaControlResolver
import dev.vxs.frostsoulx.viewmodels.LyricsMenuViewModel
import dagger.hilt.android.EntryPointAccessors
import dev.vxs.frostsoulx.db.entities.codecLabel
import dev.vxs.frostsoulx.db.entities.containerLabel
import dev.vxs.frostsoulx.db.entities.formattedBitrate
import dev.vxs.frostsoulx.db.entities.formattedSampleRate
import dev.vxs.frostsoulx.di.LyricsHelperEntryPoint
import androidx.media3.common.Timeline
import dev.vxs.frostsoulx.extensions.mediaItems
import dev.vxs.frostsoulx.extensions.metadata
import dev.vxs.frostsoulx.extensions.togglePlayPause
import dev.vxs.frostsoulx.extensions.toggleRepeatMode
import dev.vxs.frostsoulx.models.MediaMetadata
import dev.vxs.frostsoulx.constants.PlayerBackgroundStyle
import dev.vxs.frostsoulx.constants.PlayerDesignStyle
import dev.vxs.frostsoulx.playback.PlayerConnection
import dev.vxs.frostsoulx.ui.player.frostsoul.FrostSoulPlayer
import dev.vxs.frostsoulx.ui.player.frostsoul.FrostSoulPlayerActions
import dev.vxs.frostsoulx.ui.player.frostsoul.FrostSoulPlayerUiState
import dev.vxs.frostsoulx.ui.player.frostsoul.FrostSoulQueueItem
import dev.vxs.frostsoulx.ui.player.frostsoul.FrostSoulTrack
import dev.vxs.frostsoulx.ui.player.frostsoul.rememberFrostSoulPalette
import dev.vxs.frostsoulx.ui.utils.HeaderDownloadItem
import dev.vxs.frostsoulx.ui.utils.sendAddMissingDownloads

@Composable
internal fun FrostSoulPlayerAdapter(
    mediaMetadata: MediaMetadata,
    playerDesignStyle: PlayerDesignStyle = PlayerDesignStyle.FROSTSOUL,
    playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.GLOW_ANIMATED,
    blurRadius: Float = 48f,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    isLiked: Boolean,
    queueTitle: String?,
    queueWindows: List<Timeline.Window>,
    currentQueueIndex: Int,
    lyrics: String?,
    playerConnection: PlayerConnection,
    onCollapse: () -> Unit,
    onOpenOptions: () -> Unit = {},
    sleepTimerActive: Boolean = false,
    sleepTimerRemainingMs: Long = 0L,
    onOpenSleepTimer: () -> Unit = {},
    onSearchTrack: () -> Unit = {},
    onOpenAlbum: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = rememberFrostSoulPalette(mediaMetadata.thumbnailUrl)
    val lyricsMenuViewModel: LyricsMenuViewModel = hiltViewModel()
    val isRefetchingLyrics by lyricsMenuViewModel.isRefetching.collectAsStateWithLifecycle()
    val downloadUtil = LocalDownloadUtil.current
    val downloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val downloadProgress =
        downloads[mediaMetadata.id]?.let { download ->
            when (download.state) {
                Download.STATE_COMPLETED -> 1f
                Download.STATE_QUEUED,
                Download.STATE_DOWNLOADING,
                Download.STATE_RESTARTING,
                -> download.percentDownloaded.takeIf { it >= 0f }?.div(100f) ?: 0f
                else -> null
            }
        }
    val applicationContext = LocalContext.current.applicationContext
    val lyricsSynchronizationEngine =
        remember(applicationContext) {
            EntryPointAccessors
                .fromApplication(applicationContext, LyricsHelperEntryPoint::class.java)
                .lyricsSynchronizationEngine()
        }
    val currentLyricLine by lyricsSynchronizationEngine.currentLine.collectAsState()
    val lyricSyncState by lyricsSynchronizationEngine.state.collectAsState()
    val lyricsDocument by lyricsSynchronizationEngine.documentState.collectAsState()
    val currentLyricText = currentLyricLine?.text?.trim()
    val nextLyricText = lyricSyncState.nextLine?.text?.trim()
    val lyricPreviewLines = remember(lyricsDocument, lyricSyncState.currentLineIndex) {
        val lines = lyricsDocument?.original?.lines.orEmpty()
        val currentIndex = lyricSyncState.currentLineIndex
        if (currentIndex < 0) {
            emptyList()
        } else {
            lines.drop(currentIndex)
                .mapNotNull { line -> line.text.trim().takeIf { it.isNotBlank() } }
                .take(4)
        }
    }
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val outputDevice by playerConnection.service.activeAudioDevice.collectAsStateWithLifecycle()
    val audioQualityBadge =
        remember(currentFormat) {
            currentFormat?.containerLabel()?.uppercase()?.takeIf { it.isNotBlank() }
        }
    val audioTechnicalInfo =
        remember(currentFormat) {
            currentFormat?.let { format ->
                listOfNotNull(
                    format.formattedSampleRate(),
                    format.formattedBitrate(),
                    format.codecLabel().takeIf { it.isNotBlank() },
                ).joinToString(separator = "  ").takeIf { it.isNotBlank() }
            }
        }
    val queue =
        remember(queueWindows, currentQueueIndex) {
            queueWindows.mapIndexedNotNull { index, window ->
                val item = window.mediaItem.metadata ?: return@mapIndexedNotNull null
                FrostSoulQueueItem(
                    index = index,
                    id = item.id,
                    title = item.title,
                    artist = item.artists.joinToString(separator = " • ") { it.name }.ifBlank { "Unknown artist" },
                    artworkUrl = item.thumbnailUrl,
                    albumId = item.album?.id,
                    albumTitle = item.album?.title,
                    durationMs = item.duration.coerceAtLeast(0).toLong() * 1_000L,
                    isCurrent = index == currentQueueIndex,
                )
            }
        }
    val uiState =
        remember(
            mediaMetadata,
            positionMs,
            durationMs,
            isPlaying,
            isLoading,
            canSkipPrevious,
            canSkipNext,
            isLiked,
            queueTitle,
            queue,
            lyrics,
            currentLyricText,
            nextLyricText,
            currentLyricLine,
            lyricSyncState.currentWordIndex,
            lyricSyncState.wordProgress,
            lyricSyncState.lineProgress,
            lyricPreviewLines,
            audioQualityBadge,
            audioTechnicalInfo,
            outputDevice,
            downloadProgress,
            isRefetchingLyrics,
            sleepTimerActive,
            sleepTimerRemainingMs,
            repeatMode,
            blurRadius,
            palette,
            playerBackgroundStyle,
        ) {
            FrostSoulPlayerUiState(
                track = FrostSoulTrack.from(mediaMetadata, isLiked),
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                isBuffering = isLoading,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                queueTitle = queueTitle,
                queue = queue,
                lyrics = lyrics,
                currentLyricLine = currentLyricText,
                nextLyricLine = nextLyricText,
                currentLyricModel = currentLyricLine,
                currentWordIndex = lyricSyncState.currentWordIndex,
                currentWordProgress = lyricSyncState.wordProgress,
                currentLineProgress = lyricSyncState.lineProgress,
                lyricPreviewLines = lyricPreviewLines,
                audioQualityBadge = audioQualityBadge,
                audioTechnicalInfo = audioTechnicalInfo,
                outputDevice = outputDevice,
                downloadProgress = downloadProgress,
                sleepTimerActive = sleepTimerActive,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                repeatMode = repeatMode,
                blurRadius = blurRadius,
                palette = palette,
                playerBackgroundStyle = playerBackgroundStyle,
            )
        }
    val actions =
        remember(playerConnection, queueWindows, onCollapse, applicationContext, mediaMetadata, isRefetchingLyrics, downloads, onOpenSleepTimer) {
            FrostSoulPlayerActions(
                onDismiss = onCollapse,
                onTogglePlayPause = { playerConnection.player.togglePlayPause() },
                onSkipPrevious = playerConnection::seekToPrevious,
                onSkipNext = playerConnection::seekToNext,
                onToggleRepeat = { playerConnection.player.toggleRepeatMode() },
                onSeek = { targetPosition -> playerConnection.player.seekTo(targetPosition) },
                onToggleLike = playerConnection::toggleLike,
                onToggleDislike = playerConnection::toggleDislike,
                onOpenAudioOutput = {
                    SystemMediaControlResolver.openMediaOutputSwitcher(applicationContext)
                },
                onDownload = {
                    sendAddMissingDownloads(
                        context = applicationContext,
                        songs = listOf(HeaderDownloadItem(id = mediaMetadata.id, title = mediaMetadata.title)),
                        downloads = downloads,
                    )
                },
                onOpenOptions = onOpenOptions,
                onOpenSleepTimer = onOpenSleepTimer,
                onOpenAlbum = onOpenAlbum,
                onRefetchLyrics = { lyricsMenuViewModel.refetchLyrics(mediaMetadata) },
                                isRefetchingLyrics = isRefetchingLyrics,
                onSelectQueueItem = { queueIndex ->

                    val targetWindow = queueWindows.getOrNull(queueIndex)
                    if (targetWindow != null) {
                        val targetMediaId = targetWindow.mediaItem.mediaId
                        val mediaIndex = playerConnection.player.mediaItems.indexOfFirst { it.mediaId == targetMediaId }
                        if (mediaIndex >= 0) {
                            playerConnection.player.seekToDefaultPosition(mediaIndex)
                            playerConnection.player.prepare()
                            playerConnection.player.play()
                        }
                    }
                },
            )
        }

    FrostSoulPlayer(
        uiState = uiState,
        actions = actions,
        playerDesignStyle = playerDesignStyle,
        onSearchTrack = onSearchTrack,
        onOpenArtist = onOpenArtist,
        modifier = modifier,
    )
}
