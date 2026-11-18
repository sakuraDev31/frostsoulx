package dev.vxs.frostsoulx.ui.screens.settings.spatial

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.playback.ImmersiveControls
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// 10 Standard Equalizer Bands
val StandardEqFrequencies = listOf(
    "31" to 31,
    "62" to 62,
    "125" to 125,
    "250" to 250,
    "500" to 500,
    "1k" to 1000,
    "2k" to 2000,
    "4k" to 4000,
    "8k" to 8000,
    "16k" to 16000,
)

enum class EqPreset(val label: String, val gainsDb: List<Float>) {
    CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    POP("Pop", listOf(-1.5f, -0.5f, 1.0f, 2.5f, 3.5f, 3.0f, 1.5f, 0.5f, -0.5f, -1.0f)),
    DANCE("Dance", listOf(4.5f, 3.5f, 2.0f, 0.5f, 0.0f, 1.5f, 3.0f, 4.0f, 3.5f, 2.5f)),
    ROCK("Rock", listOf(4.0f, 3.0f, 1.5f, -0.5f, -1.5f, -0.5f, 1.5f, 3.0f, 4.0f, 4.5f)),
    VOCAL("Vocal", listOf(-2.5f, -1.5f, 0.0f, 1.5f, 3.5f, 4.0f, 3.0f, 1.5f, 0.5f, -1.0f)),
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
}

