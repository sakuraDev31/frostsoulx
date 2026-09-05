/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.player.frostsoul
import dev.vxs.frostsoulx.ui.utils.formatLikeCount

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.vxs.frostsoulx.ui.frostsoul.FSIcon as Icon
import dev.vxs.frostsoulx.ui.frostsoul.FSText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.PlayerBackgroundStyle
import dev.vxs.frostsoulx.constants.PlayerDesignStyle
import dev.vxs.frostsoulx.lyrics.core.LyricsLine
import dev.vxs.frostsoulx.innertube.YouTube
import dev.vxs.frostsoulx.ui.frostsoul.FSButton
import dev.vxs.frostsoulx.ui.frostsoul.MinimalistMetadataChip
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

@Composable
internal fun FrostSoulPlayer(
    uiState: FrostSoulPlayerUiState,
    actions: FrostSoulPlayerActions,
    playerDesignStyle: dev.vxs.frostsoulx.constants.PlayerDesignStyle = dev.vxs.frostsoulx.constants.PlayerDesignStyle.FROSTSOUL,
    onSearchTrack: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // QQ-style pager: Recommendations stay on the left, Main Player in the center, Lyrics on the right.
    val pages = remember { listOf(FrostSoulPage.Recommendations, FrostSoulPage.MainPlayer, FrostSoulPage.Lyrics) }
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var queueVisible by remember { mutableStateOf(false) }
    var showArtistDialog by remember(uiState.track.id) { mutableStateOf(false) }
    var showPagerDots by remember { mutableStateOf(true) }
    // While the seekbar is being dragged, the pager's own horizontal-swipe gesture must not
    // compete with it — otherwise a horizontal drag on the seekbar can get interpreted as a
    // page-change swipe instead of a seek. Disabling userScrollEnabled for the duration of the
    // drag is the reliable fix (plain pointerInput consumption on the seekbar alone doesn't
    // reliably win against the pager's own scrollable gesture detection).
    var isSeekbarDragging by remember { mutableStateOf(false) }
    var downwardDragDistance by remember { mutableFloatStateOf(0f) }
    val settledDragOffset by animateFloatAsState(
        targetValue = downwardDragDistance,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "frostsoul-player-dismiss-drag",
    )
    val collapseFraction = (settledDragOffset / 280f).coerceIn(0f, 1f)
    // On the ARTWORK_BLUR ("Immersive") style, the main player page wants its artwork to
    // reach the true top of the screen (behind the already-hidden status bar), with the
    // collapse chevron + pager dots floating over the artwork instead of sitting in their
    // own reserved row above it. Other pages/styles keep the reserved row untouched.
    val isImmersiveArtworkMainPage =
        playerDesignStyle == dev.vxs.frostsoulx.constants.PlayerDesignStyle.ARTWORK_BLUR &&
            pages.getOrNull(pagerState.currentPage) == FrostSoulPage.MainPlayer
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        showPagerDots = true
        if (!pagerState.isScrollInProgress) {
            delay(1_000L)
            if (!pagerState.isScrollInProgress) showPagerDots = false
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .graphicsLayer {
                    translationY = settledDragOffset
                    scaleX = 1f - collapseFraction * 0.035f
                    scaleY = 1f - collapseFraction * 0.035f
                    alpha = 1f - collapseFraction * 0.18f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .pointerInput(actions.onDismiss, queueVisible) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (!queueVisible) {
                                downwardDragDistance = (downwardDragDistance + dragAmount).coerceAtLeast(0f)
                            }
                        },
                        onDragEnd = {
                            if (downwardDragDistance >= 112f) actions.onDismiss()
                            downwardDragDistance = 0f
                        },
                        onDragCancel = { downwardDragDistance = 0f },
                    )
                },
        ) {
            FrostSoulDynamicBackground(
                artworkUrl = uiState.track.artworkUrl,
                playerDesignStyle = playerDesignStyle,
                playerBackgroundStyle = uiState.playerBackgroundStyle,
                blurRadius = uiState.blurRadius,
                palette = uiState.palette,
                moodSeed = "${uiState.track.title} ${uiState.track.artist} ${uiState.track.album}",
            )
            Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ),
        ) {
            // The expanded player owns the top edge. Keep only horizontal and bottom safe areas;
            // applying the top system-bar inset here shrinks the vinyl deck on devices where
            // the status bar is still reported by WindowInsets.
            //
            // On the Immersive main player page this row drops to 0dp height so the pager
            // below reclaims the space (letting the artwork header start at the true y=0),
            // while zIndex keeps the chevron/dots painted above the artwork instead of
            // being drawn underneath it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isImmersiveArtworkMainPage) 0.dp else 42.dp)
                    .zIndex(if (isImmersiveArtworkMainPage) 12f else 0f)
                    .padding(
                        start = PlayerLayoutTokens.MasterHorizontalPadding,
                        end = PlayerLayoutTokens.MasterHorizontalPadding,
                        top = 4.dp,
                        bottom = 6.dp,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = "Collapse player",
                    tint = FrostSoulTheme.colors.onSurface,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(28.dp)
                        .clickable(onClick = actions.onDismiss),
                )
                if (showPagerDots) {
                    FrostSoulPagerDots(
                        pageCount = pages.size,
                        selectedPage = pagerState.currentPage,
                        selectedPageOffsetFraction = pagerState.currentPageOffsetFraction,
                        emphasizeSelected = pagerState.isScrollInProgress,
                        onPageSelected = { targetPage ->
                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                key = { index -> pages[index].name },
                beyondViewportPageCount = 1,
                userScrollEnabled = !isSeekbarDragging,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                val pageDistance = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Keep pages flat and full-bleed: only a light cross-fade so
                                // adjacent pages never look scaled-in or pushed off-centre.
                                val distance = kotlin.math.abs(pageDistance).coerceIn(0f, 1f)
                                alpha = (1f - distance * 0.28f).coerceIn(0.70f, 1f)
                            },
                ) {
                    when (pages[pageIndex]) {
                        FrostSoulPage.Lyrics ->
                            FSLyrics(
                                rawLyrics = uiState.lyrics,
                                title = uiState.track.title,
                                artist = uiState.track.artist,
                                isPlaying = uiState.isPlaying,
                                isLiked = uiState.track.isLiked,
                                positionMs = uiState.positionMs,
                                durationMs = uiState.safeDurationMs,
                                onSeek = actions.onSeek,
                                onTogglePlayPause = actions.onTogglePlayPause,
                                onToggleLike = actions.onToggleLike,
                                onOpenAudioOutput = actions.onOpenAudioOutput,
                                onRefetchLyrics = actions.onRefetchLyrics,
                                isRefetchingLyrics = actions.isRefetchingLyrics,
                            )

                        FrostSoulPage.MainPlayer ->
                            if (playerDesignStyle == dev.vxs.frostsoulx.constants.PlayerDesignStyle.ARTWORK_BLUR) {
                                FrostSoulArtworkBlurAlbumPage(
                                    uiState = uiState,
                                    actions = actions,
                                    onOpenQueue = { queueVisible = true },
                                    onOpenOptions = actions.onOpenOptions,
                                    onSearchTrack = onSearchTrack,
                                    onShowArtists = { showArtistDialog = true },
                                    onSeekDraggingChanged = { isSeekbarDragging = it },
                                    onOpenLyrics = { scope.launch { pagerState.animateScrollToPage(2) } },
                                )
                            } else {
                                FrostSoulAlbumPage(
                                    uiState = uiState,
                                    actions = actions,
                                    onOpenQueue = { queueVisible = true },
                                    onOpenOptions = actions.onOpenOptions,
                                    onSearchTrack = onSearchTrack,
                                    onShowArtists = { showArtistDialog = true },
                                    onSeekDraggingChanged = { isSeekbarDragging = it },
                                )
                            }
                        FrostSoulPage.Recommendations -> FrostSoulRecommendationsPage(uiState = uiState, actions = actions)
                    }
                }
            }
        }
        if (showArtistDialog) {
            Dialog(
                onDismissRequest = { showArtistDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                ) {
                    FrostSoulArtistDialog(
                        artists = uiState.track.artists.ifEmpty {
                            uiState.track.artist
                                .split(" • ")
                                .map { name -> FrostSoulArtist(name = name.trim()) }
                                .filter { it.name.isNotBlank() }
                        },
                        onDismiss = { showArtistDialog = false },
                        onOpenArtist = { artistId ->
                            showArtistDialog = false
                            onOpenArtist(artistId)
                        },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = queueVisible,
            enter =
                fadeIn(animationSpec = tween(160)) +
                    slideInVertically(
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 460f),
                    ) { height -> height / 2 } +
                    scaleIn(
                        initialScale = 0.94f,
                        animationSpec = spring(dampingRatio = 0.84f, stiffness = 500f),
                    ),
            exit =
                fadeOut(animationSpec = tween(120)) +
                    slideOutVertically(animationSpec = tween(180)) { height -> height / 3 } +
                    scaleOut(targetScale = 0.96f, animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp),
        ) {
            FSGlassCard(
                accent = uiState.palette.accent,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .graphicsLayer {
                            shadowElevation = 28.dp.toPx()
                            shape = RoundedCornerShape(30.dp)
                            clip = false
                        },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                    ) {
                        Text(
                            text = uiState.queueTitle ?: "Up next",
                            color = FrostSoulOnSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        FSIconButton(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Close playback queue",
                            onClick = { queueVisible = false },
                            compact = true,
                        )
                    }
                    FSQueue(
                        title = "",
                        queue = uiState.queue,
                        onSelect = { index ->
                            actions.onSelectQueueItem(index)
                            queueVisible = false
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FSMiniPlayer(
    track: FrostSoulTrack,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    palette: FrostSoulPalette,
    height: androidx.compose.ui.unit.Dp,
    artworkSize: androidx.compose.ui.unit.Dp,
    peeked: Boolean,
    shape: RoundedCornerShape,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    onCardClick: () -> Unit,
    onLongPress: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onQueueClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val colors = FrostSoulTheme.colors
    val isLightTheme = colors.background.luminance() > 0.5f
    val surface = lerp(colors.surface, palette.artworkPrimary, if (isLightTheme) 0.10f else 0.22f)
    val accent = if (isLightTheme) colors.onSurface else lerp(palette.artworkPrimary, Color.White, 0.72f)
    val wash = remember(surface, palette.artworkSecondary) {
        Brush.horizontalGradient(listOf(surface, lerp(surface, palette.artworkSecondary, 0.12f)))
    }

    // Tinted glass, not backdrop blur: one cached brush, no elevated/offscreen layer.
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(height).clip(shape)
            .background(wash)
            .border(1.dp, accent.copy(alpha = 0.16f), shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClickLabel = "Open full player",
                onLongClickLabel = "Track actions",
                onClick = onCardClick,
                onLongClick = onLongPress,
            ),
    ) {
        // Keep the title usable on narrow displays; favorite remains in Track actions.
        val showFavorite = maxWidth >= 360.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(artworkSize).clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceRaised),
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (track.artworkUrl.isNullOrBlank()) {
                    Icon(painterResource(R.drawable.music_note), null, tint = colors.onSurfaceMuted,
                        modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(track.title, color = colors.onSurface, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = colors.onSurfaceMuted, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            if (showFavorite) {
                androidx.compose.material3.IconButton(onClick = onToggleLike, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painterResource(if (track.isLiked) R.drawable.favorite else R.drawable.favorite_border),
                        if (track.isLiked) "Remove from favorites" else "Add to favorites",
                        tint = if (track.isLiked) Color(0xFFF08D9C) else colors.onSurface,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
            androidx.compose.material3.IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(accent),
            ) {
                Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    if (isPlaying) "Pause" else "Play",
                    tint = if (isLightTheme) colors.surface else Color(0xFF131318),
                    modifier = Modifier.size(26.dp))
            }
            onQueueClick?.let { openQueue ->
                androidx.compose.material3.IconButton(onClick = openQueue, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.queue_music), "Open queue", tint = colors.onSurface,
                        modifier = Modifier.size(23.dp))
                }
            }
        }
        Canvas(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)) {
            drawRect(accent.copy(alpha = 0.14f))
            drawRect(accent, size = size.copy(width = size.width * progress))
        }
    }
}

@Composable
internal fun FSPlayerControls(
    state: FrostSoulPlayerUiState,
    actions: FrostSoulPlayerActions,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    onSeekDraggingChanged: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            FSDownloadButton(
                progress = state.downloadProgress,
                onClick = actions.onDownload,
            )
            FSIconButton(
                painter = painterResource(R.drawable.bedtime),
                contentDescription = if (state.sleepTimerActive) "Clear sleep timer" else "Set sleep timer",
                onClick = actions.onOpenSleepTimer,
                active = state.sleepTimerActive,
                buttonSize = 32.dp,
                iconSize = 21.dp,
                showContainer = false,
            )
            FrostSoulOutputDeviceButton(
                device = state.outputDevice,
                onClick = actions.onOpenAudioOutput,
                immersive = immersive,
                immersiveColor = state.palette.artworkPrimary.copy(alpha = 0.56f),
            )
            FSTwoDotButton(onClick = actions.onOpenOptions, immersive = immersive)
        }
        FSSeekbar(
            progress = state.progress,
            durationMs = state.safeDurationMs,
            onSeek = actions.onSeek,
            accent = state.palette.accent,
            onDraggingChanged = onSeekDraggingChanged,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                state.positionMs.asFrostSoulTime(),
                style = PlayerLayoutTokens.TimelineTimeStyle.copy(color = FrostSoulOnSurfaceMuted),
            )
            Text(
                state.safeDurationMs.asFrostSoulTime(),
                style = PlayerLayoutTokens.TimelineTimeStyle.copy(color = FrostSoulOnSurfaceMuted),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Repeat and queue are toggle-style controls, so they keep a soft container to make
            // their active/inactive state readable at a glance (also bumped up in size for a
            // sturdier touch target, matching the reference design).
            FSIconButton(
                painter = painterResource(
                    if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                        R.drawable.repeat_one
                    } else {
                        R.drawable.repeat
                    },
                ),
                contentDescription = "Toggle repeat mode",
                onClick = actions.onToggleRepeat,
                active = state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                buttonSize = 36.dp,
                iconSize = 19.dp,
                showContainer = false,
                forceWhite = true,
            )
            FSIconButton(
                painter = painterResource(R.drawable.skip_previous),
                contentDescription = "Previous track",
                onClick = actions.onSkipPrevious,
                enabled = state.canSkipPrevious,
                buttonSize = 44.dp,
                iconSize = 34.dp,
                showContainer = false,
                forceWhite = true,
            )
            FSPlayButton(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                onClick = actions.onTogglePlayPause,
            )
            FSIconButton(
                painter = painterResource(R.drawable.skip_next),
                contentDescription = "Next track",
                onClick = actions.onSkipNext,
                enabled = state.canSkipNext,
                buttonSize = 44.dp,
                iconSize = 34.dp,
                showContainer = false,
                forceWhite = true,
            )
            FSIconButton(
                painter = painterResource(R.drawable.queue_music),
                contentDescription = "Open playback queue",
                onClick = onOpenQueue,
                buttonSize = 36.dp,
                iconSize = 19.dp,
                showContainer = false,
                forceWhite = true,
                tintOverride = Color(0xFFD7DBE0),
            )
        }
    }
}

