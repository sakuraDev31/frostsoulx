/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vxs.frostsoulx.ui.player

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.AodAccentStyle
import dev.vxs.frostsoulx.constants.AodAccentStyleKey
import dev.vxs.frostsoulx.constants.AodAmbientIntensityKey
import dev.vxs.frostsoulx.constants.AodArtworkGlowKey
import dev.vxs.frostsoulx.constants.AodAutoDimTimeoutKey
import dev.vxs.frostsoulx.constants.AodAutoDimmingKey
import dev.vxs.frostsoulx.constants.AodAutoLockEnabledKey
import dev.vxs.frostsoulx.constants.AodAutoLockTimeoutKey
import dev.vxs.frostsoulx.constants.AodBackgroundStyle
import dev.vxs.frostsoulx.constants.AodBackgroundStyleKey
import dev.vxs.frostsoulx.constants.AodBrightnessKey
import dev.vxs.frostsoulx.constants.AodClockStyle
import dev.vxs.frostsoulx.constants.AodClockStyleKey
import dev.vxs.frostsoulx.constants.AodContentPosition
import dev.vxs.frostsoulx.constants.AodContentPositionKey
import dev.vxs.frostsoulx.constants.AodControlSizeKey
import dev.vxs.frostsoulx.constants.AodControlStyle
import dev.vxs.frostsoulx.constants.AodControlStyleKey
import dev.vxs.frostsoulx.constants.AodGesturesEnabledKey
import dev.vxs.frostsoulx.constants.AodHorizontalPaddingKey
import dev.vxs.frostsoulx.constants.AodMarqueeTitlesKey
import dev.vxs.frostsoulx.constants.AodMinimalLockedStateKey
import dev.vxs.frostsoulx.constants.AodPixelShiftEnabledKey
import dev.vxs.frostsoulx.constants.AodProximityBlackoutKey
import dev.vxs.frostsoulx.constants.AodShakeToUnlockKey
import dev.vxs.frostsoulx.constants.AodShowAlbumKey
import dev.vxs.frostsoulx.constants.AodShowArtistKey
import dev.vxs.frostsoulx.constants.AodShowBatteryKey
import dev.vxs.frostsoulx.constants.AodShowClockKey
import dev.vxs.frostsoulx.constants.AodShowControlsKey
import dev.vxs.frostsoulx.constants.AodShowExitButtonKey
import dev.vxs.frostsoulx.constants.AodShowLyricTickerKey
import dev.vxs.frostsoulx.constants.AodShowProgressKey
import dev.vxs.frostsoulx.constants.AodShowThumbnailKey
import dev.vxs.frostsoulx.constants.AodShowTimeLabelsKey
import dev.vxs.frostsoulx.constants.AodTextAlignment
import dev.vxs.frostsoulx.constants.AodTextAlignmentKey
import dev.vxs.frostsoulx.constants.AodThumbnailShape
import dev.vxs.frostsoulx.constants.AodThumbnailShapeKey
import dev.vxs.frostsoulx.constants.AodThumbnailShapeRotationKey
import dev.vxs.frostsoulx.constants.AodThumbnailSizeKey
import dev.vxs.frostsoulx.constants.AodTitleMaxLinesKey
import dev.vxs.frostsoulx.constants.AodTouchLockEnabledKey
import dev.vxs.frostsoulx.constants.AodTrueAmbientModeKey
import dev.vxs.frostsoulx.constants.AodUnlockMethod
import dev.vxs.frostsoulx.constants.AodUnlockMethodKey
import dev.vxs.frostsoulx.constants.AodVerticalSpacingKey
import dev.vxs.frostsoulx.constants.EnableHapticFeedbackKey
import dev.vxs.frostsoulx.lyrics.LyricsUtils
import dev.vxs.frostsoulx.models.MediaMetadata
import dev.vxs.frostsoulx.ui.theme.PlayerColorExtractor
import dev.vxs.frostsoulx.ui.utils.toComposeShape
import dev.vxs.frostsoulx.utils.makeTimeString
import dev.vxs.frostsoulx.utils.rememberEnumPreference
import dev.vxs.frostsoulx.utils.rememberPreference

