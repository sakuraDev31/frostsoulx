package dev.vxs.frostsoulx.ui.screens.settings

import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.StereoSurroundEnabledKey
import dev.vxs.frostsoulx.constants.StereoSurroundIntensityKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomPresetKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomMixKey
import dev.vxs.frostsoulx.constants.StereoSurroundReflectionAmountKey
import dev.vxs.frostsoulx.constants.StereoSurroundReverbTimeKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomSizeKey
import dev.vxs.frostsoulx.constants.StereoSurroundDampeningKey
import dev.vxs.frostsoulx.constants.StereoSurroundStereoWidthKey
import dev.vxs.frostsoulx.constants.StereoSurroundQuantumFramesKey
import dev.vxs.frostsoulx.constants.StereoSurroundSavedPresetsKey
import dev.vxs.frostsoulx.constants.StereoSurroundLimiterEnabledKey
import dev.vxs.frostsoulx.constants.EqualizerEnabledKey
import dev.vxs.frostsoulx.constants.EqualizerBassBoostEnabledKey
import dev.vxs.frostsoulx.constants.EqualizerBassBoostStrengthKey
import dev.vxs.frostsoulx.constants.EqualizerTrebleGainMbKey
import dev.vxs.frostsoulx.constants.EqualizerOutputGainEnabledKey
import dev.vxs.frostsoulx.constants.EqualizerOutputGainMbKey
import dev.vxs.frostsoulx.constants.ImmersiveDevelopmentWarningShownKey
import dev.vxs.frostsoulx.playback.ImmersiveAudioRuntime
import dev.vxs.frostsoulx.playback.ImmersiveRoomPreset
import dev.vxs.frostsoulx.playback.ImmersiveAudioProcessor
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale
import dev.vxs.frostsoulx.playback.ImmersiveAudioDiagnostics
import dev.vxs.frostsoulx.playback.ImmersiveAudioPreset
import dev.vxs.frostsoulx.playback.ImmersiveActiveCapture
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticCapture
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticSample
import dev.vxs.frostsoulx.playback.defaultAndroidDescription
import dev.vxs.frostsoulx.playback.defaultDeviceDescription

private enum class ImmersiveSettingsPage { Default, Advanced, Diagnostic }

