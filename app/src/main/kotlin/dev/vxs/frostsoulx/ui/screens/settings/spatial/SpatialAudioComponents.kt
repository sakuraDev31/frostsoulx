package dev.vxs.frostsoulx.ui.screens.settings.spatial

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.vxs.frostsoulx.R
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Consistent card style with 20dp radius, SurfaceContainer background, and optional 1.5dp primary outline.
 */
@Composable
fun SpatialCard(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outlineModifier = if (isActive) {
        Modifier.border(
            width = SpatialAudioTokens.StrokeOutline,
            color = MaterialTheme.colorScheme.primary,
            shape = SpatialAudioTokens.CardShape,
        )
    } else {
        Modifier
    }

    val clickModifier = if (onClick != null) {
        Modifier.bouncyClick(onClick = onClick)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(outlineModifier)
            .clip(SpatialAudioTokens.CardShape)
            .then(clickModifier),
        shape = SpatialAudioTokens.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = SpatialAudioTokens.SurfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpatialAudioTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.RowGap),
        ) {
            content()
        }
    }
}

/**
 * Reusable AdvancedSlider for Fine Tune controls.
 * - Row: label left, tappable value right (exact typing dialog).
 * - ⓘ icon opens bottom sheet with plain-language explanation (no ? glyphs, no caption lines).
 * - Bipolar fills from center with detent at 0. Unipolar fills from left.
 * - Double-tap resets to default.
 * - Haptic tick at detent (0) and bounds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    isBipolar: Boolean = false,
    displayFormatter: (Float) -> String,
    infoDescription: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showExactEditDialog by remember { mutableStateOf(false) }
    var exactInputText by remember { mutableStateOf("") }
    var previousValue by remember { mutableFloatStateOf(value) }

    val resolvedHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackType.ContextClick
    } else {
        HapticFeedbackType.TextHandleMove
    }

    val bottomSheetState = rememberModalBottomSheetState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: ${displayFormatter(value)}"
            },
        verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.GridUnit),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpatialAudioTokens.GridUnit),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // ⓘ info icon button that opens clean bottom sheet explanation
                IconButton(
                    onClick = { showBottomSheet = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = "Explain $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Tappable value readout: double-tap resets, tap opens exact typing dialog
            Box(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .pointerInput(value, defaultValue) {
                        detectTapGestures(
                            onTap = {
                                exactInputText = String.format(java.util.Locale.US, "%.2f", value)
                                showExactEditDialog = true
                            },
                            onDoubleTap = {
                                haptic.performHapticFeedback(resolvedHaptic)
                                onValueChange(defaultValue)
                            },
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = displayFormatter(value),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Custom Slider with bipolar / unipolar styling and detent haptics
        Slider(
            value = value.coerceIn(range),
            onValueChange = { newVal ->
                if (enabled) {
                    // Detect passing through zero detent on bipolar sliders
                    if (isBipolar) {
                        if ((previousValue < 0f && newVal >= 0f) || (previousValue > 0f && newVal <= 0f)) {
                            haptic.performHapticFeedback(resolvedHaptic)
                        }
                    }
                    // Detect reaching bounds
                    if ((newVal == range.start && previousValue != range.start) ||
                        (newVal == range.endInclusive && previousValue != range.endInclusive)
                    ) {
                        haptic.performHapticFeedback(resolvedHaptic)
                    }
                    previousValue = newVal
                    onValueChange(newVal)
                }
            },
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SpatialAudioTokens.UnselectedTrackAlpha),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
    }

    // Bottom sheet for ⓘ information
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState,
            shape = SpatialAudioTokens.BottomSheetShape,
            containerColor = SpatialAudioTokens.SurfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpatialAudioTokens.ScreenPadding, vertical = SpatialAudioTokens.CardPadding)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = infoDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
                Text(
                    text = "Tip: Double-tap the value to reset to default ($defaultValue). Tap the value to type an exact number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Got it")
                }
            }
        }
    }

    // Exact numeric input dialog
    if (showExactEditDialog) {
        AlertDialog(
            onDismissRequest = { showExactEditDialog = false },
            title = { Text(text = "Set $label") },
            text = {
                OutlinedTextField(
                    value = exactInputText,
                    onValueChange = { exactInputText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    label = { Text("Value (${range.start} to ${range.endInclusive})") },
                    shape = SpatialAudioTokens.InputShape,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = exactInputText.toFloatOrNull()
                        if (parsed != null) {
                            onValueChange(parsed.coerceIn(range))
                        }
                        showExactEditDialog = false
                    },
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactEditDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = SpatialAudioTokens.CardShape,
            containerColor = SpatialAudioTokens.SurfaceContainerHigh,
        )
    }
}

/**
 * Live visualizer (Canvas, crisp, zero glow).
 * - Listener glyph in center with nose/head orientation.
 * - 2-3 thin concentric rings (1dp, onSurfaceVariant at 24% alpha).
 * - 8dp primary source dot whose position live-reflects Azimuth, Distance, and CarFader.
 * - Orbits when Moving sound is on (scaled by speed).
 * - Springs live to slider updates. Pauses when lifecycle is not RESUMED.
 */