private val White70 = Color.White.copy(alpha = 0.70f)
private val White65 = Color.White.copy(alpha = 0.65f)
private val White35 = Color.White.copy(alpha = 0.35f)
private val White30 = Color.White.copy(alpha = 0.30f)
private val White15 = Color.White.copy(alpha = 0.15f)

@Composable
fun AodPlayerScreen(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    thumbnailCornerRadius: Float,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    lyricsText: String? = null,
    isLiked: Boolean = false,
    shuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    onToggleLike: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // AOD is intentionally a single, stable QQ-style surface. The former customization
    // settings are no longer read, so stale preferences cannot change its layout.
    val thumbnailShapeType = AodThumbnailShape.ROUNDED
    // Reference calibration at 1080x2400: artwork width ≈916 px / 3 = 305.3 dp.
    val thumbnailSize = 306f
    val thumbnailShapeRotation = 0
    val showThumbnail = true
    val showArtist = true
    val showAlbum = false
    val showProgress = false
    val showTimeLabels = true
    val showControls = true
    val showExitButton = true
    val showLyricTicker = true
    val backgroundStyle = AodBackgroundStyle.PURE_BLACK
    val accentStyle = AodAccentStyle.MONOCHROME
    val contentPosition = AodContentPosition.TOP
    val textAlignment = AodTextAlignment.CENTER
    val controlStyle = AodControlStyle.FILLED
    val controlSize = 72f
    val horizontalPadding = 30f
    val verticalSpacing = 18f
    val titleMaxLines = 1
    val ambientIntensity = 0f

    val touchLockEnabled = false
    val unlockMethod = AodUnlockMethod.SLIDE
    val showClock = true
    val clockStyle = AodClockStyle.BOLD_DIGITAL
    val showBattery = true
    val pixelShiftEnabled = false
    val autoDimming = false
    val autoDimTimeout = 5
    val gesturesEnabled = true
    val shakeToUnlock = false
    val autoLockEnabled = false
    val autoLockTimeout = 10
    val marqueeTitles = false
    val minimalLockedState = false
    val trueAmbientModeEnabled = false
    val aodBrightness = 0.15f
    val proximityBlackoutEnabled = false

    var isLocked by remember { mutableStateOf(touchLockEnabled) }
    var pixelShiftOffset by remember { mutableStateOf(IntOffset.Zero) }
    var isDimmed by remember { mutableStateOf(false) }
    var isCovered by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isAmbient = trueAmbientModeEnabled && isDimmed

    val contentAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.25f else 1.0f,
        animationSpec = tween(500),
        label = "dimAlpha",
    )

    fun resetInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (isDimmed) isDimmed = false
    }

    LaunchedEffect(Unit) {
        resetInteraction()
    }

    LaunchedEffect(pixelShiftEnabled) {
        if (pixelShiftEnabled) {
            val shifts = listOf(
                IntOffset(0, 0), IntOffset(8, 4), IntOffset(-8, -4),
                IntOffset(4, -8), IntOffset(-6, 6), IntOffset(6, -6)
            )
            var index = 0
            while (true) {
                delay(60000L)
                index = (index + 1) % shifts.size
                pixelShiftOffset = shifts[index]
            }
        } else {
            pixelShiftOffset = IntOffset.Zero
        }
    }

    LaunchedEffect(autoLockEnabled, autoLockTimeout, lastInteractionTime, isLocked) {
        if (!autoLockEnabled || isLocked) return@LaunchedEffect
        val timeoutMs = autoLockTimeout.coerceIn(3, 120) * 1000L
        delay(timeoutMs)
        isLocked = true
    }

    DisposableEffect(proximityBlackoutEnabled) {
        if (!proximityBlackoutEnabled) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor == null) return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val distance = event.values.getOrNull(0) ?: return
                val maxRange = proximitySensor.maximumRange
                isCovered = maxRange > 0f && distance <= (maxRange * 0.1f).coerceAtLeast(1.0f)
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    DisposableEffect(shakeToUnlock, isLocked) {
        if (!shakeToUnlock || !isLocked) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var hasBaseline = false
        var lastShakeTime = 0L
        var lastX = 0f; var lastY = 0f; var lastZ = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                if (!hasBaseline) {
                    lastX = x; lastY = y; lastZ = z
                    hasBaseline = true
                    return
                }
                val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
                lastX = x; lastY = y; lastZ = z
                val now = System.currentTimeMillis()
                if (delta > 18f && now - lastShakeTime > 1000L) {
                    lastShakeTime = now
                    isLocked = false
                    resetInteraction()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    LaunchedEffect(autoDimming, autoDimTimeout, lastInteractionTime, isDimmed) {
        if (!autoDimming || isDimmed) return@LaunchedEffect
        val timeoutMs = autoDimTimeout.coerceIn(3, 30) * 1000L
        delay(timeoutMs)
        isDimmed = true
    }

    DisposableEffect(isDimmed, isCovered, aodBrightness, proximityBlackoutEnabled) {
        val window = (context as? Activity)?.window ?: (context as? android.service.dreams.DreamService)?.window
        window?.let { w ->
            val lp = w.attributes
            if (isCovered && proximityBlackoutEnabled) {
                lp.screenBrightness = 0.001f
            } else if (isDimmed) {
                lp.screenBrightness = aodBrightness.coerceIn(0.01f, 1.0f)
            } else {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            w.attributes = lp
        }
        onDispose {
            val window = (context as? Activity)?.window ?: (context as? android.service.dreams.DreamService)?.window
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }
    var extractedArtworkColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(mediaMetadata.thumbnailUrl) {
        val url = mediaMetadata.thumbnailUrl ?: return@LaunchedEffect
        val fallbackColor = 0xFF121212.toInt()
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
            .allowHardware(false)
            .build()
        val result = runCatching {
            withContext(Dispatchers.IO) {
                context.imageLoader.execute(request)
            }
        }.getOrNull()

        if (result != null) {
            val bitmap = result.image?.toBitmap()
            if (bitmap != null) {
                val palette = withContext(Dispatchers.Default) {
                    Palette.from(bitmap)
                        .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                        .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                        .generate()
                }
                extractedArtworkColors = PlayerColorExtractor.extractGradientColors(
                    palette = palette,
                    fallbackColor = fallbackColor,
                )
            }
        }
    }

    val dominantArtworkColor = extractedArtworkColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val lyricsEntries = remember(lyricsText) {
        lyricsText?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { LyricsUtils.parseLyrics(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }
    val currentLyricIndex = remember(lyricsEntries, position) {
        if (lyricsEntries.isEmpty()) -1 else LyricsUtils.findCurrentLineIndex(lyricsEntries, position)
    }
    val currentLyricLine = lyricsEntries.getOrNull(currentLyricIndex)?.text?.takeIf { it.isNotBlank() }
    val nextLyricLine = lyricsEntries.getOrNull(currentLyricIndex + 1)?.text?.takeIf { it.isNotBlank() }

    val targetAccentColor =
        Color(0xFF52BF92)

    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(1000),
        label = "accentColorMorph",
    )
    val thumbnailShape =
        thumbnailShapeType.toComposeShape(
            cornerRadius = thumbnailCornerRadius,
            startAngle = thumbnailShapeRotation,
        )
    val artworkSize = thumbnailSize.coerceIn(160f, 340f).dp
    val artworkSizePx = with(density) { artworkSize.roundToPx().coerceAtLeast(1) }
    val imageRequest =
        remember(context, mediaMetadata.thumbnailUrl, artworkSizePx) {
            ImageRequest
                .Builder(context)
                .data(mediaMetadata.thumbnailUrl)
                .size(artworkSizePx, artworkSizePx)
                .allowHardware(true)
                .build()
        }
    val artistText =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString { it.name }
        }
    val contentAlignment = contentPosition.toBoxAlignment()
    val textHorizontalAlignment = textAlignment.toHorizontalAlignment()
    val textAlign = textAlignment.toTextAlign()

    BackHandler(enabled = true) {
        if (isLocked) {
            resetInteraction()
        } else {
            onExit()
        }
    }

    val currentLyric = lyricsEntries.getOrNull(currentLyricIndex)?.text.orEmpty()
    val previousLyric = lyricsEntries.getOrNull(currentLyricIndex - 1)?.text.orEmpty()
    val followingLyric = lyricsEntries.getOrNull(currentLyricIndex + 1)?.text.orEmpty()
    val clockState = remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockState.value = java.time.LocalDateTime.now()
            delay(1000L)
        }
    }
    val clockText = remember(clockState.value) {
        clockState.value.format(java.time.format.DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"))
    }
    val dateText = remember(clockState.value) {
        clockState.value.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM EEE"))
    }
    val showFullContent = !isLocked || !minimalLockedState

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(gesturesEnabled, isLocked) {
                detectTapGestures(
                    onTap = { resetInteraction() },
                    onDoubleTap = {
                        resetInteraction()
                        if (gesturesEnabled && !isLocked) onPlayPause()
                    },
                )
            }
            .pointerInput(gesturesEnabled, isLocked) {
                detectHorizontalDragGestures(
                    onDragStart = { resetInteraction() },
                    onHorizontalDrag = { _, _ -> },
                    onDragEnd = { resetInteraction() },
                )
            }
            .pointerInput(gesturesEnabled, isLocked) {
                if (gesturesEnabled && !isLocked) {
                    var accumulated = 0f
                    detectVerticalDragGestures(
                        onDragStart = { resetInteraction(); accumulated = 0f },
                        onVerticalDrag = { _, amount ->
                            resetInteraction()
                            accumulated += amount
                            if (kotlin.math.abs(accumulated) > 40f) {
                                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                                val direction = if (accumulated < 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
                                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, android.media.AudioManager.FLAG_SHOW_UI)
                                accumulated = 0f
                            }
                        },
                        onDragEnd = { resetInteraction() },
                    )
                }
            }
            .background(Color.Black),
    ) {
        // Full-screen blurred artwork stays behind every foreground element.
        if (!mediaMetadata.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(35.dp).alpha(0.64f),

            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.30f),
                    0.48f to Color.Black.copy(alpha = 0.42f),
                    0.80f to Color.Black.copy(alpha = 0.48f),
                    1f to Color.Black.copy(alpha = 0.52f),
                ),
            ),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .offset { pixelShiftOffset }
                .alpha(contentAlpha)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 16.dp),
        ) {
            // Size against both axes: square on phones, never clipped on short displays.
            val landscape = maxWidth > maxHeight
            val referenceArtworkSize = minOf(maxWidth, maxHeight * if (landscape) 0.52f else 0.46f)
                            Column(
                modifier = Modifier.fillMaxWidth(if (landscape) 0.48f else 0.84f),

                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = clockText,
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 42.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        text = dateText,
                        color = White65,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
                Text(
                    text = mediaMetadata.title,
                    color = White65,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE),
                )
                Text(
                    text = artistText,
                    color = White65,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showThumbnail && showFullContent) {
                Box(
                    modifier = Modifier
                        .align(if (landscape) Alignment.TopEnd else Alignment.TopCenter)
                        .offset(y = if (landscape) 8.dp else (maxHeight * 0.53f - referenceArtworkSize / 2))
                        .size(referenceArtworkSize)
                        .clip(RoundedCornerShape(1.dp)),
                ) {
                    AsyncImage(
                        model = mediaMetadata.thumbnailUrl,
                        contentDescription = "Artwork for ${mediaMetadata.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.58f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.82f),
                                ),
                            ),
                    )
                    if (showLyricTicker && (previousLyric.isNotBlank() || currentLyric.isNotBlank() || followingLyric.isNotBlank())) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            if (previousLyric.isNotBlank()) {
                                Text(
                                    text = previousLyric,
                                    color = White65,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (currentLyric.isNotBlank()) {
                                Text(
                                    text = currentLyric,
                                    color = accentColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (followingLyric.isNotBlank()) {
                                Text(
                                    text = followingLyric,
                                    color = White65,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            AodReferenceControls(
                isPlaying = isPlaying,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                isLiked = isLiked,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                accentColor = accentColor,
                onPlayPause = { resetInteraction(); onPlayPause() },
                onSkipPrevious = { resetInteraction(); onSkipPrevious() },
                onSkipNext = { resetInteraction(); onSkipNext() },
                onToggleLike = { resetInteraction(); onToggleLike() },
                onToggleShuffle = { resetInteraction(); onToggleShuffle() },
                onToggleRepeat = { resetInteraction(); onToggleRepeat() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            )

            if (showExitButton && !isAmbient) {
                // Keep the reference's unobtrusive chevrons, with both swipe and accessible tap.
                val exitThreshold = with(density) { 64.dp.toPx() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).width(120.dp).height(48.dp)
                        .clickable(onClickLabel = "Exit always-on display", onClick = onExit)
                        .pointerInput(onExit, exitThreshold) {
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
                                onDragEnd = { if (distance >= exitThreshold) onExit(); distance = 0f },
                                onDragCancel = { distance = 0f },
                            )
                        },
                ) {
                    Text("›››", color = White65, fontSize = 32.sp, fontWeight = FontWeight.Light)
                }
            }
        }

        if (isCovered && proximityBlackoutEnabled) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(999f))
        }
    }
}

@Composable
private fun AodReferenceControls(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    isLiked: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        val scale = (maxWidth / 450.dp).coerceIn(0.65f, 1f)
        val circle = 54.dp * scale
        val sideTouch = 44.dp * scale
        val stroke = 1.8.dp * scale
        val centerGap = 26.dp * scale

        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp * scale),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(centerGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AodSpecCircleButton(R.drawable.skip_previous, "Previous track", accentColor, onSkipPrevious, canSkipPrevious, circle, stroke, 22.dp * scale)
                AodSpecCircleButton(if (isPlaying) R.drawable.pause else R.drawable.play, if (isPlaying) "Pause" else "Play", accentColor, onPlayPause, true, circle, stroke, 24.dp * scale)
                AodSpecCircleButton(R.drawable.skip_next, "Next track", accentColor, onSkipNext, canSkipNext, circle, stroke, 22.dp * scale)
            }
            Box(
                modifier = Modifier
                    .size(sideTouch)
                    .align(Alignment.CenterStart)
                    .offset(x = (67.5.dp * scale) - sideTouch / 2f)
                    .clickable(onClick = onToggleRepeat),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (repeatMode == 1) R.drawable.repeat_one_on else R.drawable.repeat),
                    contentDescription = "Repeat mode",
                    tint = if (repeatMode == 0) White70 else accentColor,
                    modifier = Modifier.size(21.dp * scale),
                )
            }
            Box(
                modifier = Modifier
                    .size(sideTouch)
                    .align(Alignment.CenterStart)
                    .offset(x = (380.5.dp * scale) - sideTouch / 2f)
                    .clickable(onClick = onToggleLike),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = if (isLiked) "Remove from favorites" else "Add to favorites",
                    tint = if (isLiked) accentColor else Color.White,
                    modifier = Modifier.size(23.dp * scale),
                )
            }
        }
    }
}

