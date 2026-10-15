/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.widget

import androidx.glance.text.TextAlign
import androidx.glance.layout.fillMaxHeight
import androidx.compose.ui.unit.Dp
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.vxs.frostsoulx.R

class MusicWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            MusicWidgetContent(context)
        }
    }
}

@Composable
private fun MusicWidgetContent(context: Context) {
    val prefs = currentState<Preferences>()
    val state = prefs.toWidgetPlaybackState(context)
    val settings = prefs.toWidgetSettings()

    GlanceTheme(
        colors =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceTheme.colors
            } else {
                ArchiveTuneWidgetColors.providers
            },
    ) {
        val palette = rememberWidgetPalette(state.dominantColor)
        val size = LocalSize.current
        when (size.toMusicWidgetLayout()) {
            MusicWidgetLayout.Compact -> {
                MusicWidgetBar(
                    state = state,
                    settings = settings,
                    palette = palette,
                    context = context,
                    size = size,
                    showFullControls = false,
                    showProgress = false,
                )
            }

            MusicWidgetLayout.Bar -> {
                MusicWidgetBar(
                    state = state,
                    settings = settings,
                    palette = palette,
                    context = context,
                    size = size,
                    showFullControls = true,
                    showProgress =
                        settings.showProgress &&
                            state.isAvailable &&
                            state.playbackPosition > 0f &&
                            size.height >= 78.dp,
                )
            }

            MusicWidgetLayout.Tile -> {
                MusicWidgetTile(state, settings, palette, context, size)
            }

            MusicWidgetLayout.Split -> {
                MusicWidgetSplit(state, settings, palette, context, size)
            }

            MusicWidgetLayout.Hero -> {
                MusicWidgetHero(state, settings, palette, context, size)
            }
        }
    }
}

private val WidgetCornerRadius = 28.dp

private fun minDp(
    a: Dp,
    b: Dp,
): Dp = if (a < b) a else b

private fun maxDp(
    a: Dp,
    b: Dp,
): Dp = if (a > b) a else b

private fun artCorner(
    size: Dp,
    circle: Boolean,
): Dp = if (circle) size / 2 else size * 0.22f

@Composable
private fun MusicWidgetRoot(
    palette: WidgetPalette,
    context: Context,
    padding: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(palette.surface)
                .cornerRadius(WidgetCornerRadius)
                .padding(padding)
                .clickable(openArchiveTuneAction(context)),
    ) {
        content()
    }
}

@Composable
private fun MusicWidgetArt(
    state: WidgetPlaybackState,
    settings: WidgetSettings,
    palette: WidgetPalette,
    context: Context,
    size: Dp,
) {
    WidgetArtwork(
        artPath = state.artPath,
        context = context,
        contentDescription = context.getString(R.string.album_cover_desc),
        targetSize = size,
        cornerRadius = artCorner(size, settings.circleArtwork),
        palette = palette,
        modifier = GlanceModifier.size(size),
    )
}

@Composable
private fun MusicWidgetIdlePill(
    palette: WidgetPalette,
    context: Context,
) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(50.dp)
                .background(palette.secondaryContainer)
                .cornerRadius(25.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_tap_to_open),
            style =
                TextStyle(
                    color = palette.onSecondaryContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
        )
    }
}

/** Short widgets: art scales with the height, controls on the right. */
@Composable
private fun MusicWidgetBar(
    state: WidgetPlaybackState,
    settings: WidgetSettings,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
    showFullControls: Boolean,
    showProgress: Boolean,
) {
    val progressBlock = if (showProgress) 12.dp else 0.dp
    val artSize = minDp(maxDp(size.height - 12.dp - progressBlock, 48.dp), 88.dp)

    MusicWidgetRoot(palette = palette, context = context, padding = 6.dp) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .height(artSize),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MusicWidgetArt(state, settings, palette, context, artSize)

                Spacer(GlanceModifier.width(10.dp))

                MusicWidgetTrackText(
                    state = state,
                    palette = palette,
                    titleSize = if (artSize >= 64.dp) 15 else 14,
                    artistSize = 12,
                    modifier = GlanceModifier.defaultWeight(),
                )

                if (state.isAvailable) {
                    Spacer(GlanceModifier.width(8.dp))
                    MusicWidgetControls(
                        state = state,
                        palette = palette,
                        context = context,
                        showPreviousNext = showFullControls,
                        modifier = GlanceModifier,
                        buttonModifier = GlanceModifier.size(48.dp),
                    )
                }
            }

            if (showProgress) {
                Spacer(GlanceModifier.height(6.dp))
                MusicWidgetProgress(
                    progress = state.playbackPosition,
                    palette = palette,
                )
            }
        }
    }
}

