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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import android.os.Build
import androidx.compose.foundation.Image
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asComposeRenderEffect
import dev.vxs.frostsoulx.constants.DisableBlurKey
import dev.vxs.frostsoulx.utils.rememberPreference
import dev.vxs.frostsoulx.ui.frostsoul.frostSoulGlass
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import dev.vxs.frostsoulx.ui.component.LocalPlayerArtworkTransition
import dev.vxs.frostsoulx.ui.component.playerArtwork
import androidx.compose.runtime.SideEffect
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import dev.vxs.frostsoulx.ui.player.CanvasArtworkPlayer
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.cos
import kotlin.math.sin

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
    var queueTab by remember { mutableStateOf(0) }
    val currentQueuePosition = remember(uiState.queue) { uiState.queue.indexOfFirst { it.isCurrent } }
    val previousQueue = remember(uiState.queue, currentQueuePosition) {
        if (currentQueuePosition >= 0) uiState.queue.take(currentQueuePosition) else emptyList()
    }
    val upcomingQueue = remember(uiState.queue, currentQueuePosition) {
        if (currentQueuePosition >= 0) uiState.queue.drop(currentQueuePosition + 1) else uiState.queue
    }
    val visibleQueue = when (queueTab) {
        1 -> previousQueue
        2 -> upcomingQueue
        else -> uiState.queue
    }
    BackHandler(enabled = queueVisible) { queueVisible = false }
    val queueListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showArtistDialog by remember(uiState.track.id) { mutableStateOf(false) }
    var showPagerDots by remember { mutableStateOf(true) }
    // While the seekbar is being dragged, the pager's own horizontal-swipe gesture must not
    // compete with it — otherwise a horizontal drag on the seekbar can get interpreted as a
    // page-change swipe instead of a seek. Disabling userScrollEnabled for the duration of the
    // drag is the reliable fix (plain pointerInput consumption on the seekbar alone doesn't
    // reliably win against the pager's own scrollable gesture detection).
    var isSeekbarDragging by remember { mutableStateOf(false) }
    val artworkTransition = LocalPlayerArtworkTransition.current
    SideEffect {
        artworkTransition?.enabled = pagerState.currentPage == 1 && !pagerState.isScrollInProgress
    }
    // The enclosing sheet owns vertical drag, fling, and collapse progress. A second
    // drag offset here would snap back on release and detach artwork from its destination.
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
,
        ) {
            FrostSoulDynamicBackground(
                artworkUrl = uiState.track.artworkUrl,
                playerDesignStyle = playerDesignStyle,
                playerBackgroundStyle = uiState.playerBackgroundStyle,
                blurRadius = uiState.blurRadius,
                palette = uiState.palette,
                moodSeed = "${uiState.track.title} ${uiState.track.artist} ${uiState.track.album}",
            )
            Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
        ) {
            // Overlay a real, tappable header instead of constraining its children to 0dp.
            // Artwork starts at y=0; only non-immersive pages reserve header space.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // Compact 26dp chevron inside the existing generous 56dp touch target.
                        Modifier.height(64.dp),
                    )
                    .zIndex(12f)
                    .padding(
                        start = PlayerLayoutTokens.MasterHorizontalPadding,
                        end = PlayerLayoutTokens.MasterHorizontalPadding,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = "Collapse player",
                    tint = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(56.dp)
                        .clickable(role = Role.Button, onClick = actions.onDismiss)
                        .padding(15.dp),
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
                modifier = Modifier.fillMaxSize()
                    // Immersive artwork stays full bleed beneath the enlarged header.
                    .padding(top = if (isImmersiveArtworkMainPage) 0.dp else 64.dp),
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
                                    onOpenLyrics = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pages.indexOf(FrostSoulPage.Lyrics))
                                        }
                                    },
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
        // Dim only the player behind the sheet; tapping outside or Back closes the queue.
        if (queueVisible) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { queueVisible = false },
            )
        }
        AnimatedVisibility(
            visible = queueVisible,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { it },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color(0xFF202020))
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = 16.dp),
            ) {
                var queueDismissDrag by remember { mutableFloatStateOf(0f) }
                val dismissThreshold = with(LocalDensity.current) { 64.dp.toPx() }
                Column(
                    Modifier.fillMaxWidth().pointerInput(dismissThreshold) {
                        detectVerticalDragGestures(
                            onDragStart = { queueDismissDrag = 0f },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                queueDismissDrag = (queueDismissDrag + amount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                if (queueDismissDrag > dismissThreshold) queueVisible = false
                                queueDismissDrag = 0f
                            },
                            onDragCancel = { queueDismissDrag = 0f },
                        )
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // These are queue positions, not fabricated listening-history counts.
                        val tabs = listOf("Playing" to uiState.queue.size, "Previous" to previousQueue.size, "Up next" to upcomingQueue.size)
                        tabs.forEachIndexed { index, (label, count) ->
                            Column(
                                Modifier.weight(1f).clickable(role = Role.Tab) {
                                    queueTab = index
                                    scope.launch { queueListState.scrollToItem(0) }
                                }.padding(vertical = 8.dp),
                            ) {
                                Text(
                                    buildAnnotatedString {
                                        append(label)
                                        withStyle(SpanStyle(fontSize = 10.sp)) { append(" $count") }
                                    },
                                    color = Color.White.copy(alpha = if (queueTab == index) 0.92f else 0.38f),
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.width(62.dp).height(2.dp).background(if (queueTab == index) Color.White else Color.Transparent))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FSIconButton(
                            painterResource(R.drawable.shuffle), "Toggle shuffle", actions.onToggleShuffle,
                            active = uiState.shuffleModeEnabled, buttonSize = 48.dp, iconSize = 20.dp,
                            showContainer = false, dimBackdrop = false,
                        )
                        Text(
                            if (uiState.shuffleModeEnabled) "Shuffle on" else "Shuffle",
                            color = Color.White.copy(alpha = 0.42f), fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        FSIconButton(
                            painterResource(R.drawable.location_on), "Scroll to current song", {
                                queueTab = 0
                                if (currentQueuePosition >= 0) scope.launch { queueListState.animateScrollToItem(currentQueuePosition) }
                            },
                            buttonSize = 48.dp, iconSize = 20.dp, showContainer = false, dimBackdrop = false,
                        )
                        FSIconButton(
                            painterResource(R.drawable.download), "Download queue", actions.onDownloadQueue,
                            buttonSize = 48.dp, iconSize = 20.dp, showContainer = false, dimBackdrop = false,
                        )
                        FSIconButton(
                            painterResource(R.drawable.more_vert), "Queue options", actions.onOpenOptions,
                            buttonSize = 48.dp, iconSize = 20.dp, showContainer = false, dimBackdrop = false,
                        )
                    }
                }
                FSQueue(
                    title = "",
                    queue = visibleQueue,
                    listState = queueListState,
                    isPlaying = uiState.isPlaying,
                    onToggleLike = actions.onToggleQueueLike,
                    onRemove = actions.onRemoveQueueItem,
                    onSelect = actions.onSelectQueueItem,
                    modifier = Modifier.weight(1f),
                )
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
    val rawProgress =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(220),
        label = "frostsoul-mini-player-progress",
    )
    val isLightTheme = FrostSoulTheme.colors.background.luminance() > 0.5f
    val backgroundColor = FrostSoulTheme.colors.surface
    val primaryTextColor = if (isLightTheme) FrostSoulTheme.colors.onSurface else FrostSoulOnSurface
    val mutedTextColor = FrostSoulTheme.colors.onSurfaceMuted
    // Keep the mini player grounded in the app theme while letting the current artwork
    // provide the visual accent. The palette is extracted off the main thread and cached.
    val progressColor = palette.artworkPrimary.copy(alpha = 0.94f)
    val progressTrackColor = palette.artworkSecondary.copy(alpha = 0.42f)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    shadowElevation = if (isPlaying) 6.dp.toPx() else 2.dp.toPx()
                    this.shape = shape
                    clip = false
                }
                .clip(shape)
                .background(backgroundColor)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.artworkPrimary.copy(alpha = 0.26f),
                            palette.artworkSecondary.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.65f), shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onCardClick,
                    onLongClick = onLongPress,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(artworkSize + 10.dp),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val strokeWidth = 1.5.dp.toPx()
                    val inset = strokeWidth / 2f
                    val left = inset
                    val top = inset
                    val right = size.width - inset
                    val bottom = size.height - inset
                    val cornerRadius = 10.dp.toPx().coerceAtMost((minOf(size.width, size.height) / 2f) - inset)
                    val topMidX = (left + right) / 2f
                    val timelineColor = progressColor
                    // Built by hand (instead of Path.addRoundRect, whose start point sits near a
                    // corner and which Compose defaults to counter-clockwise) so distance=0 on
                    // this path is exactly the middle of the top edge and the path winds
                    // clockwise from there — matching the requested start point/direction for
                    // the progress sweep below.
                    val perimeterPath = Path().apply {
                        moveTo(topMidX, top)
                        lineTo(right - cornerRadius, top)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(right, bottom - cornerRadius)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(left + cornerRadius, bottom)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(left, top + cornerRadius)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(topMidX, top)
                        close()
                    }
                    val perimeterMeasure = PathMeasure()
                    perimeterMeasure.setPath(perimeterPath, forceClosed = true)
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    )
                    drawPath(
                        path = perimeterPath,
                        color = progressTrackColor,
                        style = stroke,
                    )
                    if (progress > 0f) {
                        val progressPath = Path()
                        perimeterMeasure.getSegment(
                            startDistance = 0f,
                            stopDistance = perimeterMeasure.length * progress,
                            destination = progressPath,
                            startWithMoveTo = true,
                        )
                        drawPath(
                            path = progressPath,
                            color = timelineColor,
                            style = stroke,
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(artworkSize)
                        .playerArtwork(expanded = false)
                        .clip(RoundedCornerShape(8.dp)).background(FrostSoulSurface),
                ) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = "Album artwork for ${track.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (track.artworkUrl.isNullOrBlank()) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = mutedTextColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = track.title,
                    color = primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.artist.isNotBlank()) {
                    Text(
                        text = track.artist,
                        color = mutedTextColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).zIndex(1f).clickable(role = Role.Button, onClick = onToggleLike),
            ) {
                Icon(
                    painter = painterResource(if (track.isLiked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = if (track.isLiked) "Remove from favorites" else "Add to favorites",
                    tint = if (track.isLiked) Color(0xFFFF3B4D) else primaryTextColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            FSIconButton(
                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onTogglePlayPause,
                active = false,
                buttonSize = 48.dp,
                iconSize = 24.dp,
                showContainer = true,
                dimBackdrop = false,
                tintOverride = if (isLightTheme) Color.Black else Color.White,
                modifier = Modifier.zIndex(1f),
            )
            onQueueClick?.let { openQueue ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .zIndex(2f)
                            .clickable(role = Role.Button, onClick = openQueue),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = "Open queue",
                        tint = if (isLightTheme) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            FSDownloadButton(
                progress = state.downloadProgress,
                onClick = actions.onDownload,
            )
            FSSleepTimerButton(
                active = state.sleepTimerActive,
                remainingMs = state.sleepTimerRemainingMs,
                onClick = actions.onOpenSleepTimer,
                immersive = immersive,
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
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Repeat and queue are toggle-style controls, so they keep a soft container to make
            // their active/inactive state readable at a glance (also bumped up in size for a
            // sturdier touch target, matching the reference design).
            FSIconButton(
                painter = painterResource(
                    when {
                        state.shuffleModeEnabled -> R.drawable.shuffle_on
                        state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                        else -> R.drawable.repeat
                    },
                ),
                contentDescription = if (state.shuffleModeEnabled) "Shuffle is on" else "Toggle repeat mode",
                onClick = if (state.shuffleModeEnabled) actions.onToggleShuffle else actions.onToggleRepeat,
                active = state.shuffleModeEnabled || state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
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
private fun FSSleepTimerButton(
    active: Boolean,
    remainingMs: Long,
    onClick: () -> Unit,
    immersive: Boolean,
) {
    val label =
        if (!active) {
            ""
        } else if (remainingMs > 0L) {
            val totalSeconds = (remainingMs / 1_000L).toInt().coerceAtLeast(0)
            "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        } else {
            "END"
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(40.dp).clickable(onClick = onClick),
    ) {
        FSIconButton(
            painter = painterResource(R.drawable.bedtime),
            contentDescription = if (active) "Cancel sleep timer ($label)" else "Set sleep timer",
            onClick = onClick,
            active = active,
            buttonSize = 40.dp,
            iconSize = 23.dp,
            showContainer = false,
            forceWhite = immersive,
        )
        if (active) {
            Text(
                text = label,
                color = if (immersive) Color.White else FrostSoulTheme.colors.onSurface,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
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
        modifier = Modifier.size(40.dp).clickable(onClick = onClick),
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
        modifier = Modifier.size(40.dp).clickable(onClick = onClick),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(if (immersive) 7.dp else 6.dp)
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
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.72f), CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
    ) {
        if (isBuffering) {
            FSPlaybackBars(Modifier.size(30.dp), contentDescription = "Fetching song")
        } else {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** One draw-only clock for all five bars; no recomposition or per-bar animators. */
@Composable
private fun FSPlaybackBars(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    animated: Boolean = true,
    contentDescription: String = "Playing",
) {
    val phase = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(animated, lifecycleOwner) {
        if (!animated) return@LaunchedEffect
        val durationScale = coroutineContext[MotionDurationScale]
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { durationScale?.scaleFactor ?: 1f }.collectLatest { scale ->
                if (scale <= 0f) return@collectLatest
                var previousFrame = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        if (previousFrame != 0L) {
                            val elapsed = (now - previousFrame).coerceAtMost(100_000_000L)
                            phase.floatValue = (phase.floatValue + elapsed / 1_000_000_000f * GlowTwoPi / (1.1f * scale)) % GlowTwoPi
                        }
                        previousFrame = now
                    }
                    delay(24L)
                }
            }
        }
    }
    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        val step = size.width / 6f
        repeat(5) { index ->
            val wave = (sin(phase.floatValue + index * 1.15f) + 1f) * 0.5f
            val height = size.height * (0.22f + 0.64f * wave)
            val x = step * (index + 1)
            drawLine(color, Offset(x, center.y - height / 2f), Offset(x, center.y + height / 2f), strokeWidth = step * 0.48f, cap = StrokeCap.Round)
        }
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
    // The vinyl page pulls the lyric up under the artist name and centers it, QQ Music-style,
    // instead of leaving it start-aligned at the bottom of the page.
    centered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentLine = uiState.currentLyricModel
    if (currentLine == null && uiState.lyricPreviewLines.isEmpty()) return
    val lyricTextAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
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
                fontSize = if (onlyCurrentLine) 19.sp else 21.sp,
                lineHeight = if (onlyCurrentLine) 25.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (onlyCurrentLine) 1 else maxLinesPerLyric,
                overflow = TextOverflow.Ellipsis,
                textAlign = lyricTextAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        } ?: uiState.currentLyricLine?.takeIf { it.isNotBlank() }?.let { line ->
            Text(
                text = line,
                color = FrostSoulOnSurface.copy(alpha = 0.96f),
                fontSize = if (onlyCurrentLine) 19.sp else 21.sp,
                lineHeight = if (onlyCurrentLine) 25.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (onlyCurrentLine) 1 else maxLinesPerLyric,
                overflow = TextOverflow.Ellipsis,
                textAlign = lyricTextAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showExtraPreviewLines) {
            uiState.lyricPreviewLines.drop(1).take(1).forEach { line ->
                Text(
                    text = line,
                    color = FrostSoulOnSurfaceMuted.copy(alpha = 0.58f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = lyricTextAlign,
                    modifier = Modifier.fillMaxWidth(),
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
    onOpenLyrics: () -> Unit = {},
) {
    val titleScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = PlayerLayoutTokens.MasterHorizontalPadding)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            FSAlbumArt(
                artworkUrl = uiState.track.artworkUrl,
                title = uiState.track.title,
                isPlaying = uiState.isPlaying,
                palette = uiState.palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxWidth = PlayerLayoutTokens.TurntableCardSize)
                    .aspectRatio(1f),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FrostSoulFullPlayerDislikeButton(
                        videoId = uiState.track.id,
                        onClick = actions.onToggleDislike,
                    )
                    FrostSoulFullPlayerLikeButton(
                        videoId = uiState.track.id,
                        isLiked = uiState.track.isLiked,
                        onClick = actions.onToggleLike,
                    )
                }
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
                    // Current lyric line sits directly under the artist name, flush with its
                    // left edge (same vertical line as the title/artist), and is tappable to jump
                    // straight to the full Lyrics page.
                    FrostSoulMainLyricPreview(
                        uiState = uiState,
                        onlyCurrentLine = true,
                        horizontalPadding = 0.dp,
                        centered = false,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable(onClick = onOpenLyrics),
                    )
            }
            Spacer(modifier = Modifier.weight(1f))
            FSPlayerControls(
                    state = uiState,
                    actions = actions,
                    onOpenQueue = onOpenQueue,
                    modifier = Modifier.padding(top = 2.dp),
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
    modifier: Modifier = Modifier,
) {
    val artworkHeaderBlur =
        if (uiState.blurRadius > 0f) {
            (uiState.blurRadius + 18f).coerceIn(18f, 120f)
        } else {
            0f
        }
    val immersiveBlurRadius = artworkHeaderBlur.coerceAtLeast(28f).coerceAtMost(72f)
    val sharpArtworkUrl = uiState.canvasStaticUrl ?: uiState.track.artworkUrl
    val hasCanvas = !uiState.canvasPrimaryUrl.isNullOrBlank() || !uiState.canvasFallbackUrl.isNullOrBlank()
    Box(modifier = modifier.fillMaxSize()) {
        if (!sharpArtworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = sharpArtworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(immersiveBlurRadius.dp, BlurredEdgeTreatment.Rectangle)
                    .graphicsLayer { alpha = 0.92f },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(uiState.palette.artworkPrimary, uiState.palette.artworkSecondary),
                    ),
                ),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.20f),
                    0.48f to Color.Transparent,
                    0.74f to Color.Black.copy(alpha = 0.56f),
                    1f to Color.Black.copy(alpha = 0.88f),
                ),
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
            // Full-bleed artwork header: the image spans the whole width with no card
       // inset, and fades edge-to-edge into the page background so the thumbnail
            // reads as one seamless surface (QQ Music "immersive cover" behaviour).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PlayerLayoutTokens.ArtworkBlurHeaderHeight)
                    .clipToBounds(),
            ) {
                if (!sharpArtworkUrl.isNullOrBlank()) {
                    // The blurred artwork is already rendered full-screen underneath this header.
                    // Mask the sharp cover at its lower edge instead of painting a black fade over
                    // it; this lets the two layers actually dissolve into one another like the
                    // original ArchiveTune Immersive Extended player.
                    AsyncImage(
                        model = sharpArtworkUrl,
                        contentDescription = "Album artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .playerArtwork(expanded = true)
                            // FS-BUG-IMMERSIVE-BORDER: BlendMode.DstIn only combines correctly
                            // with what's *already inside this composable's own layer*. Without
                            // an explicit offscreen layer here, this image shares the pager
                            // page's layer, and DstIn ends up cutting into whatever else is
                            // already drawn there instead of just fading this image's own alpha
                            // to transparent — which is exactly why the header shows a hard
                            // rectangular edge (the "border line square") while settled on the
                            // current page. It only looked fixed mid-drag because the pager's
                            // own alpha-fade on adjacent pages happened to force an offscreen
                            // layer at that moment. Forcing it here directly makes the fade
                            // isolated and consistent regardless of pager/drag state.
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.00f to Color.White,
                                        0.48f to Color.White,
                                        0.68f to Color.White.copy(alpha = 0.96f),
                                        0.82f to Color.White.copy(alpha = 0.72f),
                                        0.93f to Color.White.copy(alpha = 0.28f),
                                        1.00f to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    )
                    if (hasCanvas) {
                        CanvasArtworkPlayer(
                            primaryUrl = uiState.canvasPrimaryUrl,
                            fallbackUrl = uiState.canvasFallbackUrl,
                            isPlaying = uiState.isPlaying,
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(
                            Brush.verticalGradient(
                                colors = listOf(uiState.palette.artworkPrimary, uiState.palette.artworkSecondary),
                            ),
                        ),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PlayerLayoutTokens.MasterHorizontalPadding,
                        end = PlayerLayoutTokens.MasterHorizontalPadding,
                        top = 20.dp,
                        bottom = 14.dp,
                    ),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                            .clickable(onClick = onSearchTrack),
                    ) {
                        Text(
                            text = uiState.track.title,
                            style = PlayerLayoutTokens.ImmersiveTitleStyle.copy(color = FrostSoulOnSurface),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        text = uiState.track.artist,
                        style = PlayerLayoutTokens.ImmersiveArtistStyle.copy(color = FrostSoulOnSurfaceMuted.copy(alpha = 0.90f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp).clickable(onClick = onShowArtists),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    FrostSoulFullPlayerDislikeButton(
                        videoId = uiState.track.id,
                        onClick = actions.onToggleDislike,
                    )
                    FrostSoulFullPlayerLikeButton(
                        videoId = uiState.track.id,
                        isLiked = uiState.track.isLiked,
                        onClick = actions.onToggleLike,
                    )
                }
            }

            FrostSoulMainLyricPreview(
                uiState = uiState,
                showExtraPreviewLines = true,
                maxLinesPerLyric = 1,
                modifier = Modifier.heightIn(min = 60.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        FrostSoulImmersiveControls(
            state = uiState,
            actions = actions,
            accent = uiState.palette.accent,
            onOpenQueue = onOpenQueue,
            onOpenOptions = onOpenOptions,
            onSeekDraggingChanged = onSeekDraggingChanged,
            modifier = Modifier
                .padding(horizontal = PlayerLayoutTokens.MasterHorizontalPadding)
                .padding(top = 6.dp),
        )
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FSSeekbar(
            progress = state.progress,
            durationMs = state.safeDurationMs,
            onSeek = actions.onSeek,
            accent = accent,
            onDraggingChanged = onSeekDraggingChanged,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(state.positionMs.asFrostSoulTime(), style = PlayerLayoutTokens.TimelineTimeStyle)
            Text(state.safeDurationMs.asFrostSoulTime(), style = PlayerLayoutTokens.TimelineTimeStyle)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FSDownloadButton(progress = state.downloadProgress, onClick = actions.onDownload)
            FSSleepTimerButton(
                active = state.sleepTimerActive,
                remainingMs = state.sleepTimerRemainingMs,
                onClick = actions.onOpenSleepTimer,
                immersive = true,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .clickable(role = Role.Button, onClick = actions.onOpenAudioOutput)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = state.outputDevice.type.imageVector,
                    contentDescription = "Audio output",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.outputDevice.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Immersive · Spatial",
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                    )
                }
            }
            FSTwoDotButton(onClick = onOpenOptions, immersive = true)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onOpenQueue, modifier = Modifier.size(44.dp)) {
                Icon(painterResource(R.drawable.queue_music), "Open queue", tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(22.dp))
            }
            androidx.compose.material3.IconButton(onClick = actions.onSkipPrevious, enabled = state.canSkipPrevious, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.skip_previous), "Previous track", tint = Color.White.copy(alpha = if (state.canSkipPrevious) 1f else 0.3f), modifier = Modifier.size(32.dp))
            }
            FSPlayButton(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                onClick = actions.onTogglePlayPause,
            )
            androidx.compose.material3.IconButton(onClick = actions.onSkipNext, enabled = state.canSkipNext, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.skip_next), "Next track", tint = Color.White.copy(alpha = if (state.canSkipNext) 1f else 0.3f), modifier = Modifier.size(32.dp))
            }
            val repeatActive = state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
            val shuffleActive = state.shuffleModeEnabled
            androidx.compose.material3.IconButton(
                onClick = if (shuffleActive) actions.onToggleShuffle else actions.onToggleRepeat,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    painterResource(
                        when {
                            shuffleActive -> R.drawable.shuffle_on
                            state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                            else -> R.drawable.repeat
                        },
                    ),
                    if (shuffleActive) "Shuffle is on" else "Toggle repeat mode",
                    tint = if (shuffleActive || repeatActive) accent else Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(22.dp),
                )
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
    // Count now lives in a small cutout badge tucked into the heart's top-right corner
    // (QQ Music-style) instead of sitting as separate text to the icon's right.
    Box(
        modifier = modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
            contentDescription = if (isLiked) "Unlike track" else "Like track",
            tint = tint,
            modifier = Modifier.size(25.dp),
        )
        val count = likeCount ?: 0
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-1).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(FrostSoulTheme.colors.background.copy(alpha = 0.92f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = formatLikeCount(count),
                    color = tint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FrostSoulFullPlayerDislikeButton(
    videoId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDisliked by remember(videoId) { mutableStateOf(false) }
    val tint = if (isDisliked) Color(0xFFFF6B6B) else {
        if (FrostSoulTheme.colors.background.luminance() > 0.5f) Color.Black else Color(0xFFD7DBE0)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Button) {
                isDisliked = !isDisliked
                onClick()
            },
    ) {
        Icon(
            painter = painterResource(R.drawable.favorite_dislike),
            contentDescription = if (isDisliked) "Remove dislike" else "Dislike track",
            tint = tint,
            modifier = Modifier.size(25.dp),
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
                .height(468.dp)
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
                        text = "Player controls",
                        color = FrostSoulTheme.colors.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Quick access for this track",
                        color = FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 12.sp,
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
                                .height(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (actionable) FrostSoulTheme.colors.surface.copy(alpha = 0.58f)
                                    else Color.Transparent,
                                )
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
                            tint = if (actionable) accent else FrostSoulOnSurfaceMuted,
                            modifier = Modifier.size(21.dp),
                        )
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(
                                text = label,
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
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    isPlaying: Boolean = false,
    onToggleLike: (Int) -> Unit = {},
    onRemove: (Int) -> Unit = {},
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(0.dp),
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
            FrostSoulQueueRow(
                item = item, isPlaying = isPlaying,
                onClick = { onSelect(item.index) },
                onToggleLike = { onToggleLike(item.index) },
                onRemove = { onRemove(item.index) },
            )
        }
    }
}

@Composable
private fun FrostSoulQueueRow(
    item: FrostSoulQueueItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onRemove: () -> Unit,
) {
    val activeColor = Color(0xFF20B486)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = buildAnnotatedString {
                append(item.title)
                withStyle(SpanStyle(color = if (item.isCurrent) activeColor.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.36f), fontSize = 12.sp)) {
                    append(" - ${item.artist}")
                }
            },
            color = if (item.isCurrent) activeColor else Color.White.copy(alpha = 0.84f),
            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
        )
        if (item.isCurrent) {
            FSPlaybackBars(Modifier.size(18.dp), color = activeColor, animated = isPlaying, contentDescription = if (isPlaying) "Playing" else "Paused")
        }
        FSIconButton(
            painterResource(if (item.isLiked) R.drawable.favorite else R.drawable.favorite_border),
            if (item.isLiked) "Unlike ${item.title}" else "Like ${item.title}", onToggleLike,
            buttonSize = 48.dp, iconSize = 19.dp, showContainer = false, dimBackdrop = false,
            tintOverride = if (item.isLiked) activeColor else Color.White.copy(alpha = 0.40f),
        )
        FSIconButton(
            painterResource(R.drawable.close), "Remove ${item.title} from queue", onRemove,
            buttonSize = 48.dp, iconSize = 18.dp, showContainer = false, dimBackdrop = false,
            tintOverride = Color.White.copy(alpha = 0.40f),
        )
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
                            extractGlowColors(nativePalette)
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

/** Select actual artwork swatches, not hue-shifted gradient filler colors. Runs off-main. */
private fun extractGlowColors(palette: Palette): List<Color> {
    val swatches = palette.swatches.sortedByDescending { it.population }
    // Prefer a real artwork pigment over a populous black/gray background.
    val primary = (palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: swatches.firstOrNull())
        ?.let { Color(it.rgb) } ?: Color.DarkGray
    // Prefer the next populous, visibly distinct color over another quantization of the first.
    val secondary = swatches.sortedByDescending { it.hsl[1] * it.hsl[2] }.firstOrNull { swatch ->
        val candidate = Color(swatch.rgb)
        val r = candidate.red - primary.red
        val g = candidate.green - primary.green
        val b = candidate.blue - primary.blue
        r * r + g * g + b * b > 0.035f
    }?.let { Color(it.rgb) } ?: primary
    return listOf(primary, secondary)
}

private object FluidGlowSpec {
    const val Columns = 24
    const val Rows = 18
    const val FrameIntervalNanos = 33_333_333L
    const val CycleSeconds = 48f
}

private val GlowTwoPi = (2.0 * Math.PI).toFloat()

/**
 * A single untextured mesh, feathered by vertex alpha, replaces full-screen blur and additive
 * layers. All arrays and spatial waves are reused; only colors change on a motion frame.
 * Two counter-moving wave fields fold the color boundary like slowly stirred paint. The
 * broad blend retains both pigments instead of adding them into a washed-out third color.
 */
private class VinylGlowMesh {
    private val stride = FluidGlowSpec.Columns + 1
    private val vertexCount = stride * (FluidGlowSpec.Rows + 1)
    private val positions = FloatArray(vertexCount * 2)
    private val colors = IntArray(vertexCount)
    private val waveSin = FloatArray(vertexCount)
    private val waveCos = FloatArray(vertexCount)
    private val curlSin = FloatArray(vertexCount)
    private val curlCos = FloatArray(vertexCount)
    private val indices = ShortArray(FluidGlowSpec.Columns * FluidGlowSpec.Rows * 6)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    // Hardware drawVertices starts at API 29. Older devices rasterize the same mesh into a
    // reusable 36 KiB tile rather than enabling a full-screen software layer or blur buffer.
    private val legacyBitmap = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
    } else {
        null
    }
    private val legacyCanvas = legacyBitmap?.let { android.graphics.Canvas(it) }
    private val legacyBounds = android.graphics.RectF()
    private val bitmapPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private var width = -1f
    private var height = -1f

    init {
        for (row in 0..FluidGlowSpec.Rows) {
            for (column in 0..FluidGlowSpec.Columns) {
                val i = row * stride + column
                val x = column.toFloat() / FluidGlowSpec.Columns
                val y = row.toFloat() / FluidGlowSpec.Rows
                waveSin[i] = sin(x * 6f + y * 4f)
                waveCos[i] = cos(x * 6f + y * 4f)
                curlSin[i] = sin(x * 10f - y * 7f)
                curlCos[i] = cos(x * 10f - y * 7f)
            }
        }
        var offset = 0
        for (row in 0 until FluidGlowSpec.Rows) {
            for (column in 0 until FluidGlowSpec.Columns) {
                val i = row * stride + column
                indices[offset++] = i.toShort()
                indices[offset++] = (i + 1).toShort()
                indices[offset++] = (i + stride).toShort()
                indices[offset++] = (i + 1).toShort()
                indices[offset++] = (i + stride + 1).toShort()
                indices[offset++] = (i + stride).toShort()
            }
        }
    }

    fun draw(canvas: android.graphics.Canvas, w: Float, h: Float, phase: Float, primary: Color, secondary: Color) {
        if (w <= 0f || h <= 0f) return
        if (canvas.isHardwareAccelerated && legacyBitmap != null && legacyCanvas != null) {
            legacyBitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            draw(legacyCanvas, 96f, 96f, phase, primary, secondary)
            legacyBounds.set(0f, 0f, w, h)
            canvas.drawBitmap(legacyBitmap, null, legacyBounds, bitmapPaint)
            return
        }
        if (w != width || h != height) {
            width = w
            height = h
            for (row in 0..FluidGlowSpec.Rows) {
                for (column in 0..FluidGlowSpec.Columns) {
                    val i = row * stride + column
                    positions[i * 2] = column.toFloat() / FluidGlowSpec.Columns * w
                    // Bottom-anchored wash, with the top feather disappearing below the record.
                    positions[i * 2 + 1] = (0.36f + row.toFloat() / FluidGlowSpec.Rows * 0.64f) * h
                }
            }
        }
        // Integer harmonics keep the faster 48-second phase wrap seamless.
        val flowSin = sin(phase * 5f)
        val flowCos = cos(phase * 5f)
        val mixSin = sin(-phase * 3f)
        val mixCos = cos(-phase * 3f)
        val breath = sin(phase * 6f)
        val drift = sin(phase * 6f) * 0.14f
        val red = primary.red * 255f
        val green = primary.green * 255f
        val blue = primary.blue * 255f
        val redDelta = secondary.red * 255f - red
        val greenDelta = secondary.green * 255f - green
        val blueDelta = secondary.blue * 255f - blue
        for (row in 0..FluidGlowSpec.Rows) {
            val y = row.toFloat() / FluidGlowSpec.Rows
            for (column in 0..FluidGlowSpec.Columns) {
                val i = row * stride + column
                val x = column.toFloat() / FluidGlowSpec.Columns
                val fold = waveSin[i] * flowCos + waveCos[i] * flowSin
                val curl = curlSin[i] * mixCos + curlCos[i] * mixSin
                val mix = glowSmoothStep(0.12f, 0.88f, x + fold * 0.30f + curl * 0.14f + drift)
                val rise = y + fold * 0.075f + breath * 0.045f
                val envelope = glowSmoothStep(0f, 0.30f, y) * glowSmoothStep(0.02f, 0.78f, rise)
                val alpha = (255f * envelope * (0.90f + 0.045f * breath)).toInt().coerceIn(0, 255)
                colors[i] = android.graphics.Color.argb(
                    alpha,
                    (red + redDelta * mix).toInt().coerceIn(0, 255),
                    (green + greenDelta * mix).toInt().coerceIn(0, 255),
                    (blue + blueDelta * mix).toInt().coerceIn(0, 255),
                )
            }
        }
        canvas.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLES,
            positions.size, positions, 0, null, 0, colors, 0, indices, 0, indices.size, paint,
        )
    }
}

private fun glowSmoothStep(low: Float, high: Float, value: Float): Float {
    val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
private fun VinylFluidGlow(animated: Boolean, primary: State<Color>, secondary: State<Color>) {
    val mesh = remember { VinylGlowMesh() }
    val phase = remember { mutableFloatStateOf(0.15f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(animated, lifecycleOwner) {
        if (!animated) return@LaunchedEffect
        val durationScale = coroutineContext[MotionDurationScale]
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { durationScale?.scaleFactor ?: 1f }.collectLatest { scale ->
                if (scale <= 0f) return@collectLatest
                var previousFrame = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        if (previousFrame == 0L) previousFrame = now
                        val elapsed = now - previousFrame
                        if (elapsed >= FluidGlowSpec.FrameIntervalNanos) {
                            // Resume from the same phase; never catch up after suspension/jank.
                            val seconds = elapsed.coerceAtMost(100_000_000L) / 1_000_000_000f
                            phase.floatValue = (phase.floatValue + seconds * GlowTwoPi /
                                (FluidGlowSpec.CycleSeconds * scale)) % GlowTwoPi
                            previousFrame = now
                        }
                    }
                    delay(24L)
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        // State is read only during drawing: motion never recomposes the player or cache.
        drawIntoCanvas { canvas ->
            mesh.draw(canvas.nativeCanvas, size.width, size.height, phase.floatValue, primary.value, secondary.value)
        }
    }
}

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

private const val GlowTransitionDurationMs = 850

/** Lift artwork pigments for OLED visibility; preserve hue and leave neutral artwork neutral. */
private fun glowTone(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    if (hsv[1] > 0.12f) hsv[1] = (hsv[1] * 1.18f).coerceAtMost(0.95f)
    hsv[2] = hsv[2].coerceIn(0.62f, 0.96f)
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
    val isImmersiveArtwork = playerDesignStyle == PlayerDesignStyle.ARTWORK_BLUR
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
    val shouldRenderArtworkBlur = (isBlur || isImmersiveArtwork) && artworkRequest != null

    // Palette colors crossfade only when artwork changes; the fluid motion below then works with
    // those stable two-color endpoints instead of continuously chasing extraction updates.
    val primaryTarget = remember(palette) { glowTone(palette.artworkPrimary) }
    val secondaryTarget = remember(palette) { glowTone(palette.artworkSecondary) }
    val primary = animateColorAsState(
        targetValue = primaryTarget,
        animationSpec = tween(GlowTransitionDurationMs),
        label = "vinyl-glow-primary",
    )
    val secondary = animateColorAsState(
        targetValue = secondaryTarget,
        animationSpec = tween(GlowTransitionDurationMs),
        label = "vinyl-glow-secondary",
    )
    // Base layer: a very low-alpha wash of the track's own palette mixed into near-black, so
    // the page never reads as flat pure-black even when no glow/blur/gradient style is active.
    // The style-specific layers below (blur, glow, gradient) still layer on top of this as
    // before; this only replaces what used to be plain solid black underneath them.
    val baseTint = remember(palette) {
        Brush.verticalGradient(
            colors = listOf(
                lerp(Color(0xFF0A0B0E), palette.artworkPrimary, 0.12f),
                Color(0xFF050506),
                lerp(Color(0xFF07080A), palette.artworkSecondary, 0.10f),
            ),
        )
    }
    Box(modifier = Modifier.fillMaxSize().background(baseTint)) {
        if (shouldRenderArtworkBlur) {
            AsyncImage(
                model = artworkRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }
                    .blur(
                        radius = (if (isImmersiveArtwork) blurRadius.coerceAtLeast(42f) else blurRadius)
                            .coerceIn(0f, 72f)
                            .dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    )
                    .alpha(if (isImmersiveArtwork) 0.86f else 0.72f),
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Color.Black.copy(alpha = if (isImmersiveArtwork) 0.30f else 0.38f),
                ),
            )
        }

        if (isVinyl) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.76f),
                        0.44f to Color.Black.copy(alpha = 0.58f),
                        0.78f to Color.Black.copy(alpha = 0.34f),
                        1f to Color.Black.copy(alpha = 0.22f),
                    ),
                ),
            )
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
            VinylFluidGlow(animated = isAnimatedGlow, primary = primary, secondary = secondary)
        }
    }
}