@Composable
private fun AodSpecCircleButton(
    icon: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    diameter: androidx.compose.ui.unit.Dp,
    stroke: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    val color = tint.copy(alpha = if (enabled) 1f else 0.3f)
    Box(
        modifier = Modifier
            .size(diameter)
            .border(stroke, color, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = color, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun AodSliderSection(
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    accentColor: Color,
    showTimeLabels: Boolean,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
) {
    val seekEnabled = duration > 0L && duration != C.TIME_UNSET
    val displayPosition = sliderPosition ?: position
    val sliderValue =
        remember(displayPosition, seekEnabled) {
            if (seekEnabled) displayPosition.toFloat() else 0f
        }
    val positionText = remember(displayPosition) { makeTimeString(displayPosition) }
    val durationText =
        remember(duration, seekEnabled) {
            if (seekEnabled) makeTimeString(duration) else ""
        }
    val sliderColors =
        SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            inactiveTrackColor = White30,
            disabledThumbColor = White30,
            disabledActiveTrackColor = White30,
            disabledInactiveTrackColor = White15,
        )

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { onSeek(it.toLong()) },
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..(if (seekEnabled) duration.toFloat() else 1f),
            enabled = seekEnabled,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showTimeLabels) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = positionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = White65,
                )
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = White65,
                )
            }
        }
    }
}