@Composable
private fun FSDownloadButton(
    progress: Float?,
    onClick: () -> Unit,
) {
    val normalizedProgress = progress?.coerceIn(0f, 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clickable(onClick = onClick),
    ) {
        normalizedProgress?.let { value ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0xFFD7DBE0).copy(alpha = 0.22f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()),
                )
                drawArc(
                    color = Color(0xFFD7DBE0),
                    startAngle = -90f,
                    sweepAngle = 360f * value,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Icon(
            painter = painterResource(if (normalizedProgress == 1f) R.drawable.check else R.drawable.ic_download),
            contentDescription = if (normalizedProgress == null) "Download song" else "Download progress ${((normalizedProgress * 100f).toInt())}%",
            tint = Color(0xFFD7DBE0),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FSTwoDotButton(
    onClick: () -> Unit,
    immersive: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.size(if (immersive) 40.dp else 36.dp).clickable(onClick = onClick),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(if (immersive) 7.dp else 5.dp)
                    .background(
                        if (immersive) Color(0xFFD7DBE0) else FrostSoulTheme.colors.onSurface,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun FSPlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 540f),
        label = "fs-play-button-scale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(if (isBuffering) 36.dp else 44.dp).alpha(if (isBuffering) 0.54f else 1f),
        )
    }
}

@Composable
internal fun FrostSoulPagerDots(
    pageCount: Int,
    selectedPage: Int,
    selectedPageOffsetFraction: Float = 0f,
    emphasizeSelected: Boolean = true,
    onPageSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerPosition =
        (selectedPage + selectedPageOffsetFraction)
            .coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(pageCount) { index ->
            val selection = (1f - kotlin.math.abs(pagerPosition - index.toFloat())).coerceIn(0f, 1f)
            val width = if (emphasizeSelected) 7.dp + (22.dp - 7.dp) * selection else 7.dp
            val alpha = if (emphasizeSelected) 0.36f + (1f - 0.36f) * selection else if (index == selectedPage) 0.95f else 0.34f
            Box(
                modifier =
                    Modifier
                        .height(4.dp)
                        .width(width)
                        .graphicsLayer {
                            this.alpha = alpha
                            shadowElevation = 10.dp.toPx() * selection
                        }
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (selection > 0.5f) Color.White else Color.White.copy(alpha = 0.34f))
                        .clickable { onPageSelected(index) },
            )
        }
    }
}