@Composable
fun StereoSurroundScreen(navController: NavController) {
    val enabledPreference = rememberPreference(StereoSurroundEnabledKey, defaultValue = false)
    val intensityPreference = rememberPreference(StereoSurroundIntensityKey, defaultValue = 0.5f)
    val enabled by enabledPreference
    val persistedIntensity by intensityPreference
    val roomPresetPreference = rememberPreference(StereoSurroundRoomPresetKey, defaultValue = ImmersiveRoomPreset.STUDIO.nativeValue)
    val roomMixPreference = rememberPreference(StereoSurroundRoomMixKey, defaultValue = 0.18f)
    val reflectionPreference = rememberPreference(StereoSurroundReflectionAmountKey, defaultValue = 0.28f)
    val reverbTimePreference = rememberPreference(StereoSurroundReverbTimeKey, defaultValue = 1.35f)
    val roomSizePreference = rememberPreference(StereoSurroundRoomSizeKey, defaultValue = 0.5f)
    val dampeningPreference = rememberPreference(StereoSurroundDampeningKey, defaultValue = 0.5f)
    val stereoWidthPreference = rememberPreference(StereoSurroundStereoWidthKey, defaultValue = 0.5f)
    val quantumPreference = rememberPreference(StereoSurroundQuantumFramesKey, defaultValue = ImmersiveAudioProcessor.DEFAULT_QUANTUM_FRAMES)
    val limiterPreference = rememberPreference(StereoSurroundLimiterEnabledKey, defaultValue = true)
    val eqEnabledPreference = rememberPreference(EqualizerEnabledKey, defaultValue = false)
    val bassEnabledPreference = rememberPreference(EqualizerBassBoostEnabledKey, defaultValue = false)
    val bassStrengthPreference = rememberPreference(EqualizerBassBoostStrengthKey, defaultValue = 0)
    val trebleGainPreference = rememberPreference(EqualizerTrebleGainMbKey, defaultValue = 0)
    val outputGainEnabledPreference = rememberPreference(EqualizerOutputGainEnabledKey, defaultValue = false)
    val outputGainPreference = rememberPreference(EqualizerOutputGainMbKey, defaultValue = 0)
    val savedPresetsPreference = rememberPreference(StereoSurroundSavedPresetsKey, defaultValue = "")
    val developmentWarningPreference = rememberPreference(ImmersiveDevelopmentWarningShownKey, defaultValue = false)
    val persistedRoomPreset by roomPresetPreference
    val persistedRoomMix by roomMixPreference
    val persistedReflectionAmount by reflectionPreference
    val persistedReverbTime by reverbTimePreference
    val persistedRoomSize by roomSizePreference
    val persistedDampening by dampeningPreference
    val persistedStereoWidth by stereoWidthPreference
    val persistedQuantum by quantumPreference
    val limiterEnabled by limiterPreference
    val bassEnabled by bassEnabledPreference
    val bassStrength by bassStrengthPreference
    val trebleGainMb by trebleGainPreference
    val outputGainEnabled by outputGainEnabledPreference
    val outputGainMb by outputGainPreference
    val savedPresetsRaw by savedPresetsPreference
    val developmentWarningShown by developmentWarningPreference

    var selectedPage by remember { mutableStateOf(ImmersiveSettingsPage.Default) }
    var draftIntensity by remember { mutableFloatStateOf(persistedIntensity.coerceIn(0f, 1f)) }
    var draftRoomMix by remember { mutableFloatStateOf(persistedRoomMix.coerceIn(0f, 1f)) }
    var draftReflectionAmount by remember { mutableFloatStateOf(persistedReflectionAmount.coerceIn(0f, 1f)) }
    var draftReverbTime by remember { mutableFloatStateOf(persistedReverbTime.coerceIn(0.2f, 8f)) }
    var draftRoomSize by remember { mutableFloatStateOf(persistedRoomSize.coerceIn(0f, 1f)) }
    var draftDampening by remember { mutableFloatStateOf(persistedDampening.coerceIn(0f, 1f)) }
    var draftStereoWidth by remember { mutableFloatStateOf(persistedStereoWidth.coerceIn(0f, 1f)) }
    var draftQuantum by remember { mutableStateOf(persistedQuantum.coerceAtLeast(1)) }
    var isDragging by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(ImmersiveAudioDiagnostics()) }
    var showSavePreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    val savedPresets = remember(savedPresetsRaw) { ImmersiveAudioPreset.decodeAll(savedPresetsRaw) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeCapture by remember { mutableStateOf<ImmersiveActiveCapture?>(null) }
    var latestCapture by remember { mutableStateOf<ImmersiveDiagnosticCapture?>(null) }
    var captureJob by remember { mutableStateOf<Job?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val report = latestCapture
        if (uri != null && report != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(
                        report.toText(
                            device = defaultDeviceDescription(),
                            androidVersion = defaultAndroidDescription(),
                            audioRoute = "AudioManager output",
                            hostBufferFrames = context.getSystemService(AudioManager::class.java)?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0,
                        ),
                    )
                }
            }
        }
    }

    fun startCapture(durationSeconds: Int) {
        captureJob?.cancel()
        val processorOn = ImmersiveAudioRuntime.isEnabled()
        val startedAt = System.currentTimeMillis()
        ImmersiveAudioRuntime.resetDiagnostics()
        latestCapture = null
        activeCapture = ImmersiveActiveCapture(processorOn, durationSeconds, startedAt)
        captureJob = coroutineScope.launch {
            val deadline = startedAt + durationSeconds * 1_000L
            while (System.currentTimeMillis() < deadline) {
                delay(200)
                val current = ImmersiveAudioRuntime.readDiagnostics()
                if (ImmersiveAudioRuntime.isEnabled() != processorOn) {
                    activeCapture = null
                    return@launch
                }
                val elapsed = ((System.currentTimeMillis() - startedAt) / 1000f).coerceAtMost(durationSeconds.toFloat())
                val sample = ImmersiveDiagnosticSample(
                    elapsedSeconds = elapsed,
                    inputPeakL = current.inputPeakL,
                    inputPeakR = current.inputPeakR,
                    outputPeakL = current.outputPeakL,
                    outputPeakR = current.outputPeakR,
                    inputRmsL = current.inputRmsL,
                    inputRmsR = current.inputRmsR,
                    outputRmsL = current.outputRmsL,
                    outputRmsR = current.outputRmsR,
                )
                val capture = activeCapture ?: return@launch
                activeCapture = capture.copy(elapsedSeconds = elapsed, samples = capture.samples + sample)
            }
            val finalDiagnostics = ImmersiveAudioRuntime.readDiagnostics()
            val completed = activeCapture
            if (completed != null) {
                latestCapture = ImmersiveDiagnosticCapture(
                    processorOn = completed.processorOn,
                    durationSeconds = completed.durationSeconds,
                    startedAtMillis = completed.startedAtMillis,
                    samples = completed.samples,
                    finalDiagnostics = finalDiagnostics,
                )
            }
            activeCapture = null
        }
    }

    LaunchedEffect(persistedIntensity) {
        if (!isDragging) {
            draftIntensity = persistedIntensity.coerceIn(0f, 1f)
            ImmersiveAudioRuntime.setIntensity(draftIntensity)
        }
    }

    LaunchedEffect(persistedRoomPreset, persistedRoomMix, persistedReflectionAmount, persistedReverbTime, persistedRoomSize, persistedDampening, persistedStereoWidth, persistedQuantum) {
        ImmersiveAudioRuntime.setRoomPreset(ImmersiveRoomPreset.fromNative(persistedRoomPreset))
        draftRoomMix = persistedRoomMix.coerceIn(0f, 1f)
        draftReflectionAmount = persistedReflectionAmount.coerceIn(0f, 1f)
        draftReverbTime = persistedReverbTime.coerceIn(0.2f, 8f)
        ImmersiveAudioRuntime.setRoomMix(draftRoomMix)
        ImmersiveAudioRuntime.setReflectionAmount(draftReflectionAmount)
        ImmersiveAudioRuntime.setReverbTimeSeconds(draftReverbTime)
        draftRoomSize = persistedRoomSize.coerceIn(0f, 1f)
        draftDampening = persistedDampening.coerceIn(0f, 1f)
        draftStereoWidth = persistedStereoWidth.coerceIn(0f, 1f)
        ImmersiveAudioRuntime.setRoomSize(draftRoomSize)
        ImmersiveAudioRuntime.setDampening(draftDampening)
        ImmersiveAudioRuntime.setStereoWidth(draftStereoWidth)
        draftQuantum = persistedQuantum.coerceAtLeast(1)
        ImmersiveAudioRuntime.setQuantumFrames(draftQuantum)
    }

    LaunchedEffect(enabled) {
        ImmersiveAudioRuntime.setEnabled(enabled)
    }

    LaunchedEffect(limiterEnabled) {
        ImmersiveAudioRuntime.setLimiterEnabled(limiterEnabled)
    }

    LaunchedEffect(bassEnabled, bassStrength, trebleGainMb, outputGainEnabled, outputGainMb) {
        // The immersive engine uses dB controls. Keep the existing preference units
        // (millibel for treble/output and 0..1000 strength for bass) at the UI boundary.
        ImmersiveAudioRuntime.setBassGainDb(
            if (bassEnabled) (bassStrength / 1000f * 12f) else 0f,
        )
        ImmersiveAudioRuntime.setTrebleGainDb(trebleGainMb / 1500f * 12f)
        ImmersiveAudioRuntime.setOutputGainDb(
            if (outputGainEnabled) (outputGainMb / 1500f * 12f).coerceIn(-24f, 12f) else 0f,
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            diagnostics = ImmersiveAudioRuntime.readDiagnostics()
            delay(500)
        }
    }

    Scaffold(
        containerColor = FrostSoulTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Immersive audio",
                            color = FrostSoulTheme.colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Steam Audio HRTF",
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Back",
                            tint = FrostSoulTheme.colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FrostSoulTheme.colors.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            ImmersivePageTabs(
                selectedPage = selectedPage,
                onPageSelected = { selectedPage = it },
            )
            Spacer(Modifier.height(28.dp))

            when (selectedPage) {
                ImmersiveSettingsPage.Default -> DefaultImmersivePage(
                    enabled = enabled,
                    intensity = draftIntensity,
                    onEnabledChange = { enabledPreference.value = it },
                    onIntensityChange = { value ->
                        isDragging = true
                        draftIntensity = value.coerceIn(0f, 1f)
                        ImmersiveAudioRuntime.setIntensity(draftIntensity)
                    },
                    onIntensityFinished = {
                        isDragging = false
                        intensityPreference.value = draftIntensity
                    },
                )
                ImmersiveSettingsPage.Advanced -> AdvancedImmersivePage(
                    enabled = enabled,
                    intensity = draftIntensity,
                    onEnabledChange = { enabledPreference.value = it },
                    onIntensityChange = { value ->
                        isDragging = true
                        draftIntensity = value.coerceIn(0f, 1f)
                        ImmersiveAudioRuntime.setIntensity(draftIntensity)
                    },
                    onIntensityFinished = {
                        isDragging = false
                        intensityPreference.value = draftIntensity
                    },
                    roomPreset = ImmersiveRoomPreset.fromNative(persistedRoomPreset),
                    roomMix = draftRoomMix,
                    reflectionAmount = draftReflectionAmount,
                    reverbTimeSeconds = draftReverbTime,
                    roomSize = draftRoomSize,
                    dampening = draftDampening,
                    stereoWidth = draftStereoWidth,
                    quantumFrames = draftQuantum,
                    limiterEnabled = limiterEnabled,
                    bassEnabled = bassEnabled,
                    bassStrength = bassStrength,
                    trebleGainMb = trebleGainMb,
                    outputGainEnabled = outputGainEnabled,
                    outputGainMb = outputGainMb,
                    savedPresets = savedPresets,
                    onRoomPresetChange = { roomPresetPreference.value = it.nativeValue },
                    onRoomMixChange = { draftRoomMix = it; roomMixPreference.value = it; ImmersiveAudioRuntime.setRoomMix(it) },
                    onReflectionChange = { draftReflectionAmount = it; reflectionPreference.value = it; ImmersiveAudioRuntime.setReflectionAmount(it) },
                    onReverbTimeChange = { draftReverbTime = it; reverbTimePreference.value = it; ImmersiveAudioRuntime.setReverbTimeSeconds(it) },
                    onRoomSizeChange = { draftRoomSize = it; roomSizePreference.value = it; ImmersiveAudioRuntime.setRoomSize(it) },
                    onDampeningChange = { draftDampening = it; dampeningPreference.value = it; ImmersiveAudioRuntime.setDampening(it) },
                    onStereoWidthChange = { draftStereoWidth = it; stereoWidthPreference.value = it; ImmersiveAudioRuntime.setStereoWidth(it) },
                    onQuantumChange = { value ->
                        draftQuantum = value.coerceAtLeast(1)
                        quantumPreference.value = draftQuantum
                        ImmersiveAudioRuntime.setQuantumFrames(draftQuantum)
                    },
                    onLimiterChange = { limiterPreference.value = it },
                    onBassEnabledChange = { bassEnabledPreference.value = it; eqEnabledPreference.value = true },
                    onBassStrengthChange = { bassStrengthPreference.value = it.coerceIn(0, 1000); eqEnabledPreference.value = true },
                    onTrebleGainChange = { trebleGainPreference.value = it.coerceIn(-1500, 1500); eqEnabledPreference.value = true },
                    onOutputGainEnabledChange = { outputGainEnabledPreference.value = it; eqEnabledPreference.value = true },
                    onOutputGainChange = { outputGainPreference.value = it.coerceIn(-1500, 1500); eqEnabledPreference.value = true },
                    onPresetSelected = { preset ->
                        enabledPreference.value = preset.enabled
                        intensityPreference.value = preset.intensity
                        roomPresetPreference.value = preset.roomPreset.nativeValue
                        roomMixPreference.value = preset.roomMix
                        reflectionPreference.value = preset.reflectionAmount
                        reverbTimePreference.value = preset.reverbTimeSeconds
                        roomSizePreference.value = preset.roomSize
                        dampeningPreference.value = preset.dampening
                        stereoWidthPreference.value = preset.stereoWidth
                        quantumPreference.value = preset.quantumFrames
                    },
                    onSavePreset = { showSavePreset = true },
                    onResetRoom = {
                        roomPresetPreference.value = ImmersiveRoomPreset.STUDIO.nativeValue
                        roomMixPreference.value = 0.18f
                        reflectionPreference.value = 0.28f
                        reverbTimePreference.value = 1.35f
                        roomSizePreference.value = 0.5f
                        dampeningPreference.value = 0.5f
                        stereoWidthPreference.value = 0.5f
                        quantumPreference.value = ImmersiveAudioProcessor.DEFAULT_QUANTUM_FRAMES
                    },
                    diagnostics = diagnostics,
                )
                ImmersiveSettingsPage.Diagnostic -> DiagnosticImmersivePage(
                    diagnostics = diagnostics,
                    activeCapture = activeCapture,
                    latestCapture = latestCapture,
                    onCapture = ::startCapture,
                    onExport = { latestCapture?.let { exportLauncher.launch(it.fileName()) } },
                    onReset = {
                        captureJob?.cancel()
                        activeCapture = null
                        latestCapture = null
                        ImmersiveAudioRuntime.resetDiagnostics()
                        diagnostics = ImmersiveAudioRuntime.readDiagnostics()
                    },
                )
            }
            Spacer(Modifier.height(dev.vxs.frostsoulx.constants.MiniPlayerHeight + 40.dp))
        }
    }

    if (!developmentWarningShown) {
        AlertDialog(
            onDismissRequest = { developmentWarningPreference.value = true },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.error),
                    contentDescription = "Warning",
                    tint = FrostSoulTheme.colors.onSurface,
                    modifier = Modifier.size(30.dp),
                )
            },
            title = { Text("Immersive audio is in development") },
            text = {
                Text(
                    "This feature may produce distorted or clipped sound on some devices. " +
                        "Turn it off if playback becomes unpleasant or unstable.",
                )
            },
            confirmButton = {
                Button(onClick = { developmentWarningPreference.value = true }) {
                    Text("Continue")
                }
            },
        )
    }
    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("Save engine preset") },
            text = {
                TextField(
                    value = presetName,
                    onValueChange = { presetName = it.take(64) },
                    singleLine = true,
                    label = { Text("Preset name") },
                )
            },
            dismissButton = {
                Button(onClick = { showSavePreset = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    enabled = presetName.trim().isNotEmpty(),
                    onClick = {
                        val snapshot = ImmersiveAudioPreset(
                            name = presetName.trim(),
                            enabled = enabled,
                            intensity = draftIntensity,
                            roomPreset = ImmersiveRoomPreset.fromNative(persistedRoomPreset),
                            roomMix = draftRoomMix,
                            reflectionAmount = draftReflectionAmount,
                            reverbTimeSeconds = draftReverbTime,
                            roomSize = draftRoomSize,
                            dampening = draftDampening,
                            stereoWidth = draftStereoWidth,
                            quantumFrames = draftQuantum,
                        )
                        val updated = savedPresets.filterNot { it.name.equals(snapshot.name, ignoreCase = true) } + snapshot
                        savedPresetsPreference.value = ImmersiveAudioPreset.encodeAll(updated)
                        presetName = ""
                        showSavePreset = false
                    },
                ) { Text("Save") }
            },
        )
    }
}

