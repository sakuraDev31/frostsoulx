package dev.vxs.frostsoulx.ui.screens.settings.spatial

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.ConvolverActiveIrPresetKey
import dev.vxs.frostsoulx.constants.ConvolverIrPresetsKey
import dev.vxs.frostsoulx.playback.ImmersiveAudioDiagnostics
import dev.vxs.frostsoulx.playback.ImmersiveAudioRuntime
import dev.vxs.frostsoulx.playback.ImmersiveControls
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticCapture
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticSample
import dev.vxs.frostsoulx.playback.ImmersiveRoomPreset
import dev.vxs.frostsoulx.playback.WavImpulseResponse
import dev.vxs.frostsoulx.playback.defaultAndroidDescription
import dev.vxs.frostsoulx.playback.defaultDeviceDescription
import dev.vxs.frostsoulx.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FineTuneScreen(
    currentControls: ImmersiveControls,
    onControlsChange: (ImmersiveControls) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Accordion group expanded states
    var positionExpanded by rememberSaveable { mutableStateOf(true) }
    var roomExpanded by rememberSaveable { mutableStateOf(true) }
    var soundExpanded by rememberSaveable { mutableStateOf(true) }
    var engineExpanded by rememberSaveable { mutableStateOf(false) }

    // IR Preset management
    var irPresets by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var activeIrId by remember { mutableStateOf("") }
    var irError by remember { mutableStateOf<String?>(null) }
    var showIrSaveDialog by remember { mutableStateOf(false) }
    var pendingIrBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingIrName by remember { mutableStateOf("") }
    var renameIrId by remember { mutableStateOf<String?>(null) }
    var renameIrName by remember { mutableStateOf("") }
    var roomMenuExpanded by remember { mutableStateOf(false) }

    // Read IR presets from DataStore
    LaunchedEffect(Unit) {
        context.dataStore.data.collect { preferences ->
            activeIrId = preferences[ConvolverActiveIrPresetKey].orEmpty()
            irPresets = runCatching {
                val array = JSONArray(preferences[ConvolverIrPresetsKey] ?: "[]")
                buildList {
                    for (i in 0 until array.length()) {
                        array.optJSONObject(i)?.let { obj ->
                            val id = obj.optString("id")
                            val name = obj.optString("name").trim()
                            val file = obj.optString("file")
                            if (id.isNotBlank() && name.isNotBlank() && file.isNotBlank()) {
                                add(Triple(id, name, file))
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    // WAV File Picker
    val irPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 32 * 1024 * 1024) { "IR WAV must be 32 MB or smaller" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: error("Could not read selected file")
                    }
                    val decoded = withContext(Dispatchers.Default) { WavImpulseResponse.decode(bytes) }
                    pendingIrBytes = bytes
                    pendingIrName = runCatching {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0)?.substringBeforeLast('.') else null
                        }
                    }.getOrNull().orEmpty().ifBlank { "Custom IR" }.take(64)
                    irError = null
                    showIrSaveDialog = true
                }.onFailure {
                    irError = it.message ?: "Could not import this WAV"
                }
            }
        }
    }

    // Diagnostics & telemetry polling
    var diagnostics by remember { mutableStateOf(ImmersiveAudioDiagnostics()) }
    var capturing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableFloatStateOf(0f) }
    var capture by remember { mutableStateOf<ImmersiveDiagnosticCapture?>(null) }
    var showRawDetails by rememberSaveable { mutableStateOf(false) }
    var showFullReport by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            diagnostics = ImmersiveAudioRuntime.readDiagnostics()
            delay(500)
        }
    }

    val isHealthy = diagnostics.deadlineMisses == 0L && diagnostics.nativeProcessFailures == 0L
    val isEngineIdle = diagnostics.processedFrames == 0L

    fun capture10s() {
        if (capturing) return
        scope.launch {
            capturing = true
            captureProgress = 0f
            val samples = mutableListOf<ImmersiveDiagnosticSample>()
            val started = System.currentTimeMillis()
            val clock = SystemClock.elapsedRealtime()
            val on = currentControls.enabled
            while (true) {
                delay(250)
                val d = ImmersiveAudioRuntime.readDiagnostics()
                val elapsed = (SystemClock.elapsedRealtime() - clock) / 1000f
                if (elapsed >= 10f) break
                samples.add(
                    ImmersiveDiagnosticSample(
                        elapsedSeconds = elapsed,
                        inputPeakL = d.inputPeakL,
                        inputPeakR = d.inputPeakR,
                        outputPeakL = d.outputPeakL,
                        outputPeakR = d.outputPeakR,
                        inputRmsL = d.inputRmsL,
                        inputRmsR = d.inputRmsR,
                        outputRmsL = d.outputRmsL,
                        outputRmsR = d.outputRmsR,
                    ),
                )
                captureProgress = elapsed / 10f
            }
            capture = ImmersiveDiagnosticCapture(
                processorOn = on,
                durationSeconds = 10,
                startedAtMillis = started,
                samples = samples,
                finalDiagnostics = ImmersiveAudioRuntime.readDiagnostics(),
            )
            capturing = false
            snackbarHostState.showSnackbar("10 s telemetry capture completed")
        }
    }

    fun generateReport(): String = (capture ?: ImmersiveDiagnosticCapture(
        processorOn = currentControls.enabled,
        durationSeconds = 0,
        startedAtMillis = System.currentTimeMillis(),
        samples = emptyList(),
        finalDiagnostics = diagnostics,
    )).toText(
        defaultDeviceDescription(),
        defaultAndroidDescription(),
        "Device playback route (not measured)",
        diagnostics.hostCallbackFrames,
    ) + "\nCONTROL TARGETS\n$currentControls\n"

    // Checks for modified group values
    val isPositionEdited = currentControls.azimuth != 0f ||
        currentControls.elevation != 0f ||
        currentControls.distance != 1.0f ||
        currentControls.carFader != 0f

    val isRoomEdited = currentControls.roomMix != 0.18f ||
        currentControls.reflectionAmount != 0.28f ||
        currentControls.reverbTimeSeconds != 1.35f ||
        currentControls.roomSize != 0.5f ||
        currentControls.dampening != 0.5f ||
        activeIrId.isNotBlank()

    val isSoundEdited = currentControls.stereoWidth != 0.5f ||
        currentControls.bassWidth != 1.0f ||
        currentControls.bassGainDb != 0f ||
        currentControls.outputGainDb != 0f

    val isEngineEdited = currentControls.quantumFrames != 384

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fine tune",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Back",
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
            verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.RowGap),
        ) {
            // Group 1: POSITION
            item(key = "group_position") {
                AccordionGroup(
                    title = "Position",
                    isEdited = isPositionEdited,
                    expanded = positionExpanded,
                    onToggleExpand = { positionExpanded = !positionExpanded },
                    onResetGroup = {
                        val prev = currentControls
                        onControlsChange(
                            currentControls.copy(
                                azimuth = 0f,
                                elevation = 0f,
                                distance = 1.0f,
                                carFader = 0f,
                            ),
                        )
                        scope.launch {
                            val res = snackbarHostState.showSnackbar("Position reset · Undo", "Undo")
                            if (res == SnackbarResult.ActionPerformed) onControlsChange(prev)
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Left / Right (azimuth -180°..180°, bipolar, detent 0°)
                        AdvancedSlider(
                            label = "Left / Right",
                            value = currentControls.azimuth,
                            range = -180f..180f,
                            defaultValue = 0f,
                            isBipolar = true,
                            displayFormatter = {
                                val deg = it.roundToInt()
                                when {
                                    deg == 0 -> "Center"
                                    deg < 0 -> "${-deg}° left"
                                    else -> "${deg}° right"
                                }
                            },
                            infoDescription = "Horizontally positions the audio source around your head. 0° is straight ahead, 90° is directly to your right, and -90° is directly to your left.",
                            onValueChange = { onControlsChange(currentControls.copy(azimuth = it)) },
                        )

                        // Up / Down (elevation -90°..90°, bipolar, detent 0°)
                        AdvancedSlider(
                            label = "Up / Down",
                            value = currentControls.elevation,
                            range = -90f..90f,
                            defaultValue = 0f,
                            isBipolar = true,
                            displayFormatter = {
                                val deg = it.roundToInt()
                                when {
                                    deg == 0 -> "Eye level"
                                    deg < 0 -> "${-deg}° down"
                                    else -> "${deg}° up"
                                }
                            },
                            infoDescription = "Vertical elevation angle. 0° is directly at ear/eye level, +90° is overhead, and -90° is below.",
                            onValueChange = { onControlsChange(currentControls.copy(elevation = it)) },
                        )

                        // Distance (0.2m..10m, unipolar)
                        AdvancedSlider(
                            label = "Distance",
                            value = currentControls.distance,
                            range = 0.2f..10.0f,
                            defaultValue = 1.0f,
                            isBipolar = false,
                            displayFormatter = { String.format(Locale.US, "%.1f m", it) },
                            infoDescription = "Simulated physical distance from the acoustic sound source in meters.",
                            onValueChange = { onControlsChange(currentControls.copy(distance = it)) },
                        )

                        // Front / Back (carFader -1f..1f, bipolar, detent 0)
                        AdvancedSlider(
                            label = "Front / Back",
                            value = currentControls.carFader,
                            range = -1.0f..1.0f,
                            defaultValue = 0f,
                            isBipolar = true,
                            displayFormatter = {
                                val pct = (it * 100).roundToInt()
                                when {
                                    pct == 0 -> "Center"
                                    pct < 0 -> "${-pct}% back"
                                    else -> "${pct}% front"
                                }
                            },
                            infoDescription = "Acoustic front-to-back fader balance.",
                            onValueChange = { onControlsChange(currentControls.copy(carFader = it)) },
                        )
                    }
                }
            }

            // Group 2: ROOM
            item(key = "group_room") {
                AccordionGroup(
                    title = "Room",
                    isEdited = isRoomEdited,
                    expanded = roomExpanded,
                    onToggleExpand = { roomExpanded = !roomExpanded },
                    onResetGroup = {
                        val prev = currentControls
                        onControlsChange(
                            currentControls.copy(
                                roomMix = 0.18f,
                                reflectionAmount = 0.28f,
                                reverbTimeSeconds = 1.35f,
                                roomSize = 0.5f,
                                dampening = 0.5f,
                            ),
                        )
                        scope.launch {
                            context.dataStore.edit { it.remove(ConvolverActiveIrPresetKey) }
                            ImmersiveAudioRuntime.setCustomIr(null)
                            val res = snackbarHostState.showSnackbar("Room reset · Undo", "Undo")
                            if (res == SnackbarResult.ActionPerformed) onControlsChange(prev)
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Room preset selection
                        Box {
                            OutlinedButton(
                                onClick = { roomMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = SpatialAudioTokens.PillShape,
                                border = BorderStroke(SpatialAudioTokens.StrokeHairline, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (activeIrId.isNotBlank()) {
                                            "Custom IR: ${irPresets.firstOrNull { it.first == activeIrId }?.second ?: "Loaded"}"
                                        } else {
                                            "Room preset: ${currentControls.roomPreset.label}"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.expand_more),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = roomMenuExpanded,
                                onDismissRequest = { roomMenuExpanded = false },
                            ) {
                                ImmersiveRoomPreset.entries.forEach { room ->
                                    DropdownMenuItem(
                                        text = { Text(room.label) },
                                        onClick = {
                                            roomMenuExpanded = false
                                            scope.launch {
                                                context.dataStore.edit { it.remove(ConvolverActiveIrPresetKey) }
                                                ImmersiveAudioRuntime.setCustomIr(null)
                                            }
                                            onControlsChange(currentControls.copy(roomPreset = room))
                                        },
                                    )
                                }
                            }
                        }

                        // Echo (roomMix)
                        AdvancedSlider(
                            label = "Echo",
                            value = currentControls.roomMix * 100f,
                            range = 0f..100f,
                            defaultValue = 18f,
                            isBipolar = false,
                            displayFormatter = { "${it.roundToInt()}%" },
                            infoDescription = "Wet/dry balance of the room reverberation and echo reflection paths.",
                            onValueChange = { onControlsChange(currentControls.copy(roomMix = it / 100f)) },
                        )

                        // Early reflections (reflectionAmount)
                        AdvancedSlider(
                            label = "Early reflections",
                            value = currentControls.reflectionAmount * 100f,
                            range = 0f..100f,
                            defaultValue = 28f,
                            isBipolar = false,
                            displayFormatter = { "${it.roundToInt()}%" },
                            infoDescription = "Energy level of immediate discrete wall reflections before late diffuse reverberation.",
                            onValueChange = { onControlsChange(currentControls.copy(reflectionAmount = it / 100f)) },
                        )

                        // Decay time (reverbTimeSeconds)
                        AdvancedSlider(
                            label = "Decay time",
                            value = currentControls.reverbTimeSeconds,
                            range = 0.2f..8.0f,
                            defaultValue = 1.35f,
                            isBipolar = false,
                            displayFormatter = { String.format(Locale.US, "%.2f s", it) },
                            infoDescription = "RT60 decay time: the duration required for reflections to attenuate by 60 dB.",
                            onValueChange = { onControlsChange(currentControls.copy(reverbTimeSeconds = it)) },
                        )

                        // Room size (roomSize)
                        AdvancedSlider(
                            label = "Room size",
                            value = currentControls.roomSize * 100f,
                            range = 0f..100f,
                            defaultValue = 50f,
                            isBipolar = false,
                            displayFormatter = { "${it.roundToInt()}%" },
                            infoDescription = "Simulated volume and boundary dimensions of the room.",
                            onValueChange = { onControlsChange(currentControls.copy(roomSize = it / 100f)) },
                        )

                        // High-frequency dampening (dampening)
                        AdvancedSlider(
                            label = "High-frequency dampening",
                            value = currentControls.dampening * 100f,
                            range = 0f..100f,
                            defaultValue = 50f,
                            isBipolar = false,
                            displayFormatter = { "${it.roundToInt()}%" },
                            infoDescription = "Acoustic absorption of high frequencies by air and room surface materials.",
                            onValueChange = { onControlsChange(currentControls.copy(dampening = it / 100f)) },
                        )

                        // Import your own room section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Import your own room",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Import a measured impulse response (IR) WAV file to reproduce authentic room acoustics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        irError = null
                                        irPicker.launch(arrayOf("audio/*", "application/octet-stream"))
                                    },
                                    shape = SpatialAudioTokens.PillShape,
                                    border = BorderStroke(SpatialAudioTokens.StrokeHairline, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Import WAV")
                                }

                                if (activeIrId.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            ImmersiveAudioRuntime.setCustomIr(null)
                                            scope.launch { context.dataStore.edit { it.remove(ConvolverActiveIrPresetKey) } }
                                        },
                                        shape = SpatialAudioTokens.PillShape,
                                        border = BorderStroke(SpatialAudioTokens.StrokeHairline, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Built-in room")
                                    }
                                }
                            }

                            // Loaded IR list
                            irPresets.forEach { preset ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SpatialAudioTokens.SurfaceContainerHigh, SpatialAudioTokens.CardShape)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                scope.launch {
                                                    context.dataStore.edit { it[ConvolverActiveIrPresetKey] = preset.first }
                                                }
                                            },
                                    ) {
                                        Text(
                                            text = (if (preset.first == activeIrId) "Active · " else "") + preset.second,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (preset.first == activeIrId) FontWeight.Bold else FontWeight.Normal,
                                            ),
                                            color = if (preset.first == activeIrId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                renameIrId = preset.first
                                                renameIrName = preset.second
                                            },
                                        ) {
                                            Text("Rename", style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    context.dataStore.edit { p ->
                                                        p[ConvolverIrPresetsKey] = JSONArray().apply {
                                                            irPresets.filterNot { it.first == preset.first }.forEach { (id, name, file) ->
                                                                put(JSONObject().put("id", id).put("name", name).put("file", file))
                                                            }
                                                        }.toString()
                                                        if (activeIrId == preset.first) p.remove(ConvolverActiveIrPresetKey)
                                                    }
                                                    withContext(Dispatchers.IO) {
                                                        File(File(context.filesDir, "convolver-ir"), preset.third).delete()
                                                    }
                                                }
                                            },
                                        ) {
                                            Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }

                            irError?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Text(
                                text = "Mono / stereo / 4-path WAV · PCM 16/24/32-bit or float32 · up to 32,768 taps per path",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Group 3: SOUND
            item(key = "group_sound") {
                AccordionGroup(
                    title = "Sound",
                    isEdited = isSoundEdited,
                    expanded = soundExpanded,
                    onToggleExpand = { soundExpanded = !soundExpanded },
                    onResetGroup = {
                        val prev = currentControls
                        onControlsChange(
                            currentControls.copy(
                                stereoWidth = 0.5f,
                                bassWidth = 1.0f,
                                bassGainDb = 0f,
                                outputGainDb = 0f,
                            ),
                        )
                        scope.launch {
                            val res = snackbarHostState.showSnackbar("Sound reset · Undo", "Undo")
                            if (res == SnackbarResult.ActionPerformed) onControlsChange(prev)
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Stereo width (0f..1f, unipolar, 0.5f = 1.0× unity)
                        AdvancedSlider(
                            label = "Stereo width",
                            value = currentControls.stereoWidth,
                            range = 0f..1f,
                            defaultValue = 0.5f,
                            isBipolar = false,
                            displayFormatter = { String.format(Locale.US, "%.1f×", it * 2f) },
                            infoDescription = "Narrows or expands the upper stereo soundstage without altering bass frequencies.",
                            onValueChange = { onControlsChange(currentControls.copy(stereoWidth = it)) },
                        )

                        // Bass spread (0f..2f, unipolar, 1.0× = standard)
                        AdvancedSlider(
                            label = "Bass spread",
                            value = currentControls.bassWidth,
                            range = 0f..2f,
                            defaultValue = 1.0f,
                            isBipolar = false,
                            displayFormatter = { String.format(Locale.US, "%.1f×", it) },
                            infoDescription = "Low-frequency stereo width. 0.0× centers all bass in mono to prevent room phase cancellation.",
                            onValueChange = { onControlsChange(currentControls.copy(bassWidth = it)) },
                        )

                        // Bass boost (-12dB..+6dB, bipolar, detent 0dB)
                        AdvancedSlider(
                            label = "Bass boost",
                            value = currentControls.bassGainDb,
                            range = -12f..6f,
                            defaultValue = 0f,
                            isBipolar = true,
                            displayFormatter = { String.format(Locale.US, "%+.1f dB", it) },
                            infoDescription = "Low shelf frequency boost or cut applied directly to the stereo bass band.",
                            onValueChange = { onControlsChange(currentControls.copy(bassGainDb = it)) },
                        )

                        // Anti-distortion margin (-24dB..0dB, unipolar)
                        AdvancedSlider(
                            label = "Anti-distortion margin",
                            value = currentControls.outputGainDb,
                            range = -24f..0f,
                            defaultValue = 0f,
                            isBipolar = false,
                            displayFormatter = { String.format(Locale.US, "%.1f dB", it) },
                            infoDescription = "Output headroom attenuation ensuring zero digital clipping even with aggressive reverb and spatial boost.",
                            onValueChange = { onControlsChange(currentControls.copy(outputGainDb = it)) },
                        )
                    }
                }
            }

            // Group 4: ENGINE
            item(key = "group_engine") {
                AccordionGroup(
                    title = "Engine",
                    isEdited = isEngineEdited,
                    expanded = engineExpanded,
                    onToggleExpand = { engineExpanded = !engineExpanded },
                    onResetGroup = {
                        val prev = currentControls
                        onControlsChange(currentControls.copy(quantumFrames = 384))
                        scope.launch {
                            val res = snackbarHostState.showSnackbar("Engine buffer reset · Undo", "Undo")
                            if (res == SnackbarResult.ActionPerformed) onControlsChange(prev)
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Buffer size chips
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Buffer size",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(192, 384, 768).forEach { frames ->
                                    val isSelected = currentControls.quantumFrames == frames
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onControlsChange(currentControls.copy(quantumFrames = frames)) },
                                        label = { Text("$frames frames") },
                                        shape = SpatialAudioTokens.PillShape,
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            borderWidth = if (isSelected) SpatialAudioTokens.StrokeOutline else SpatialAudioTokens.StrokeHairline,
                                        ),
                                    )
                                }
                            }
                        }

                        // 3 Status Chips: Engine Healthy, Latency, Clipping
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Chip 1: Engine Healthy
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = when {
                                            !currentControls.enabled -> "Audio engine: Disabled"
                                            isEngineIdle -> "Audio engine: Idle"
                                            isHealthy -> "Audio engine: Healthy"
                                            else -> "Audio engine: Latency alert"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                },
                                shape = SpatialAudioTokens.PillShape,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isHealthy && currentControls.enabled && !isEngineIdle) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        SpatialAudioTokens.SurfaceContainerHigh
                                    },
                                    labelColor = if (isHealthy && currentControls.enabled && !isEngineIdle) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    borderWidth = SpatialAudioTokens.StrokeHairline,
                                ),
                            )

                            // Chip 2: Latency
                            val latencyMs = diagnostics.algorithmicLatencySamples * 1000f / diagnostics.sampleRate.coerceAtLeast(44100)
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = String.format(Locale.US, "Latency: %.1f ms (%d spl)", latencyMs, diagnostics.algorithmicLatencySamples),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                shape = SpatialAudioTokens.PillShape,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = SpatialAudioTokens.SurfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    borderWidth = SpatialAudioTokens.StrokeHairline,
                                ),
                            )

                            // Chip 3: Clipping
                            val hasClipping = diagnostics.limiterGainReductionDb > 0.1f
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = if (hasClipping) {
                                            String.format(Locale.US, "Clipping: -%.1f dB guard", diagnostics.limiterGainReductionDb)
                                        } else {
                                            "Clipping: Clean"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                shape = SpatialAudioTokens.PillShape,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = SpatialAudioTokens.SurfaceContainerHigh,
                                    labelColor = if (hasClipping) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    borderWidth = SpatialAudioTokens.StrokeHairline,
                                ),
                            )
                        }

                        // Real buttons: Capture 10 s (outlined), Full report (outlined), Share report (filled)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { capture10s() },
                                enabled = !capturing,
                                shape = SpatialAudioTokens.PillShape,
                                border = BorderStroke(SpatialAudioTokens.StrokeHairline, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = if (capturing) "Capturing…" else "Capture 10 s",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }

                            OutlinedButton(
                                onClick = { showFullReport = !showFullReport },
                                shape = SpatialAudioTokens.PillShape,
                                border = BorderStroke(SpatialAudioTokens.StrokeHairline, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = if (showFullReport) "Hide report" else "Full report",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }

                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "FrostSoulX DSP Telemetry Report")
                                        putExtra(Intent.EXTRA_TEXT, generateReport())
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share DSP Report"))
                                },
                                shape = SpatialAudioTokens.PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "Share report",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }

                        // Progress indicator while capturing 10s
                        if (capturing) {
                            LinearProgressIndicator(
                                progress = { captureProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // "Show details" Expander with raw meters & B1-B7 pipeline stages
                        SpatialCard(
                            onClick = { showRawDetails = !showRawDetails },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Show details",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    painter = painterResource(R.drawable.expand_more),
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (showRawDetails) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showRawDetails,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SpatialAudioTokens.SurfaceContainerHigh, SpatialAudioTokens.CardShape)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "${diagnostics.backendLabel()} · ${diagnostics.sampleRate} Hz",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                TelemetryMetricRow("Processed frames", diagnostics.processedFrames.toString())
                                TelemetryMetricRow("DSP mean / max", String.format(Locale.US, "%.3f / %.3f ms", diagnostics.averageProcessingTimeMs, diagnostics.maxProcessingTimeMs))
                                TelemetryMetricRow("Deadline misses", diagnostics.deadlineMisses.toString())
                                TelemetryMetricRow("Input RMS L / R", String.format(Locale.US, "%.4f / %.4f", diagnostics.inputRmsL, diagnostics.inputRmsR))
                                TelemetryMetricRow("Output RMS L / R", String.format(Locale.US, "%.4f / %.4f", diagnostics.outputRmsL, diagnostics.outputRmsR))
                                TelemetryMetricRow("Output peak L / R", String.format(Locale.US, "%.4f / %.4f", diagnostics.outputPeakL, diagnostics.outputPeakR))
                                TelemetryMetricRow("Limiter gain reduction", String.format(Locale.US, "%.1f dB", diagnostics.limiterGainReductionDb))
                                TelemetryMetricRow("Clipping source", diagnostics.clippingSource())
                                TelemetryMetricRow("Non-finite samples (NaN/Inf)", "${diagnostics.nanCount + diagnostics.infCount}")

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Pipeline Stages (B1–B7)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                diagnostics.pipelineStages().forEach { stage ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "${stage.id} ${stage.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = if (stage.available) {
                                                String.format(Locale.US, "RMS %.3f · Peak %.3f", stage.output.rms, stage.output.peak)
                                            } else {
                                                stage.unavailableReason ?: "Waiting for PCM"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        ImmersiveAudioRuntime.resetDiagnostics()
                                        capture = null
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("Reset meters")
                                }
                            }
                        }

                        // Full report monospaced text view
                        AnimatedVisibility(
                            visible = showFullReport,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Surface(
                                shape = SpatialAudioTokens.CardShape,
                                color = SpatialAudioTokens.SurfaceContainerHigh,
                                border = BorderStroke(SpatialAudioTokens.StrokeHairline, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SelectionContainer(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = generateReport(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Save IR dialog
    if (showIrSaveDialog) {
        AlertDialog(
            onDismissRequest = { showIrSaveDialog = false },
            title = { Text("Save custom room") },
            text = {
                OutlinedTextField(
                    value = pendingIrName,
                    onValueChange = { pendingIrName = it.take(64) },
                    label = { Text("Room name") },
                    singleLine = true,
                    shape = SpatialAudioTokens.InputShape,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val bytes = pendingIrBytes
                        if (bytes != null && pendingIrName.isNotBlank()) {
                            val id = java.util.UUID.randomUUID().toString()
                            val filename = "$id.wav"
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    File(File(context.filesDir, "convolver-ir").apply { mkdirs() }, filename).writeBytes(bytes)
                                }
                                val next = irPresets + Triple(id, pendingIrName.trim(), filename)
                                context.dataStore.edit {
                                    it[ConvolverIrPresetsKey] = JSONArray().apply { next.takeLast(32).forEach { (i, n, f) -> put(JSONObject().put("id", i).put("name", n).put("file", f)) } }.toString()
                                    it[ConvolverActiveIrPresetKey] = id
                                }
                            }
                        }
                        showIrSaveDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIrSaveDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = SpatialAudioTokens.CardShape,
            containerColor = SpatialAudioTokens.SurfaceContainerHigh,
        )
    }

    // Rename IR dialog
    renameIrId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { renameIrId = null },
            title = { Text("Rename room") },
            text = {
                OutlinedTextField(
                    value = renameIrName,
                    onValueChange = { renameIrName = it.take(64) },
                    label = { Text("New name") },
                    singleLine = true,
                    shape = SpatialAudioTokens.InputShape,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = renameIrName.trim()
                        if (target.isNotBlank()) {
                            val next = irPresets.map { if (it.first == targetId) Triple(it.first, target, it.third) else it }
                            scope.launch {
                                context.dataStore.edit {
                                    it[ConvolverIrPresetsKey] = JSONArray().apply { next.forEach { (i, n, f) -> put(JSONObject().put("id", i).put("name", n).put("file", f)) } }.toString()
                                }
                            }
                        }
                        renameIrId = null
                    },
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameIrId = null }) {
                    Text("Cancel")
                }
            },
            shape = SpatialAudioTokens.CardShape,
            containerColor = SpatialAudioTokens.SurfaceContainerHigh,
        )
    }
}

@Composable
private fun AccordionGroup(
    title: String,
    isEdited: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onResetGroup: () -> Unit,
    content: @Composable () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = SpatialAudioTokens.SlideSpringSpec,
        label = "ChevronRotation",
    )

    SpatialCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Group Header: Title, Edited dot, Reset text button, Chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Edited dot indicator (6dp primary dot)
                    if (isEdited) {
                        Surface(
                            modifier = Modifier.size(6.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {}
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onResetGroup,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(chevronRotation),
                    )
                }
            }

            // Group Body
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun TelemetryMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