@Composable
private fun FrostSoulArtistDialog(
    artists: List<FrostSoulArtist>,
    onDismiss: () -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    FSGlassCard(
        accent = Color.White,
        modifier = Modifier.fillMaxWidth().height(300.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Artists involved",
                    color = FrostSoulOnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                FSIconButton(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Close artists dialog",
                    onClick = onDismiss,
                    compact = true,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                artists.forEach { artist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),

                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(FrostSoulSurfaceElevated)
                                .clickable(enabled = !artist.id.isNullOrBlank()) {
                                    artist.id?.let(onOpenArtist)
                                },
                        ) {
                            if (artist.artworkUrl.isNullOrBlank()) {
                                Icon(
                                    painter = painterResource(R.drawable.artist),
                                    contentDescription = null,
                                    tint = FrostSoulOnSurface.copy(alpha = 0.72f),
                                    modifier = Modifier.size(24.dp),
                                )
                            } else {
                                AsyncImage(
                                    model = artist.artworkUrl,
                                    contentDescription = "${artist.name} artist image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Text(
                            text = artist.name,
                            color = FrostSoulOnSurface,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostSoulMainLyricPreview(
    uiState: FrostSoulPlayerUiState,
    onlyCurrentLine: Boolean = false,
    // The artwork-blur player wants exactly the current 2-line block, bigger and more
    // prominent, with no extra trailing preview lines below it (reference: a clean 2-line
    // block only). The vinyl page still uses onlyCurrentLine = true (single line) and is
    // unaffected by this flag.
    showExtraPreviewLines: Boolean = !onlyCurrentLine,
    maxLinesPerLyric: Int = 2,
    horizontalPadding: Dp = PlayerLayoutTokens.MasterHorizontalPadding,
    modifier: Modifier = Modifier,
) {
    val currentLine = uiState.currentLyricModel
    if (currentLine == null && uiState.lyricPreviewLines.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
    ) {
        currentLine?.let { line ->
            Text(
                text = line.asMainPlayerKaraokeText(
                    currentWordIndex = uiState.currentWordIndex,
                    wordProgress = uiState.currentWordProgress,
                    lineProgress = uiState.currentLineProgress,
                ),
                color = FrostSoulOnSurface.copy(alpha = 0.96f),
                fontSize = if (onlyCurrentLine) 17.sp else 21.sp,
                lineHeight = if (onlyCurrentLine) 23.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (onlyCurrentLine) 1 else maxLinesPerLyric,
                overflow = TextOverflow.Ellipsis,
            )
        } ?: uiState.currentLyricLine?.takeIf { it.isNotBlank() }?.let { line ->
            Text(
                text = line,
                color = FrostSoulOnSurface.copy(alpha = 0.96f),
                fontSize = if (onlyCurrentLine) 17.sp else 21.sp,
                lineHeight = if (onlyCurrentLine) 23.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (onlyCurrentLine) 1 else maxLinesPerLyric,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showExtraPreviewLines) {
            uiState.lyricPreviewLines.drop(1).take(1).forEach { line ->
                Text(
                    text = line,
                    color = FrostSoulOnSurfaceMuted.copy(alpha = 0.82f),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun LyricsLine.asMainPlayerKaraokeText(
    currentWordIndex: Int,
    wordProgress: Float,
    lineProgress: Float,
): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        if (words.isEmpty()) {
            val fill = lineProgress.coerceIn(0f, 1f)
            withStyle(
                SpanStyle(
                    color = Color.White.copy(alpha = 0.68f + (0.32f * fill)),
                    shadow = Shadow(
                        color = Color.White.copy(alpha = 0.32f * fill),
                        blurRadius = 14f * fill,
                    ),
                ),
            ) {
                append(text)
            }
            return@buildAnnotatedString
        }

        words.forEachIndexed { index, word ->
            val fill = when {
                index < currentWordIndex -> 1f
                index == currentWordIndex -> wordProgress.coerceIn(0f, 1f)
                else -> 0f
            }
            val wordColor = when {
                fill <= 0.02f -> FrostSoulOnSurfaceMuted.copy(alpha = 0.72f)
                fill >= 0.98f -> Color.White
                else -> Color.Unspecified
            }
            val brush = if (fill > 0.02f && fill < 0.98f) {
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.White,
                        fill to Color.White,
                        (fill + 0.02f).coerceAtMost(1f) to FrostSoulOnSurfaceMuted.copy(alpha = 0.72f),
                        1f to FrostSoulOnSurfaceMuted.copy(alpha = 0.72f),
                    ),
                )
            } else {
                null
            }
            withStyle(
                if (brush != null) {
                    SpanStyle(
                        brush = brush,
                        shadow = if (fill > 0.02f) Shadow(color = Color.White.copy(alpha = 0.44f * fill), blurRadius = 14f * fill) else null,
                    )
                } else {
                    SpanStyle(
                        color = wordColor,
                        shadow = if (fill > 0.02f) Shadow(color = Color.White.copy(alpha = 0.44f * fill), blurRadius = 14f * fill) else null,
                    )
                },
            ) {
                append(word.text)
                if (index < words.lastIndex && word.text.lastOrNull()?.isWhitespace() != true) append(" ")
            }
        }
    }

@Composable
private fun FrostSoulAlbumPage(
    uiState: FrostSoulPlayerUiState,
    actions: FrostSoulPlayerActions,
    onOpenQueue: () -> Unit,
    onOpenOptions: () -> Unit,
    onSearchTrack: () -> Unit,
    onShowArtists: () -> Unit,
    onSeekDraggingChanged: (Boolean) -> Unit = {},
) {
    val titleScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = PlayerLayoutTokens.MasterHorizontalPadding)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            FSAlbumArt(
                artworkUrl = uiState.track.artworkUrl,
                title = uiState.track.title,
                isPlaying = uiState.isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxWidth = PlayerLayoutTokens.TurntableCardSize)
                    .aspectRatio(1f),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
            ) {
                FrostSoulFullPlayerLikeButton(
                    videoId = uiState.track.id,
                    isLiked = uiState.track.isLiked,
                    onClick = actions.onToggleLike,
                )
            }
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(titleScrollState)
                            .clickable(onClick = onSearchTrack),
                    ) {
                        Text(
                            text = uiState.track.title,
                            style = PlayerLayoutTokens.TrackTitleStyle.copy(color = FrostSoulOnSurface),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        text = uiState.track.artist,
                        style = PlayerLayoutTokens.ArtistSubtitleStyle.copy(color = FrostSoulOnSurfaceMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp).clickable(onClick = onShowArtists),
                    )
            }
            Spacer(modifier = Modifier.weight(1f))
            FrostSoulMainLyricPreview(
                uiState = uiState,
                onlyCurrentLine = true,
                horizontalPadding = 0.dp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 28.dp)
                    .graphicsLayer { translationY = -12.dp.toPx() },
            )
            FSPlayerControls(
                    state = uiState,
                    actions = actions,
                    onOpenQueue = onOpenQueue,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .graphicsLayer { translationY = -12.dp.toPx() },
                    immersive = true,
                    onSeekDraggingChanged = onSeekDraggingChanged,
            )
        }
    }
}

@Composable
private fun FrostSoulArtworkBlurAlbumPage(
    uiState: FrostSoulPlayerUiState,
    actions: FrostSoulPlayerActions,
    onOpenQueue: () -> Unit,
    onOpenOptions: () -> Unit,
    onSearchTrack: () -> Unit,
    onShowArtists: () -> Unit,
    onSeekDraggingChanged: (Boolean) -> Unit = {},
    onOpenLyrics: () -> Unit = {},
) {
    val base = remember(uiState.palette) { lerp(Color(0xFF0D0F14), uiState.palette.artworkPrimary, 0.10f) }
    val accent = remember(uiState.palette) { lerp(uiState.palette.artworkPrimary, Color.White, 0.72f) }
    val backdrop = remember(base, uiState.palette) {
        Brush.verticalGradient(listOf(lerp(base, uiState.palette.artworkSecondary, 0.16f), base))
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(backdrop)) {
        val landscape = maxWidth > maxHeight
        val viewportHeight = maxHeight
        val artworkHeight = (maxHeight * 0.43f).coerceIn(180.dp, 440.dp)
        val artwork: @Composable (Modifier) -> Unit = { artworkModifier ->
            Box(modifier = artworkModifier.clipToBounds(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = uiState.track.artworkUrl,
                    contentDescription = "Album artwork for ${uiState.track.title}",
                    contentScale = if (landscape) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (uiState.track.artworkUrl.isNullOrBlank()) {
                    Icon(painterResource(R.drawable.music_note), null, tint = accent, modifier = Modifier.size(72.dp))
                }
                // An ordinary scrim dissolves into the exact surface color. No DstIn,
                // RenderEffect or full-screen offscreen texture is needed for this fade.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.24f),
                    0.50f to Color.Transparent,
                    1f to base,
                )))
            }
        }
        val details: @Composable () -> Unit = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                uiState.isBuffering -> "BUFFERING"
                                uiState.isPlaying -> "NOW PLAYING"
                                else -> "PAUSED"
                            },
                            color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
                        )
                        Text(uiState.track.title, color = Color.White, fontSize = 25.sp, lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp).clickable(onClick = onSearchTrack))
                        Text(uiState.track.artist, color = Color.White.copy(alpha = 0.70f), fontSize = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(onClick = onShowArtists).padding(vertical = 8.dp))
                    }
                    androidx.compose.material3.IconButton(onClick = actions.onToggleLike, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(if (uiState.track.isLiked) R.drawable.favorite else R.drawable.favorite_border),
                            if (uiState.track.isLiked) "Remove from favorites" else "Add to favorites",
                            tint = if (uiState.track.isLiked) Color(0xFFF08D9C) else Color.White,
                            modifier = Modifier.size(26.dp))
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClickLabel = "Open lyrics", onClick = onOpenLyrics)
                        .padding(16.dp),
                ) {
                    Text("LYRICS  /  OPEN", color = accent, fontSize = 10.sp,
                        letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                    Text(uiState.currentLyricLine?.takeIf { it.isNotBlank() }
                        ?: uiState.lyricPreviewLines.firstOrNull()?.takeIf { it.isNotBlank() }
                        ?: "Follow the words", color = Color.White, fontSize = 18.sp,
                        lineHeight = 24.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    uiState.nextLyricLine?.takeIf { it.isNotBlank() }?.let { next ->
                        Text(next, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Separate controls preserve the vinyl player's existing layout exactly.
                FrostSoulImmersiveControls(uiState, actions, accent, onOpenQueue, onOpenOptions, onSeekDraggingChanged)
                val next = uiState.queue.dropWhile { !it.isCurrent }.drop(1).firstOrNull()
                if (next != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable(role = Role.Button, onClick = onOpenQueue).padding(12.dp),
                    ) {
                        AsyncImage(model = next.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UP NEXT", color = accent, fontSize = 9.sp, letterSpacing = 1.sp)
                            Text(next.title, color = Color.White, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(painterResource(R.drawable.queue_music), "Open queue", tint = accent, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        if (landscape) {
            Row(modifier = Modifier.fillMaxSize().padding(top = 42.dp)) {
                artwork(Modifier.weight(0.44f).fillMaxHeight())
                Column(modifier = Modifier.weight(0.56f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    details()
                }
            }
        } else {
            // Small screens and large fonts can scroll; tall screens distribute the space.
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight),
            ) {
                artwork(Modifier.fillMaxWidth().height(artworkHeight))
                details()
            }
        }
    }
}

@Composable
private fun FrostSoulImmersiveControls(
    state: FrostSoulPlayerUiState,
    actions: FrostSoulPlayerActions,
    accent: Color,
    onOpenQueue: () -> Unit,
    onOpenOptions: () -> Unit,
    onSeekDraggingChanged: (Boolean) -> Unit,
) {
    var seekPreview by remember(state.track.id) { mutableStateOf<Float?>(null) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { onSeekDraggingChanged(dragging) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { onSeekDraggingChanged(false) }
    }
    Column {
        androidx.compose.material3.Slider(
            value = seekPreview ?: state.progress,
            onValueChange = { seekPreview = it },
            onValueChangeFinished = {
                seekPreview?.let { actions.onSeek((state.safeDurationMs * it).toLong()) }
                seekPreview = null
            },
            enabled = state.safeDurationMs > 0L,
            interactionSource = interactionSource,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent, activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f),
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Playback position" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text((seekPreview?.let { (state.safeDurationMs * it).toLong() } ?: state.positionMs).asFrostSoulTime(),
                color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            Text(state.safeDurationMs.asFrostSoulTime(), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            val repeatActive = state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
            androidx.compose.material3.IconButton(onClick = actions.onToggleRepeat, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat),
                    when (state.repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat one; change repeat mode"
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat all; change repeat mode"
                        else -> "Repeat off; change repeat mode"
                    }, tint = if (repeatActive) accent else Color.White.copy(alpha = 0.45f), modifier = Modifier.size(22.dp))
            }
            androidx.compose.material3.IconButton(onClick = actions.onSkipPrevious, enabled = state.canSkipPrevious, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.skip_previous), "Previous track",
                    tint = Color.White.copy(alpha = if (state.canSkipPrevious) 1f else 0.3f), modifier = Modifier.size(32.dp))
            }
            androidx.compose.material3.IconButton(
                onClick = actions.onTogglePlayPause,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(24.dp)).background(accent),
            ) {
                Icon(painterResource(if (state.isPlaying) R.drawable.pause else R.drawable.play),
                    if (state.isPlaying) "Pause" else "Play", tint = Color(0xFF131318), modifier = Modifier.size(32.dp))
            }
            androidx.compose.material3.IconButton(onClick = actions.onSkipNext, enabled = state.canSkipNext, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.skip_next), "Next track",
                    tint = Color.White.copy(alpha = if (state.canSkipNext) 1f else 0.3f), modifier = Modifier.size(32.dp))
            }
            androidx.compose.material3.IconButton(onClick = onOpenQueue, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.queue_music), "Open queue", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f)).heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = actions.onOpenAudioOutput).padding(horizontal = 12.dp),
            ) {
                androidx.compose.material3.Icon(state.outputDevice.type.imageVector, null, tint = accent, modifier = Modifier.size(20.dp))
                Text(state.outputDevice.name, color = accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            androidx.compose.material3.IconButton(onClick = actions.onOpenSleepTimer, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.bedtime), if (state.sleepTimerActive) "Sleep timer active" else "Set sleep timer",
                    tint = if (state.sleepTimerActive) accent else Color.White.copy(alpha = 0.65f), modifier = Modifier.size(22.dp))
            }
            androidx.compose.material3.IconButton(onClick = onOpenOptions, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.more_horiz), "More track actions", tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun FrostSoulFullPlayerLikeButton(
    videoId: String,
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var likeCount by remember(videoId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(videoId) {
        if (videoId.isNotBlank()) likeCount = YouTube.getMediaInfo(videoId).getOrNull()?.like
    }
    val tint = if (isLiked) Color(0xFFFF3B4D) else {
        if (FrostSoulTheme.colors.background.luminance() > 0.5f) Color.Black else Color(0xFFD7DBE0)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
    ) {
        Icon(
            painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
            contentDescription = if (isLiked) "Unlike track" else "Like track",
            tint = tint,
            modifier = Modifier.size(25.dp),
        )
        Text(
            text = formatLikeCount(likeCount ?: 0),
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp).widthIn(min = 24.dp),
        )
    }
}