@Composable
fun EqualizerTab(
    currentControls: ImmersiveControls,
    onControlsChange: (ImmersiveControls) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPreset by remember { mutableStateOf(EqPreset.FLAT) }

    // 10 Band Gain levels in dB (-12dB to +12dB)
    val bandGains = remember {
        mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SpatialAudioTokens.ScreenPadding,
                end = SpatialAudioTokens.ScreenPadding,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + SpatialAudioTokens.SectionGap,
            ),
            verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.SectionGap),
        ) {
            // Preset Cards Row (scrollable): Custom, Pop, Dance, Rock, Vocal, Flat
            item(key = "eq_preset_cards_row") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(SpatialAudioTokens.ChipGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EqPreset.entries.forEach { preset ->
                            val isSelected = preset == selectedPreset
                            Surface(
                                modifier = Modifier
                                    .bouncyClick {
                                        selectedPreset = preset
                                        if (preset != EqPreset.CUSTOM) {
                                            preset.gainsDb.forEachIndexed { i, db ->
                                                if (i < bandGains.size) bandGains[i] = db
                                            }
                                        }
                                    },
                                shape = SpatialAudioTokens.CardShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else SpatialAudioTokens.SurfaceContainerHigh,
                                border = BorderStroke(
                                    if (isSelected) SpatialAudioTokens.StrokeOutline else SpatialAudioTokens.StrokeHairline,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Equalizer Card: Header "Equalizer", Reset & Share text buttons, 10 vertical sliders
            item(key = "eq_sliders_card") {
                SpatialCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Card Header: Equalizer + Reset + Share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Equalizer",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val backup = bandGains.toList()
                                        for (i in bandGains.indices) bandGains[i] = 0f
                                        selectedPreset = EqPreset.FLAT
                                        scope.launch {
                                            val res = snackbarHostState.showSnackbar("Equalizer reset · Undo", "Undo")
                                            if (res == SnackbarResult.ActionPerformed) {
                                                backup.forEachIndexed { idx, v -> bandGains[idx] = v }
                                            }
                                        }
                                    },
                                ) {
                                    Text("Reset")
                                }

                                TextButton(
                                    onClick = {
                                        val shareText = buildString {
                                            append("FrostSoulX EQ Settings (${selectedPreset.label}):\n")
                                            StandardEqFrequencies.forEachIndexed { i, (label, _) ->
                                                append("$label Hz: ${String.format(Locale.US, "%+.1f dB", bandGains[i])}\n")
                                            }
                                        }
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "FrostSoulX EQ Profile")
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Equalizer"))
                                    },
                                ) {
                                    Text("Share")
                                }
                            }
                        }

                        // 10 Vertical Sliders
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StandardEqFrequencies.forEachIndexed { index, (label, _) ->
                                VerticalBandSlider(
                                    frequencyLabel = label,
                                    valueDb = bandGains[index],
                                    minDb = -12f,
                                    maxDb = 12f,
                                    onValueChange = { newVal ->
                                        bandGains[index] = newVal
                                        selectedPreset = EqPreset.CUSTOM
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 4 Rotary Knobs (2x2): Hi-fi level, Reverb, Surround level, Dynamic punch
            item(key = "eq_rotary_knobs_card") {
                SpatialCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Tone & Acoustics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // 2x2 Grid of Knobs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Knob 1: Hi-fi level (-24dB to 0dB output headroom margin)
                            RotaryKnob(
                                label = "Hi-fi level",
                                value = currentControls.outputGainDb,
                                range = -24f..0f,
                                defaultValue = 0f,
                                displayFormatter = { String.format(Locale.US, "%.1f dB", it) },
                                modifier = Modifier.weight(1f),
                                onValueChange = { onControlsChange(currentControls.copy(outputGainDb = it)) },
                            )

                            // Knob 2: Reverb (0% to 100% roomMix)
                            RotaryKnob(
                                label = "Reverb",
                                value = currentControls.roomMix * 100f,
                                range = 0f..100f,
                                defaultValue = 18f,
                                displayFormatter = { "${it.roundToInt()}%" },
                                modifier = Modifier.weight(1f),
                                onValueChange = { onControlsChange(currentControls.copy(roomMix = it / 100f)) },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Knob 3: Surround level (0.0× to 2.0× stereoWidth)
                            RotaryKnob(
                                label = "Surround level",
                                value = currentControls.stereoWidth * 2f,
                                range = 0f..2f,
                                defaultValue = 1.0f,
                                displayFormatter = { String.format(Locale.US, "%.1f×", it) },
                                modifier = Modifier.weight(1f),
                                onValueChange = { onControlsChange(currentControls.copy(stereoWidth = it / 2f)) },
                            )

                            // Knob 4: Dynamic punch (-12dB to +6dB bassGainDb)
                            RotaryKnob(
                                label = "Dynamic punch",
                                value = currentControls.bassGainDb,
                                range = -12f..6f,
                                defaultValue = 0f,
                                displayFormatter = { String.format(Locale.US, "%+.1f dB", it) },
                                modifier = Modifier.weight(1f),
                                onValueChange = { onControlsChange(currentControls.copy(bassGainDb = it)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 10 Vertical Sliders:
 * - Center-fill from 0 dB
 * - Detent at 0 with haptic tick
 * - Thin track (4dp)
 * - 20dp thumb
 * - Frequency label below, value below that in tabular figures
 * - Double tap resets to 0 dB
 */
@Composable
private fun VerticalBandSlider(
    frequencyLabel: String,
    valueDb: Float,
    minDb: Float,
    maxDb: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val resolvedHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackType.ContextClick
    } else {
        HapticFeedbackType.TextHandleMove
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SpatialAudioTokens.UnselectedTrackAlpha)
    val thumbColor = MaterialTheme.colorScheme.primary

    val sliderHeight = 130.dp
    val sliderWidth = 32.dp

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "$frequencyLabel Hz band: ${String.format(Locale.US, "%+.1f dB", valueDb)}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = sliderWidth, height = sliderHeight)
                .pointerInput(valueDb) {
                    detectTapGestures(
                        onDoubleTap = {
                            haptic.performHapticFeedback(resolvedHaptic)
                            onValueChange(0f)
                        },
                    )
                }
                .pointerInput(valueDb) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDb = (-dragAmount.y / 120f) * (maxDb - minDb)
                        var next = (valueDb + deltaDb).coerceIn(minDb, maxDb)
                        // Snap to 0 dB detent
                        if (abs(next) < 0.35f) {
                            if (valueDb != 0f) haptic.performHapticFeedback(resolvedHaptic)
                            next = 0f
                        } else if ((next == minDb && valueDb != minDb) || (next == maxDb && valueDb != maxDb)) {
                            haptic.performHapticFeedback(resolvedHaptic)
                        }
                        onValueChange(next)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidthPx = 4.dp.toPx()
                val thumbRadiusPx = 10.dp.toPx()
                val centerX = size.width / 2f
                val topY = thumbRadiusPx
                val bottomY = size.height - thumbRadiusPx
                val usableHeight = bottomY - topY
                val centerY = topY + (usableHeight / 2f)

                // Background track (4dp stroke, rounded ends)
                drawLine(
                    color = trackColor,
                    start = Offset(centerX, topY),
                    end = Offset(centerX, bottomY),
                    strokeWidth = trackWidthPx,
                    cap = StrokeCap.Round,
                )

                // Zero dB center tick (1dp line)
                drawLine(
                    color = trackColor.copy(alpha = 0.6f),
                    start = Offset(centerX - 6.dp.toPx(), centerY),
                    end = Offset(centerX + 6.dp.toPx(), centerY),
                    strokeWidth = 1.dp.toPx(),
                )

                // Normalized position: 0 dB is at centerY, maxDb is at topY, minDb is at bottomY
                val normalized = (valueDb - minDb) / (maxDb - minDb)
                val thumbY = bottomY - (normalized * usableHeight)

                // Center-fill from 0 dB
                if (abs(valueDb) > 0.05f) {
                    drawLine(
                        color = primaryColor,
                        start = Offset(centerX, centerY),
                        end = Offset(centerX, thumbY),
                        strokeWidth = trackWidthPx,
                        cap = StrokeCap.Round,
                    )
                }

                // 20dp Thumb (10dp radius)
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadiusPx,
                    center = Offset(centerX, thumbY),
                )
            }
        }

        // Frequency Label below
        Text(
            text = frequencyLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Value in Tabular figures below
        Text(
            text = String.format(Locale.US, "%+.1f", valueDb),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            ),
            color = if (abs(valueDb) > 0.05f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
