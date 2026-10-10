/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import dev.vxs.frostsoulx.MainActivity
import dev.vxs.frostsoulx.R
import java.io.File

@Immutable
internal data class WidgetPlaybackState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val artPath: String?,
    val isAvailable: Boolean,
    val dominantColor: Int?,
    val playbackPosition: Float,
)

@Immutable
internal data class WidgetPalette(
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val primaryContainer: ColorProvider,
    val onPrimaryContainer: ColorProvider,
    val secondaryContainer: ColorProvider,
    val onSecondaryContainer: ColorProvider,
    val progress: ColorProvider,
    val progressTrack: ColorProvider,
    val artworkFallback: ColorProvider,
)

internal fun Preferences.toWidgetPlaybackState(context: Context): WidgetPlaybackState {
    val isAvailable = this[MusicWidgetKeys.IS_AVAILABLE] ?: false
    val rawTitle = this[MusicWidgetKeys.TRACK_TITLE].orEmpty()
    val rawArtist = this[MusicWidgetKeys.TRACK_ARTIST].orEmpty()

    return WidgetPlaybackState(
        title =
            rawTitle.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.no_track_playing),
        artist =
            when {
                !isAvailable -> context.getString(R.string.widget_tap_to_open)
                rawArtist.isNotBlank() -> rawArtist
                else -> context.getString(R.string.unknown_artist)
            },
        isPlaying = this[MusicWidgetKeys.IS_PLAYING] ?: false,
        artPath = this[MusicWidgetKeys.ART_PATH],
        isAvailable = isAvailable,
        dominantColor = this[MusicWidgetKeys.DOMINANT_COLOR],
        playbackPosition = (this[MusicWidgetKeys.PLAYBACK_POSITION] ?: 0f).coerceIn(0f, 1f),
    )
}

@Composable
internal fun rememberWidgetPalette(dominantColor: Int?): WidgetPalette {
    if (dominantColor == null) return FrostSoulNeutralPalette
    return remember(dominantColor) { frostSoulArtworkPalette(Color(dominantColor)) }
}

/**
 * Idle / no-artwork look. Mirrors the app's calm monochrome language:
 * near-black surface, white accent, quiet translucent chips. Follows system day/night.
 */
private val FrostSoulNeutralPalette =
    WidgetPalette(
        surface = ColorProvider(Color(0xF2F7F8FA), Color(0xF20B0B0B)),
        onSurface = ColorProvider(Color(0xFF111111), Color(0xFFF5F5F5)),
        onSurfaceVariant = ColorProvider(Color(0xFF666666), Color(0xFFA8A8A8)),
        primaryContainer = ColorProvider(Color(0xFF111111), Color(0xFFF5F5F5)),
        onPrimaryContainer = ColorProvider(Color(0xFFFFFFFF), Color(0xFF0B0B0B)),
        secondaryContainer = ColorProvider(Color(0x0F000000), Color(0x14FFFFFF)),
        onSecondaryContainer = ColorProvider(Color(0xFF111111), Color(0xFFF5F5F5)),
        progress = ColorProvider(Color(0xFF111111), Color(0xFFF5F5F5)),
        progressTrack = ColorProvider(Color(0x1F000000), Color(0x29FFFFFF)),
        artworkFallback = ColorProvider(Color(0xFFECEFF3), Color(0xFF151515)),
    )

/**
 * Artwork look: always dark (matches the app's player pages), with the artwork's
 * dominant color only deeply tinting the surface. Glass is kept subtle: a slightly
 * see-through surface and faint white chips, no heavy frosted layers.
 */
private fun frostSoulArtworkPalette(dominant: Color): WidgetPalette {
    val surface = dominant.blendWith(Color(0xFF0B0B0B), 0.76f).copy(alpha = 0.95f)
    val ink = Color(0xFFF5F5F5)
    val onAccent = dominant.blendWith(Color.Black, 0.84f).copy(alpha = 1f)

    return WidgetPalette(
        surface = ColorProvider(surface),
        onSurface = ColorProvider(ink),
        onSurfaceVariant = ColorProvider(ink.copy(alpha = 0.66f)),
        primaryContainer = ColorProvider(ink),
        onPrimaryContainer = ColorProvider(onAccent),
        secondaryContainer = ColorProvider(Color.White.copy(alpha = 0.09f)),
        onSecondaryContainer = ColorProvider(ink),
        progress = ColorProvider(ink),
        progressTrack = ColorProvider(Color.White.copy(alpha = 0.18f)),
        artworkFallback = ColorProvider(Color.White.copy(alpha = 0.08f)),
    )
}

@Composable
internal fun WidgetArtwork(
    artPath: String?,
    context: Context,
    contentDescription: String,
    targetSize: Dp,
    cornerRadius: Dp,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fallbackIconSize: Dp = targetSize * 0.54f,
) {
    val bitmap =
        remember(artPath, targetSize) {
            artPath?.let { WidgetArtworkCache.decode(it, context, targetSize) }
        }

    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(cornerRadius),
            )
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(cornerRadius)
                        .background(palette.artworkFallback),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.music_note),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(palette.onSurfaceVariant),
                    modifier = GlanceModifier.size(fallbackIconSize),
                )
            }
        }
    }
}