@Composable
private fun ImmersivePageTabs(
    selectedPage: ImmersiveSettingsPage,
    onPageSelected: (ImmersiveSettingsPage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FrostSoulTheme.colors.surface)
            .selectableGroup()
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ImmersivePageTab(
            label = "Default",
            modifier = Modifier.weight(1f),
            selected = selectedPage == ImmersiveSettingsPage.Default,
            onClick = { onPageSelected(ImmersiveSettingsPage.Default) },
        )
        ImmersivePageTab(
            label = "Advanced",
            modifier = Modifier.weight(1f),
            selected = selectedPage == ImmersiveSettingsPage.Advanced,
            onClick = { onPageSelected(ImmersiveSettingsPage.Advanced) },
        )
        ImmersivePageTab(
            label = "Diagnostic",
            modifier = Modifier.weight(1f),
            selected = selectedPage == ImmersiveSettingsPage.Diagnostic,
            onClick = { onPageSelected(ImmersiveSettingsPage.Diagnostic) },
        )
    }
}

@Composable
private fun ImmersivePageTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) FrostSoulTheme.colors.surfaceRaised else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) FrostSoulTheme.colors.onSurface else FrostSoulTheme.colors.onSurfaceMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun DefaultImmersivePage(
    enabled: Boolean,
    intensity: Float,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onIntensityFinished: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(FrostSoulTheme.colors.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        ImmersiveSectionLabel("SURROUND")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.equalizer),
                contentDescription = null,
                tint = if (enabled) FrostSoulTheme.colors.onSurface else FrostSoulTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(25.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Surround", color = FrostSoulTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (enabled) "Steam Audio immersive processing" else "Steam Audio is bypassed",
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        HorizontalDivider(color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.18f))
        ImmersiveSectionLabel("SPATIAL BLEND")
        SpatialBlendControl(
            enabled = enabled,
            intensity = intensity,
            onValueChange = onIntensityChange,
            onValueChangeFinished = onIntensityFinished,
        )
        HorizontalDivider(color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.18f))
        StatusLine(
            title = "HRTF binaural processing",
            detail = if (enabled) "Default Steam Audio HRTF is active for stereo PCM." else "Audio follows the original Media3 path.",
        )
    }
}