@Composable
private fun AodControls(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    controlStyle: AodControlStyle,
    controlSize: Float,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val playButtonSize = controlSize.dp
    val skipButtonSize = (controlSize * 0.75f).dp
    val playIconSize = (controlSize * 0.5f).dp
    val skipIconSize = (controlSize * 0.5f).dp
    val playButtonColors =
        IconButtonDefaults.filledIconButtonColors(
            containerColor = accentColor,
            contentColor = if (accentColor == Color.White) Color.Black else MaterialTheme.colorScheme.onPrimary,
        )
    val tonalButtonColors =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = accentColor.copy(alpha = 0.22f),
            contentColor = Color.White,
        )

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
                onSkipPrevious()
            },
            enabled = canSkipPrevious,
            modifier = Modifier.size(skipButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_previous),
                contentDescription = null,
                tint = if (canSkipPrevious) Color.White else White35,
                modifier = Modifier.size(skipIconSize),
            )
        }

        when (controlStyle) {
            AodControlStyle.FILLED -> {
                FilledIconButton(
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onPlayPause()
                    },
                    modifier =
                        Modifier
                            .size(playButtonSize)
                            .clip(CircleShape),
                    colors = playButtonColors,
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(playIconSize),
                    )
                }
            }

            AodControlStyle.TONAL -> {
                FilledTonalIconButton(
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onPlayPause()
                    },
                    modifier =
                        Modifier
                            .size(playButtonSize)
                            .clip(CircleShape),
                    colors = tonalButtonColors,
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(playIconSize),
                    )
                }
            }

            AodControlStyle.MINIMAL -> {
                IconButton(
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onPlayPause()
                    },
                    modifier = Modifier.size(playButtonSize),
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(playIconSize),
                    )
                }
            }
        }

        IconButton(
            onClick = {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
                onSkipNext()
            },
            enabled = canSkipNext,
            modifier = Modifier.size(skipButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_next),
                contentDescription = null,
                tint = if (canSkipNext) Color.White else White35,
                modifier = Modifier.size(skipIconSize),
            )
        }
    }
}