@Composable
internal fun WidgetControlButton(
    modifier: GlanceModifier,
    action: Action,
    @DrawableRes icon: Int,
    contentDescription: String,
    backgroundColor: ColorProvider,
    contentColor: ColorProvider,
    cornerRadius: Dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier =
            modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = GlanceModifier.size(iconSize),
        )
    }
}

internal fun openArchiveTuneAction(context: Context): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )

internal fun playPauseAction(): Action = actionRunCallback<PlayPauseAction>()

internal fun skipNextAction(): Action = actionRunCallback<SkipNextAction>()

internal fun skipPreviousAction(): Action = actionRunCallback<SkipPrevAction>()

internal object ArchiveTuneWidgetColors {
    val providers =
        ColorProviders(
            light =
                lightColorScheme(
                    primary = Color(0xFF111111),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFECEFF3),
                    onPrimaryContainer = Color(0xFF111111),
                    secondary = Color(0xFF555555),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFECEFF3),
                    onSecondaryContainer = Color(0xFF111111),
                    surface = Color(0xFFF7F8FA),
                    onSurface = Color(0xFF111111),
                    onSurfaceVariant = Color(0xFF666666),
                    surfaceVariant = Color(0xFFECEFF3),
                ),
            dark =
                darkColorScheme(
                    primary = Color(0xFFF5F5F5),
                    onPrimary = Color(0xFF0B0B0B),
                    primaryContainer = Color(0xFF292929),
                    onPrimaryContainer = Color(0xFFF5F5F5),
                    secondary = Color(0xFFB6B6B6),
                    onSecondary = Color(0xFF0B0B0B),
                    secondaryContainer = Color(0xFF151515),
                    onSecondaryContainer = Color(0xFFF5F5F5),
                    surface = Color(0xFF0B0B0B),
                    onSurface = Color(0xFFF5F5F5),
                    onSurfaceVariant = Color(0xFF9A9A9A),
                    surfaceVariant = Color(0xFF151515),
                ),
        )
}

@Composable
internal fun WidgetExpressiveControlPill(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(palette.secondaryContainer)
                .cornerRadius(25.dp),
    ) {
        Row(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetControlButton(
                modifier = GlanceModifier.size(44.dp),
                action = skipPreviousAction(),
                icon = R.drawable.skip_previous,
                contentDescription = context.getString(R.string.widget_previous),
                backgroundColor = ColorProvider(Color.Transparent),
                contentColor = palette.onSecondaryContainer,
                cornerRadius = 22.dp,
            )
            WidgetControlButton(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .height(44.dp),
                action = playPauseAction(),
                icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                contentDescription =
                    context.getString(
                        if (state.isPlaying) R.string.widget_pause else R.string.play,
                    ),
                backgroundColor = palette.primaryContainer,
                contentColor = palette.onPrimaryContainer,
                cornerRadius = if (state.isPlaying) 13.dp else 22.dp,
                iconSize = 26.dp,
            )
            WidgetControlButton(
                modifier = GlanceModifier.size(44.dp),
                action = skipNextAction(),
                icon = R.drawable.skip_next,
                contentDescription = context.getString(R.string.next),
                backgroundColor = ColorProvider(Color.Transparent),
                contentColor = palette.onSecondaryContainer,
                cornerRadius = 22.dp,
            )
        }
    }
}

private object WidgetArtworkCache {
    private const val CacheSizeBytes = 4 * 1024 * 1024

    private val cache =
        object : LruCache<String, Bitmap>(CacheSizeBytes) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    fun decode(
        path: String,
        context: Context,
        targetSize: Dp,
    ): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val targetPx =
            (targetSize.value * context.resources.displayMetrics.density)
                .toInt()
                .coerceAtLeast(64)
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:$targetPx"
        cache.get(cacheKey)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, targetPx, targetPx)
            }
        return BitmapFactory.decodeFile(file.absolutePath, options)?.also {
            cache.put(cacheKey, it)
        }
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    requestedWidth: Int,
    requestedHeight: Int,
): Int {
    var sampleSize = 1
    val width = options.outWidth
    val height = options.outHeight

    if (height > requestedHeight || width > requestedWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / sampleSize >= requestedHeight && halfWidth / sampleSize >= requestedWidth) {
            sampleSize *= 2
        }
    }

    return sampleSize.coerceAtLeast(1)
}

private fun Color.blendWith(
    other: Color,
    fraction: Float,
): Color {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    val inverse = 1f - clampedFraction
    return Color(
        red = red * inverse + other.red * clampedFraction,
        green = green * inverse + other.green * clampedFraction,
        blue = blue * inverse + other.blue * clampedFraction,
        alpha = alpha * inverse + other.alpha * clampedFraction,
    )
}
