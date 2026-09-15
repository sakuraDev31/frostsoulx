/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.player.frostsoul

import dev.vxs.frostsoulx.ui.player.LyricsZoomContainer

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import dev.vxs.frostsoulx.ui.frostsoul.FSIcon as Icon
import dev.vxs.frostsoulx.ui.frostsoul.FSText as Text
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import me.bush.translator.Translator
import dagger.hilt.android.EntryPointAccessors
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.utils.TranslatorLang
import dev.vxs.frostsoulx.utils.TranslatorLanguages
import dev.vxs.frostsoulx.di.LyricsHelperEntryPoint
import dev.vxs.frostsoulx.lyrics.core.LyricsLine
import dev.vxs.frostsoulx.lyrics.core.LyricsSyncState
import dev.vxs.frostsoulx.lyrics.core.LyricsWord
import dev.vxs.frostsoulx.utils.rememberPreference

private val LyricsHeaderPadding = PlayerLayoutTokens.MasterHorizontalPadding
private val LyricsActiveFontSize = PlayerLayoutTokens.LyricsActiveFontSize
private val LyricsInactiveFontSize = PlayerLayoutTokens.LyricsInactiveFontSize
private val LyricsControlLabelSize = 9.sp

/**
 * Renders karaoke directly from the singleton lyrics synchronization engine.
 *
 * [rawLyrics], [positionMs], and [durationMs] remain part of the player-surface contract while
 * the service owns parsing, caching, offset correction, and timestamp resolution. This prevents
 * a second parser or clock from drifting away from notification and overlay lyric consumers.
 */
