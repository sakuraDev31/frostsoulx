package dev.vxs.frostsoulx.ui.screens.settings

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.constants.SpatialAzimuthKey
import dev.vxs.frostsoulx.constants.SpatialBassWidthKey
import dev.vxs.frostsoulx.constants.SpatialDistanceKey
import dev.vxs.frostsoulx.constants.SpatialOrbitEnabledKey
import dev.vxs.frostsoulx.constants.SpatialElevationKey
import dev.vxs.frostsoulx.constants.StereoSurroundBassGainDbKey
import dev.vxs.frostsoulx.constants.StereoSurroundCarFaderKey
import dev.vxs.frostsoulx.constants.StereoSurroundDampeningKey
import dev.vxs.frostsoulx.constants.StereoSurroundEnabledKey
import dev.vxs.frostsoulx.constants.StereoSurroundIntensityKey
import dev.vxs.frostsoulx.constants.StereoSurroundOutputGainDbKey
import dev.vxs.frostsoulx.constants.StereoSurroundQuantumFramesKey
import dev.vxs.frostsoulx.constants.StereoSurroundReflectionAmountKey
import dev.vxs.frostsoulx.constants.StereoSurroundReverbTimeKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomMixKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomPresetKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomSizeKey
import dev.vxs.frostsoulx.constants.StereoSurroundSavedPresetsKey
import dev.vxs.frostsoulx.constants.StereoSurroundStereoWidthKey
import dev.vxs.frostsoulx.playback.ImmersiveAudioDiagnostics
import dev.vxs.frostsoulx.playback.ImmersiveAudioPreset
import dev.vxs.frostsoulx.playback.ImmersiveAudioRuntime
import dev.vxs.frostsoulx.playback.ImmersiveControls
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticCapture
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticSample
import dev.vxs.frostsoulx.playback.ImmersiveRoomPreset
import dev.vxs.frostsoulx.playback.defaultAndroidDescription
import dev.vxs.frostsoulx.playback.defaultDeviceDescription
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.utils.dataStore
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialAudioScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = FrostSoulTheme.colors
    val preferences by context.dataStore.data.collectAsState(initial = null)
    var controls by remember { mutableStateOf(ImmersiveAudioRuntime.currentControls()) }
    var diagnostics by remember { mutableStateOf(ImmersiveAudioDiagnostics()) }
    var presetMenu by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableStateOf(0f) }
    var capture by remember { mutableStateOf<ImmersiveDiagnosticCapture?>(null) }
    val savedPresets = remember(preferences) {
        ImmersiveAudioPreset.decodeAll(preferences?.get(StereoSurroundSavedPresetsKey) ?: "[]")
    }
    LaunchedEffect(preferences) {
        preferences?.let { controls = readSpatialControls(it); ImmersiveAudioRuntime.applyControls(controls) }
    }
    LaunchedEffect(Unit) {
        while (true) { diagnostics = ImmersiveAudioRuntime.readDiagnostics(); delay(500) }
    }
    LaunchedEffect(capturing) {
        if (!capturing) return@LaunchedEffect
        val on = controls.enabled
        val started = System.currentTimeMillis()
        val clock = SystemClock.elapsedRealtime()
        val samples = mutableListOf<ImmersiveDiagnosticSample>()
        ImmersiveAudioRuntime.resetDiagnostics()
        repeat(40) {
            delay(250)
            val d = ImmersiveAudioRuntime.readDiagnostics()
            val elapsed = (SystemClock.elapsedRealtime() - clock) / 1000f
            samples += ImmersiveDiagnosticSample(elapsed, d.inputPeakL, d.inputPeakR,
                d.outputPeakL, d.outputPeakR, d.inputRmsL, d.inputRmsR, d.outputRmsL, d.outputRmsR)
            captureProgress = elapsed / 10f
        }
        capture = ImmersiveDiagnosticCapture(processorOn = on, durationSeconds = 10,
            startedAtMillis = started, samples = samples, finalDiagnostics = ImmersiveAudioRuntime.readDiagnostics())
        capturing = false
    }
    fun update(value: ImmersiveControls) {
        controls = value.sanitized()
        ImmersiveAudioRuntime.applyControls(controls)
        val snapshot = controls
        scope.launch { context.dataStore.edit { writeSpatialControls(it, snapshot) } }
    }
    fun report(): String = (capture ?: ImmersiveDiagnosticCapture(
        processorOn = controls.enabled, durationSeconds = 0, startedAtMillis = System.currentTimeMillis(),
        samples = emptyList(), finalDiagnostics = diagnostics,
    )).toText(defaultDeviceDescription(), defaultAndroidDescription(), "Device playback route (not measured)", diagnostics.hostCallbackFrames) +
        "\nCONTROL TARGETS\n$controls\n"

    var screenMode by remember { mutableStateOf("simple") }
    var diagnosticsReturnMode by remember { mutableStateOf("simple") }
    var customAdvanced by remember { mutableStateOf(false) }
    var positionExpanded by remember { mutableStateOf(true) }
    var roomExpanded by remember { mutableStateOf(false) }
    var soundExpanded by remember { mutableStateOf(true) }
    var engineExpanded by remember { mutableStateOf(true) }
    var helpText by remember { mutableStateOf<String?>(null) }
    fun updateAdvanced(value: ImmersiveControls) { customAdvanced = true; update(value) }
    val healthy = diagnostics.deadlineMisses == 0 && diagnostics.nativeProcessFailures == 0
    val mutedAlpha = if (controls.enabled) 1f else 0.48f

    Scaffold(
        containerColor = colors.background,
        topBar = { TopAppBar(
            title = { Text(when (screenMode) { "advanced" -> "Advanced"; "diagnostics" -> "Diagnostics"; else -> "Spatial audio" }, color = colors.onSurface) },
            navigationIcon = { IconButton(onClick = {
                when (screenMode) { "advanced" -> screenMode = "simple"; "diagnostics" -> screenMode = diagnosticsReturnMode; else -> navController.navigateUp() }
            }, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹", fontSize = 30.sp) } }
        ) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)
            .padding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues())
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (screenMode) {
                "simple" -> {
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Spatial audio", style = MaterialTheme.typography.titleLarge)
                            Text(if (controls.enabled) "On" else "Off", color = colors.onSurfaceMuted, fontSize = 13.sp)
                        }
                        Switch(checked = controls.enabled, enabled = preferences != null && !capturing,
                            onCheckedChange = { update(controls.copy(enabled = it)) },
                            modifier = Modifier.semantics { contentDescription = "Enable spatial audio" })
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.outline.copy(alpha = 0.35f))
                    Column(Modifier.alpha(mutedAlpha), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Sound space", color = colors.onSurfaceMuted, style = MaterialTheme.typography.titleSmall)
                        val chipLabels = listOf("Off", "Tunnel", "Studio", "Hall")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            chipLabels.take(3).forEach { label ->
                                val selected = if (label == "Off") controls.roomPreset == ImmersiveRoomPreset.entries.firstOrNull() else controls.roomPreset.label.equals(label, true)
                                PresetChip(label, selected && !customAdvanced, !capturing && controls.enabled) {
                                    val preset = if (label == "Off") ImmersiveRoomPreset.entries.firstOrNull() else ImmersiveRoomPreset.entries.firstOrNull { it.label.equals(label, true) }
                                    if (preset != null) { customAdvanced = false; update(controls.copy(roomPreset = preset, roomMix = if (label == "Off") 0f else controls.roomMix)) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val label = "Hall"
                            PresetChip(label, controls.roomPreset.label.equals(label, true) && !customAdvanced, !capturing && controls.enabled) {
                                ImmersiveRoomPreset.entries.firstOrNull { it.label.equals(label, true) }?.let { customAdvanced = false; update(controls.copy(roomPreset = it)) }
                            }
                            PresetChip("+", false, !capturing && controls.enabled) { showSave = true }
                        }
                        SimpleSlider("Intensity", controls.intensity, percent(controls.intensity), !capturing && controls.enabled) { update(controls.copy(intensity = it)) }
                        SimpleSlider("Room feel", controls.roomMix, percent(controls.roomMix), !capturing && controls.enabled) { update(controls.copy(roomMix = it)) }
                        SimpleSlider("Bass", ((controls.bassGainDb + 12f) / 18f).coerceIn(0f, 1f), percent(((controls.bassGainDb + 12f) / 18f).coerceIn(0f, 1f)), !capturing && controls.enabled) {
                            update(controls.copy(bassGainDb = -12f + 18f * it))
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.outline.copy(alpha = 0.35f))
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { screenMode = "advanced" }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Advanced", style = MaterialTheme.typography.titleMedium)
                        Text("›", fontSize = 28.sp, color = colors.onSurfaceMuted)
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.outline.copy(alpha = 0.35f))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { diagnosticsReturnMode = "simple"; screenMode = "diagnostics" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Canvas(Modifier.size(9.dp)) { drawCircle(if (healthy) androidx.compose.ui.graphics.Color(0xFF20C45A) else androidx.compose.ui.graphics.Color(0xFFFFB020)) }
                        Text(if (healthy) "Engine running smoothly" else "Engine deadline misses detected", color = colors.onSurfaceMuted, modifier = Modifier.weight(1f))
                        Text("Diagnostics ›", color = colors.accent)
                    }
                }
                "advanced" -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Advanced controls", style = MaterialTheme.typography.titleLarge)
                        Text(if (customAdvanced) "Custom" else "Preset", color = colors.accent, style = MaterialTheme.typography.labelLarge)
                    }
                    SectionHeader("Position", positionExpanded) { positionExpanded = !positionExpanded }
                    if (positionExpanded) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AdvancedSlider("Azimuth", controls.azimuth, -180f..180f, "${fmt(controls.azimuth, 0)}°", "Front = 0°; positive azimuth = left", !capturing, { helpText = "Azimuth sets the source direction around the listener. Front is 0°." }) { updateAdvanced(controls.copy(azimuth = it)) }
                        AdvancedSlider("Elevation", controls.elevation, -90f..90f, "${fmt(controls.elevation, 0)}°", "Below −90° to above +90°", !capturing, { helpText = "Elevation controls the source angle above or below the listener." }) { updateAdvanced(controls.copy(elevation = it)) }
                        AdvancedSlider("Distance", controls.distance, 0.2f..10f, "${fmt(controls.distance)} m", "Source distance", !capturing, { helpText = "Distance is expressed in metres." }) { updateAdvanced(controls.copy(distance = it)) }
                        AdvancedSlider("Front / rear", controls.carFader, -1f..1f, fmt(controls.carFader), "Rear ← centre → front", !capturing, { helpText = "Moves the source balance between rear and front." }) { updateAdvanced(controls.copy(carFader = it)) }
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                    SectionHeader("Room", roomExpanded) { roomExpanded = !roomExpanded }
                    if (roomExpanded) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AdvancedSlider("Reflections", controls.reflectionAmount, 0f..1f, percent(controls.reflectionAmount), "Early reflection strength", !capturing, { helpText = "Controls the level of early room reflections." }) { updateAdvanced(controls.copy(reflectionAmount = it)) }
                        AdvancedSlider("Reverb decay", controls.reverbTimeSeconds, 0.2f..8f, "${fmt(controls.reverbTimeSeconds)} s", "Decay target", !capturing, { helpText = "Approximate room decay target in seconds." }) { updateAdvanced(controls.copy(reverbTimeSeconds = it)) }
                        AdvancedSlider("Room size", controls.roomSize, 0f..1f, percent(controls.roomSize), "Room scale", !capturing, { helpText = "Controls the room-size parameter." }) { updateAdvanced(controls.copy(roomSize = it)) }
                        AdvancedSlider("Dampening", controls.dampening, 0f..1f, percent(controls.dampening), "Reflective → absorbent", !capturing, { helpText = "Higher dampening means more absorption." }) { updateAdvanced(controls.copy(dampening = it)) }
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                    SectionHeader("Sound", soundExpanded) { soundExpanded = !soundExpanded }
                    if (soundExpanded) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AdvancedSlider("High-band width", controls.stereoWidth, 0f..1f, "${fmt(controls.stereoWidth * 2)}×", "Stereo width", !capturing, { helpText = "Mid/side width for the upper band; 1× is unity." }) { updateAdvanced(controls.copy(stereoWidth = it)) }
                        AdvancedSlider("Bass width", controls.bassWidth, 0f..2f, "${fmt(controls.bassWidth)}×", "Low-band stereo width", !capturing, { helpText = "Low-band width; 1× is unity." }) { updateAdvanced(controls.copy(bassWidth = it)) }
                        AdvancedSlider("Bass gain", controls.bassGainDb, -12f..6f, "${fmt(controls.bassGainDb)} dB", "Bass gain", !capturing, { helpText = "Gain applied by the existing bass processing." }) { updateAdvanced(controls.copy(bassGainDb = it)) }
                        AdvancedSlider("Output trim", controls.outputGainDb, -24f..0f, "${fmt(controls.outputGainDb)} dB", "Output attenuation", !capturing, { helpText = "Pre-engine output trim in dB." }) { updateAdvanced(controls.copy(outputGainDb = it)) }
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                    SectionHeader("Engine", engineExpanded) { engineExpanded = !engineExpanded }
                    if (engineExpanded) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Buffer size", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(192, 384, 768).forEach { frames ->
                                PresetChip(frames.toString(), controls.quantumFrames == frames, !capturing) { updateAdvanced(controls.copy(quantumFrames = frames)) }
                            }
                        }
                        Text("${diagnostics.backendLabel()} · ${diagnostics.sampleRate} Hz · ${diagnostics.algorithmicLatencySamples} samples", color = colors.onSurfaceMuted, fontSize = 12.sp)
                        TelemetryRow("Processed frames", diagnostics.processedFrames.toString())
                        TelemetryRow("DSP mean / max", "${fmt(diagnostics.averageProcessingTimeMs.toFloat(), 3)} / ${fmt(diagnostics.maxProcessingTimeMs.toFloat(), 3)} ms")
                        TelemetryRow("Deadline misses", diagnostics.deadlineMisses.toString())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { captureProgress = 0f; capturing = true }, enabled = !capturing) { Text(if (capturing) "Capturing…" else "Capture 10 s") }
                            TextButton(onClick = { diagnosticsReturnMode = "advanced"; screenMode = "diagnostics" }) { Text("Diagnostics") }
                        }
                        if (capturing) LinearProgressIndicator(progress = { captureProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { updateAdvanced(ImmersiveControls(enabled = controls.enabled)) }, enabled = !capturing, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Reset") }
                        Button(onClick = { showSave = true }, enabled = !capturing && savedPresets.size < 32, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Save as preset") }
                    }
                }
                else -> {
                    Text("Engine health", style = MaterialTheme.typography.titleLarge)
                    Text(if (healthy) "Engine running smoothly" else "Deadline misses or processing failures detected", color = if (healthy) androidx.compose.ui.graphics.Color(0xFF20C45A) else androidx.compose.ui.graphics.Color(0xFFFFB020))
                    Text("${diagnostics.backendLabel()} · ${diagnostics.sampleRate} Hz · ${diagnostics.algorithmicLatencySamples} samples latency", color = colors.onSurfaceMuted)
                    TelemetryRow("Input RMS L / R", "${fmt(diagnostics.inputRmsL, 4)} / ${fmt(diagnostics.inputRmsR, 4)}")
                    TelemetryRow("Output RMS L / R", "${fmt(diagnostics.outputRmsL, 4)} / ${fmt(diagnostics.outputRmsR, 4)}")
                    TelemetryRow("Output peak L / R", "${fmt(diagnostics.outputPeakL, 4)} / ${fmt(diagnostics.outputPeakR, 4)}")
                    TelemetryRow("Safety gain reduction", "${fmt(diagnostics.limiterGainReductionDb)} dB")
                    TelemetryRow("DSP mean / max", "${fmt(diagnostics.averageProcessingTimeMs.toFloat(), 3)} / ${fmt(diagnostics.maxProcessingTimeMs.toFloat(), 3)} ms")
                    TelemetryRow("Deadline misses / failures", "${diagnostics.deadlineMisses} / ${diagnostics.nativeProcessFailures}")
                    TelemetryRow("Clipping / non-finite", "${diagnostics.clippingSource()} / ${diagnostics.nanCount + diagnostics.infCount}")
                    diagnostics.pipelineStages().forEach { stage ->
                        Text("${stage.id} · ${stage.name}", style = MaterialTheme.typography.labelMedium)
                        Text(if (stage.available) "RMS ${fmt(stage.output.rms, 4)} · peak ${fmt(stage.output.peak, 4)} · frames ${stage.output.frames}" else stage.unavailableReason ?: "Waiting for PCM", color = colors.onSurfaceMuted, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { ImmersiveAudioRuntime.resetDiagnostics(); capture = null }) { Text("Reset meters") }
                        TextButton(onClick = { captureProgress = 0f; capturing = true }, enabled = !capturing) { Text("Capture 10 s") }
                        TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Hide report" else "Full report") }
                    }
                    if (capturing) LinearProgressIndicator(progress = { captureProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    if (showDetails) SelectionContainer { Text(report(), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "FrostSoulX DSP telemetry"); putExtra(Intent.EXTRA_TEXT, report()) }, "Share DSP report")) }) { Text("Share report") }
                }
            }
        }
    }
    if (helpText != null) AlertDialog(onDismissRequest = { helpText = null }, title = { Text("Control help") }, text = { Text(helpText.orEmpty()) }, confirmButton = { TextButton(onClick = { helpText = null }) { Text("Got it") } })
    if (showSave) AlertDialog(onDismissRequest = { showSave = false }, title = { Text("Save listening space") },
        text = { OutlinedTextField(value = presetName, onValueChange = { presetName = it.take(64) }, label = { Text("Preset name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = presetName.isNotBlank(), onClick = {
            val preset = ImmersiveAudioPreset.fromControls(presetName, controls)
            scope.launch { context.dataStore.edit { it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(savedPresets + preset) } }
            presetName = ""; showSave = false
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } })
}

@Composable
private fun PresetChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    else OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun SimpleSlider(label: String, value: Float, display: String, enabled: Boolean, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.coerceIn(0f, 1f)) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(display, style = MaterialTheme.typography.titleMedium, color = FrostSoulTheme.colors.onSurface)
        }
        Slider(value = draft, onValueChange = { draft = it }, valueRange = 0f..1f, enabled = enabled,
            onValueChangeFinished = { onCommit(draft) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label })
    }
}