@Composable
fun LiveVisualizerCanvas(
    azimuthDeg: Float,
    distanceM: Float,
    carFader: Float,
    isOrbiting: Boolean,
    orbitSpeed: Float = 1.0f,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenResumed by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isScreenResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SpatialAudioTokens.ConcentricRingAlpha)
    val listenerColor = MaterialTheme.colorScheme.onSurface

    // Infinite orbit transition (11 seconds base per circle / orbitSpeed)
    val baseDurationMs = (11_000f / orbitSpeed.coerceIn(0.2f, 3.0f)).roundToInt()
    val infiniteTransition = rememberInfiniteTransition(label = "OrbitVisualizer")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = baseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "OrbitAngle",
    )

    // Spring animated coordinates for smooth live movement
    val effectiveAzimuth = if (isOrbiting && isScreenResumed) orbitAngle else azimuthDeg
    val animatedAzimuth = remember { Animatable(effectiveAzimuth) }
    val animatedDistance = remember { Animatable(distanceM) }

    LaunchedEffect(effectiveAzimuth, distanceM) {
        launch {
            animatedAzimuth.animateTo(effectiveAzimuth, SpatialAudioTokens.SlideSpringSpec)
        }
        launch {
            animatedDistance.animateTo(distanceM, SpatialAudioTokens.SlideSpringSpec)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = minOf(size.width, size.height) * 0.42f

        // Draw 3 concentric rings (1dp width, 24% alpha)
        val ringSteps = listOf(0.33f, 0.66f, 1.0f)
        ringSteps.forEach { ratio ->
            drawCircle(
                color = ringColor,
                radius = maxRadius * ratio,
                center = center,
                style = Stroke(width = SpatialAudioTokens.StrokeHairline.toPx()),
            )
        }

        // Crosshair axes (thin 1dp lines, 24% alpha)
        drawLine(
            color = ringColor,
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = SpatialAudioTokens.StrokeHairline.toPx(),
        )
        drawLine(
            color = ringColor,
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = SpatialAudioTokens.StrokeHairline.toPx(),
        )

        // Draw listener glyph at center (Head circle 12dp + nose triangle)
        val headRadius = 7.dp.toPx()
        drawCircle(
            color = listenerColor,
            radius = headRadius,
            center = center,
        )
        // Listener nose facing forward (up)
        val nosePath = Path().apply {
            moveTo(center.x - 3.dp.toPx(), center.y - headRadius)
            lineTo(center.x + 3.dp.toPx(), center.y - headRadius)
            lineTo(center.x, center.y - headRadius - 5.dp.toPx())
            close()
        }
        drawPath(nosePath, color = listenerColor)

        // Source dot position calculated from azimuth and normalized distance (0.2m..10m -> 0.25..1.0 radius)
        val normalizedDist = ((animatedDistance.value - 0.2f) / 9.8f).coerceIn(0f, 1f)
        val radius = maxRadius * (0.28f + (normalizedDist * 0.68f))

        // Convert angle (0° = front, 90° = right in standard audio azimuth)
        val angleRad = (animatedAzimuth.value - 90f) * (PI / 180.0)
        val sourceX = center.x + (radius * cos(angleRad)).toFloat()
        val sourceY = center.y + (radius * sin(angleRad)).toFloat()

        // 8dp Primary Source Dot
        drawCircle(
            color = primaryColor,
            radius = 4.dp.toPx(),
            center = Offset(sourceX, sourceY),
        )

        // Subtle radiating ring around source dot (1.5dp stroke, 36% alpha)
        drawCircle(
            color = primaryColor.copy(alpha = 0.36f),
            radius = 8.dp.toPx(),
            center = Offset(sourceX, sourceY),
            style = Stroke(width = SpatialAudioTokens.StrokeHairline.toPx()),
        )
    }
}