@Composable
internal fun FSLyrics(
    rawLyrics: String?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    isLiked: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onOpenAudioOutput: () -> Unit = {},
    onRefetchLyrics: () -> Unit = {},
    isRefetchingLyrics: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val applicationContext = LocalContext.current.applicationContext
    val synchronizationEngine =
        remember(applicationContext) {
            EntryPointAccessors
                .fromApplication(applicationContext, LyricsHelperEntryPoint::class.java)
                .lyricsSynchronizationEngine()
        }
    val document by synchronizationEngine.documentState.collectAsState()
    val syncState by synchronizationEngine.state.collectAsState()
    val lines = document?.original?.lines.orEmpty()
    val currentIndex = syncState.currentLineIndex.takeIf { it in lines.indices } ?: -1
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val translatorLanguages = remember(applicationContext) { TranslatorLanguages.load(applicationContext) }
    var languageMenuExpanded by remember(rawLyrics) { mutableStateOf(false) }
    var showTranslation by remember(rawLyrics) { mutableStateOf(false) }
    var selectedLanguageCode by remember(rawLyrics) { mutableStateOf("ENGLISH") }
    var translatedLines by remember(rawLyrics) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isTranslating by remember(rawLyrics) { mutableStateOf(false) }
    var showOffsetDialog by remember(document?.songId) { mutableStateOf(false) }
    var draftOffsetMs by remember(document?.songId, document?.offsetMs) {
        mutableStateOf(document?.offsetMs ?: 0L)
    }
    val lyricsRepository = remember(applicationContext) {
        EntryPointAccessors
            .fromApplication(applicationContext, LyricsHelperEntryPoint::class.java)
            .lyricsRepository()
    }

    fun applyLyricsOffset(offsetMs: Long) {
        val safeOffset = offsetMs.coerceIn(-5_000L, 5_000L)
        synchronizationEngine.setOffset(safeOffset)
        document?.songId?.let { songId ->
            coroutineScope.launch { lyricsRepository.updateOffset(songId, safeOffset) }
        }
    }

    fun translateCurrentLyrics(languageCode: String) {
        if (lines.isEmpty() || isTranslating) return
        isTranslating = true
        coroutineScope.launch {
            try {
                val language = Language(languageCode)
                val translated =
                    withContext(Dispatchers.IO) {
                        val translator = Translator()
                        lines.mapIndexed { index, line ->
                            index to translator.translateBlocking(line.text, language).translatedText
                        }.toMap()
                    }
                translatedLines = translated
                showTranslation = true
            } catch (error: Exception) {
                Toast.makeText(
                    applicationContext,
                    "Translation failed: ${error.localizedMessage ?: "try again"}",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                isTranslating = false
            }
        }
    }

    // Park the active line roughly a third down the sheet, matching QQ's reading position,
    // and never fight a user-initiated scroll.
    LaunchedEffect(currentIndex, lines.size, isPlaying) {
        if (isPlaying && currentIndex >= 0 && listState.isScrollInProgress.not()) {
            listState.animateScrollToItem(
                index = (currentIndex - 2).coerceAtLeast(0),
                scrollOffset = 0,
            )
        }
    }

    PremiumLyricsBackgroundContainer(modifier = modifier) {
        if (lines.isEmpty()) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = LyricsHeaderPadding),
            ) {
                Text(
                    text = if (rawLyrics.isNullOrBlank()) "Lyrics are unavailable for this track." else "Preparing synchronized lyrics for this track.",
                    color = FrostSoulOnSurfaceMuted,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
                Text(
                    text = "FrostSoul keeps lyrics, notification updates, and overlays on one shared timeline.",
                    color = FrostSoulOnSurfaceMuted.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = LyricsHeaderPadding,
                        end = LyricsHeaderPadding,
                        top = 10.dp,
                        bottom = 10.dp,
                    ),
                ) {
                    Text(
                        text = title,
                        color = FrostSoulOnSurface,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artist,
                        color = FrostSoulOnSurfaceMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                // Lyric column: text begins flush with the page gutter on the left and keeps a
                // wider right inset, so long lines wrap instead of appearing to slide under the
                // right screen edge (QQ Music lyric-sheet behaviour).
                LyricsZoomContainer(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = PlayerLayoutTokens.LyricsTextStartInset,
                            end = PlayerLayoutTokens.LyricsTextEndInset,
                            top = 4.dp,
                            bottom = PlayerLayoutTokens.LyricsBottomControlsReserve,
                        ),
                        verticalArrangement = Arrangement.spacedBy(PlayerLayoutTokens.LyricsLineSpacing),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    itemsIndexed(
                        items = lines,
                        key = { _, line -> "${line.startMs}-${line.endMs}-${line.text}" },
                    ) { index, line ->
                        val displayLine = if (showTranslation) {
                            line.copy(text = translatedLines[index] ?: line.text, words = emptyList(), translation = null)
                        } else {
                            line
                        }
                        val isCurrent = index == currentIndex
                        FrostSoulKaraokeLine(
                            line = displayLine,
                            isCurrent = isCurrent,
                            isPast = currentIndex >= 0 && index < currentIndex,
                            currentWordIndex = if (isCurrent) syncState.currentWordIndex else -1,
                            wordProgress = if (isCurrent) syncState.wordProgress else 0f,
                            lineProgress = if (isCurrent) syncState.lineProgress else 0f,
                            onSeek = onSeek,
                        )
                    }
                }
                }
            }
        }

        FrostSoulLyricsBottomControls(
            onToggleLike = onToggleLike,
            onTogglePlayPause = onTogglePlayPause,
            onRefetchLyrics = onRefetchLyrics,
            isRefetchingLyrics = isRefetchingLyrics,
            isPlaying = isPlaying,
            isLiked = isLiked,
            showTranslation = showTranslation,
            isTranslating = isTranslating,
            onToggleTranslation = {
                if (showTranslation) {
                    showTranslation = false
                } else if (translatedLines.isNotEmpty()) {
                    showTranslation = true
                } else {
                    translateCurrentLyrics(selectedLanguageCode)
                }
            },
            onChooseTranslationLanguage = { code ->
                selectedLanguageCode = code
                languageMenuExpanded = false
                translateCurrentLyrics(code)
            },
            languageMenuExpanded = languageMenuExpanded,
            onLanguageMenuExpandedChange = { languageMenuExpanded = it },
            languages = translatorLanguages,
            selectedLanguageCode = selectedLanguageCode,
            currentOffsetMs = document?.offsetMs ?: 0L,
            onOpenOffset = {
                draftOffsetMs = document?.offsetMs ?: 0L
                showOffsetDialog = true
            },
        )

        if (showOffsetDialog) {
            AlertDialog(
                onDismissRequest = { showOffsetDialog = false },
                title = { Text("Lyrics sync offset") },
                text = {
                    Column {
                        Text(
                            text = "%+d ms".format(draftOffsetMs),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Slider(
                            value = draftOffsetMs.toFloat(),
                            onValueChange = { draftOffsetMs = it.toLong() },
                            valueRange = -5_000f..5_000f,
                            steps = 99,
                        )
                        Text(
                            text = "Positive values advance lyric selection; negative values delay it.",
                            color = FrostSoulOnSurfaceMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            applyLyricsOffset(draftOffsetMs)
                            showOffsetDialog = false
                        },
                    ) { Text("Apply") }
                },
                dismissButton = {
                    TextButton(onClick = { showOffsetDialog = false }) { Text("Cancel") }
                },
            )
        }

        // Compact inline spinner instead of a full-screen scrim, so lyrics stay readable
        // while a refetch or translation is in flight.
        if (isRefetchingLyrics || isTranslating) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.62f), androidx.compose.foundation.shape.CircleShape),
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

    }
}