@Composable
private fun AdvancedSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String,
    help: String, enabled: Boolean, onHelp: () -> Unit, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.coerceIn(range)) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text("ⓘ", color = FrostSoulTheme.colors.onSurfaceMuted, modifier = Modifier.clickable(onClick = onHelp).padding(4.dp))
            }
            Text(display, style = MaterialTheme.typography.titleSmall)
        }
        Slider(value = draft, onValueChange = { draft = it }, valueRange = range, enabled = enabled,
            onValueChangeFinished = { onCommit(draft) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$label. $help" })
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(if (expanded) "⌃" else "⌄", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 22.sp)
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp, color = FrostSoulTheme.colors.onSurfaceMuted)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
private fun fmt(value: Float, digits: Int = 2): String = String.format(Locale.US, "%.$digits" + "f", value)
private fun percent(value: Float): String = "${fmt(value * 100, 0)}%"

private fun readSpatialControls(p: Preferences): ImmersiveControls = ImmersiveControls(
    enabled = p[StereoSurroundEnabledKey] ?: false, intensity = p[StereoSurroundIntensityKey] ?: 0.5f,
    roomPreset = ImmersiveRoomPreset.fromNative(p[StereoSurroundRoomPresetKey] ?: 2),
    roomMix = p[StereoSurroundRoomMixKey] ?: 0.18f, reflectionAmount = p[StereoSurroundReflectionAmountKey] ?: 0.28f,
    reverbTimeSeconds = p[StereoSurroundReverbTimeKey] ?: 1.35f, roomSize = p[StereoSurroundRoomSizeKey] ?: 0.5f,
    dampening = p[StereoSurroundDampeningKey] ?: 0.5f, stereoWidth = p[StereoSurroundStereoWidthKey] ?: 0.5f,
    carFader = p[StereoSurroundCarFaderKey] ?: 0f, bassGainDb = p[StereoSurroundBassGainDbKey] ?: 0f,
    outputGainDb = p[StereoSurroundOutputGainDbKey] ?: 0f, quantumFrames = p[StereoSurroundQuantumFramesKey] ?: 384,
    azimuth = p[SpatialAzimuthKey] ?: 0f, elevation = p[SpatialElevationKey] ?: 0f,
    distance = p[SpatialDistanceKey] ?: 1f, orbitEnabled = p[SpatialOrbitEnabledKey] ?: false,
    bassWidth = p[SpatialBassWidthKey] ?: 1f,
).sanitized()

private fun writeSpatialControls(p: androidx.datastore.preferences.core.MutablePreferences, c: ImmersiveControls) {
    p[StereoSurroundEnabledKey] = c.enabled; p[StereoSurroundIntensityKey] = c.intensity
    p[StereoSurroundRoomPresetKey] = c.roomPreset.nativeValue; p[StereoSurroundRoomMixKey] = c.roomMix
    p[StereoSurroundReflectionAmountKey] = c.reflectionAmount; p[StereoSurroundReverbTimeKey] = c.reverbTimeSeconds
    p[StereoSurroundRoomSizeKey] = c.roomSize; p[StereoSurroundDampeningKey] = c.dampening
    p[StereoSurroundStereoWidthKey] = c.stereoWidth; p[StereoSurroundCarFaderKey] = c.carFader
    p[StereoSurroundBassGainDbKey] = c.bassGainDb; p[StereoSurroundOutputGainDbKey] = c.outputGainDb
    p[StereoSurroundQuantumFramesKey] = c.quantumFrames
    p[SpatialAzimuthKey] = c.azimuth; p[SpatialElevationKey] = c.elevation
    p[SpatialDistanceKey] = c.distance; p[SpatialOrbitEnabledKey] = c.orbitEnabled
    p[SpatialBassWidthKey] = c.bassWidth
}