/** Small squares (about 2x2): cover on top, title and a play button underneath. */
@Composable
private fun MusicWidgetTile(
    state: WidgetPlaybackState,
    settings: WidgetSettings,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    val pad = 10.dp
    val artSize = maxDp(minDp(size.width - pad * 2, size.height - pad * 2 - 52.dp), 48.dp)

    MusicWidgetRoot(palette = palette, context = context, padding = pad) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                MusicWidgetArt(state, settings, palette, context, artSize)
            }

            Spacer(GlanceModifier.height(8.dp))

            Row(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MusicWidgetTrackText(
                    state = state,
                    palette = palette,
                    titleSize = 13,
                    artistSize = 11,
                    modifier = GlanceModifier.defaultWeight(),
                )

                if (state.isAvailable) {
                    Spacer(GlanceModifier.width(6.dp))
                    MusicWidgetControls(
                        state = state,
                        palette = palette,
                        context = context,
                        showPreviousNext = false,
                        modifier = GlanceModifier,
                        buttonModifier = GlanceModifier.size(44.dp),
                    )
                }
            }
        }
    }
}

/** Wide and medium height (about 4x2): big cover on the left, info and controls on the right. */
@Composable
private fun MusicWidgetSplit(
    state: WidgetPlaybackState,
    settings: WidgetSettings,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    val pad = 12.dp
    val gap = 14.dp
    val artSize = minDp(minDp(size.height - pad * 2, size.width * 0.42f), 220.dp)
    val rightWidth = size.width - pad * 2 - artSize - gap
    val showProgress = settings.showProgress && state.isAvailable

    MusicWidgetRoot(palette = palette, context = context, padding = pad) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicWidgetArt(state, settings, palette, context, artSize)

            Spacer(GlanceModifier.width(gap))

            Column(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
            ) {
                MusicWidgetTrackText(
                    state = state,
                    palette = palette,
                    titleSize = if (artSize >= 96.dp) 17 else 15,
                    artistSize = 13,
                    modifier = GlanceModifier.fillMaxWidth(),
                )

                Spacer(GlanceModifier.defaultWeight())

                if (!state.isAvailable) {
                    MusicWidgetIdlePill(palette, context)
                } else if (rightWidth >= 190.dp) {
                    WidgetExpressiveControlPill(
                        state = state,
                        palette = palette,
                        context = context,
                    )
                } else {
                    MusicWidgetControls(
                        state = state,
                        palette = palette,
                        context = context,
                        showPreviousNext = rightWidth >= 150.dp,
                        modifier = GlanceModifier,
                        buttonModifier = GlanceModifier.size(44.dp),
                    )
                }

                if (showProgress) {
                    Spacer(GlanceModifier.height(8.dp))
                    MusicWidgetProgress(
                        progress = state.playbackPosition,
                        palette = palette,
                    )
                }
            }
        }
    }
}

