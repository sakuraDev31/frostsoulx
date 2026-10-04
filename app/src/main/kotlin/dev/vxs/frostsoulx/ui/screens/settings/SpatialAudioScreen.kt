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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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

    Scaffold(
        containerColor = colors.background,
        topBar = { TopAppBar(title = { Text("Spatial audio", color = colors.onSurface) },
            navigationIcon = { IconButton(onClick = { navController.navigateUp() },
                modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹", fontSize = 30.sp) } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)
            .padding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues())
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SpatialCard("FROSTSOULX / AUDIO LAB", "A space built around your music") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(if (controls.enabled) "Spatial engine enabled" else "Original audio • strict bypass", style = MaterialTheme.typography.titleMedium)
                        Text("Unified HRTF + physical room convolution", color = colors.onSurfaceMuted, fontSize = 12.sp)
                    }
                    Switch(checked = controls.enabled, enabled = preferences != null && !capturing,
                        onCheckedChange = { update(controls.copy(enabled = it)) },
                        modifier = Modifier.semantics { contentDescription = "Enable spatial audio" })
                }
                Text(if (controls.enabled && !diagnostics.processorEnabled) "Waiting for stereo PCM. Unsupported formats or unavailable native library remain bypassed."
                    else if (controls.enabled) "${diagnostics.backendLabel()} • ${diagnostics.sampleRate} Hz • ${diagnostics.algorithmicLatencySamples} samples latency"
                    else "Turn on to render a virtual listening space. Best experienced with headphones.",
                    color = colors.onSurfaceMuted, fontSize = 12.sp)
                SpatialSlider("Spatial blend", controls.intensity, 0f..1f, percent(controls.intensity), "Dry → spatial", !capturing) { update(controls.copy(intensity = it)) }
            }
            SpatialCard("01 / SOURCE", "Position & perspective") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("3D Orbit Mode", style = MaterialTheme.typography.titleMedium)
                        Text(if (controls.orbitEnabled) "Classic 8D motion · 360° auto orbit · works with every room" else "Off · source position is static", color = colors.onSurfaceMuted, fontSize = 12.sp)
                    }
                    Switch(checked = controls.orbitEnabled, enabled = !capturing,
                        onCheckedChange = { update(controls.copy(orbitEnabled = it)) },
                        modifier = Modifier.semantics { contentDescription = "3D Orbit Mode" })
                }
                SourceOrbit(controls.azimuth, controls.elevation)
                Text("Front = 0° • positive azimuth = left • positive elevation = up", color = colors.onSurfaceMuted, fontSize = 12.sp)
                SpatialSlider("Azimuth", controls.azimuth, -180f..180f, "${fmt(controls.azimuth, 0)}°", "Right ← front → left", !capturing) { update(controls.copy(azimuth = it)) }
                SpatialSlider("Elevation", controls.elevation, -90f..90f, "${fmt(controls.elevation, 0)}°", "Below → above", !capturing) { update(controls.copy(elevation = it)) }
                SpatialSlider("Source distance", controls.distance, 0.2f..10f, "${fmt(controls.distance)} m", "Position is bounded by the selected room", !capturing) { update(controls.copy(distance = it)) }
                SpatialSlider("Front / rear fader", controls.carFader, -1f..1f, fmt(controls.carFader), "Rear ← centre → front (±0.5 m source offset)", !capturing) { update(controls.copy(carFader = it)) }
            }
            SpatialCard("02 / ENVIRONMENT", "Room & acoustics") {
                OutlinedButton(onClick = { presetMenu = true }, enabled = !capturing) { Text(controls.roomPreset.label + "  ▾") }
                DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                    ImmersiveRoomPreset.entries.forEach { preset ->
                        DropdownMenuItem(text = { Text(preset.label) }, onClick = { update(controls.copy(roomPreset = preset)); presetMenu = false })
                    }
                }
                Text("Room controls reshape the preset's geometry and absorption. Anechoic removes room reflections, not direct spatialization.", color = colors.onSurfaceMuted, fontSize = 12.sp)
                SpatialSlider("Room mix", controls.roomMix, 0f..1f, percent(controls.roomMix), "Direct → room energy", !capturing) { update(controls.copy(roomMix = it)) }
                SpatialSlider("Reflections", controls.reflectionAmount, 0f..1f, percent(controls.reflectionAmount), "Geometric early reflection strength", !capturing) { update(controls.copy(reflectionAmount = it)) }
                SpatialSlider("Reverb decay time", controls.reverbTimeSeconds, 0.2f..8f, "${fmt(controls.reverbTimeSeconds)} s", "Decay target; finite IR length bounds the rendered tail", !capturing) { update(controls.copy(reverbTimeSeconds = it)) }
                SpatialSlider("Room size", controls.roomSize, 0f..1f, percent(controls.roomSize), "Physical room scale", !capturing) { update(controls.copy(roomSize = it)) }
                SpatialSlider("Dampening", controls.dampening, 0f..1f, percent(controls.dampening), "Reflective → absorbent boundaries", !capturing) { update(controls.copy(dampening = it)) }
            }
            SpatialCard("03 / SIGNAL", "Width & gain") {
                SpatialSlider("High-band width", controls.stereoWidth, 0f..1f, "${fmt(controls.stereoWidth * 2)}×", "Independent upper-band M/S • 1× = unity", !capturing) { update(controls.copy(stereoWidth = it)) }
                SpatialSlider("Bass width", controls.bassWidth, 0f..2f, "${fmt(controls.bassWidth)}×", "Independent low-band M/S • 1× = unity", !capturing) { update(controls.copy(bassWidth = it)) }
                SpatialSlider("Bass gain", controls.bassGainDb, -12f..6f, "${fmt(controls.bassGainDb)} dB", "Boosts consume bounded engine headroom", !capturing) { update(controls.copy(bassGainDb = it)) }
                SpatialSlider("Output trim", controls.outputGainDb, -24f..0f, "${fmt(controls.outputGainDb)} dB", "Smoothed pre-engine attenuation", !capturing) { update(controls.copy(outputGainDb = it)) }
                Text("Linked true-peak safety is always active. No duplicate limiter or backend selector.", color = colors.onSurfaceMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(192, 384, 768).forEach { frames ->
                        TextButton(onClick = { update(controls.copy(quantumFrames = frames)) }, enabled = !capturing) {
                            Text(if (controls.quantumFrames == frames) "[$frames]" else "$frames")
                        }
                    }
                }
                Text("Host quantum: ${controls.quantumFrames} frames • 384 recommended", color = colors.onSurfaceMuted, fontSize = 12.sp)
            }
            SpatialCard("04 / PRESETS", "Your listening spaces") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSave = true }, enabled = !capturing && savedPresets.size < 32) { Text("Save") }
                    OutlinedButton(onClick = { update(ImmersiveControls(enabled = controls.enabled)) }, enabled = !capturing) { Text("Reset controls") }
                }
                if (savedPresets.isEmpty()) Text("Save all controls together. Existing presets are migrated automatically.", color = colors.onSurfaceMuted, fontSize = 12.sp)
                savedPresets.forEachIndexed { index, preset ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { update(preset.toControls()) }, enabled = !capturing, modifier = Modifier.weight(1f)) { Text(preset.name) }
                        TextButton(onClick = { scope.launch { context.dataStore.edit { it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(savedPresets.filterIndexed { i, _ -> i != index }) } } }, enabled = !capturing) { Text("Delete") }
                    }
                }
            }
            SpatialCard("05 / TELEMETRY", "Signal health") {
                Text("${diagnostics.backendLabel()} • ${diagnostics.processedFrames} frames • ${diagnostics.totalBlocks} blocks", color = colors.accent)
                TelemetryRow("Input RMS L / R", "${fmt(diagnostics.inputRmsL, 4)} / ${fmt(diagnostics.inputRmsR, 4)}")
                TelemetryRow("Output RMS L / R", "${fmt(diagnostics.outputRmsL, 4)} / ${fmt(diagnostics.outputRmsR, 4)}")
                TelemetryRow("Output peak L / R", "${fmt(diagnostics.outputPeakL, 4)} / ${fmt(diagnostics.outputPeakR, 4)}")
                TelemetryRow("Safety gain reduction", "${fmt(diagnostics.limiterGainReductionDb)} dB")
                TelemetryRow("DSP mean / max", "${fmt(diagnostics.averageProcessingTimeMs.toFloat(), 3)} / ${fmt(diagnostics.maxProcessingTimeMs.toFloat(), 3)} ms")
                TelemetryRow("Deadline misses / failures", "${diagnostics.deadlineMisses} / ${diagnostics.nativeProcessFailures}")
                TelemetryRow("Clipping / non-finite", "${diagnostics.clippingSource()} / ${diagnostics.nanCount + diagnostics.infCount}")
                diagnostics.pipelineStages().forEach { stage ->
                    Text("${stage.id} · ${stage.name}", style = MaterialTheme.typography.labelMedium)
                    Text(if (stage.available) "RMS ${fmt(stage.output.rms, 4)} · peak ${fmt(stage.output.peak, 4)} · frames ${stage.output.frames}"
                        else stage.unavailableReason ?: "Waiting for PCM", color = colors.onSurfaceMuted, fontSize = 11.sp)
                }
                Text("Meters are cumulative since reset, not instantaneous. AudioTrack/DAC measurements remain unavailable.", color = colors.onSurfaceMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { ImmersiveAudioRuntime.resetDiagnostics(); capture = null }, enabled = !capturing) { Text("Reset meters") }
                    TextButton(onClick = { captureProgress = 0f; capturing = true }, enabled = !capturing) { Text("Capture 10 s") }
                }
                if (capturing) LinearProgressIndicator(progress = { captureProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Hide full telemetry" else "Full telemetry") }
                    TextButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "FrostSoulX DSP telemetry"); putExtra(Intent.EXTRA_TEXT, report())
                        }, "Share DSP report"))
                    }, enabled = !capturing) { Text("Share report") }
                }
                if (capture != null) Text("10-second capture ready • ${capture!!.samples.size} samples", color = colors.accent, fontSize = 12.sp)
                if (showDetails) SelectionContainer { Text(report(), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        }
    }
    if (showSave) AlertDialog(onDismissRequest = { showSave = false }, title = { Text("Save listening space") },
        text = { OutlinedTextField(value = presetName, onValueChange = { presetName = it.take(64) }, label = { Text("Preset name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = presetName.isNotBlank(), onClick = {
            val preset = ImmersiveAudioPreset.fromControls(presetName, controls)
            scope.launch { context.dataStore.edit { it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(savedPresets + preset) } }
            presetName = ""; showSave = false
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } })
}

@Composable
private fun SpatialCard(eyebrow: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = FrostSoulTheme.colors
    Card(colors = CardDefaults.cardColors(containerColor = colors.surface, contentColor = colors.onSurface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(eyebrow, color = colors.accent, style = MaterialTheme.typography.labelSmall)
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun SpatialSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String,
    hint: String, enabled: Boolean, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.coerceIn(range)) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(if (draft == value) display else fmt(draft), color = FrostSoulTheme.colors.accent)
        }
        Slider(value = draft, onValueChange = { draft = it }, valueRange = range, enabled = enabled,
            onValueChangeFinished = { onCommit(draft) }, modifier = Modifier.semantics { contentDescription = label })
        Text(hint, color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SourceOrbit(azimuth: Float, elevation: Float) {
    val colors = FrostSoulTheme.colors
    Canvas(Modifier.fillMaxWidth().height(190.dp).semantics {
        contentDescription = "Source at azimuth $azimuth degrees, elevation $elevation degrees"
    }) {
        val centre = center
        val radius = size.minDimension * 0.40f
        drawCircle(colors.outline, radius, centre, style = Stroke(1.dp.toPx()))
        drawCircle(colors.outline.copy(alpha = 0.5f), radius * 0.5f, centre, style = Stroke(1.dp.toPx()))
        drawLine(colors.outline, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y))
        drawLine(colors.outline, Offset(centre.x, centre.y - radius), Offset(centre.x, centre.y + radius))
        val angle = azimuth * Math.PI.toFloat() / 180f
        val projected = radius * cos(elevation * Math.PI.toFloat() / 180f)
        val source = Offset(centre.x - sin(angle) * projected, centre.y - cos(angle) * projected)
        drawLine(colors.accent.copy(alpha = 0.5f), centre, source, 2.dp.toPx())
        drawCircle(colors.accent.copy(alpha = 0.15f), 18.dp.toPx(), source)
        drawCircle(colors.accentBright, 6.dp.toPx(), source)
        drawCircle(colors.onSurface, 5.dp.toPx(), centre)
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