@Composable
private fun FrostSoulOutputDeviceButton(
    device: dev.vxs.frostsoulx.models.ActiveOutputDevice,
    onClick: () -> Unit,
    immersive: Boolean = false,
    immersiveColor: Color = FrostSoulTheme.colors.surfaceGlass,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (immersive) immersiveColor else FrostSoulTheme.colors.surface.copy(alpha = 0.58f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = device.type.imageVector,
            contentDescription = "Audio output device",
            tint = FrostSoulTheme.colors.onSurface,
            modifier = Modifier.size(if (immersive) 22.dp else 20.dp),
        )
        Text(
            text = device.name,
            color = FrostSoulTheme.colors.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 126.dp),
        )
    }
}

@Composable
private fun FrostSoulPlayerOptionsSheet(
    accent: Color,
    onDismiss: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onShareSong: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onToggleLike: () -> Unit,
) {
    val options =
        listOf(
            Triple(R.drawable.playlist_play, "Queue", true),
            Triple(R.drawable.lyrics, "Lyrics", true),
            Triple(R.drawable.bluetooth, "Audio output", true),
            Triple(R.drawable.share, "Share Song", true),
            Triple(R.drawable.favorite_border, "Like track", true),
        )
    FSGlassCard(
        accent = accent,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp)
                .height(610.dp)
                .graphicsLayer {
                    shadowElevation = 28.dp.toPx()
                    shape = RoundedCornerShape(30.dp)
                    clip = false
                },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FrostSoulOnSurfaceMuted.copy(alpha = 0.35f)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PLAYER OPTIONS",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.7.sp,
                    )
                    Text(
                        text = "QQ-style listening tools",
                        color = FrostSoulOnSurfaceMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                FSIconButton(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Close player options",
                    onClick = onDismiss,
                    compact = true,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (icon, label, actionable) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(43.dp)
                                .clickable(enabled = actionable) {
                                    if (actionable) {
                                        when (label) {
                                            "Queue" -> onOpenQueue()
                                            "Lyrics" -> onOpenLyrics()
                                            "Audio output" -> onOpenAudioOutput()
                                            "Share Song" -> onShareSong()
                                            "Like track" -> onToggleLike()
                                        }
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 14.dp),
                    ) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = if (actionable) Color.White else FrostSoulOnSurface,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = label,
                                color = FrostSoulOnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
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

@Composable
private fun FrostSoulRecommendationsPage(
    uiState: FrostSoulPlayerUiState,
    actions: FrostSoulPlayerActions,
) {
    val recommendationQueue = uiState.queue.filterNot { it.isCurrent }.take(12)
    val albumSongs =
        uiState.queue
            .filter { item -> uiState.track.albumId != null && item.albumId == uiState.track.albumId }
            .distinctBy { it.id }
            .take(5)
            .ifEmpty { recommendationQueue.take(5) }
    val recommendationKey = remember(recommendationQueue) {
        recommendationQueue.joinToString(separator = "|") { it.id }
    }
    val viewCounts by produceState<Map<String, Int?>>(emptyMap(), recommendationKey) {
        val requestLimiter = Semaphore(permits = 3)
        value = coroutineScope {
            recommendationQueue
                .map { item ->
                    async(Dispatchers.IO) {
                        requestLimiter.withPermit {
                            item.id to YouTube.getMediaInfo(item.id).getOrNull()?.viewCount
                        }
                    }
                }.awaitAll()
                .toMap()
        }
    }
    // This page renders directly over FrostSoulDynamicBackground's low-contrast artwork.
    // which stays dark in both app themes — so text/chip colors stay white-based regardless of
    // the app's light/dark theme setting (fixes FS-BUG-LIGHTMODE: text was flipping to
    // near-black here and disappearing against the still-dark backdrop in light theme).
    val primaryText = FrostSoulOnSurface
    val mutedText = FrostSoulOnSurfaceMuted
    val chipText = Color.White
    val chipSurface = Color.White.copy(alpha = 0.08f)
    val chipOutline = Color.White.copy(alpha = 0.18f)
    val cardSurface = Color.White.copy(alpha = 0.07f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 10.dp, bottom = 6.dp),
    ) {
        // Header block keeps a single shared gutter so nothing hangs off-screen.
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerLayoutTokens.MasterHorizontalPadding),
        ) {
            Text(
                text = "RECOMMENDATIONS",
                color = primaryText.copy(alpha = 0.72f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                maxLines = 1,
            )
            Text(
                text = uiState.track.title,
                color = primaryText,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = uiState.track.artist,
                color = mutedText,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                AsyncImage(
                    model = uiState.track.artworkUrl,
                    contentDescription = "Album artwork for ${uiState.track.album}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape),
                )
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        text = uiState.track.album.ifBlank { "Unknown album" },
                        color = primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Album",
                        color = mutedText,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                AsyncImage(
                    model = uiState.track.artworkUrl,
                    contentDescription = "Artwork for ${uiState.track.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                )
                Text(
                    text = uiState.track.title,
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                FrostSoulRecommendationChip(
                    label = uiState.audioTechnicalInfo ?: uiState.audioQualityBadge ?: "AUDIO INFO",
                    textColor = chipText,
                    surfaceColor = chipSurface,
                    outlineColor = chipOutline,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clickable(enabled = uiState.track.albumId != null, onClick = actions.onOpenAlbum),
            ) {
                Text(
                    text = uiState.track.album.ifBlank { "Unknown album" },
                    color = primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = "Open album",
                    tint = mutedText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                start = PlayerLayoutTokens.MasterHorizontalPadding,
                end = PlayerLayoutTokens.MasterHorizontalPadding,
                top = 12.dp,
                bottom = 2.dp,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                FrostSoulRecommendationChip(
                    label = "UP NEXT",
                    textColor = chipText,
                    surfaceColor = chipSurface,
                    outlineColor = chipOutline,
                    emphasized = true,
                )
            }
            item {
                FrostSoulRecommendationChip(
                    label = "${recommendationQueue.size} TRACKS",
                    textColor = chipText,
                    surfaceColor = chipSurface,
                    outlineColor = chipOutline,
                )
            }
            uiState.audioQualityBadge?.takeIf { it.isNotBlank() }?.let { quality ->
                item {
                    FrostSoulRecommendationChip(
                        label = quality,
                        textColor = chipText,
                        surfaceColor = chipSurface,
                        outlineColor = chipOutline,
                    )
                }
            }
            uiState.queueTitle?.takeIf { it.isNotBlank() }?.let { title ->
                item {
                    FrostSoulRecommendationChip(
                        label = title,
                        textColor = chipText,
                        surfaceColor = chipSurface,
                        outlineColor = chipOutline,
                    )
                }
            }
        }
        if (albumSongs.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PlayerLayoutTokens.MasterHorizontalPadding,
                        end = PlayerLayoutTokens.MasterHorizontalPadding,
                        top = 12.dp,
                    ),
            ) {
                Text(
                    text = "Songs from this album",
                    color = primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                albumSongs.forEach { item ->
                    FrostSoulAlbumSongRow(
                        item = item,
                        textColor = primaryText,
                        mutedTextColor = mutedText,
                        onClick = { actions.onSelectQueueItem(item.index) },
                    )
                }
            }
        }
        if (recommendationQueue.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
            ) {
                Text(
                    text = "No more songs in this queue.",
                    color = mutedText,
                    fontSize = 14.sp,
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PlayerLayoutTokens.MasterHorizontalPadding,
                        end = PlayerLayoutTokens.MasterHorizontalPadding,
                        top = 14.dp,
                        bottom = 16.dp,
                    ),
            ) {
                recommendationQueue.chunked(3).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowItems.forEach { item ->
                            // Tile = artwork card + text below it, matching the QQ recommendation grid.
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { actions.onSelectQueueItem(item.index) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(cardSurface),
                                ) {
                                    AsyncImage(
                                        model = item.artworkUrl,
                                        contentDescription = "Artwork for ${item.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    viewCounts[item.id]?.takeIf { it >= 0 }?.let { count ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(5.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.74f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(if (item.isCurrent) R.drawable.pause else R.drawable.play),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp),
                                            )
                                            Text(
                                                text = formatRecommendationViewCount(count),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                modifier = Modifier.padding(start = 3.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = item.title,
                                    color = primaryText,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Text(
                                    text = item.artist,
                                    color = mutedText,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostSoulAlbumSongRow(
    item: FrostSoulQueueItem,
    textColor: Color,
    mutedTextColor: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = "Artwork for ${item.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                text = item.title,
                color = textColor,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                color = mutedTextColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            painter = painterResource(if (item.isCurrent) R.drawable.pause else R.drawable.play),
            contentDescription = if (item.isCurrent) "Playing" else "Play ${item.title}",
            tint = textColor.copy(alpha = 0.74f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatRecommendationViewCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000f)}M"
        count >= 1_000 -> "${"%.1f".format(count / 1_000f)}K"
        else -> count.toString()
    }
}

@Composable
private fun FrostSoulRecommendationChip(
    label: String,
    textColor: Color,
    surfaceColor: Color,
    outlineColor: Color,
    emphasized: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (emphasized) textColor.copy(alpha = 0.14f) else surfaceColor)
            .border(1.dp, if (emphasized) textColor.copy(alpha = 0.32f) else outlineColor, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = textColor.copy(alpha = if (emphasized) 0.96f else 0.78f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun FSQueue(
    title: String,
    queue: List<FrostSoulQueueItem>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (title.isNotBlank()) {
            item {
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    letterSpacing = 1.7.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                )
            }
        }
        if (queue.isEmpty()) {
            item {
                Text(
                    text = "Your queue is empty.",
                    color = FrostSoulOnSurfaceMuted,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 22.dp),
                )
            }
        }
        items(queue, key = { item -> "${item.index}-${item.id}" }) { item ->
            FrostSoulQueueRow(item = item, onClick = { onSelect(item.index) })
        }
    }
}

@Composable
private fun FrostSoulQueueRow(
    item: FrostSoulQueueItem,
    onClick: () -> Unit,
) {
    FSGlassCard(
        accent = if (item.isCurrent) Color.White else Color.White,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (item.isCurrent) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.06f)),
            ) {
                Text(
                    text = if (item.isCurrent) "•" else (item.index + 1).toString(),
                    color = if (item.isCurrent) Color.White else FrostSoulOnSurfaceMuted,
                    fontSize = if (item.isCurrent) 24.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = if (item.isCurrent) FrostSoulOnSurface else FrostSoulOnSurfaceMuted,
                    fontSize = 15.sp,
                    fontWeight = if (item.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.artist,
                    color = FrostSoulOnSurfaceMuted.copy(alpha = 0.76f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun rememberFrostSoulPalette(artworkUrl: String?): FrostSoulPalette {
    val context = LocalContext.current
    val paletteCache =
        remember {
            object : LinkedHashMap<String, FrostSoulPalette>(PaletteCacheCapacity, 0.75f, true) {
                protected override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FrostSoulPalette>?): Boolean =
                    size > PaletteCacheCapacity
            }
        }
    var palette by remember(artworkUrl) { mutableStateOf(FrostSoulPalette.Default) }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            palette = FrostSoulPalette.Default
            return@LaunchedEffect
        }
        paletteCache[artworkUrl]?.let {
            palette = it
            return@LaunchedEffect
        }
        val extracted =
            try {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(artworkUrl)
                        .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                        .allowHardware(false)
                        .build()
                val bitmap =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request).image?.toBitmap()
                    }
                if (bitmap == null) {
                    null
                } else {
                    val colors =
                        withContext(Dispatchers.Default) {
                            val nativePalette =
                                Palette
                                    .from(bitmap)
                                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                    .generate()
                            PlayerColorExtractor.extractGradientColors(nativePalette, Color.Black.toArgb())
                        }
                    FrostSoulPalette(
                        artworkPrimary = colors.firstOrNull() ?: Color.White,
                        artworkSecondary = colors.getOrElse(1) { FrostSoulSurfaceElevated },
                        accent = Color.White,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        palette = extracted ?: FrostSoulPalette.Default
        paletteCache[artworkUrl] = palette
    }
    return palette
}

private const val PaletteCacheCapacity = 24

/**
 * Bottom ambient-glow constraints, derived by sampling the reference recording at 1 fps and
 * measuring the per-row / per-column medians of the bottom region (medians, so the seek bar and
 * transport icons drawn on top do not skew the numbers).
 *
 * The reference glow is a *geometry-free* wash: it has no circle, ellipse, capsule or blob edge
 * anywhere. It is a single continuous two-hue field that spans the full width, fades out upward
 * with an eased ramp, and runs all the way into the bottom screen edge with no gap. Everything
 * below encodes that measurement, and the values double as the guard rails ("glow constraints")
 * that keep the effect from ever growing into the turntable deck or washing out the controls.
 */
private object GlowConstraints {
    /**
     * Measured vertical extent: the wash first lifts off the flat background at ~0.74 of screen
     * height and reaches full strength at the very bottom row, i.e. ~26–27% of the screen.
     */
    const val BandHeightFraction = 0.27f

    /** Absolute clamps so short/tall screens keep the deck area clear and the wash stays visible. */
    val BandMinHeight = 160.dp
    val BandMaxHeight = 264.dp

    /**
     * Peak coverage of the wash. Held just below 1.0 so the white transport icons keep their
     * contrast; the darkened backdrop underneath is what lets the glow read as light, not paint.
     */
    const val PeakAlpha = 0.95f

    /**
     * Horizontal drift of the hue field, as a fraction of width. Measured by tracking the
     * warm-minus-cool centroid of the bottom rows: it swings ~±0.06w, but because the field now
     * has real lobes (see [GlowHueStopAlphas]) the *local* brightness swing that produces is
     * large — the reference's left edge goes from lum≈52 to lum≈110 within one cycle.
     */
    const val DriftFraction = 0.14f

    /**
     * The hue field is painted wider than the band by this fraction on each side. It is strictly
     * greater than [DriftFraction], which is what guarantees drift can never pull an unpainted
     * edge into view — the wash stays edgeless at every phase.
     */
    const val BleedFraction = 0.18f

    /**
     * Brightness breathing. The reference's mean bottom luminance swings ~±10% around its
     * average (78 → 96 on a 0–255 scale), clearly visible on top of the drift.
     */
    const val BreathFraction = 0.10f

    /**
     * One full drift cycle. Re-measured across a clean 19s window of the reference recording
     * (corner-patch luminance peak-to-peak and trough-to-trough): consistent ~5.3–5.5s, not the
     * earlier 6.0s estimate. Brightness peaks lead the drift by ~84°, reproduced by taking
     * sin/cos of the same phase.
     */
    const val CycleDurationMs = 5_400

    /**
     * Lightness / saturation window (HSL) that every palette hue is pushed into before it is
     * painted. This is the single most important constraint for visibility: the extracted
     * `artworkSecondary` is frequently a near-black like `#30262B`, and painting a dark colour
     * SrcOver a dark scrim *lowers* luminance — the old build measured −41 at the bottom edge
     * where the reference measures +63. The reference's painted hues resolve to L≈0.45–0.55 with
     * a moderate chroma once composited, so palette hues are lifted into that window here.
     */
    const val MinLightness = 0.56f
    const val MaxLightness = 0.68f
    const val MinSaturation = 0.30f
    const val MaxSaturation = 0.55f

    /** Below this saturation a swatch is treated as grey and keeps its (low) chroma. */
    const val GreySaturationThreshold = 0.10f
    const val GreyLiftedSaturation = 0.12f
}

/**
 * Pushes an extracted palette colour into the [GlowConstraints] lightness / saturation window so
 * it reads as *light* when painted over the darkened backdrop. Greys keep their neutral character
 * (forcing chroma onto a grey would invent a hue); everything else gets a floor on saturation so
 * the two lobes stay distinguishable after the alpha composite desaturates them.
 */
private fun Color.toGlowHue(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] =
        if (hsl[1] < GlowConstraints.GreySaturationThreshold) {
            GlowConstraints.GreyLiftedSaturation
        } else {
            hsl[1].coerceIn(GlowConstraints.MinSaturation, GlowConstraints.MaxSaturation)
        }
    hsl[2] = hsl[2].coerceIn(GlowConstraints.MinLightness, GlowConstraints.MaxLightness)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Horizontal stop positions and coverages of the measured hue field.
 *
 * The reference profile is two soft lobes on one continuous ramp: the primary hue peaks at
 * x≈0.24, a dim crossover sits at x≈0.46, the secondary hue peaks at x≈0.66, and both ends decay
 * toward the screen edges. No discrete shapes, just a ramp.
 *
 * The coverages are deliberately *not* flat. The previous 0.72–0.86 range produced a uniform
 * tint, so sliding it sideways changed nothing on screen. Two pronounced lobes with dim troughs
 * between and outside them are what make the drift and breath legible as moving light: with
 * these stops a simulated cycle swings the left-edge luminance by ≈50–60 (0–255), matching the
 * ≈58 swing measured in the reference recording.
 */
private val GlowHueStopPositions = floatArrayOf(0f, 0.10f, 0.24f, 0.46f, 0.66f, 0.84f, 1f)
private val GlowHueStopAlphas = floatArrayOf(0.14f, 0.36f, 1.00f, 0.34f, 0.98f, 0.36f, 0.14f)

/**
 * Eased vertical ramp of the wash, sampled from the reference at 0.02-screen steps and normalised
 * so 0 = the band's top edge and 1 = the bottom screen edge. Starting at exactly 0 is what removes
 * any visible top border; the ramp is deliberately soft through the middle so the falloff reads as
 * light bleeding upward rather than as a filled rectangle.
 */
private val GlowVerticalRamp =
    arrayOf(
        0.00f to 0.00f,
        0.15f to 0.06f,
        0.25f to 0.16f,
        0.38f to 0.33f,
        0.54f to 0.62f,
        0.70f to 0.88f,
        0.85f to 1.00f,
        1.00f to 1.00f,
    )

/** Full turn in radians. Not a `const` because it is computed from [Math.PI]. */
private val GlowTwoPi = (2.0 * Math.PI).toFloat()

/**
 * Pixel size the ambient backdrop artwork is decoded at. The image is blurred into a soft wash,
 * so full-resolution detail is thrown away anyway — decoding a small bitmap and letting it scale
 * up costs a fraction of the memory and bandwidth, and lets the blur radius drop sharply.
 */
private const val AmbientArtworkSampleSize = 192

/**
 * Background styles that paint a palette-tinted gradient over the artwork.
 * Hoisted to file scope so the set is allocated once rather than on every recomposition.
 */
private val GradientBackgroundStyles: Set<PlayerBackgroundStyle> =
    java.util.EnumSet.of(
        PlayerBackgroundStyle.GRADIENT,
        PlayerBackgroundStyle.COLORING,
        PlayerBackgroundStyle.BLUR_GRADIENT,
    )

private const val GlowBandFraction = 0.38f
private const val GlowTransitionDurationMs = 1_200

/**
 * Minimum saturation/value forced onto palette colors before they are painted.
 *
 * Album palettes are frequently near-black (the default secondary is `#30262B`, value ~0.16).
 * Painting those directly over black and then scaling by alpha collapses the wash to a dim
 * grey smear, which is exactly the "barely visible, colorless" failure mode. Lifting the tone
 * into a bright, saturated band keeps the artwork's hue while guaranteeing it reads on screen.
 *
 * The saturation ceiling prevents already-vivid artwork from turning neon.
 */
private const val GlowMinSaturation = 0.34f
private const val GlowMaxSaturation = 0.82f
private const val GlowMinValue = 0.70f

/**
 * Saturation below which a palette color is treated as intentionally achromatic.
 *
 * Applying a saturation floor to a truly grey color would invent a hue out of nothing (grey has
 * hue 0, so it would turn red). Below this threshold the color is only brightened, never tinted.
 */
private const val GlowAchromaticThreshold = 0.10f

/**
 * Lifts [color] into a vibrant tone suitable for an additive glow while preserving its hue.
 *
 * Hue is never modified, so the wash still reads as "this album's color". Only saturation and
 * value are adjusted: this rescues dark or muddy palettes without over-saturating vivid ones,
 * and leaves genuinely monochrome artwork looking monochrome.
 */
private fun glowTone(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    if (hsv[1] > GlowAchromaticThreshold) {
        hsv[1] = hsv[1].coerceIn(GlowMinSaturation, GlowMaxSaturation)
    }
    hsv[2] = hsv[2].coerceAtLeast(GlowMinValue)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun FrostSoulDynamicBackground(
    artworkUrl: String?,
    playerDesignStyle: PlayerDesignStyle,
    playerBackgroundStyle: PlayerBackgroundStyle,
    blurRadius: Float,
    palette: FrostSoulPalette,
    moodSeed: String,
) {
    val isVinyl = playerDesignStyle == PlayerDesignStyle.FROSTSOUL
    val isAnimatedGlow = isVinyl && playerBackgroundStyle == PlayerBackgroundStyle.GLOW_ANIMATED
    val isStaticGlow = isVinyl && playerBackgroundStyle == PlayerBackgroundStyle.GLOW
    val isGlow = isAnimatedGlow || isStaticGlow
    val isBlur = isVinyl && (
        playerBackgroundStyle == PlayerBackgroundStyle.BLUR ||
            playerBackgroundStyle == PlayerBackgroundStyle.BLUR_GRADIENT
    )
    val isGradient = isVinyl && playerBackgroundStyle in GradientBackgroundStyles
    val context = LocalContext.current
    val artworkRequest = remember(artworkUrl, context) {
        artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(Size(AmbientArtworkSampleSize, AmbientArtworkSampleSize))
                .build()
        }
    }

    // Palette colors interpolate only when artwork changes. The glow remains still between
    // transitions, so the vinyl can rotate independently without a perpetual background sweep.
    val primaryTarget = remember(palette) { glowTone(palette.artworkPrimary) }
    val secondaryTarget = remember(palette) { glowTone(palette.artworkSecondary) }
    val primary by animateColorAsState(
        targetValue = primaryTarget,
        animationSpec = tween(GlowTransitionDurationMs),
        label = "vinyl-glow-primary",
    )
    val secondary by animateColorAsState(
        targetValue = secondaryTarget,
        animationSpec = tween(GlowTransitionDurationMs),
        label = "vinyl-glow-secondary",
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isBlur && artworkRequest != null) {
            AsyncImage(
                model = artworkRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }
                    .blur(
                        radius = blurRadius.coerceIn(0f, 64f).dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    )
                    .alpha(0.72f),
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
        }

        if (isGradient) {
            val gradient = remember(palette) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.92f),
                        palette.artworkPrimary.copy(alpha = 0.34f),
                        palette.artworkSecondary.copy(alpha = 0.24f),
                        Color.Black.copy(alpha = 0.84f),
                    ),
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(gradient))
        }

        if (isGlow) {
            val bandHeight = (LocalConfiguration.current.screenHeightDp * GlowBandFraction)
                .dp.coerceIn(260.dp, 420.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bandHeight)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        // Four oversized, pre-softened fields overlap in the lower band. Their
                        // large radial falloffs provide a blur-like ambient pool without running
                        // Modifier.blur over the whole player or allocating a canvas per frame.
                        val mixed = lerp(primary, secondary, 0.5f)
                        val leftField = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.48f),
                                primary.copy(alpha = 0.22f),
                                primary.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.08f, size.height * 1.04f),
                            radius = size.width * 0.78f,
                        )
                        val rightField = Brush.radialGradient(
                            colors = listOf(
                                secondary.copy(alpha = 0.44f),
                                secondary.copy(alpha = 0.20f),
                                secondary.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.94f, size.height * 0.92f),
                            radius = size.width * 0.72f,
                        )
                        val centerField = Brush.radialGradient(
                            colors = listOf(
                                mixed.copy(alpha = 0.30f),
                                mixed.copy(alpha = 0.14f),
                                mixed.copy(alpha = 0.03f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.54f, size.height * 1.14f),
                            radius = size.width * 1.04f,
                        )
                        val upperField = Brush.radialGradient(
                            colors = listOf(
                                secondary.copy(alpha = 0.18f),
                                primary.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.38f, size.height * 0.46f),
                            radius = size.width * 0.74f,
                        )
                        val falloff = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.22f to Color.White.copy(alpha = 0.06f),
                            0.52f to Color.White.copy(alpha = 0.34f),
                            0.80f to Color.White.copy(alpha = 0.78f),
                            1f to Color.White,
                        )
                        val upperVeil = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.64f),
                            0.50f to Color.Black.copy(alpha = 0.42f),
                            0.78f to Color.Black.copy(alpha = 0.12f),
                            1f to Color.Transparent,
                        )
                        onDrawBehind {
                            // Static geometry keeps the wash stable while vinyl artwork rotates.
                            // Palette colors crossfade through animateColorAsState when artwork
                            // changes, avoiding an abrupt color swap.
                            drawRect(brush = leftField, alpha = 0.92f, blendMode = BlendMode.Plus)
                            drawRect(brush = rightField, alpha = 0.86f, blendMode = BlendMode.Plus)
                            drawRect(brush = centerField, alpha = 0.82f, blendMode = BlendMode.Plus)
                            drawRect(brush = upperField, alpha = 0.62f, blendMode = BlendMode.Plus)
                            drawRect(brush = falloff, blendMode = BlendMode.DstIn)
                            drawRect(brush = upperVeil)
                        }
                    },
            )
        }
    }
}
