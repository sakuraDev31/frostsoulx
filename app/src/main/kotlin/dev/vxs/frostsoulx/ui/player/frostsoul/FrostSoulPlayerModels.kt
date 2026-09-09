/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.player.frostsoul

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.sp
import dev.vxs.frostsoulx.models.ActiveOutputDevice
import dev.vxs.frostsoulx.models.PlayerOutputDevice
import androidx.compose.ui.graphics.Color
import dev.vxs.frostsoulx.models.MediaMetadata
import dev.vxs.frostsoulx.constants.PlayerBackgroundStyle
import dev.vxs.frostsoulx.lyrics.core.LyricsLine

internal val FrostSoulSurface = Color(0xFF1E1E1E)
internal val FrostSoulSurfaceElevated = Color(0xFF242424)
internal val FrostSoulOnSurface = Color(0xFFFDFDFD)
internal val FrostSoulOnSurfaceMuted = Color(0xFFA5A5A5)

@Immutable
internal data class FrostSoulPalette(
    val artworkPrimary: Color = Color(0xFF8A8A8A),
    val artworkSecondary: Color = Color(0xFF30262B),
    val accent: Color = Color.White,
) {
    companion object {
        val Default = FrostSoulPalette()
    }
}

@Immutable
internal data class FrostSoulArtist(
    val id: String? = null,
    val name: String,
    val artworkUrl: String? = null,
)

@Immutable
internal data class FrostSoulTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artists: List<FrostSoulArtist> = emptyList(),
    val album: String,
    val albumId: String? = null,
    val artworkUrl: String?,
    val durationMs: Long,
    val isLiked: Boolean,
) {
    companion object {
        fun from(metadata: MediaMetadata, isLiked: Boolean = metadata.liked): FrostSoulTrack =
            FrostSoulTrack(
                id = metadata.id,
                title = metadata.title.ifBlank { "Unknown track" },
                artist = metadata.artists.joinToString(separator = " • ") { it.name }.ifBlank { "Unknown artist" },
                artists = metadata.artists.map { FrostSoulArtist(id = it.id, name = it.name, artworkUrl = it.thumbnailUrl) },
                album = metadata.album?.title.orEmpty(),
                albumId = metadata.album?.id,
                artworkUrl = metadata.thumbnailUrl,
                durationMs = metadata.duration.coerceAtLeast(0).toLong() * 1_000L,
                isLiked = isLiked,
            )
    }
}

@Immutable
internal data class FrostSoulQueueItem(
    val index: Int,
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
    val isCurrent: Boolean,
)

@Immutable
internal data class FrostSoulPlayerUiState(
    val track: FrostSoulTrack,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val queueTitle: String?,
    val queue: List<FrostSoulQueueItem>,
    val lyrics: String?,
    val currentLyricLine: String? = null,
    val nextLyricLine: String? = null,
    val currentLyricModel: LyricsLine? = null,
    val currentWordIndex: Int = -1,
    val currentWordProgress: Float = 0f,
    val currentLineProgress: Float = 0f,
    val lyricPreviewLines: List<String> = emptyList(),
    val audioQualityBadge: String? = null,
    val audioTechnicalInfo: String? = null,
    val outputDevice: ActiveOutputDevice = ActiveOutputDevice(
        type = PlayerOutputDevice.Unknown,
        name = PlayerOutputDevice.Unknown.defaultName,
    ),
    val downloadProgress: Float? = null,
    val sleepTimerActive: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val repeatMode: Int = 0,
    val blurRadius: Float = 48f,
    val palette: FrostSoulPalette = FrostSoulPalette.Default,
    val playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.GLOW_ANIMATED,
) {
    val safeDurationMs: Long
        get() = durationMs.takeIf { it > 0L } ?: track.durationMs

    val progress: Float
        get() =
            if (safeDurationMs <= 0L) {
                0f
            } else {
                (positionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
            }
}

@Immutable
internal data class FrostSoulPlayerActions(
    val onDismiss: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onSkipNext: () -> Unit,
    val onToggleRepeat: () -> Unit = {},
    val onSeek: (Long) -> Unit,
    val onToggleLike: () -> Unit,
    val onToggleDislike: () -> Unit = {},
    val onOpenAudioOutput: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onOpenSleepTimer: () -> Unit = {},
    val onOpenOptions: () -> Unit = {},
    val onOpenAlbum: () -> Unit = {},
    val onRefetchLyrics: () -> Unit = {},
    val isRefetchingLyrics: Boolean = false,
    val onSelectQueueItem: (Int) -> Unit,
)

internal enum class FrostSoulPage {
    Lyrics,
    MainPlayer,
    Recommendations,
}

internal fun Long.asFrostSoulTime(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1_000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