/** Tall widgets: the cover grows to fill the free space, with centered text and full controls below. */
@Composable
private fun MusicWidgetHero(
    state: WidgetPlaybackState,
    settings: WidgetSettings,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    val pad = 14.dp
    val showProgress = settings.showProgress && state.isAvailable
    val controlsBlock = if (showProgress) 76.dp else 60.dp
    val textBlock = 52.dp
    val artSize =
        maxDp(
            minDp(size.width - pad * 2, size.height - pad * 2 - controlsBlock - textBlock),
            56.dp,
        )

    MusicWidgetRoot(palette = palette, context = context, padding = pad) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    MusicWidgetArt(state, settings, palette, context, artSize)

                    Spacer(GlanceModifier.height(12.dp))

                    MusicWidgetTrackText(
                        state = state,
                        palette = palette,
                        titleSize = 17,
                        artistSize = 13,
                        modifier = GlanceModifier.fillMaxWidth(),
                        centered = true,
                    )
                }
            }

            Spacer(GlanceModifier.height(10.dp))

            if (state.isAvailable) {
                WidgetExpressiveControlPill(
                    state = state,
                    palette = palette,
                    context = context,
                )
            } else {
                MusicWidgetIdlePill(palette, context)
            }

            if (showProgress) {
                Spacer(GlanceModifier.height(10.dp))
                MusicWidgetProgress(
                    progress = state.playbackPosition,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun MusicWidgetTrackText(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    titleSize: Int,
    artistSize: Int,
    modifier: GlanceModifier,
    centered: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = if (centered) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
    ) {
        Text(
            text = state.title,
            modifier = if (centered) GlanceModifier.fillMaxWidth() else GlanceModifier,
            style =
                TextStyle(
                    color = palette.onSurface,
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (centered) TextAlign.Center else null,
                ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = state.artist,
            modifier = if (centered) GlanceModifier.fillMaxWidth() else GlanceModifier,
            style =
                TextStyle(
                    color = palette.onSurfaceVariant,
                    fontSize = artistSize.sp,
                    textAlign = if (centered) TextAlign.Center else null,
                ),
            maxLines = 1,
        )
    }
}

@Composable
private fun MusicWidgetControls(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    showPreviousNext: Boolean,
    modifier: GlanceModifier,
    buttonModifier: GlanceModifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPreviousNext) {
            WidgetControlButton(
                modifier = buttonModifier,
                action = skipPreviousAction(),
                icon = R.drawable.skip_previous,
                contentDescription = context.getString(R.string.widget_previous),
                backgroundColor = palette.secondaryContainer,
                contentColor = palette.onSecondaryContainer,
                cornerRadius = 22.dp,
            )
            Spacer(GlanceModifier.width(6.dp))
        }

        WidgetControlButton(
            modifier = buttonModifier,
            action = playPauseAction(),
            icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
            contentDescription =
                context.getString(
                    if (state.isPlaying) R.string.widget_pause else R.string.play,
                ),
            backgroundColor = palette.primaryContainer,
            contentColor = palette.onPrimaryContainer,
            cornerRadius = if (state.isPlaying) 13.dp else 24.dp,
            iconSize = 24.dp,
        )

        if (showPreviousNext) {
            Spacer(GlanceModifier.width(6.dp))
            WidgetControlButton(
                modifier = buttonModifier,
                action = skipNextAction(),
                icon = R.drawable.skip_next,
                contentDescription = context.getString(R.string.next),
                backgroundColor = palette.secondaryContainer,
                contentColor = palette.onSecondaryContainer,
                cornerRadius = 22.dp,
            )
        }
    }
}

@Composable
private fun MusicWidgetProgress(
    progress: Float,
    palette: WidgetPalette,
) {
    LinearProgressIndicator(
        progress = progress,
        color = palette.progress,
        backgroundColor = palette.progressTrack,
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(6.dp)
                .cornerRadius(3.dp),
    )
}

private enum class MusicWidgetLayout {
    Compact,
    Bar,
    Tile,
    Split,
    Hero,
}

private fun DpSize.toMusicWidgetLayout(): MusicWidgetLayout =
    when {
        width < height * 1.3f && height >= 210.dp -> MusicWidgetLayout.Hero
        width < height * 1.3f && height >= 110.dp -> MusicWidgetLayout.Tile
        height >= 104.dp -> MusicWidgetLayout.Split
        width < 300.dp -> MusicWidgetLayout.Compact
        else -> MusicWidgetLayout.Bar
    }
