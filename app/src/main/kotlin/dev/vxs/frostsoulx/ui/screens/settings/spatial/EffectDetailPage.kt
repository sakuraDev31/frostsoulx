package dev.vxs.frostsoulx.ui.screens.settings.spatial

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.playback.ImmersiveControls
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectDetailPage(
    effect: SpatialEffect,
    currentControls: ImmersiveControls,
    isUsing: Boolean,
    isFavorite: Boolean,
    movingSoundSpeed: Float,
    onMovingSoundSpeedChange: (Float) -> Unit,
    onApplyEffect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onControlsChange: (ImmersiveControls) -> Unit,
    onOpenFineTune: () -> Unit,
    onSaveAsPreset: (name: String, controls: ImmersiveControls, baseEffectName: String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("${effect.name} custom") }

    // Check if current controls differ from effect default controls
    val isEdited = remember(currentControls, effect.defaultControls) {
        currentControls.intensity != effect.defaultControls.intensity ||
            currentControls.roomPreset != effect.defaultControls.roomPreset ||
            currentControls.roomMix != effect.defaultControls.roomMix ||
            currentControls.reflectionAmount != effect.defaultControls.reflectionAmount ||
            currentControls.reverbTimeSeconds != effect.defaultControls.reverbTimeSeconds ||
            currentControls.roomSize != effect.defaultControls.roomSize ||
            currentControls.dampening != effect.defaultControls.dampening ||
            currentControls.stereoWidth != effect.defaultControls.stereoWidth ||
            currentControls.bassWidth != effect.defaultControls.bassWidth ||
            currentControls.bassGainDb != effect.defaultControls.bassGainDb ||
            currentControls.outputGainDb != effect.defaultControls.outputGainDb ||
            currentControls.carFader != effect.defaultControls.carFader ||
            currentControls.azimuth != effect.defaultControls.azimuth ||
            currentControls.elevation != effect.defaultControls.elevation ||
            currentControls.distance != effect.defaultControls.distance ||
            currentControls.orbitEnabled != effect.defaultControls.orbitEnabled ||
            currentControls.quantumFrames != effect.defaultControls.quantumFrames
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { /* No title in top bar per spec */ },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "FrostSoulX Spatial Effect: ${effect.name}\n${effect.description}\nStrength: ${(currentControls.intensity * 100).roundToInt()}%",
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Effect"))
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = "Share effect",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = SpatialAudioTokens.ScreenPadding,
                end = SpatialAudioTokens.ScreenPadding,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + SpatialAudioTokens.SectionGap,
            ),
            verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.SectionGap),
        ) {
            // Header block, centered: name (headline) + "Edited" chip, circular favorite + primary Use/Using button
            item(key = "header_block") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = effect.name,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isEdited) {
                            Surface(
                                shape = SpatialAudioTokens.PillShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "Edited",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Circular favorite button (40dp circle)
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .bouncyClick(onClick = onToggleFavorite),
                            shape = CircleShape,
                            color = SpatialAudioTokens.SurfaceContainerHigh,
                            border = BorderStroke(
                                SpatialAudioTokens.StrokeHairline,
                                if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(if (isFavorite) R.drawable.favorite else R.drawable.favorite_border),
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // Primary Use / Using ✓ button
                        if (isUsing) {
                            Button(
                                onClick = onApplyEffect,
                                shape = SpatialAudioTokens.PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text(
                                    text = "Using ✓",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        } else {
                            Button(
                                onClick = onApplyEffect,
                                shape = SpatialAudioTokens.PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text(
                                    text = "Use",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }
            }

            // Info card: Category chip, description (max 3 lines)
            item(key = "info_card") {
                SpatialCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = SpatialAudioTokens.PillShape,
                            color = SpatialAudioTokens.SurfaceContainerHigh,
                            border = BorderStroke(SpatialAudioTokens.StrokeHairline, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                text = effect.category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            text = effect.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            // Live visualizer card (Canvas, crisp, zero glow)
            item(key = "visualizer_card") {
                SpatialCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Sound space",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LiveVisualizerCanvas(
                            azimuthDeg = currentControls.azimuth,
                            distanceM = currentControls.distance,
                            carFader = currentControls.carFader,
                            isOrbiting = currentControls.orbitEnabled,
                            orbitSpeed = movingSoundSpeed,
                        )
                    }
                }
            }

            // Simple controls card: Effect strength, Moving sound switch + Speed slider, Keep bass centered switch
            item(key = "simple_controls_card") {
                SpatialCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Effect strength slider (percent)
                        AdvancedSlider(
                            label = "Effect strength",
                            value = currentControls.intensity * 100f,
                            range = 0f..100f,
                            defaultValue = effect.defaultControls.intensity * 100f,
                            isBipolar = false,
                            displayFormatter = { "${it.roundToInt()}%" },
                            infoDescription = "Controls the overall depth and immersion of the spatial acoustic effect.",
                            onValueChange = { newVal ->
                                onControlsChange(currentControls.copy(intensity = newVal / 100f))
                            },
                        )

                        // Moving sound (8D) switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Moving sound",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "360° rotating orbit around your head",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = currentControls.orbitEnabled,
                                onCheckedChange = { checked ->
                                    onControlsChange(currentControls.copy(orbitEnabled = checked))
                                },
                            )
                        }

                        // Speed slider animates in only when Moving sound is on
                        AnimatedVisibility(
                            visible = currentControls.orbitEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            AdvancedSlider(
                                label = "Orbit speed",
                                value = movingSoundSpeed,
                                range = 0.2f..3.0f,
                                defaultValue = 1.0f,
                                isBipolar = false,
                                displayFormatter = { String.format(Locale.US, "%.1f×", it) },
                                infoDescription = "Controls how fast the sound orbits around your head.",
                                onValueChange = onMovingSoundSpeedChange,
                            )
                        }

                        // Keep bass centered switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Keep bass centered",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Anchors low frequencies to prevent spatial muddiness",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = currentControls.bassWidth == 0f,
                                onCheckedChange = { checked ->
                                    onControlsChange(
                                        currentControls.copy(
                                            bassWidth = if (checked) 0f else if (effect.defaultControls.bassWidth > 0f) effect.defaultControls.bassWidth else 1f,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Fine tune row (chevron) opens advanced screen
            item(key = "fine_tune_entry") {
                SpatialCard(onClick = onOpenFineTune) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fine tune",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Position, room acoustic, sound spread, and engine metrics",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.navigate_next),
                            contentDescription = "Open fine tune",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // Footer: Reset (outlined) and Save as preset (filled)
            item(key = "footer_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpatialAudioTokens.RowGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            val previousControls = currentControls
                            onControlsChange(effect.defaultControls.copy(enabled = currentControls.enabled))
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Reset · Undo",
                                    actionLabel = "Undo",
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onControlsChange(previousControls)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = SpatialAudioTokens.PillShape,
                        border = BorderStroke(SpatialAudioTokens.StrokeHairline, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Button(
                        onClick = {
                            presetNameInput = "${effect.name} custom"
                            showSavePresetDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = SpatialAudioTokens.PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = "Save as preset",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }

    // Save as preset dialog
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save as preset") },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    shape = SpatialAudioTokens.InputShape,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = presetNameInput.trim().ifEmpty { "${effect.name} custom" }
                        onSaveAsPreset(name, currentControls, effect.name)
                        showSavePresetDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Preset \"$name\" saved")
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = SpatialAudioTokens.CardShape,
            containerColor = SpatialAudioTokens.SurfaceContainerHigh,
        )
    }
}