@Composable
private fun BoxScope.FrostSoulLyricsBottomControls(
    onToggleLike: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onRefetchLyrics: () -> Unit,
    isRefetchingLyrics: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    showTranslation: Boolean,
    isTranslating: Boolean,
    onToggleTranslation: () -> Unit,
    onChooseTranslationLanguage: (String) -> Unit,
    languageMenuExpanded: Boolean,
    onLanguageMenuExpandedChange: (Boolean) -> Unit,
    languages: List<TranslatorLang>,
    selectedLanguageCode: String,
    currentOffsetMs: Long,
    onOpenOffset: () -> Unit,
) {
    // Floating action pill: kept centred with a fixed bottom offset and uniform 6.dp inner
    // padding so Refresh / Translate / Like and the play button share one baseline and the
    // pill never clips against the screen edge.
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(30.dp))
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        LyricsActionButton(
            painter = painterResource(R.drawable.tune),
            label = "Offset",
            contentDescription = "Adjust lyrics sync offset (${currentOffsetMs}ms)",
            onClick = onOpenOffset,
            enabled = !isRefetchingLyrics && !isTranslating,
            active = currentOffsetMs != 0L,
        )
        LyricsActionButton(
            painter = painterResource(R.drawable.sync),
            label = "Refresh",
            contentDescription = "Refetch lyrics",
            onClick = onRefetchLyrics,
            enabled = !isRefetchingLyrics && !isTranslating,
            active = isRefetchingLyrics,
        )
        Box {
            LyricsActionButton(
                painter = painterResource(R.drawable.translate),
                label = "Translate",
                contentDescription = "Translate lyrics",
                onClick = {
                    if (!isTranslating) {
                        if (showTranslation) onToggleTranslation() else onLanguageMenuExpandedChange(true)
                    }
                },
                enabled = !isRefetchingLyrics,
                active = showTranslation || isTranslating,
            )
            if (languageMenuExpanded) {
                AlertDialog(
                    onDismissRequest = { onLanguageMenuExpandedChange(false) },
                    containerColor = FrostSoulTheme.colors.surfaceRaised,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    shape = RoundedCornerShape(28.dp),
                    title = {
                        Column {
                            Text(
                                text = "Translate lyrics",
                                color = FrostSoulTheme.colors.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Choose a language for this song",
                                color = FrostSoulOnSurfaceMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            languages.forEach { language ->
                                val selected = language.code == selectedLanguageCode
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected) FrostSoulTheme.colors.accentBright.copy(alpha = 0.16f)
                                            else FrostSoulTheme.colors.surface.copy(alpha = 0.42f),
                                        )
                                        .clickable { onChooseTranslationLanguage(language.code) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = language.name,
                                        color = if (selected) FrostSoulTheme.colors.accentBright else FrostSoulTheme.colors.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (selected) {
                                        Text(
                                            text = "Selected",
                                            color = FrostSoulTheme.colors.accentBright,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { onLanguageMenuExpandedChange(false) }) {
                            Text("Cancel", color = FrostSoulTheme.colors.accentBright)
                        }
                    },
                )
            }
        }
        LyricsActionButton(
            painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
            label = "Like",
            contentDescription = "Like this song",
            onClick = onToggleLike,
            enabled = !isRefetchingLyrics && !isTranslating,
            active = isLiked,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(46.dp)
                .background(FrostSoulTheme.colors.accentBright, androidx.compose.foundation.shape.CircleShape)
                .clickable(
                    enabled = !isRefetchingLyrics && !isTranslating,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlayPause,
                ),
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = "Play or pause",
                tint = FrostSoulTheme.colors.surface,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun LyricsActionButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    active: Boolean,
) {
    // Fixed-width slot keeps the icon perfectly centred over its label regardless of the
    // label's text width, so the pill's items stay evenly spaced.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(54.dp),
    ) {
        FSIconButton(
            painter = painter,
            contentDescription = contentDescription,
            onClick = onClick,
            enabled = enabled,
            active = active,
            compact = true,
            buttonSize = 26.dp,
            iconSize = 20.dp,
            showContainer = false,
        )
        Text(
            text = label,
            color = if (active) FrostSoulTheme.colors.accentBright else FrostSoulOnSurfaceMuted,
            fontSize = LyricsControlLabelSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PremiumLyricsBackgroundContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // The page itself is full-bleed; the shared gutter is applied by the header and the
    // lyric list separately so lyric text can hug the left edge without a nested inset
    // pushing wrapped lines under the right side of the screen.
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PlayerLayoutTokens.MasterHorizontalPadding),
        content = content,
    )
}

@Composable
private fun FrostSoulKaraokeLine(
    line: LyricsLine,
    isCurrent: Boolean,
    isPast: Boolean,
    currentWordIndex: Int,
    wordProgress: Float,
    lineProgress: Float,
    onSeek: (Long) -> Unit,
) {
    val emphasis by animateFloatAsState(
        targetValue = if (isCurrent) 1f else if (isPast) 0.60f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 500f),
        label = "fs-karaoke-line-emphasis",
    )
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
        label = "fs-karaoke-line-scale",
    )
    val lineColor =
        when {
            isCurrent -> FrostSoulOnSurface
            isPast -> FrostSoulOnSurfaceMuted.copy(alpha = 0.70f)
            else -> FrostSoulOnSurfaceMuted.copy(alpha = 0.45f)
        }
    val annotatedText =
        remember(line, currentWordIndex, wordProgress, lineProgress, isCurrent, lineColor, emphasis) {
            line.asKaraokeAnnotatedText(
                activeLine = isCurrent,
                currentWordIndex = currentWordIndex,
                wordProgress = wordProgress,
                lineProgress = lineProgress,
                inactiveColor = lineColor,
                activeColor = Color.White,
                glowStrength = emphasis,
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    // Grow the active line from its left edge so emphasis never pushes text
                    // beyond the right gutter.
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f
                }.clickable(
                    enabled = line.startMs >= 0L,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSeek(line.startMs) },
                ),
    ) {
        Text(
            text = annotatedText,
            color = lineColor,
            fontSize = if (isCurrent) LyricsActiveFontSize else LyricsInactiveFontSize,
            lineHeight = PlayerLayoutTokens.LyricsLineHeight,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Start,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        line.translation?.takeIf { it.isNotBlank() }?.let { translation ->
            Text(
                text = translation,
                color = if (isCurrent) FrostSoulOnSurface.copy(alpha = 0.88f) else FrostSoulOnSurfaceMuted,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        line.romanization?.takeIf { it.isNotBlank() }?.let { romanization ->
            Text(
                text = romanization,
                color = if (isCurrent) FrostSoulOnSurface.copy(alpha = 0.72f) else FrostSoulOnSurfaceMuted.copy(alpha = 0.78f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

private fun LyricsLine.asKaraokeAnnotatedText(
    activeLine: Boolean,
    currentWordIndex: Int,
    wordProgress: Float,
    lineProgress: Float,
    inactiveColor: Color,
    activeColor: Color,
    glowStrength: Float,
) =
    buildAnnotatedString {
        if (!activeLine) {
            withStyle(SpanStyle(color = inactiveColor)) { append(text) }
            return@buildAnnotatedString
        }

        val timedWords =
            if (words.isNotEmpty()) {
                words
            } else {
                text.split(Regex("\\s+")).filter { it.isNotBlank() }.map { LyricsWord(it, 0L, 0L) }
            }
        if (timedWords.isEmpty()) return@buildAnnotatedString

        val fallbackProgress = lineProgress.coerceIn(0f, 1f)
        val fallbackPosition = fallbackProgress * timedWords.size
        val fallbackIndex = fallbackPosition.toInt().coerceAtMost(timedWords.lastIndex)
        val fallbackWordProgress = (fallbackPosition - fallbackIndex).coerceIn(0f, 1f)
        val hasRealWordTiming = words.isNotEmpty()
        timedWords.forEachIndexed { index, word ->
            val progress =
                if (hasRealWordTiming) {
                    when {
                        index < currentWordIndex -> 1f
                        index == currentWordIndex -> wordProgress.coerceIn(0f, 1f)
                        else -> 0f
                    }
                } else {
                    when {
                        index < fallbackIndex -> 1f
                        index == fallbackIndex -> fallbackWordProgress
                        else -> 0f
                    }
                }
            withStyle(
                wordKaraokeStyle(
                    progress = progress,
                    inactiveColor = inactiveColor,
                    activeColor = activeColor,
                    glowStrength = glowStrength,
                ),
            ) {
                append(word.text)
                if (index < timedWords.lastIndex) append(" ")
            }
        }
    }

private fun wordKaraokeStyle(
    progress: Float,
    inactiveColor: Color,
    activeColor: Color,
    glowStrength: Float,
): SpanStyle {
    val fill = progress.coerceIn(0f, 1f)
    val brush =
        if (fill > 0.02f && fill < 0.98f) {
            Brush.horizontalGradient(
                colorStops =
                    arrayOf(
                        0f to activeColor,
                        fill to activeColor,
                        (fill + 0.018f).coerceAtMost(1f) to inactiveColor,
                        1f to inactiveColor,
                    ),
            )
        } else {
            null
        }
    val color =
        when {
            fill <= 0.02f -> inactiveColor
            fill >= 0.98f -> activeColor
            else -> Color.Unspecified
        }
    val shadow =
        if (fill > 0.02f) {
            Shadow(
                color = activeColor.copy(alpha = 0.52f * fill * glowStrength),
                blurRadius = 18f * fill,
            )
        } else {
            null
        }
    return if (brush != null) {
        SpanStyle(brush = brush, shadow = shadow)
    } else {
        SpanStyle(color = color, shadow = shadow)
    }
}

private fun lerpColor(
    start: Color,
    stop: Color,
    fraction: Float,
): Color =
    Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction,
    )