@Composable
private fun DiagnosticImmersivePage(
    diagnostics: ImmersiveAudioDiagnostics,
    activeCapture: ImmersiveActiveCapture?,
    latestCapture: ImmersiveDiagnosticCapture?,
    onCapture: (Int) -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit,
) {
    val stateOn = diagnostics.processorEnabled
    val truePeakWarningSource = diagnostics.truePeakWarningSource()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (stateOn) FrostSoulTheme.colors.accent.copy(alpha = 0.16f) else FrostSoulTheme.colors.surface)
                .border(1.dp, if (stateOn) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.outline, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PROCESSOR ${if (stateOn) "ON" else "OFF"}", color = FrostSoulTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Input → ${if (stateOn) "Native DSP" else "Bypass"} → Output", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp)
            }
            Text(if (diagnostics.processCallCount > 0) "Receiving PCM" else "Waiting for audio", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
        }

        StageTelemetryCard(diagnostics)

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(FrostSoulTheme.colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("SIGNAL MONITOR", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("", modifier = Modifier.weight(1.2f))
                Text("INPUT", modifier = Modifier.weight(1f), color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("OUTPUT", modifier = Modifier.weight(1f), color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            DiagnosticRow("L RMS", formatDb(diagnostics.inputRmsL), formatDb(diagnostics.outputRmsL))
            DiagnosticRow("R RMS", formatDb(diagnostics.inputRmsR), formatDb(diagnostics.outputRmsR))
            DiagnosticRow("L Peak", formatDb(diagnostics.inputPeakL), formatDb(diagnostics.outputPeakL))
            DiagnosticRow("R Peak", formatDb(diagnostics.inputPeakR), formatDb(diagnostics.outputPeakR))
            DiagnosticRow("L True peak", formatDb(diagnostics.inputTruePeakL) + "TP", formatDb(diagnostics.outputTruePeakL) + "TP")
            DiagnosticRow("R True peak", formatDb(diagnostics.inputTruePeakR) + "TP", formatDb(diagnostics.outputTruePeakR) + "TP")
            DiagnosticRow("NaN", diagnostics.nanCount.toString(), diagnostics.nanCount.toString())
            DiagnosticRow("Inf", diagnostics.infCount.toString(), diagnostics.infCount.toString())
            DiagnosticRow("Clipped", diagnostics.clippedInput.toString(), diagnostics.clippedOutput.toString())
            HorizontalDivider(color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.14f))
            StatusLine("Difference", "max ${formatRaw(diagnostics.maxAbsDifference)} · average ${formatRaw(diagnostics.averageAbsDifference)} · changed ${String.format(Locale.US, "%.2f", diagnostics.changedPercentage)}%")
            StatusLine("Clipping source", diagnostics.clippingSource())
            if (truePeakWarningSource != "NONE") {
                Text("True peak above -0.1 dBTP · $truePeakWarningSource · ${if (stateOn) "ON" else "OFF"} capture", color = Color(0xFFFFB4AB), fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(FrostSoulTheme.colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("CAPTURE", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            if (activeCapture != null) {
                Text("CAPTURING ${String.format(Locale.US, "%.1f", activeCapture.elapsedSeconds)} / ${activeCapture.durationSeconds}.0s", color = FrostSoulTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("Playback continues; compact samples are collected every 200 ms.", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCapture(10) }) { Text("Capture 10s") }
                    Button(onClick = { onCapture(20) }) { Text("Capture 20s") }
                }
            }
            if (latestCapture != null) {
                Text("Last capture: ${if (latestCapture.processorOn) "PROCESSOR ON" else "PROCESSOR OFF"} · ${latestCapture.samples.size} time-series samples", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp)
                Button(onClick = onExport) { Text("Export TXT") }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(FrostSoulTheme.colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ENGINE TELEMETRY", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            StatusLine("Format", "${diagnostics.sampleRate} Hz · stereo · ${if (diagnostics.pcmEncoding == 4) "PCM float" else if (diagnostics.pcmEncoding == 2) "PCM 16-bit" else "unknown PCM"}")
            StatusLine("Quantum / callback", "${diagnostics.quantumFrames} / ${diagnostics.hostCallbackFrames} frames")
            StatusLine("Processing", "${diagnostics.totalBlocks} blocks · ${diagnostics.processedFrames} frames · ${String.format(Locale.US, "%.2f", diagnostics.averageProcessingTimeMs)} ms average")
            StatusLine("Realtime safety", "${diagnostics.deadlineMisses} deadline misses · ${diagnostics.nativeProcessFailures} native failures")
            Button(onClick = onReset) { Text("Reset diagnostics") }
        }
    }
}

@Composable
private fun StageTelemetryCard(diagnostics: ImmersiveAudioDiagnostics) {
    val on = diagnostics.processorEnabled
    val b1 = diagnostics.b1AfterSilenceSkipping
    val b2 = diagnostics.b2AfterSonic
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(FrostSoulTheme.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SIGNAL PATH BOUNDARIES", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Text("Real PCM measurements · ${if (on) "processor ON" else "processor OFF / bypass"}", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp)
        StageRow("B1 · after silence skip", b1.available, b1)
        StageRow("B2 · after Sonic", b2.available, b2)
        StageRow("B3 · native DSP input", diagnostics.processCallCount > 0, stageFromNative(diagnostics, input = true))
        StageRow("B4 · native DSP output", diagnostics.processCallCount > 0, stageFromNative(diagnostics, input = false))
        StageRow("B5 · AudioTrack/device", false, null)
        Text(
            if (on) "DSP-only controls and processing metrics are active." else "DSP parameters, DSP processing time and input→output difference: N/A while OFF.",
            color = FrostSoulTheme.colors.onSurfaceMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun StageRow(label: String, available: Boolean, stage: dev.vxs.frostsoulx.playback.ImmersiveStageDiagnostics?) {
    val value = if (!available || stage == null) "N/A" else "RMS ${formatDb(stage.rms)} · peak ${formatDb(stage.peak)} · TP ${formatDb(stage.truePeak)} · clip ${stage.clippedSamples}"
    StatusLine(label, value)
}

private fun stageFromNative(diagnostics: ImmersiveAudioDiagnostics, input: Boolean): dev.vxs.frostsoulx.playback.ImmersiveStageDiagnostics =
    if (input) dev.vxs.frostsoulx.playback.ImmersiveStageDiagnostics(
        available = diagnostics.processedFrames > 0,
        rms = diagnostics.inputRms,
        peak = diagnostics.inputPeak,
        truePeak = maxOf(diagnostics.inputTruePeakL, diagnostics.inputTruePeakR),
        clippedSamples = diagnostics.clippedInput,
        nanCount = diagnostics.nanCount,
        infCount = diagnostics.infCount,
        frames = diagnostics.processedFrames,
        sampleRate = diagnostics.sampleRate,
        encoding = diagnostics.pcmEncoding,
    ) else dev.vxs.frostsoulx.playback.ImmersiveStageDiagnostics(
        available = diagnostics.processedFrames > 0,
        rms = diagnostics.outputRms,
        peak = diagnostics.outputPeak,
        truePeak = maxOf(diagnostics.outputTruePeakL, diagnostics.outputTruePeakR),
        clippedSamples = diagnostics.clippedOutput,
        nanCount = diagnostics.nanCount,
        infCount = diagnostics.infCount,
        frames = diagnostics.processedFrames,
        sampleRate = diagnostics.sampleRate,
        encoding = diagnostics.pcmEncoding,
    )

@Composable
private fun DiagnosticRow(label: String, input: String, output: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1.2f), color = FrostSoulTheme.colors.onSurface, fontSize = 12.sp)
        Text(input, modifier = Modifier.weight(1f), color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(output, modifier = Modifier.weight(1f), color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun formatDb(value: Float): String = if (!value.isFinite() || value <= 1.0e-9f) "-inf dB" else String.format(Locale.US, "%.2f dB", 20.0 * kotlin.math.log10(value.toDouble()))
private fun formatRaw(value: Float): String = String.format(Locale.US, "%.6f", value)

@Composable
private fun AdvancedImmersivePage(
    enabled: Boolean,
    intensity: Float,
    roomPreset: ImmersiveRoomPreset,
    roomMix: Float,
    reflectionAmount: Float,
    reverbTimeSeconds: Float,
    roomSize: Float,
    dampening: Float,
    stereoWidth: Float,
    quantumFrames: Int,
    limiterEnabled: Boolean,
    bassEnabled: Boolean,
    bassStrength: Int,
    trebleGainMb: Int,
    outputGainEnabled: Boolean,
    outputGainMb: Int,
    savedPresets: List<ImmersiveAudioPreset>,
    diagnostics: ImmersiveAudioDiagnostics,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onIntensityFinished: () -> Unit,
    onRoomPresetChange: (ImmersiveRoomPreset) -> Unit,
    onRoomMixChange: (Float) -> Unit,
    onReflectionChange: (Float) -> Unit,
    onReverbTimeChange: (Float) -> Unit,
    onRoomSizeChange: (Float) -> Unit,
    onDampeningChange: (Float) -> Unit,
    onStereoWidthChange: (Float) -> Unit,
    onQuantumChange: (Int) -> Unit,
    onLimiterChange: (Boolean) -> Unit,
    onBassEnabledChange: (Boolean) -> Unit,
    onBassStrengthChange: (Int) -> Unit,
    onTrebleGainChange: (Int) -> Unit,
    onOutputGainEnabledChange: (Boolean) -> Unit,
    onOutputGainChange: (Int) -> Unit,
    onPresetSelected: (ImmersiveAudioPreset) -> Unit,
    onSavePreset: () -> Unit,
    onResetRoom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ImmersiveSectionLabel("HRTF")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("HRTF binaural processing", color = FrostSoulTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("Default Steam Audio profile", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 13.sp)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        SpatialBlendControl(
            enabled = enabled,
            intensity = intensity,
            onValueChange = onIntensityChange,
            onValueChangeFinished = onIntensityFinished,
            technical = true,
        )
        StatusLine("Interpolation", "Bilinear is fixed by the current native engine.")
        StatusLine("Source direction", "Forward-facing source at (0, 0, 1). Runtime direction control is not connected.")
        HorizontalDivider(color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.18f))
        ImmersiveSectionLabel("ROOM MODEL")
        Text("Preset", color = FrostSoulTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        ImmersiveRoomPresetSelector(roomPreset, onRoomPresetChange)
        RoomParameterSlider("Room mix", roomMix, 0f..1f, "${(roomMix * 100).roundToInt()}%", onRoomMixChange)
        RoomParameterSlider("Reflections", reflectionAmount, 0f..1f, "${(reflectionAmount * 100).roundToInt()}%", onReflectionChange)
        RoomParameterSlider("Reverb time", reverbTimeSeconds, 0.2f..8f, String.format(Locale.US, "%.1fs", reverbTimeSeconds), onReverbTimeChange)
        HorizontalDivider(color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.18f))
        ImmersiveSectionLabel("SPACE DESIGN")
        RoomParameterSlider("Room size", roomSize, 0f..1f, "${(roomSize * 100).roundToInt()}%", onRoomSizeChange)
        RoomParameterSlider("Dampening", dampening, 0f..1f, "${(dampening * 100).roundToInt()}%", onDampeningChange)
        RoomParameterSlider("Stereo width", stereoWidth, 0f..1f, "${(stereoWidth * 100).roundToInt()}%", onStereoWidthChange)
        QuantumSlider(quantumFrames, onQuantumChange)
        ToneOutputControls(
            limiterEnabled = limiterEnabled,
            bassEnabled = bassEnabled,
            bassStrength = bassStrength,
            trebleGainMb = trebleGainMb,
            outputGainEnabled = outputGainEnabled,
            outputGainMb = outputGainMb,
            onLimiterChange = onLimiterChange,
            onBassEnabledChange = onBassEnabledChange,
            onBassStrengthChange = onBassStrengthChange,
            onTrebleGainChange = onTrebleGainChange,
            onOutputGainEnabledChange = onOutputGainEnabledChange,
            onOutputGainChange = onOutputGainChange,
        )
        SavedPresetSection(savedPresets, onPresetSelected, onSavePreset)
        StatusLine("Space design", "Room size scales delay/reverb; dampening shapes decay; width controls decorrelation.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onResetRoom) { Text("Reset room") }
        }
        StatusLine("Active room", roomPreset.label)
        HorizontalDivider(color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.18f))
        ImmersiveSectionLabel("SAFETY")
        StatusLine("OFF behavior", "Native processing is bypassed and the Media3 PCM buffer remains unchanged.")
        StatusLine("Supported input", "Stereo PCM 16-bit and PCM float.")
        HorizontalDivider(color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.18f))
        ImmersiveDiagnosticsSection(diagnostics)
    }
}

@Composable
private fun ToneOutputControls(
    limiterEnabled: Boolean,
    bassEnabled: Boolean,
    bassStrength: Int,
    trebleGainMb: Int,
    outputGainEnabled: Boolean,
    outputGainMb: Int,
    onLimiterChange: (Boolean) -> Unit,
    onBassEnabledChange: (Boolean) -> Unit,
    onBassStrengthChange: (Int) -> Unit,
    onTrebleGainChange: (Int) -> Unit,
    onOutputGainEnabledChange: (Boolean) -> Unit,
    onOutputGainChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(FrostSoulTheme.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ImmersiveSectionLabel("TONE & OUTPUT")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Limiter", color = FrostSoulTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Native peak protection", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
            }
            Switch(checked = limiterEnabled, onCheckedChange = onLimiterChange)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bass", color = FrostSoulTheme.colors.onSurface, fontSize = 14.sp)
                Text("Android BassBoost effect", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
            }
            Switch(checked = bassEnabled, onCheckedChange = onBassEnabledChange)
        }
        TechnicalSlider(
            value = (bassStrength / 1000f).coerceIn(0f, 1f),
            onValueChange = { onBassStrengthChange((it * 1000f).roundToInt()) },
            valueRange = 0f..1f,
            enabled = bassEnabled,
            label = "Bass strength",
        )
        Text("${bassStrength / 10}%", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
        RoomParameterSlider("Treble", trebleGainMb / 1500f, -1f..1f, "${trebleGainMb} mB", { onTrebleGainChange((it * 1500f).roundToInt()) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Output gain", color = FrostSoulTheme.colors.onSurface, fontSize = 14.sp)
                Text("Android output gain effect", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
            }
            Switch(checked = outputGainEnabled, onCheckedChange = onOutputGainEnabledChange)
        }
        RoomParameterSlider("Gain", outputGainMb / 1500f, -1f..1f, "${outputGainMb} mB", { onOutputGainChange((it * 1500f).roundToInt()) })
    }
}

@Composable
private fun RoomParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(FrostSoulTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = FrostSoulTheme.colors.onSurface, fontSize = 13.sp)
            Text(
                valueLabel,
                color = FrostSoulTheme.colors.accent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.background(FrostSoulTheme.colors.accent.copy(alpha = 0.09f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        TechnicalSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChange(it.coerceIn(range.start, range.endInclusive)) },
            valueRange = range,
            label = label,
        )
    }
}

@Composable
private fun QuantumSlider(
    quantumFrames: Int,
    onQuantumChange: (Int) -> Unit,
) {
    var inputText by remember(quantumFrames) { mutableStateOf(quantumFrames.toString()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(FrostSoulTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Processing quantum", color = FrostSoulTheme.colors.onSurface, fontSize = 13.sp)
                Text("Native processing block size · enter 1–1,000,000 frames", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
            }
            TextField(
                value = inputText,
                onValueChange = { value ->
                    val digitsOnly = value.filter(Char::isDigit).take(10)
                    inputText = digitsOnly
                },
                modifier = Modifier.width(118.dp).onFocusChanged { state ->
                    if (!state.isFocused) {
                        val safe = inputText.toIntOrNull()?.coerceIn(
                            ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES,
                            ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES,
                        ) ?: quantumFrames
                        inputText = safe.toString()
                        onQuantumChange(safe)
                    }
                },
                singleLine = true,
                keyboardActions = KeyboardActions(onDone = {
                    val safe = inputText.toLongOrNull()?.coerceIn(
                        ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES.toLong(),
                        ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES.toLong(),
                    )?.toInt() ?: quantumFrames
                    inputText = safe.toString()
                    onQuantumChange(safe)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
                label = { Text("frames") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                isError = inputText.isNotEmpty() && (inputText.toIntOrNull()?.let {
                    it !in ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES..ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES
                } ?: true),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = FrostSoulTheme.colors.surfaceRaised,
                    unfocusedContainerColor = FrostSoulTheme.colors.surfaceRaised,
                    focusedTextColor = FrostSoulTheme.colors.onSurface,
                    unfocusedTextColor = FrostSoulTheme.colors.onSurface,
                    cursorColor = FrostSoulTheme.colors.accent,
                    focusedIndicatorColor = FrostSoulTheme.colors.accent,
                    unfocusedIndicatorColor = FrostSoulTheme.colors.outline,
                ),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 · lower latency", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
            Text("1,000,000 · larger blocks", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SavedPresetSection(
    presets: List<ImmersiveAudioPreset>,
    onSelected: (ImmersiveAudioPreset) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(FrostSoulTheme.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Saved engine presets", color = FrostSoulTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Restore every mapped control and quantum", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
            }
            Button(onClick = onSave) { Text("Save current") }
        }
        if (presets.isEmpty()) {
            Text("No saved presets yet", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FrostSoulTheme.colors.accent.copy(alpha = 0.12f))
                            .border(1.dp, FrostSoulTheme.colors.outline, RoundedCornerShape(12.dp))
                            .clickable { onSelected(preset) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(preset.name, color = FrostSoulTheme.colors.onSurface, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveRoomPresetSelector(
    selected: ImmersiveRoomPreset,
    onSelected: (ImmersiveRoomPreset) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ImmersiveRoomPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) FrostSoulTheme.colors.accent.copy(alpha = 0.12f) else FrostSoulTheme.colors.surface)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.outline.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelected(preset) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    preset.label,
                    color = if (isSelected) FrostSoulTheme.colors.onSurface else FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .background(if (isSelected) FrostSoulTheme.colors.accent else Color.Transparent, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun TechnicalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = FrostSoulTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.accent,
        activeTrackColor = colors.accent,
        inactiveTrackColor = colors.onSurface.copy(alpha = 0.14f),
        disabledThumbColor = colors.onSurfaceMuted,
        disabledActiveTrackColor = colors.onSurfaceMuted.copy(alpha = 0.50f),
        disabledInactiveTrackColor = colors.onSurface.copy(alpha = 0.08f),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = sliderColors,
        thumb = {
            Box(
                Modifier.size(20.dp)
                    .background(if (enabled) colors.accent else colors.onSurfaceMuted, CircleShape)
                    .border(3.dp, colors.surfaceRaised, CircleShape),
            )
        },
        track = { state ->
            // Let Material measure both thumb and track: correct end points, RTL and semantics.
            SliderDefaults.Track(
                sliderState = state,
                enabled = enabled,
                colors = sliderColors,
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
                modifier = Modifier.height(6.dp),
            )
        },
        modifier = Modifier.fillMaxWidth().height(48.dp)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun ImmersiveDiagnosticsSection(diagnostics: ImmersiveAudioDiagnostics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ImmersiveSectionLabel("LIVE DIAGNOSTICS")
        StatusLine("Processor", if (diagnostics.processCallCount > 0) "Active" else "Waiting for audio")
        StatusLine("Input RMS / peak", "${formatAudioValue(diagnostics.inputRms)} / ${formatAudioValue(diagnostics.inputPeak)}")
        StatusLine("Output RMS / peak", "${formatAudioValue(diagnostics.outputRms)} / ${formatAudioValue(diagnostics.outputPeak)}")
        StatusLine("Changed samples", "${diagnostics.changedPercentage.toInt()}%  ·  max difference ${formatAudioValue(diagnostics.maxAbsDifference)}")
        StatusLine("Safety", "NaN ${diagnostics.nanCount}  ·  Inf ${diagnostics.infCount}  ·  calls ${diagnostics.processCallCount}")
    }
}

private fun formatAudioValue(value: Float): String = String.format(Locale.US, "%.4f", value)

@Composable
private fun SpatialBlendControl(
    enabled: Boolean,
    intensity: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    technical: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (technical) 3.dp else 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spatial blend", color = FrostSoulTheme.colors.onSurface, fontSize = if (technical) 13.sp else 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(intensity * 100f).roundToInt()}%",
                color = if (enabled) FrostSoulTheme.colors.onSurface else FrostSoulTheme.colors.onSurfaceMuted,
                fontSize = if (technical) 12.sp else 17.sp,
                fontWeight = if (technical) FontWeight.Normal else FontWeight.SemiBold,
                fontFamily = if (technical) FontFamily.Monospace else FontFamily.Default,
            )
        }
        TechnicalSlider(
            value = intensity,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..1f,
            enabled = enabled,
            label = "Spatial blend",
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Transparent", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
            Text("Wide", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ImmersiveSectionLabel(label: String) {
    Text(
        text = label,
        color = FrostSoulTheme.colors.onSurfaceMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
    )
}

@Composable
private fun StatusLine(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = FrostSoulTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(detail, color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