/**
 * 4 Rotary Knobs (2x2): Hi-fi level, Reverb, Surround level, Dynamic punch.
 * Flat design: 270° arc track, 4dp primary arc, flat tonal center disc, 2dp pointer line.
 * Vertical drag to change, tap to type exact value.
 */
@Composable
fun RotaryKnob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    displayFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showDialog by remember { mutableStateOf(false) }
    var exactText by remember { mutableStateOf("") }
    val resolvedHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackType.ContextClick
    } else {
        HapticFeedbackType.TextHandleMove
    }

    val currentVal by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(range)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SpatialAudioTokens.UnselectedTrackAlpha)
    val discColor = SpatialAudioTokens.SurfaceContainerHigh

    val normalized = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: ${displayFormatter(value)}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.GridUnit),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .pointerInput(range.start, range.endInclusive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val span = currentRange.endInclusive - currentRange.start
                        var dragAccumulator = 0f
                        var isDragging = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                if (!isDragging) {
                                    exactText = String.format(java.util.Locale.US, "%.1f", currentVal)
                                    showDialog = true
                                }
                                break
                            }
                            val dragY = change.position.y - change.previousPosition.y
                            dragAccumulator += kotlin.math.abs(dragY)
                            if (dragAccumulator > 6f) {
                                isDragging = true
                                change.consume()
                                val delta = (-dragY / 200f) * span
                                val next = (currentVal + delta).coerceIn(currentRange.start, currentRange.endInclusive)
                                if ((next == currentRange.start && currentVal != currentRange.start) ||
                                    (next == currentRange.endInclusive && currentVal != currentRange.endInclusive)) {
                                    haptic.performHapticFeedback(resolvedHaptic)
                                }
                                currentOnValueChange(next)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(72.dp)) {
                val strokePx = 4.dp.toPx()
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val arcTopLeft = Offset(strokePx / 2f, strokePx / 2f)

                // 270° arc: start at 135° (bottom left) and sweep 270° to 405° (bottom right)
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )

                // Active primary arc
                val activeSweep = 270f * normalized
                if (activeSweep > 0.5f) {
                    drawArc(
                        color = primaryColor,
                        startAngle = 135f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }

                // Flat tonal center disc
                val discRadius = (size.width / 2f) - strokePx - 3.dp.toPx()
                drawCircle(
                    color = discColor,
                    radius = discRadius,
                    center = Offset(size.width / 2f, size.height / 2f),
                )

                // 2dp pointer line indicating angle
                val currentAngleDeg = 135f + activeSweep
                val currentAngleRad = currentAngleDeg * (PI / 180.0)
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val innerPoint = Offset(
                    (centerOffset.x + (discRadius * 0.35f * cos(currentAngleRad))).toFloat(),
                    (centerOffset.y + (discRadius * 0.35f * sin(currentAngleRad))).toFloat(),
                )
                val outerPoint = Offset(
                    (centerOffset.x + (discRadius * 0.85f * cos(currentAngleRad))).toFloat(),
                    (centerOffset.y + (discRadius * 0.85f * sin(currentAngleRad))).toFloat(),
                )
                drawLine(
                    color = primaryColor,
                    start = innerPoint,
                    end = outerPoint,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = displayFormatter(value),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = label) },
            text = {
                OutlinedTextField(
                    value = exactText,
                    onValueChange = { exactText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    label = { Text("Value (${range.start} to ${range.endInclusive})") },
                    shape = SpatialAudioTokens.InputShape,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = exactText.toFloatOrNull()
                        if (parsed != null) {
                            onValueChange(parsed.coerceIn(range))
                        }
                        showDialog = false
                    },
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = SpatialAudioTokens.CardShape,
            containerColor = SpatialAudioTokens.SurfaceContainerHigh,
        )
    }
}
