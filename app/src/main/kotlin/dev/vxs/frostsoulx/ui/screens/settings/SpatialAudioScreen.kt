package dev.vxs.frostsoulx.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import dev.vxs.frostsoulx.constants.ConvolverActiveIrPresetKey
import dev.vxs.frostsoulx.constants.ConvolverIrPresetsKey
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
import dev.vxs.frostsoulx.playback.WavImpulseResponse
import dev.vxs.frostsoulx.playback.ImmersiveControls
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticCapture
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticSample
import dev.vxs.frostsoulx.playback.ImmersiveRoomPreset
import dev.vxs.frostsoulx.playback.defaultAndroidDescription
import dev.vxs.frostsoulx.playback.defaultDeviceDescription
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.utils.dataStore
import java.util.Locale
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var showIrSave by remember { mutableStateOf(false) }
    var irName by remember { mutableStateOf("") }
    var pendingIr by remember { mutableStateOf<WavImpulseResponse?>(null) }
    var pendingIrBytes by remember { mutableStateOf<ByteArray?>(null) }
    var irError by remember { mutableStateOf<String?>(null) }
    var irExpanded by remember { mutableStateOf(false) }
    var renameIrId by remember { mutableStateOf<String?>(null) }
    var renameIrName by remember { mutableStateOf("") }
    val irPresets = remember(preferences) {
        runCatching {
            val array=JSONArray(preferences?.get(ConvolverIrPresetsKey) ?: "[]")
            buildList { for(i in 0 until array.length()) array.optJSONObject(i)?.let { obj ->
                val id=obj.optString("id");val name=obj.optString("name").trim();val file=obj.optString("file")
                if(id.isNotBlank()&&name.isNotBlank()&&file.isNotBlank()) add(Triple(id,name,file))
            } }
        }.getOrDefault(emptyList())
    }
    val activeIrId=preferences?.get(ConvolverActiveIrPresetKey).orEmpty()
    val irPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri? ->
        if(uri!=null) scope.launch { runCatching {
            val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 32 * 1024 * 1024) { "IR WAV must be 32 MB or smaller" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("Could not read selected file")}
            val decoded=withContext(Dispatchers.Default){WavImpulseResponse.decode(bytes)}
            pendingIrBytes=bytes;pendingIr=decoded
            irName=runCatching{context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)?.substringBeforeLast('.') else null}}.getOrNull().orEmpty().ifBlank{"Custom IR"}.take(64)
            irError=null;showIrSave=true
        }.onFailure{irError=it.message ?: "Could not import this WAV"} }
    }
    LaunchedEffect(activeIrId,irPresets){
        val selected=irPresets.firstOrNull{it.first==activeIrId}
        if(selected==null){ImmersiveAudioRuntime.setCustomIr(null)}
        else runCatching{
            val bytes=withContext(Dispatchers.IO){File(File(context.filesDir,"convolver-ir"),selected.third).readBytes()}
            ImmersiveAudioRuntime.setCustomIr(withContext(Dispatchers.Default){WavImpulseResponse.decode(bytes)})
        }.onFailure{ImmersiveAudioRuntime.setCustomIr(null);irError=it.message ?: "Could not load saved IR"}
    }
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
    var engineExpanded by remember { mutableStateOf(false) }
    var helpText by remember { mutableStateOf<String?>(null) }
    fun updateAdvanced(value: ImmersiveControls) { customAdvanced = true; update(value) }
    val healthy = diagnostics.deadlineMisses == 0L && diagnostics.nativeProcessFailures == 0L

    Scaffold(
        containerColor = colors.background,
        topBar = { TopAppBar(
            title = { Text(when (screenMode) { "advanced" -> "Fine tuning"; "diagnostics" -> "Diagnostics"; else -> "Spatial audio" }, color = colors.onSurface) },
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
                    SpatialCard("Your listening space", "Headphones recommended for binaural sound") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (controls.enabled) "Spatial audio is on" else "Original stereo", style = MaterialTheme.typography.titleMedium)
                                Text(if (controls.enabled) "Stereo bass stays outside the room" else "Turn on to use rooms and 3D motion", color = colors.onSurfaceMuted, fontSize = 12.sp)
                            }
                            Switch(checked = controls.enabled, enabled = preferences != null && !capturing,
                                onCheckedChange = { update(controls.copy(enabled = it)) },
                                modifier = Modifier.semantics { contentDescription = "Enable spatial audio" })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { presetMenu = true }, enabled = preferences != null && !capturing, modifier = Modifier.weight(1f)) { Text("My presets (${savedPresets.size})") }
                            Button(onClick = { showSave = true }, enabled = preferences != null && !capturing && savedPresets.size < 32, modifier = Modifier.weight(1f)) { Text("Save setup") }
                        }
                    }
                    SpatialCard("3D orbit · 8D motion", "A slow circle around your head, with height cues") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (controls.orbitEnabled) "Orbit on · 11 s per circle" else "Orbit off · fixed position", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = controls.orbitEnabled, enabled = preferences != null && !capturing,
                                onCheckedChange = { update(controls.copy(orbitEnabled = it)) },
                                modifier = Modifier.semantics { contentDescription = "3D orbit, independent of room selection" })
                        }
                        Text("Works with no room, any room, or a custom response. Bass does not orbit. The spatial audio switch is the master bypass.", color = colors.onSurfaceMuted, fontSize = 12.sp)
                    }
                    SpatialCard("Room ambience", "Choose the space, then adjust how much you hear") {
                        var roomMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { roomMenu = true }, enabled = preferences != null && !capturing, modifier = Modifier.fillMaxWidth()) {
                                Text(if (activeIrId.isNotBlank()) "Custom: ${irPresets.firstOrNull { it.first == activeIrId }?.second ?: "response"}" else controls.roomPreset.label)
                            }
                            DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                                ImmersiveRoomPreset.entries.forEach { room ->
                                    DropdownMenuItem(text = { Text(room.label) }, onClick = {
                                        roomMenu = false; customAdvanced = false
                                        scope.launch { context.dataStore.edit { it.remove(ConvolverActiveIrPresetKey) } }
                                        ImmersiveAudioRuntime.setCustomIr(null)
                                        update(controls.copy(roomPreset = room))
                                    })
                                }
                            }
                        }
                        val roomActive = activeIrId.isNotBlank() || controls.roomPreset != ImmersiveRoomPreset.OFF
                        SimpleSlider("Spatial effect mix", controls.intensity, percent(controls.intensity), !capturing && controls.enabled) { update(controls.copy(intensity = it)) }
                        Text("Original stereo ← → room / headphone effect. Orbit has its own switch.", color = colors.onSurfaceMuted, fontSize = 12.sp)
                        SimpleSlider("Room echo level", controls.roomMix, percent(controls.roomMix), !capturing && controls.enabled && roomActive && activeIrId.isBlank()) { update(controls.copy(roomMix = it)) }
                        Text(if (activeIrId.isNotBlank()) "Custom responses include their own echoes. Use Spatial effect mix to blend them." else "No room removes echoes, not orbit or stereo bass.", color = colors.onSurfaceMuted, fontSize = 12.sp)
                        SectionHeader("Custom room responses (WAV)", irExpanded) { irExpanded = !irExpanded }
                        if (irExpanded) {
                            Text("Import an impulse response (IR) to replace the generated room.", color = colors.onSurfaceMuted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { irError = null; irPicker.launch(arrayOf("audio/*", "application/octet-stream")) }, enabled = !capturing && irPresets.size < 32) { Text("Import WAV") }
                                OutlinedButton(onClick = { ImmersiveAudioRuntime.setCustomIr(null); scope.launch { context.dataStore.edit { it.remove(ConvolverActiveIrPresetKey) } } }, enabled = !capturing && activeIrId.isNotBlank()) { Text("Built-in room") }
                            }
                            irPresets.forEach { preset ->
                                Column(Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { scope.launch { context.dataStore.edit { it[ConvolverActiveIrPresetKey] = preset.first } } }, enabled = !capturing) { Text((if (preset.first == activeIrId) "Active · " else "") + preset.second) }
                                    Row {
                                        TextButton(onClick = { renameIrId = preset.first; renameIrName = preset.second }, enabled = !capturing) { Text("Rename") }
                                        TextButton(onClick = { scope.launch {
                                            context.dataStore.edit { p ->
                                                p[ConvolverIrPresetsKey] = JSONArray().apply { irPresets.filterNot { it.first == preset.first }.forEach { (id, name, file) -> put(JSONObject().put("id", id).put("name", name).put("file", file)) } }.toString()
                                                if (activeIrId == preset.first) p.remove(ConvolverActiveIrPresetKey)
                                            }
                                            withContext(Dispatchers.IO) { File(File(context.filesDir, "convolver-ir"), preset.third).delete() }
                                        } }, enabled = !capturing) { Text("Delete") }
                                    }
                                }
                            }
                            Text("Mono / stereo / 4-path WAV · PCM 16/24/32-bit or float32 · up to 32,768 taps per path", color = colors.onSurfaceMuted, fontSize = 11.sp)
                            Text("4-path order: L→L, L→R, R→L, R→R. Not four-speaker playback. Responses longer than 32,768 taps must be shortened first.", color = colors.onSurfaceMuted, fontSize = 11.sp)
                        }
                        irError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                    SpatialCard("Stereo bass & balance", "Bass is split at 180 Hz and kept out of all room responses") {
                        AdvancedSlider("Bass level", controls.bassGainDb, -12f..6f, "${fmt(controls.bassGainDb, 1)} dB", "0 dB = unchanged", !capturing && controls.enabled, { helpText = "Adjusts only the stereo low band. Bass bypasses room echoes, custom IRs and orbit; safety limiting still protects the final output." }) { update(controls.copy(bassGainDb = it)) }
                        AdvancedSlider("Stereo width", controls.stereoWidth, 0f..1f, "${fmt(controls.stereoWidth * 2)}×", "1× = original width above the bass band", !capturing && controls.enabled, { helpText = "Narrows or widens the upper stereo band. Bass width is separate in fine tuning." }) { update(controls.copy(stereoWidth = it)) }
                    }
                    OutlinedButton(onClick = { screenMode = "advanced" }, modifier = Modifier.fillMaxWidth()) { Text("Fine tuning · position, room & output") }
                    TextButton(onClick = { diagnosticsReturnMode = "simple"; screenMode = "diagnostics" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (!controls.enabled) "Diagnostics · spatial audio off" else if (diagnostics.processedFrames == 0L) "Diagnostics · waiting for playback" else if (healthy) "Diagnostics · engine healthy" else "Diagnostics · check engine")
                    }
                }
                "advanced" -> {
                    val tuningEnabled = controls.enabled && preferences != null && !capturing
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fine tuning", style = MaterialTheme.typography.titleLarge)
                        Text(if (customAdvanced) "Custom" else "Preset", color = colors.accent, style = MaterialTheme.typography.labelLarge)
                    }
                    SectionHeader("Position", positionExpanded) { positionExpanded = !positionExpanded }
                    if (positionExpanded) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (controls.orbitEnabled) Text("Turn off 3D orbit on the main page to adjust a fixed position.", color = colors.onSurfaceMuted)
                        AdvancedSlider("Direction around you", controls.azimuth, -180f..180f, "${fmt(controls.azimuth, 0)}°", "Front = 0°; left = +90°; right = −90°", tuningEnabled && !controls.orbitEnabled, { helpText = "Azimuth sets the source direction around the listener. Front is 0°." }) { updateAdvanced(controls.copy(azimuth = it)) }
                        AdvancedSlider("Height angle", controls.elevation, -90f..90f, "${fmt(controls.elevation, 0)}°", "Below −90° to above +90°", tuningEnabled && !controls.orbitEnabled, { helpText = "Elevation controls the source angle above or below the listener." }) { updateAdvanced(controls.copy(elevation = it)) }
                        AdvancedSlider("Distance from you", controls.distance, 0.2f..10f, "${fmt(controls.distance)} m", "Source distance", tuningEnabled && !controls.orbitEnabled, { helpText = "Distance is expressed in metres." }) { updateAdvanced(controls.copy(distance = it)) }
                        AdvancedSlider("Front / rear", controls.carFader, -1f..1f, fmt(controls.carFader), "Rear ← centre → front", tuningEnabled && !controls.orbitEnabled, { helpText = "Moves the source balance between rear and front." }) { updateAdvanced(controls.copy(carFader = it)) }
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                    SectionHeader("Room", roomExpanded) { roomExpanded = !roomExpanded }
                    if (roomExpanded) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AdvancedSlider("Reflections", controls.reflectionAmount, 0f..1f, percent(controls.reflectionAmount), "Early reflection strength", tuningEnabled, { helpText = "Controls the level of early room reflections." }) { updateAdvanced(controls.copy(reflectionAmount = it)) }
                        AdvancedSlider("Reverb decay", controls.reverbTimeSeconds, 0.2f..8f, "${fmt(controls.reverbTimeSeconds)} s", "Decay target", tuningEnabled, { helpText = "Approximate room decay target in seconds." }) { updateAdvanced(controls.copy(reverbTimeSeconds = it)) }
                        AdvancedSlider("Room size", controls.roomSize, 0f..1f, percent(controls.roomSize), "Room scale", tuningEnabled, { helpText = "Controls the room-size parameter." }) { updateAdvanced(controls.copy(roomSize = it)) }
                        AdvancedSlider("Echo softness", controls.dampening, 0f..1f, percent(controls.dampening), "Reflective → absorbent", tuningEnabled, { helpText = "Higher dampening means more absorption." }) { updateAdvanced(controls.copy(dampening = it)) }
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                    SectionHeader("Sound", soundExpanded) { soundExpanded = !soundExpanded }
                    if (soundExpanded) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AdvancedSlider("Stereo width (above bass)", controls.stereoWidth, 0f..1f, "${fmt(controls.stereoWidth * 2)}×", "Stereo width", tuningEnabled, { helpText = "Mid/side width for the upper band; 1× is unity." }) { updateAdvanced(controls.copy(stereoWidth = it)) }
                        AdvancedSlider("Stereo bass width", controls.bassWidth, 0f..2f, "${fmt(controls.bassWidth)}×", "Low-band stereo width", tuningEnabled, { helpText = "Low-band width; 1× is unity." }) { updateAdvanced(controls.copy(bassWidth = it)) }
                        AdvancedSlider("Bass gain", controls.bassGainDb, -12f..6f, "${fmt(controls.bassGainDb)} dB", "Bass gain", tuningEnabled, { helpText = "Adjusts the stereo bass band before its delay-matched bypass. It never enters room responses or orbit." }) { updateAdvanced(controls.copy(bassGainDb = it)) }
                        AdvancedSlider("Output headroom", controls.outputGainDb, -24f..0f, "${fmt(controls.outputGainDb)} dB", "Output attenuation", tuningEnabled, { helpText = "Reduces the final playback level to leave headroom for loud material." }) { updateAdvanced(controls.copy(outputGainDb = it)) }
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
    if (presetMenu) AlertDialog(
        onDismissRequest = { presetMenu = false }, title = { Text("My listening presets") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (savedPresets.isEmpty()) Text("No saved setups yet. Adjust your sound, then tap Save setup.")
                savedPresets.forEachIndexed { index, preset ->
                    Column {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                        Text("${preset.roomPreset.label} · orbit ${if (preset.orbitEnabled) "on" else "off"}", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = {
                                customAdvanced = true
                                update(preset.toControls())
                                val irId = preset.customIrPresetId.takeIf { id -> irPresets.any { it.first == id } }.orEmpty()
                                if (preset.customIrPresetId.isNotBlank() && irId.isBlank()) irError = "This preset's custom response was deleted; using its built-in room."
                                scope.launch { context.dataStore.edit { p ->
                                    if (irId.isBlank()) p.remove(ConvolverActiveIrPresetKey) else p[ConvolverActiveIrPresetKey] = irId
                                } }
                                presetMenu = false
                            }, enabled = !capturing) { Text("Load") }
                            TextButton(onClick = { scope.launch { context.dataStore.edit { it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(savedPresets.filterIndexed { i, _ -> i != index }) } } }, enabled = !capturing) { Text("Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { presetMenu = false }) { Text("Done") } },
    )
    if (helpText != null) AlertDialog(onDismissRequest = { helpText = null }, title = { Text("Control help") }, text = { Text(helpText.orEmpty()) }, confirmButton = { TextButton(onClick = { helpText = null }) { Text("Got it") } })
    if(showIrSave) AlertDialog(
        onDismissRequest={showIrSave=false;pendingIr=null;pendingIrBytes=null},title={Text("Save impulse response")},
        text={OutlinedTextField(value=irName,onValueChange={irName=it.take(64)},label={Text("Preset name")},singleLine=true)},
        confirmButton={TextButton(enabled=irName.isNotBlank()&&pendingIr!=null&&pendingIrBytes!=null&&irPresets.size<32,onClick={
            val decoded=pendingIr ?: return@TextButton;val bytes=pendingIrBytes ?: return@TextButton
            val id=java.util.UUID.randomUUID().toString();val filename="$id.wav"
            scope.launch{runCatching{
                withContext(Dispatchers.IO){File(File(context.filesDir,"convolver-ir").apply{mkdirs()},filename).writeBytes(bytes)}
                val next=irPresets.map{(a,b,c)->JSONObject().put("id",a).put("name",b).put("file",c)}.toMutableList()
                next.add(JSONObject().put("id",id).put("name",irName.trim()).put("file",filename))
                context.dataStore.edit{it[ConvolverIrPresetsKey]=JSONArray().apply{next.takeLast(32).forEach{put(it)}}.toString();it[ConvolverActiveIrPresetKey]=id}
                ImmersiveAudioRuntime.setCustomIr(decoded);showIrSave=false;pendingIr=null;pendingIrBytes=null
            }.onFailure{irError=it.message ?: "Could not save IR preset"}}
        }){Text("Save & load")}},
        dismissButton={TextButton(onClick={showIrSave=false;pendingIr=null;pendingIrBytes=null}){Text("Cancel")}}
    )
    if(renameIrId!=null) AlertDialog(
        onDismissRequest={renameIrId=null},title={Text("Rename IR preset")},
        text={OutlinedTextField(value=renameIrName,onValueChange={renameIrName=it.take(64)},label={Text("Preset name")},singleLine=true)},
        confirmButton={TextButton(enabled=renameIrName.isNotBlank(),onClick={
            val target=renameIrId ?: return@TextButton
            scope.launch{
                val next=irPresets.map{(id,name,file)->JSONObject().put("id",id).put("name",if(id==target)renameIrName.trim() else name).put("file",file)}
                context.dataStore.edit{it[ConvolverIrPresetsKey]=JSONArray().apply{next.forEach{put(it)}}.toString()}
                renameIrId=null
            }
        }){Text("Save name")}},
        dismissButton={TextButton(onClick={renameIrId=null}){Text("Cancel")}}
    )
    if (showSave) AlertDialog(onDismissRequest = { showSave = false }, title = { Text("Save listening space") },
        text = { OutlinedTextField(value = presetName, onValueChange = { presetName = it.take(64) }, label = { Text("Preset name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = presetName.isNotBlank() && savedPresets.size < 32, onClick = {
            val preset = ImmersiveAudioPreset.fromControls(presetName, controls, activeIrId)
            scope.launch { context.dataStore.edit { it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(savedPresets + preset) } }
            presetName = ""; showSave = false
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } })
}

@Composable
private fun SpatialCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = FrostSoulTheme.colors.surface.copy(alpha = 0.72f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FrostSoulTheme.colors.onSurfaceMuted)
            content()
        }
    }
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
                Box(Modifier.size(48.dp).clickable(onClick = onHelp), contentAlignment = Alignment.Center) {
                    Text("?", color = FrostSoulTheme.colors.onSurfaceMuted)
                }
            }
            Text(display, style = MaterialTheme.typography.titleSmall)
        }
        Text(help, style = MaterialTheme.typography.bodySmall, color = FrostSoulTheme.colors.onSurfaceMuted)
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
