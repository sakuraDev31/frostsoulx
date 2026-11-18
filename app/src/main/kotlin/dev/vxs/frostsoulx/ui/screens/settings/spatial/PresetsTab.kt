package dev.vxs.frostsoulx.ui.screens.settings.spatial

import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.playback.ImmersiveAudioPreset
import dev.vxs.frostsoulx.playback.ImmersiveControls
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PresetsTab(
    presets: List<ImmersiveAudioPreset>,
    currentControls: ImmersiveControls,
    activeEffectName: String?,
    onApplyPreset: (ImmersiveAudioPreset) -> Unit,
    onSaveCurrentAsPreset: (name: String) -> Unit,
    onRenamePreset: (index: Int, newName: String) -> Unit,
    onDeletePreset: (index: Int) -> Unit,
    onRestorePreset: (index: Int, preset: ImmersiveAudioPreset) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSaveDialog by remember { mutableStateOf(false) }
    var savePresetName by remember { mutableStateOf("") }
    var renameTargetIndex by remember { mutableStateOf<Int?>(null) }
    var renameTargetName by remember { mutableStateOf("") }

    if (presets.isEmpty()) {
        // Empty state: one sentence plus a "Save current setup" button
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = SpatialAudioTokens.ScreenPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding() + 40.dp),
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = SpatialAudioTokens.SurfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.bookmark),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Text(
                    text = "No saved presets yet. Adjust your sound space, then tap Save current setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                Button(
                    onClick = {
                        savePresetName = if (!activeEffectName.isNullOrBlank()) "$activeEffectName custom" else "My setup"
                        showSaveDialog = true
                    },
                    shape = SpatialAudioTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Save current setup",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SpatialAudioTokens.ScreenPadding,
                end = SpatialAudioTokens.ScreenPadding,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + SpatialAudioTokens.SectionGap,
            ),
            verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.RowGap),
        ) {
            // Count header + Save button
            item(key = "presets_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${presets.size} ${if (presets.size == 1) "preset" else "presets"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    TextButton(
                        onClick = {
                            savePresetName = if (!activeEffectName.isNullOrBlank()) "$activeEffectName custom" else "My setup"
                            showSaveDialog = true
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "Save current setup",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // List of saved presets
            itemsIndexed(
                items = presets,
                key = { index, item -> "${item.name}_${item.createdAt}_$index" },
            ) { index, preset ->
                var menuExpanded by remember { mutableStateOf(false) }

                val formattedDate = remember(preset.createdAt) {
                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    sdf.format(Date(preset.createdAt))
                }

                val isUsingThisPreset = remember(preset, currentControls) {
                    preset.intensity == currentControls.intensity &&
                        preset.roomPreset == currentControls.roomPreset &&
                        preset.roomMix == currentControls.roomMix &&
                        preset.stereoWidth == currentControls.stereoWidth &&
                        preset.bassWidth == currentControls.bassWidth &&
                        preset.bassGainDb == currentControls.bassGainDb &&
                        preset.outputGainDb == currentControls.outputGainDb &&
                        preset.azimuth == currentControls.azimuth &&
                        preset.elevation == currentControls.elevation &&
                        preset.distance == currentControls.distance &&
                        preset.orbitEnabled == currentControls.orbitEnabled
                }

                SpatialCard(
                    isActive = isUsingThisPreset,
                    onClick = { onApplyPreset(preset) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpatialAudioTokens.RowGap),
                    ) {
                        // 48dp leading tonal circle
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = if (isUsingThisPreset) MaterialTheme.colorScheme.primaryContainer else SpatialAudioTokens.SurfaceContainerHigh,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.bookmark),
                                    contentDescription = null,
                                    tint = if (isUsingThisPreset) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        // Preset name, date, based on
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = buildString {
                                append(formattedDate)
                                if (preset.basedOn.isNotBlank()) {
                                    append(" · based on ")
                                    append(preset.basedOn)
                                } else {
                                    append(" · ")
                                    append(preset.roomPreset.label)
                                }
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Use / Using pill button
                        if (isUsingThisPreset) {
                            Button(
                                onClick = { onApplyPreset(preset) },
                                shape = SpatialAudioTokens.PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Text(
                                    text = "Using ✓",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onApplyPreset(preset) },
                                shape = SpatialAudioTokens.PillShape,
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    width = SpatialAudioTokens.StrokeHairline,
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Text(
                                    text = "Use",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // Overflow menu (Rename, Share as text/JSON, Delete with undo)
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = "Preset options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.edit),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        renameTargetIndex = index
                                        renameTargetName = preset.name
                                    },
                                )

                                DropdownMenuItem(
                                    text = { Text("Share as text") },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.share),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        val shareText = "FrostSoulX Spatial Preset: ${preset.name}\n" +
                                            "Room: ${preset.roomPreset.label}, Reverb: ${preset.reverbTimeSeconds}s\n" +
                                            "Stereo Width: ${preset.stereoWidth * 2}×, Bass Gain: ${preset.bassGainDb}dB"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Spatial Preset: ${preset.name}")
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Preset"))
                                    },
                                )

                                DropdownMenuItem(
                                    text = { Text("Share as JSON") },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.share),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        val jsonString = preset.toJson().toString(2)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_SUBJECT, "${preset.name}.json")
                                            putExtra(Intent.EXTRA_TEXT, jsonString)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Preset JSON"))
                                    },
                                )

                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.delete),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        val deletedPreset = preset
                                        val deletedIndex = index
                                        onDeletePreset(deletedIndex)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Preset \"${deletedPreset.name}\" deleted",
                                                actionLabel = "Undo",
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                onRestorePreset(deletedIndex, deletedPreset)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Save Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save current setup") },
            text = {
                OutlinedTextField(
                    value = savePresetName,
                    onValueChange = { savePresetName = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    shape = SpatialAudioTokens.InputShape,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = savePresetName.trim().ifEmpty { "My setup" }
                        onSaveCurrentAsPreset(name)
                        showSaveDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Preset \"$name\" saved")
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = SpatialAudioTokens.CardShape,
            containerColor = SpatialAudioTokens.SurfaceContainerHigh,
        )
    }

    // Rename Preset Dialog
    renameTargetIndex?.let { targetIndex ->
        AlertDialog(
            onDismissRequest = { renameTargetIndex = null },
            title = { Text("Rename preset") },
            text = {
                OutlinedTextField(
                    value = renameTargetName,
                    onValueChange = { renameTargetName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    shape = SpatialAudioTokens.InputShape,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renameTargetName.trim()
                        if (name.isNotEmpty()) {
                            onRenamePreset(targetIndex, name)
                        }
                        renameTargetIndex = null
                    },
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetIndex = null }) {
                    Text("Cancel")
                }
            },
            shape = SpatialAudioTokens.CardShape,
            containerColor = SpatialAudioTokens.SurfaceContainerHigh,
        )
    }
}