@Composable
private fun Modifier.aodBackground(
    style: AodBackgroundStyle,
    accentColor: Color,
    ambientIntensity: Float,
): Modifier {
    val alpha = ambientIntensity.coerceIn(0f, 1f)
    val brush =
        remember(style, accentColor, alpha) {
            when (style) {
                AodBackgroundStyle.PURE_BLACK -> {
                    Brush.verticalGradient(listOf(Color.Black, Color.Black))
                }

                AodBackgroundStyle.SOFT_RADIAL -> {
                    Brush.radialGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.30f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.TONAL_EDGE -> {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.25f * alpha),
                                Color.Black,
                                accentColor.copy(alpha = 0.15f * alpha),
                            ),
                    )
                }

                AodBackgroundStyle.AMBIENT_GLOW -> {
                    Brush.radialGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.35f * alpha),
                                accentColor.copy(alpha = 0.10f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.ADAPTIVE_ART -> {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.40f * alpha),
                                accentColor.copy(alpha = 0.15f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.FROSTED_WALLPAPER -> {
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFF1E1E24).copy(alpha = 0.60f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.ADAPTIVE_FROSTED -> {
                    Brush.linearGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.35f * alpha),
                                Color(0xFF121216),
                                Color.Black,
                            ),
                    )
                }
            }
        }

    return background(brush)
}

private fun AodContentPosition.toBoxAlignment(): Alignment =
    when (this) {
        AodContentPosition.TOP -> Alignment.TopCenter
        AodContentPosition.CENTER -> Alignment.Center
        AodContentPosition.BOTTOM -> Alignment.BottomCenter
    }

private fun AodTextAlignment.toTextAlign(): TextAlign =
    when (this) {
        AodTextAlignment.START -> TextAlign.Start
        AodTextAlignment.CENTER -> TextAlign.Center
        AodTextAlignment.END -> TextAlign.End
    }

private fun AodTextAlignment.toHorizontalAlignment(): Alignment.Horizontal =
    when (this) {
        AodTextAlignment.START -> Alignment.Start
        AodTextAlignment.CENTER -> Alignment.CenterHorizontally
        AodTextAlignment.END -> Alignment.End
    }
