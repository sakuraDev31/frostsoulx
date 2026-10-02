package dev.vxs.frostsoulx.ui.screens.settings

import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.MiniPlayerHeight
import dev.vxs.frostsoulx.constants.StereoSurroundBassGainDbKey
import dev.vxs.frostsoulx.constants.StereoSurroundCarFaderKey
import dev.vxs.frostsoulx.constants.StereoSurroundDampeningKey
import dev.vxs.frostsoulx.constants.StereoSurroundEnabledKey
import dev.vxs.frostsoulx.constants.StereoSurroundIntensityKey
import dev.vxs.frostsoulx.constants.StereoSurroundLimiterEnabledKey
import dev.vxs.frostsoulx.constants.StereoSurroundOutputGainDbKey
import dev.vxs.frostsoulx.constants.StereoSurroundQuantumFramesKey
import dev.vxs.frostsoulx.constants.StereoSurroundReflectionAmountKey
import dev.vxs.frostsoulx.constants.StereoSurroundReverbTimeKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomMixKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomPresetKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomSizeKey
import dev.vxs.frostsoulx.constants.StereoSurroundSavedPresetsKey
import dev.vxs.frostsoulx.constants.StereoSurroundStereoWidthKey
import dev.vxs.frostsoulx.constants.StereoSurroundTrebleGainDbKey
import dev.vxs.frostsoulx.playback.ImmersiveActiveCapture
import dev.vxs.frostsoulx.playback.ImmersiveAudioDiagnostics
import dev.vxs.frostsoulx.playback.ImmersiveStageDiagnostics
import dev.vxs.frostsoulx.playback.ImmersivePipelineStageTelemetry
import dev.vxs.frostsoulx.playback.ImmersiveAudioPreset
import dev.vxs.frostsoulx.playback.ImmersiveAudioProcessor
import dev.vxs.frostsoulx.playback.ImmersiveAudioRuntime
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticCapture
import dev.vxs.frostsoulx.playback.ImmersiveDiagnosticSample
import dev.vxs.frostsoulx.playback.ImmersiveRoomPreset
import dev.vxs.frostsoulx.playback.defaultAndroidDescription
import dev.vxs.frostsoulx.playback.defaultDeviceDescription
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.utils.rememberPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

private enum class ImmersiveCategory(val label: String) {
    Acoustics("Acoustics"),
    Soundstage("Soundstage"),
    StudioLab("Studio Lab"),
}

@Composable
fun StereoSurroundScreen(navController: NavController) {
    val enabledPreference = rememberPreference(StereoSurroundEnabledKey, defaultValue = false)
    val intensityPreference = rememberPreference(StereoSurroundIntensityKey, defaultValue = 0.5f)
    val roomPresetPreference = rememberPreference(StereoSurroundRoomPresetKey, defaultValue = ImmersiveRoomPreset.STUDIO.nativeValue)
    val roomMixPreference = rememberPreference(StereoSurroundRoomMixKey, defaultValue = 0.18f)
    val reflectionPreference = rememberPreference(StereoSurroundReflectionAmountKey, defaultValue = 0.28f)
    val reverbTimePreference = rememberPreference(StereoSurroundReverbTimeKey, defaultValue = 1.35f)
    val roomSizePreference = rememberPreference(StereoSurroundRoomSizeKey, defaultValue = 0.5f)
    val dampeningPreference = rememberPreference(StereoSurroundDampeningKey, defaultValue = 0.5f)
    val stereoWidthPreference = rememberPreference(StereoSurroundStereoWidthKey, defaultValue = 0.5f)
    val carFaderPreference = rememberPreference(StereoSurroundCarFaderKey, defaultValue = 0f)
    val quantumPreference = rememberPreference(StereoSurroundQuantumFramesKey, defaultValue = ImmersiveAudioProcessor.DEFAULT_QUANTUM_FRAMES)
    val limiterPreference = rememberPreference(StereoSurroundLimiterEnabledKey, defaultValue = true)
    val bassGainDbPreference = rememberPreference(StereoSurroundBassGainDbKey, defaultValue = 0f)
    val trebleGainDbPreference = rememberPreference(StereoSurroundTrebleGainDbKey, defaultValue = 0f)
    val outputGainDbPreference = rememberPreference(StereoSurroundOutputGainDbKey, defaultValue = 0f)
    val savedPresetsPreference = rememberPreference(StereoSurroundSavedPresetsKey, defaultValue = "")

    val enabled by enabledPreference
    val persistedIntensity by intensityPreference
    val persistedRoomPreset by roomPresetPreference
    val persistedRoomMix by roomMixPreference
    val persistedReflectionAmount by reflectionPreference
    val persistedReverbTime by reverbTimePreference
    val persistedRoomSize by roomSizePreference
    val persistedDampening by dampeningPreference
    val persistedStereoWidth by stereoWidthPreference
    val persistedCarFader by carFaderPreference
    val persistedQuantum by quantumPreference
    val limiterEnabled by limiterPreference
    val persistedBassGainDb by bassGainDbPreference
    val persistedTrebleGainDb by trebleGainDbPreference
    val persistedOutputGainDb by outputGainDbPreference
    val savedPresetsRaw by savedPresetsPreference

    var selectedCategory by remember { mutableStateOf(ImmersiveCategory.Acoustics) }
    var draftIntensity by remember { mutableFloatStateOf(persistedIntensity.coerceIn(0f, 1f)) }
    var draftRoomMix by remember { mutableFloatStateOf(persistedRoomMix.coerceIn(0f, 1f)) }
    var draftReflectionAmount by remember { mutableFloatStateOf(persistedReflectionAmount.coerceIn(0f, 1f)) }
    var draftReverbTime by remember { mutableFloatStateOf(persistedReverbTime.coerceIn(0.2f, 8f)) }
    var draftRoomSize by remember { mutableFloatStateOf(persistedRoomSize.coerceIn(0f, 1f)) }
    var draftDampening by remember { mutableFloatStateOf(persistedDampening.coerceIn(0f, 1f)) }
    var draftStereoWidth by remember { mutableFloatStateOf(persistedStereoWidth.coerceIn(0f, 1f)) }
    var draftSourceAzimuth by remember { mutableFloatStateOf(ImmersiveAudioRuntime.sourceAzimuth()) }
    var draftSourceElevation by remember { mutableFloatStateOf(ImmersiveAudioRuntime.sourceElevation()) }
    var draftSourceDistance by remember { mutableFloatStateOf(ImmersiveAudioRuntime.sourceDistance()) }
    var draftCarFader by remember { mutableFloatStateOf(persistedCarFader.coerceIn(-1f, 1f)) }
    var draftQuantum by remember { mutableIntStateOf(persistedQuantum.coerceIn(ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES, ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES)) }
    var draftBassGainDb by remember { mutableFloatStateOf(persistedBassGainDb.coerceIn(-12f, 12f)) }
    var draftTrebleGainDb by remember { mutableFloatStateOf(persistedTrebleGainDb.coerceIn(-12f, 12f)) }
    var draftOutputGainDb by remember { mutableFloatStateOf(persistedOutputGainDb.coerceIn(-24f, 12f)) }

    var isDragging by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(ImmersiveAudioDiagnostics()) }
    var showSavePreset by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
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
                            audioRoute = "AudioTrack Stereo Output",
                            hostBufferFrames = context.getSystemService(AudioManager::class.java)
                                ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0,
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

    LaunchedEffect(
        persistedRoomPreset,
        persistedRoomMix,
        persistedReflectionAmount,
        persistedReverbTime,
        persistedRoomSize,
        persistedDampening,
        persistedStereoWidth,
        persistedCarFader,
        persistedQuantum,
    ) {
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
        draftCarFader = persistedCarFader.coerceIn(-1f, 1f)
        ImmersiveAudioRuntime.setCarFader(draftCarFader)
        draftQuantum = persistedQuantum.coerceIn(ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES, ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES)
        ImmersiveAudioRuntime.setQuantumFrames(draftQuantum)
    }

    LaunchedEffect(enabled) {
        ImmersiveAudioRuntime.setEnabled(enabled)
    }

    LaunchedEffect(limiterEnabled) {
        ImmersiveAudioRuntime.setLimiterEnabled(limiterEnabled)
    }

    LaunchedEffect(persistedBassGainDb, persistedTrebleGainDb, persistedOutputGainDb) {
        draftBassGainDb = persistedBassGainDb.coerceIn(-12f, 12f)
        draftTrebleGainDb = persistedTrebleGainDb.coerceIn(-12f, 12f)
        draftOutputGainDb = persistedOutputGainDb.coerceIn(-24f, 12f)
        ImmersiveAudioRuntime.setBassGainDb(draftBassGainDb)
        ImmersiveAudioRuntime.setTrebleGainDb(draftTrebleGainDb)
        ImmersiveAudioRuntime.setOutputGainDb(draftOutputGainDb)
    }

    LaunchedEffect(Unit) {
        while (true) {
            diagnostics = ImmersiveAudioRuntime.readDiagnostics()
            delay(350)
        }
    }

    Scaffold(
        containerColor = FrostSoulTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Immersive Audio",
                            color = FrostSoulTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 1.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (enabled) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.5f)),
                            )
                            Text(
                                text = if (enabled) "Spatial Engine · Active" else "Direct Bypass · Bit-Perfect",
                                color = if (enabled) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
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
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.info),
                            contentDescription = "Architecture & Engine Info",
                            tint = FrostSoulTheme.colors.onSurfaceMuted,
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
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. Hero Acoustic Stage Visualizer
            AcousticStageHero(
                enabled = enabled,
                intensity = draftIntensity,
                roomPreset = ImmersiveRoomPreset.fromNative(persistedRoomPreset),
                stereoWidth = draftStereoWidth,
                carFader = draftCarFader,
                roomSize = draftRoomSize,
            )

            // 2. Master Immersion Engine Card
            MasterImmersionCard(
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

            // 3. Category Selector Navigation Tabs
            ImmersiveCategorySelector(
                selected = selectedCategory,
                onSelected = { selectedCategory = it },
            )

            // 4. Tab Content
            when (selectedCategory) {
                ImmersiveCategory.Acoustics -> {
                    AcousticsTabContent(
                        roomPreset = ImmersiveRoomPreset.fromNative(persistedRoomPreset),
                        roomMix = draftRoomMix,
                        reflectionAmount = draftReflectionAmount,
                        reverbTime = draftReverbTime,
                        roomSize = draftRoomSize,
                        dampening = draftDampening,
                        onRoomPresetChange = { preset ->
                            roomPresetPreference.value = preset.nativeValue
                            ImmersiveAudioRuntime.setRoomPreset(preset)
                            when (preset) {
                                ImmersiveRoomPreset.OFF -> {
                                    draftRoomMix = 0f
                                    roomMixPreference.value = 0f
                                }
                                ImmersiveRoomPreset.SMALL_ROOM -> {
                                    draftRoomMix = 0.16f
                                    draftReverbTime = 0.8f
                                    draftRoomSize = 0.35f
                                    roomMixPreference.value = 0.16f
                                    reverbTimePreference.value = 0.8f
                                    roomSizePreference.value = 0.35f
                                }
                                ImmersiveRoomPreset.EIGHT_D_ORBIT -> {
                                    draftIntensity = 1f
                                    intensityPreference.value = 1f
                                    ImmersiveAudioRuntime.setIntensity(1f)
                                    draftRoomMix = 0.18f
                                    draftReverbTime = 0.8f
                                    draftRoomSize = 0.5f
                                    roomMixPreference.value = 0.18f
                                    reverbTimePreference.value = 0.8f
                                    roomSizePreference.value = 0.5f
                                }
                                ImmersiveRoomPreset.STUDIO -> {
                                    draftRoomMix = 0.18f
                                    draftReverbTime = 1.35f
                                    draftRoomSize = 0.5f
                                    roomMixPreference.value = 0.18f
                                    reverbTimePreference.value = 1.35f
                                    roomSizePreference.value = 0.5f
                                }
                                ImmersiveRoomPreset.CONCERT_HALL -> {
                                    draftRoomMix = 0.32f
                                    draftReverbTime = 3.2f
                                    draftRoomSize = 0.85f
                                    roomMixPreference.value = 0.32f
                                    reverbTimePreference.value = 3.2f
                                    roomSizePreference.value = 0.85f
                                }
                                ImmersiveRoomPreset.CATHEDRAL -> {
                                    draftRoomMix = 0.40f
                                    draftReverbTime = 4.8f
                                    draftRoomSize = 0.95f
                                    roomMixPreference.value = 0.40f
                                    reverbTimePreference.value = 4.8f
                                    roomSizePreference.value = 0.95f
                                }
                                ImmersiveRoomPreset.SUBWAY -> {
                                    draftRoomMix = 0.30f
                                    draftReverbTime = 2.4f
                                    draftRoomSize = 0.70f
                                    roomMixPreference.value = 0.30f
                                    reverbTimePreference.value = 2.4f
                                    roomSizePreference.value = 0.70f
                                }
                                ImmersiveRoomPreset.CLOSED_CAR -> {
                                    draftRoomMix = 0.14f
                                    draftReverbTime = 0.6f
                                    draftRoomSize = 0.25f
                                    roomMixPreference.value = 0.14f
                                    reverbTimePreference.value = 0.6f
                                    roomSizePreference.value = 0.25f
                                }
                                ImmersiveRoomPreset.MEDIUM_HALL -> {
                                    draftRoomMix = 0.26f
                                    draftReverbTime = 2.1f
                                    draftRoomSize = 0.65f
                                    roomMixPreference.value = 0.26f
                                    reverbTimePreference.value = 2.1f
                                    roomSizePreference.value = 0.65f
                                }
                                ImmersiveRoomPreset.SUBWAY_PLATFORM -> {
                                    draftRoomMix = 0.34f
                                    draftReverbTime = 2.8f
                                    draftRoomSize = 0.80f
                                    roomMixPreference.value = 0.34f
                                    reverbTimePreference.value = 2.8f
                                    roomSizePreference.value = 0.80f
                                }
                                ImmersiveRoomPreset.LONG_TUNNEL -> {
                                    draftRoomMix = 0.38f
                                    draftReverbTime = 5.5f
                                    draftRoomSize = 0.95f
                                    roomMixPreference.value = 0.38f
                                    reverbTimePreference.value = 5.5f
                                    roomSizePreference.value = 0.95f
                                }
                                ImmersiveRoomPreset.OPEN_ROAD -> {
                                    draftRoomMix = 0.10f
                                    draftReverbTime = 0.2f
                                    draftRoomSize = 0.40f
                                    roomMixPreference.value = 0.10f
                                    reverbTimePreference.value = 0.2f
                                    roomSizePreference.value = 0.40f
                                }
                                ImmersiveRoomPreset.CAVE -> {
                                    draftRoomMix = 0.36f
                                    draftReverbTime = 3.8f
                                    draftRoomSize = 0.85f
                                    roomMixPreference.value = 0.36f
                                    reverbTimePreference.value = 3.8f
                                    roomSizePreference.value = 0.85f
                                }
                                ImmersiveRoomPreset.STADIUM -> {
                                    draftRoomMix = 0.42f
                                    draftReverbTime = 4.2f
                                    draftRoomSize = 1.0f
                                    roomMixPreference.value = 0.42f
                                    reverbTimePreference.value = 4.2f
                                    roomSizePreference.value = 1.0f
                                }
                            }
                        },
                        onRoomMixChange = { draftRoomMix = it; roomMixPreference.value = it; ImmersiveAudioRuntime.setRoomMix(it) },
                        onReflectionChange = { draftReflectionAmount = it; reflectionPreference.value = it; ImmersiveAudioRuntime.setReflectionAmount(it) },
                        onReverbTimeChange = { draftReverbTime = it; reverbTimePreference.value = it; ImmersiveAudioRuntime.setReverbTimeSeconds(it) },
                        onRoomSizeChange = { draftRoomSize = it; roomSizePreference.value = it; ImmersiveAudioRuntime.setRoomSize(it) },
                        onDampeningChange = { draftDampening = it; dampeningPreference.value = it; ImmersiveAudioRuntime.setDampening(it) },
                        onResetAcoustics = {
                            roomPresetPreference.value = ImmersiveRoomPreset.STUDIO.nativeValue
                            draftRoomMix = 0.18f; roomMixPreference.value = 0.18f
                            draftReflectionAmount = 0.28f; reflectionPreference.value = 0.28f
                            draftReverbTime = 1.35f; reverbTimePreference.value = 1.35f
                            draftRoomSize = 0.5f; roomSizePreference.value = 0.5f
                            draftDampening = 0.5f; dampeningPreference.value = 0.5f
                        },
                    )
                }
                ImmersiveCategory.Soundstage -> {
                    SoundstageTabContent(
                        sourceAzimuth = draftSourceAzimuth,
                        sourceElevation = draftSourceElevation,
                        sourceDistance = draftSourceDistance,
                        stereoWidth = draftStereoWidth,
                        carFader = draftCarFader,
                        quantumFrames = draftQuantum,
                        savedPresets = savedPresets,
                        onSourceAzimuthChange = { draftSourceAzimuth = it; ImmersiveAudioRuntime.setSourcePosition(draftSourceAzimuth, draftSourceElevation) },
                        onSourceElevationChange = { draftSourceElevation = it; ImmersiveAudioRuntime.setSourcePosition(draftSourceAzimuth, draftSourceElevation) },
                        onSourceDistanceChange = { draftSourceDistance = it; ImmersiveAudioRuntime.setSourceDistance(it) },
                        onStereoWidthChange = { draftStereoWidth = it; stereoWidthPreference.value = it; ImmersiveAudioRuntime.setStereoWidth(it) },
                        onCarFaderChange = { draftCarFader = it; carFaderPreference.value = it; ImmersiveAudioRuntime.setCarFader(it) },
                        onQuantumChange = {
                            val clamped = it.coerceIn(ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES, ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES)
                            draftQuantum = clamped
                            quantumPreference.value = clamped
                            ImmersiveAudioRuntime.setQuantumFrames(clamped)
                        },
                        onPresetSelected = { preset ->
                            enabledPreference.value = preset.enabled
                            intensityPreference.value = preset.intensity
                            draftIntensity = preset.intensity
                            roomPresetPreference.value = preset.roomPreset.nativeValue
                            draftRoomMix = preset.roomMix; roomMixPreference.value = preset.roomMix
                            draftReflectionAmount = preset.reflectionAmount; reflectionPreference.value = preset.reflectionAmount
                            draftReverbTime = preset.reverbTimeSeconds; reverbTimePreference.value = preset.reverbTimeSeconds
                            draftRoomSize = preset.roomSize; roomSizePreference.value = preset.roomSize
                            draftDampening = preset.dampening; dampeningPreference.value = preset.dampening
                            draftStereoWidth = preset.stereoWidth; stereoWidthPreference.value = preset.stereoWidth
                            draftCarFader = preset.carFader; carFaderPreference.value = preset.carFader
                            draftQuantum = preset.quantumFrames.coerceIn(ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES, ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES)
                            quantumPreference.value = draftQuantum
                            ImmersiveAudioRuntime.setIntensity(draftIntensity)
                            ImmersiveAudioRuntime.setRoomPreset(preset.roomPreset)
                            ImmersiveAudioRuntime.setRoomMix(draftRoomMix)
                            ImmersiveAudioRuntime.setReflectionAmount(draftReflectionAmount)
                            ImmersiveAudioRuntime.setReverbTimeSeconds(draftReverbTime)
                            ImmersiveAudioRuntime.setRoomSize(draftRoomSize)
                            ImmersiveAudioRuntime.setDampening(draftDampening)
                            ImmersiveAudioRuntime.setStereoWidth(draftStereoWidth)
                            ImmersiveAudioRuntime.setCarFader(draftCarFader)
                            ImmersiveAudioRuntime.setQuantumFrames(draftQuantum)
                        },
                        onSavePreset = { showSavePreset = true },
                        onDeletePreset = { preset ->
                            val updated = savedPresets.filterNot { it.name.equals(preset.name, ignoreCase = true) }
                            savedPresetsPreference.value = ImmersiveAudioPreset.encodeAll(updated)
                        },
                    )
                }
                ImmersiveCategory.StudioLab -> {
                    StudioLabTabContent(
                        diagnostics = diagnostics,
                        limiterEnabled = limiterEnabled,
                        bassGainDb = draftBassGainDb,
                        trebleGainDb = draftTrebleGainDb,
                        outputGainDb = draftOutputGainDb,
                        activeCapture = activeCapture,
                        latestCapture = latestCapture,
                        onLimiterChange = { limiterPreference.value = it; ImmersiveAudioRuntime.setLimiterEnabled(it) },
                        onBassGainChange = { draftBassGainDb = it; bassGainDbPreference.value = it; ImmersiveAudioRuntime.setBassGainDb(it) },
                        onTrebleGainChange = { draftTrebleGainDb = it; trebleGainDbPreference.value = it; ImmersiveAudioRuntime.setTrebleGainDb(it) },
                        onOutputGainChange = { draftOutputGainDb = it; outputGainDbPreference.value = it; ImmersiveAudioRuntime.setOutputGainDb(it) },
                        onCapture = ::startCapture,
                        onExport = { latestCapture?.let { exportLauncher.launch(it.fileName()) } },
                        onResetDiagnostics = {
                            captureJob?.cancel()
                            activeCapture = null
                            latestCapture = null
                            ImmersiveAudioRuntime.resetDiagnostics()
                            diagnostics = ImmersiveAudioRuntime.readDiagnostics()
                        },
                    )
                }
            }

            Spacer(Modifier.height(MiniPlayerHeight + 36.dp))
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = FrostSoulTheme.colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    "Acoustic Engine Architecture",
                    fontWeight = FontWeight.Bold,
                    color = FrostSoulTheme.colors.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "• Engine: Full-Partitioned Linear Convolution C++17 runtime.\n" +
                            "• Spatial Convolution: Pure physical binaural Room Impulse Response (BRIR) multi-tier partitioned convolution with zero audio-thread allocations.\n" +
                            "• Acoustic Spaces: Exact physical convolution with geometric image reflections and Sabine/Eyring statistical diffuse tails.\n" +
                            "• Direct Spatial: Anechoic direct HRTF binaural convolution without artificial room reflections.\n" +
                            "• Anti-Clipping Architecture: Energy-normalized impulse responses, zero-overshoot soft-knee saturation, and smooth sample-accurate dynamics.\n" +
                            "• Bit-Perfect Bypass: When toggled off, audio passes unaltered directly to AudioTrack with zero re-quantization.",
                        color = FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = FrostSoulTheme.colors.accent),
                ) {
                    Text("Got It", color = FrostSoulTheme.colors.background, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = FrostSoulTheme.colors.surfaceRaised,
        )
    }

    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = {
                Text(
                    "Save Custom Acoustic Space",
                    fontWeight = FontWeight.Bold,
                    color = FrostSoulTheme.colors.onSurface,
                )
            },
            text = {
                TextField(
                    value = presetName,
                    onValueChange = { presetName = it.take(32) },
                    singleLine = true,
                    label = { Text("Preset name (e.g. My Warm Hall)") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = FrostSoulTheme.colors.surface,
                        unfocusedContainerColor = FrostSoulTheme.colors.surface,
                        focusedIndicatorColor = FrostSoulTheme.colors.accent,
                        unfocusedIndicatorColor = FrostSoulTheme.colors.outline,
                    ),
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { showSavePreset = false }) {
                    Text("Cancel", color = FrostSoulTheme.colors.onSurfaceMuted)
                }
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
                            carFader = draftCarFader,
                            quantumFrames = draftQuantum,
                        )
                        val updated = savedPresets.filterNot { it.name.equals(snapshot.name, ignoreCase = true) } + snapshot
                        savedPresetsPreference.value = ImmersiveAudioPreset.encodeAll(updated)
                        presetName = ""
                        showSavePreset = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FrostSoulTheme.colors.accent),
                ) {
                    Text("Save", color = FrostSoulTheme.colors.background)
                }
            },
            containerColor = FrostSoulTheme.colors.surfaceRaised,
        )
    }
}

// -------------------------------------------------------------------------
// 1. HERO 3D ACOUSTIC SOUNDSTAGE VISUALIZER
// -------------------------------------------------------------------------

@Composable
private fun AcousticStageHero(
    enabled: Boolean,
    intensity: Float,
    roomPreset: ImmersiveRoomPreset,
    stereoWidth: Float,
    carFader: Float,
    roomSize: Float,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveOffset",
    )

    val animatedEnabledProgress by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.15f,
        animationSpec = tween(400),
        label = "enabledProgress",
    )

    val primaryColor = FrostSoulTheme.colors.accent
    val surfaceColor = FrostSoulTheme.colors.surfaceRaised
    val outlineColor = FrostSoulTheme.colors.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        surfaceColor.copy(alpha = 0.98f),
                        FrostSoulTheme.colors.surface.copy(alpha = 0.98f),
                    ),
                    radius = 500f,
                ),
            )
            .border(
                1.5.dp,
                if (enabled) primaryColor.copy(alpha = 0.45f) else outlineColor.copy(alpha = 0.5f),
                RoundedCornerShape(26.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f + 12.dp.toPx()

            // 1. Draw Physical Room Acoustics Boundary Box
            val roomScaleFactor = 0.62f + roomSize * 0.33f
            val roomW = w * 0.88f * roomScaleFactor
            val roomH = h * 0.70f * roomScaleFactor
            val roomLeft = cx - roomW / 2f
            val roomTop = cy - roomH / 2f

            drawRoundRect(
                color = primaryColor.copy(alpha = 0.06f * animatedEnabledProgress),
                topLeft = Offset(roomLeft, roomTop),
                size = Size(roomW, roomH),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
            )
            drawRoundRect(
                color = if (enabled) primaryColor.copy(alpha = 0.38f) else outlineColor.copy(alpha = 0.22f),
                topLeft = Offset(roomLeft, roomTop),
                size = Size(roomW, roomH),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                ),
            )

            // 2. Speaker Positions (modulated by stereoWidth and carFader)
            val spread = 72.dp.toPx() * (0.55f + stereoWidth * 0.90f)
            val faderY = -carFader * 26.dp.toPx()
            val spkLeftX = cx - spread
            val spkLeftY = cy - 36.dp.toPx() + faderY
            val spkRightX = cx + spread
            val spkRightY = cy - 36.dp.toPx() + faderY

            // 3. Sound Wave Radiations (when active)
            if (enabled) {
                val baseRadii = floatArrayOf(20f, 40f, 62f, 86f)
                baseRadii.forEachIndexed { idx, radius ->
                    val r = (radius + waveOffset) * (0.75f + intensity * 0.45f)
                    val alpha = (pulseAlpha * (1f - (idx / 4.2f))).coerceIn(0.04f, 0.60f)
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha * 0.42f),
                        radius = r,
                        center = Offset(spkLeftX, spkLeftY),
                        style = Stroke(width = 1.6f),
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha * 0.42f),
                        radius = r,
                        center = Offset(spkRightX, spkRightY),
                        style = Stroke(width = 1.6f),
                    )
                }
            }

            // 4. Draw Left & Right Virtual Speakers
            val speakerRadius = 9.dp.toPx()
            drawCircle(
                color = if (enabled) primaryColor else Color.Gray.copy(alpha = 0.4f),
                radius = speakerRadius,
                center = Offset(spkLeftX, spkLeftY),
            )
            drawCircle(
                color = surfaceColor,
                radius = speakerRadius * 0.45f,
                center = Offset(spkLeftX, spkLeftY),
            )
            drawCircle(
                color = if (enabled) primaryColor else Color.Gray.copy(alpha = 0.4f),
                radius = speakerRadius,
                center = Offset(spkRightX, spkRightY),
            )
            drawCircle(
                color = surfaceColor,
                radius = speakerRadius * 0.45f,
                center = Offset(spkRightX, spkRightY),
            )

            // Direct line-of-sight sound rays to listener
            if (enabled) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.22f),
                    start = Offset(spkLeftX, spkLeftY),
                    end = Offset(cx - 9.dp.toPx(), cy),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.22f),
                    start = Offset(spkRightX, spkRightY),
                    end = Offset(cx + 9.dp.toPx(), cy),
                    strokeWidth = 1.5f,
                )
            }

            // 5. Draw Center Listener Avatar
            // Headphone Headband
            drawArc(
                color = if (enabled) primaryColor else Color.Gray.copy(alpha = 0.7f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - 17.dp.toPx(), cy - 17.dp.toPx()),
                size = Size(34.dp.toPx(), 34.dp.toPx()),
                style = Stroke(width = 3.2f),
            )
            // Head
            drawCircle(
                color = surfaceColor,
                radius = 13.dp.toPx(),
                center = Offset(cx, cy),
            )
            drawCircle(
                color = if (enabled) primaryColor else Color.Gray.copy(alpha = 0.7f),
                radius = 13.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2f),
            )
            // Left & Right Ear Cushions
            drawRoundRect(
                color = if (enabled) primaryColor else Color.Gray.copy(alpha = 0.7f),
                topLeft = Offset(cx - 19.dp.toPx(), cy - 7.dp.toPx()),
                size = Size(5.dp.toPx(), 14.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            drawRoundRect(
                color = if (enabled) primaryColor else Color.Gray.copy(alpha = 0.7f),
                topLeft = Offset(cx + 14.dp.toPx(), cy - 7.dp.toPx()),
                size = Size(5.dp.toPx(), 14.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }

        // Top Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(FrostSoulTheme.colors.background.copy(alpha = 0.85f))
                    .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Acoustic Space · ${roomPreset.label}",
                    color = FrostSoulTheme.colors.onSurface,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (enabled) FrostSoulTheme.colors.accent.copy(alpha = 0.16f)
                        else FrostSoulTheme.colors.surface.copy(alpha = 0.75f),
                    )
                    .border(
                        1.dp,
                        if (enabled) FrostSoulTheme.colors.accent.copy(alpha = 0.5f)
                        else FrostSoulTheme.colors.outline.copy(alpha = 0.4f),
                        CircleShape,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (enabled) "Immersion ${(intensity * 100).roundToInt()}%" else "Bypass",
                    color = if (enabled) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// 2. MASTER IMMERSION ENGINE CARD
// -------------------------------------------------------------------------

@Composable
private fun MasterImmersionCard(
    enabled: Boolean,
    intensity: Float,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onIntensityFinished: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = FrostSoulTheme.colors.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (enabled) FrostSoulTheme.colors.accent.copy(alpha = 0.40f) else FrostSoulTheme.colors.outline.copy(alpha = 0.4f),
                RoundedCornerShape(22.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) FrostSoulTheme.colors.accent.copy(alpha = 0.18f)
                            else FrostSoulTheme.colors.surfaceRaised,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = null,
                        tint = if (enabled) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = "Spatial Sound Engine",
                        color = FrostSoulTheme.colors.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (enabled) "Binaural HRTF & acoustic convolution active" else "Direct bit-perfect Media3 stream",
                        color = if (enabled) FrostSoulTheme.colors.accent.copy(alpha = 0.9f) else FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 12.sp,
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FrostSoulTheme.colors.background,
                        checkedTrackColor = FrostSoulTheme.colors.accent,
                        uncheckedThumbColor = FrostSoulTheme.colors.onSurfaceMuted,
                        uncheckedTrackColor = FrostSoulTheme.colors.surfaceRaised,
                    ),
                )
            }

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = FrostSoulTheme.colors.outline.copy(alpha = 0.25f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Spatial Immersion Blend",
                                color = FrostSoulTheme.colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Balance between direct source and 3D soundfield",
                                color = FrostSoulTheme.colors.onSurfaceMuted,
                                fontSize = 11.5.sp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(FrostSoulTheme.colors.accent.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "${(intensity * 100).roundToInt()}%",
                                color = FrostSoulTheme.colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    TechnicalSlider(
                        value = intensity,
                        onValueChange = onIntensityChange,
                        onValueChangeFinished = onIntensityFinished,
                        valueRange = 0f..1f,
                        label = "Spatial blend slider",
                    )

                    // Quick blend snaps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0.25f to "Subtle", 0.50f to "Balanced", 0.75f to "Immersive", 1.0f to "Max Wide").forEach { (v, tag) ->
                            val isCurrent = (intensity - v).let { kotlin.math.abs(it) } < 0.08f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isCurrent) FrostSoulTheme.colors.accent.copy(alpha = 0.16f)
                                        else FrostSoulTheme.colors.surfaceRaised,
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrent) FrostSoulTheme.colors.accent else Color.Transparent,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        onIntensityChange(v)
                                        onIntensityFinished()
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = tag,
                                    color = if (isCurrent) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// 3. CATEGORY SELECTOR TABS
// -------------------------------------------------------------------------

@Composable
private fun ImmersiveCategorySelector(
    selected: ImmersiveCategory,
    onSelected: (ImmersiveCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FrostSoulTheme.colors.surface)
            .selectableGroup()
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ImmersiveCategory.entries.forEach { category ->
            val isSelected = category == selected
            val animatedColor by animateColorAsState(
                targetValue = if (isSelected) FrostSoulTheme.colors.surfaceRaised else Color.Transparent,
                label = "tabBg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(animatedColor)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelected(category) },
                    )
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.label,
                    color = if (isSelected) FrostSoulTheme.colors.onSurface else FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// 4. ACOUSTICS TAB
// -------------------------------------------------------------------------

@Composable
private fun AcousticsTabContent(
    roomPreset: ImmersiveRoomPreset,
    roomMix: Float,
    reflectionAmount: Float,
    reverbTime: Float,
    roomSize: Float,
    dampening: Float,
    onRoomPresetChange: (ImmersiveRoomPreset) -> Unit,
    onRoomMixChange: (Float) -> Unit,
    onReflectionChange: (Float) -> Unit,
    onReverbTimeChange: (Float) -> Unit,
    onRoomSizeChange: (Float) -> Unit,
    onDampeningChange: (Float) -> Unit,
    onResetAcoustics: () -> Unit,
) {
    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitleHeader("ACOUSTIC ENVIRONMENTS", "Physics-based room impulse simulation & binaural acoustics")

        // Environment Preset Cards Carousel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ImmersiveRoomPreset.entries.forEach { preset ->
                val isSelected = preset == roomPreset
                AcousticPresetCard(
                    preset = preset,
                    selected = isSelected,
                    onClick = { onRoomPresetChange(preset) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        SectionTitleHeader("ACOUSTIC SCULPTING", "Tailor room dynamics and physical materials")

        AcousticParamSliderCard(
            label = "Room Reverberation Mix",
            hint = "Direct dry signal vs reflected acoustic energy",
            value = roomMix,
            range = 0f..1f,
            displayValue = "${(roomMix * 100).roundToInt()}%",
            onValueChange = onRoomMixChange,
        )

        AcousticParamSliderCard(
            label = "Early Reflection Density",
            hint = "First-order wall and ceiling reflection energy",
            value = reflectionAmount,
            range = 0f..1f,
            displayValue = "${(reflectionAmount * 100).roundToInt()}%",
            onValueChange = onReflectionChange,
        )

        AcousticParamSliderCard(
            label = "Reverb Decay Time (T60)",
            hint = "Time for acoustic reverberance to attenuate by 60 dB",
            value = reverbTime,
            range = 0.2f..8.0f,
            displayValue = String.format(Locale.US, "%.2f s", reverbTime),
            onValueChange = onReverbTimeChange,
        )

        AcousticParamSliderCard(
            label = "Room Dimensions & Scale",
            hint = "Expands virtual room volume and flight delay",
            value = roomSize,
            range = 0f..1f,
            displayValue = "${(roomSize * 100).roundToInt()}%",
            onValueChange = onRoomSizeChange,
        )

        AcousticParamSliderCard(
            label = "Surface Absorption & Damping",
            hint = "High-frequency material damping across surfaces",
            value = dampening,
            range = 0f..1f,
            displayValue = "${(dampening * 100).roundToInt()}%",
            onValueChange = onDampeningChange,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = onResetAcoustics,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Reset Room Parameters", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AcousticPresetCard(
    preset: ImmersiveRoomPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.outline.copy(alpha = 0.4f),
        label = "presetBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) FrostSoulTheme.colors.accent.copy(alpha = 0.12f) else FrostSoulTheme.colors.surface,
        label = "presetBg",
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .width(140.dp)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = preset.label,
                    color = FrostSoulTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(FrostSoulTheme.colors.accent, CircleShape),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(FrostSoulTheme.colors.surfaceRaised)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = when (preset) {
                        ImmersiveRoomPreset.OFF -> "Direct HRTF"
                        ImmersiveRoomPreset.SMALL_ROOM -> "0.80s T60"
                        ImmersiveRoomPreset.STUDIO -> "1.35s T60"
                        ImmersiveRoomPreset.CONCERT_HALL -> "3.20s T60"
                        ImmersiveRoomPreset.CATHEDRAL -> "4.80s T60"
                        ImmersiveRoomPreset.SUBWAY -> "2.40s T60"
                        ImmersiveRoomPreset.CLOSED_CAR -> "0.60s T60"
                        ImmersiveRoomPreset.MEDIUM_HALL -> "2.10s T60"
                        ImmersiveRoomPreset.SUBWAY_PLATFORM -> "2.80s T60"
                        ImmersiveRoomPreset.LONG_TUNNEL -> "5.50s T60"
                        ImmersiveRoomPreset.OPEN_ROAD -> "0.20s T60"
                        ImmersiveRoomPreset.CAVE -> "3.80s T60"
                        ImmersiveRoomPreset.STADIUM -> "4.20s T60"
                        ImmersiveRoomPreset.EIGHT_D_ORBIT -> "8s HRTF orbit"
                    },
                    color = if (selected) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Text(
                text = when (preset) {
                    ImmersiveRoomPreset.OFF -> "Pure spatial stage\nno room reflection"
                    ImmersiveRoomPreset.SMALL_ROOM -> "Compact room\nfast diffusion"
                    ImmersiveRoomPreset.STUDIO -> "Balanced acoustics\nclean decay"
                    ImmersiveRoomPreset.CONCERT_HALL -> "Orchestral stage\nlush reverberance"
                    ImmersiveRoomPreset.CATHEDRAL -> "Monumental space\nethereal sustain"
                    ImmersiveRoomPreset.SUBWAY -> "Reflective tunnel\nhigh reflections"
                    ImmersiveRoomPreset.CLOSED_CAR -> "Cabin enclosure\nfront/rear focus"
                    ImmersiveRoomPreset.MEDIUM_HALL -> "Performance hall\nbalanced warmth"
                    ImmersiveRoomPreset.SUBWAY_PLATFORM -> "Vaulted station\nstone reflections"
                    ImmersiveRoomPreset.LONG_TUNNEL -> "Corridor tube\ndeep resonance"
                    ImmersiveRoomPreset.OPEN_ROAD -> "Open expanse\nground reflection"
                    ImmersiveRoomPreset.CAVE -> "Subterranean rock\ndiffuse scatter"
                    ImmersiveRoomPreset.STADIUM -> "Grand arena\nvast perimeter"
                    ImmersiveRoomPreset.EIGHT_D_ORBIT -> "Automatic HRTF orbit\nbass stays anchored"
                },
                color = FrostSoulTheme.colors.onSurfaceMuted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                minLines = 2,
            )
        }
    }
}

// -------------------------------------------------------------------------
// 5. SOUNDSTAGE TAB
// -------------------------------------------------------------------------

@Composable
private fun SoundstageTabContent(
    sourceAzimuth: Float,
    sourceElevation: Float,
    sourceDistance: Float,
    stereoWidth: Float,
    carFader: Float,
    quantumFrames: Int,
    savedPresets: List<ImmersiveAudioPreset>,
    onSourceAzimuthChange: (Float) -> Unit,
    onSourceElevationChange: (Float) -> Unit,
    onSourceDistanceChange: (Float) -> Unit,
    onStereoWidthChange: (Float) -> Unit,
    onCarFaderChange: (Float) -> Unit,
    onQuantumChange: (Int) -> Unit,
    onPresetSelected: (ImmersiveAudioPreset) -> Unit,
    onSavePreset: () -> Unit,
    onDeletePreset: (ImmersiveAudioPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitleHeader("BINAURAL SOUNDSTAGE", "Spatial width and listener focal point")

        AcousticParamSliderCard(
            label = "Azimuth",
            hint = "Spatial Panner: 0° front, +90° right, -90° left, ±180° rear",
            value = sourceAzimuth,
            range = -180f..180f,
            displayValue = sourceAzimuth.roundToInt().toString() + "°",
            onValueChange = onSourceAzimuthChange,
        )

        AcousticParamSliderCard(
            label = "Elevation",
            hint = "Vertical source angle supported by the current HRTF model",
            value = sourceElevation,
            range = -45f..90f,
            displayValue = sourceElevation.roundToInt().toString() + "°",
            onValueChange = onSourceElevationChange,
        )

        AcousticParamSliderCard(
            label = "Distance",
            hint = "Point-source distance cue with 1/r attenuation",
            value = sourceDistance,
            range = 1f..10f,
            displayValue = String.format(Locale.US, "%.2f m", sourceDistance),
            onValueChange = onSourceDistanceChange,
        )

        LegacyFadedControlCard("M/S Stereo Width", "Not used by the active point-source renderer")
        AcousticParamSliderCard(
            label = "Closed Car Front ↔ Rear",
            hint = "Active only for Closed Car. Center = front, ends = rear hemisphere.",
            value = carFader,
            range = -1f..1f,
            displayValue = when {
                carFader < -0.05f -> "Rear −"
                carFader > 0.05f -> "Rear +"
                else -> "Front"
            },
            onValueChange = onCarFaderChange,
        )

        SectionTitleHeader("PROCESSING BUFFER (LATENCY)", "Quantum block size for real-time DSP")

        QuantumSettingsCard(
            quantumFrames = quantumFrames,
            onQuantumChange = onQuantumChange,
        )

        SectionTitleHeader("SAVED CUSTOM SPACES", "Store and recall full acoustic configurations")

        SavedSpacesCard(
            presets = savedPresets,
            onPresetSelected = onPresetSelected,
            onSavePreset = onSavePreset,
            onDeletePreset = onDeletePreset,
        )
    }
}

@Composable
private fun LegacyFadedControlCard(label: String, hint: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FrostSoulTheme.colors.surface.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Unavailable · Spatial Panner runtime", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
            Text(hint, color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun QuantumSettingsCard(
    quantumFrames: Int,
    onQuantumChange: (Int) -> Unit,
) {
    var inputText by remember(quantumFrames) { mutableStateOf(quantumFrames.toString()) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val latencyMs = (quantumFrames.toFloat() / 48000f) * 1000f

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FrostSoulTheme.colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quantum Block Size",
                        color = FrostSoulTheme.colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Real-time DSP latency: ${String.format(Locale.US, "%.1f", latencyMs)} ms @ 48kHz",
                        color = FrostSoulTheme.colors.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                TextField(
                    value = inputText,
                    onValueChange = { value ->
                        val digits = value.filter(Char::isDigit).take(4)
                        inputText = digits
                    },
                    modifier = Modifier
                        .width(100.dp)
                        .onFocusChanged { state ->
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val safe = inputText.toIntOrNull()?.coerceIn(
                            ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES,
                            ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES,
                        ) ?: quantumFrames
                        inputText = safe.toString()
                        onQuantumChange(safe)
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = FrostSoulTheme.colors.surfaceRaised,
                        unfocusedContainerColor = FrostSoulTheme.colors.surfaceRaised,
                        focusedTextColor = FrostSoulTheme.colors.onSurface,
                        unfocusedTextColor = FrostSoulTheme.colors.onSurface,
                        focusedIndicatorColor = FrostSoulTheme.colors.accent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
            }

            // Quick Quantum preset chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    192 to "192 · 4.0ms",
                    384 to "384 · 8.0ms",
                    512 to "512 · 10.7ms",
                    1024 to "1024 · 21.3ms",
                ).forEach { (frames, label) ->
                    val isSelected = quantumFrames == frames
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) FrostSoulTheme.colors.accent.copy(alpha = 0.16f)
                                else FrostSoulTheme.colors.surfaceRaised,
                            )
                            .border(
                                1.dp,
                                if (isSelected) FrostSoulTheme.colors.accent else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                onQuantumChange(frames)
                                inputText = frames.toString()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSpacesCard(
    presets: List<ImmersiveAudioPreset>,
    onPresetSelected: (ImmersiveAudioPreset) -> Unit,
    onSavePreset: () -> Unit,
    onDeletePreset: (ImmersiveAudioPreset) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FrostSoulTheme.colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Saved Space Snapshots",
                        color = FrostSoulTheme.colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "${presets.size} custom acoustics saved",
                        color = FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 11.5.sp,
                    )
                }

                Button(
                    onClick = onSavePreset,
                    colors = ButtonDefaults.buttonColors(containerColor = FrostSoulTheme.colors.accent),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("+ Save Current", color = FrostSoulTheme.colors.background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (presets.isEmpty()) {
                Text(
                    text = "No custom acoustic spaces saved yet. Tune your space and tap \"+ Save Current\" to preserve it.",
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(FrostSoulTheme.colors.surfaceRaised)
                                .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable { onPresetSelected(preset) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = preset.name,
                                color = FrostSoulTheme.colors.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = "Delete",
                                tint = FrostSoulTheme.colors.onSurfaceMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onDeletePreset(preset) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// 6. STUDIO LAB (METERS, TONE & DIAGNOSTICS)
// -------------------------------------------------------------------------

@Composable
private fun StudioLabTabContent(
    diagnostics: ImmersiveAudioDiagnostics,
    limiterEnabled: Boolean,
    bassGainDb: Float,
    trebleGainDb: Float,
    outputGainDb: Float,
    activeCapture: ImmersiveActiveCapture?,
    latestCapture: ImmersiveDiagnosticCapture?,
    onLimiterChange: (Boolean) -> Unit,
    onBassGainChange: (Float) -> Unit,
    onTrebleGainChange: (Float) -> Unit,
    onOutputGainChange: (Float) -> Unit,
    onCapture: (Int) -> Unit,
    onExport: () -> Unit,
    onResetDiagnostics: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitleHeader("B1–B7 SIGNAL LAB", "Measured PCM boundaries from Media3 through the native DSP")

        PipelineTelemetryCard(
            diagnostics = diagnostics,
            activeCapture = activeCapture,
            latestCapture = latestCapture,
            onCapture = onCapture,
            onExport = onExport,
            onResetDiagnostics = onResetDiagnostics,
        )

        SectionTitleHeader("OUTPUT SAFETY & SHAPING", "True peak ceiling & native frequency trim")

        OutputSafetyToneCard(
            limiterEnabled = limiterEnabled,
            bassGainDb = bassGainDb,
            trebleGainDb = trebleGainDb,
            outputGainDb = outputGainDb,
            onLimiterChange = onLimiterChange,
            onBassGainChange = onBassGainChange,
            onTrebleGainChange = onTrebleGainChange,
            onOutputGainChange = onOutputGainChange,
        )

    }
}

@Composable
private fun RealtimeAudioMetersCard(diagnostics: ImmersiveAudioDiagnostics) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FrostSoulTheme.colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("INPUT STREAM", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(FrostSoulTheme.colors.surfaceRaised)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (diagnostics.processCallCount > 0) "PCM ACTIVE" else "IDLE",
                        color = if (diagnostics.processCallCount > 0) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Input L & R meters
            StereoChannelMeterRow("In L", diagnostics.inputRmsL, diagnostics.inputPeakL, diagnostics.inputTruePeakL)
            StereoChannelMeterRow("In R", diagnostics.inputRmsR, diagnostics.inputPeakR, diagnostics.inputTruePeakR)

            HorizontalDivider(color = FrostSoulTheme.colors.outline.copy(alpha = 0.25f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("OUTPUT STREAM (DSP)", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(FrostSoulTheme.colors.surfaceRaised)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (diagnostics.processorEnabled) "BINAURAL PROCESSED" else "BYPASSED",
                        color = if (diagnostics.processorEnabled) FrostSoulTheme.colors.accent else FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Output L & R meters
            StereoChannelMeterRow("Out L", diagnostics.outputRmsL, diagnostics.outputPeakL, diagnostics.outputTruePeakL)
            StereoChannelMeterRow("Out R", diagnostics.outputRmsR, diagnostics.outputPeakR, diagnostics.outputTruePeakR)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FrostSoulTheme.colors.surfaceRaised)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HEADROOM SAFETY", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
                Text(
                    text = when {
                        diagnostics.clippedOutput > 0L -> "OUTPUT CLIP COUNT ${diagnostics.clippedOutput}"
                        diagnostics.limiterGainReductionDb > 0.01f -> "LIMITING ${String.format(Locale.US, "%.2f", diagnostics.limiterGainReductionDb)} dB"
                        diagnostics.processCallCount == 0L -> "WAITING FOR PCM"
                        else -> "NO CLIP OBSERVED"
                    },
                    color = if (diagnostics.clippedOutput > 0L) Color(0xFFFF5252) else FrostSoulTheme.colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryMetricItem("Pre-limiter TP", String.format(Locale.US, "%.4f", diagnostics.preLimiterTruePeak))
                TelemetryMetricItem("Gain budget", String.format(Locale.US, "%.2f dB", diagnostics.gainBudgetDb))
            }
        }
    }
}

@Composable
private fun StereoChannelMeterRow(
    label: String,
    rms: Float,
    peak: Float,
    truePeak: Float,
) {
    val peakDb = if (peak > 1.0e-9f) 20.0 * log10(peak.toDouble()) else -60.0
    val normalizedMeter = ((peakDb + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()

    val meterColor = when {
        truePeak >= 0.98f -> Color(0xFFFF5252) // Peak alert
        peakDb >= -3.0 -> Color(0xFFFFB74D)    // Amber warm
        else -> FrostSoulTheme.colors.accent   // Crisp accent
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            color = FrostSoulTheme.colors.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(46.dp),
        )

        // Animated Meter Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(FrostSoulTheme.colors.surfaceRaised),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalizedMeter)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(meterColor),
            )
        }

        Text(
            text = formatDb(peak),
            color = if (truePeak >= 0.98f) Color(0xFFFF5252) else FrostSoulTheme.colors.onSurfaceMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(62.dp),
        )
    }
}

@Composable
private fun OutputSafetyToneCard(
    limiterEnabled: Boolean,
    bassGainDb: Float,
    trebleGainDb: Float,
    outputGainDb: Float,
    onLimiterChange: (Boolean) -> Unit,
    onBassGainChange: (Float) -> Unit,
    onTrebleGainChange: (Float) -> Unit,
    onOutputGainChange: (Float) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FrostSoulTheme.colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Native Safety Limiter",
                        color = FrostSoulTheme.colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Zero-overshoot smooth ceiling protects against transients",
                        color = FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 11.sp,
                    )
                }

                Switch(
                    checked = limiterEnabled,
                    onCheckedChange = onLimiterChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FrostSoulTheme.colors.background,
                        checkedTrackColor = FrostSoulTheme.colors.accent,
                    ),
                )
            }

            HorizontalDivider(color = FrostSoulTheme.colors.outline.copy(alpha = 0.25f))

            AcousticParamSliderCard(
                label = "Spatial Bass Warmth",
                hint = "Dedicated low-frequency acoustic warmth (-12 to +12 dB)",
                value = bassGainDb,
                range = -12f..12f,
                displayValue = String.format(Locale.US, "%+.1f dB", bassGainDb),
                onValueChange = onBassGainChange,
            )

            AcousticParamSliderCard(
                label = "Spatial Treble Air",
                hint = "High-frequency presence and clarity (-12 to +12 dB)",
                value = trebleGainDb,
                range = -12f..12f,
                displayValue = String.format(Locale.US, "%+.1f dB", trebleGainDb),
                onValueChange = onTrebleGainChange,
            )

            AcousticParamSliderCard(
                label = "Output Gain Trim",
                hint = "Pre-output signal gain trim (-24 to +12 dB)",
                value = outputGainDb,
                range = -24f..12f,
                displayValue = String.format(Locale.US, "%+.1f dB", outputGainDb),
                onValueChange = onOutputGainChange,
            )
        }
    }
}

@Composable
private fun PipelineStageTelemetryRow(stage: ImmersivePipelineStageTelemetry) {
    val colors = FrostSoulTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceRaised)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${stage.id}  ${stage.name}", color = colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (stage.available) "LIVE" else "UNAVAILABLE",
                color = if (stage.available) colors.accent else colors.onSurfaceMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        if (stage.available || stage.input.available || stage.output.available) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageBoundaryMetrics("IN", stage.input, Modifier.weight(1f))
                StageBoundaryMetrics("OUT", stage.output, Modifier.weight(1f))
            }
        }
        if (!stage.available) {
            Text(
                stage.unavailableReason ?: "Measurement unavailable",
                color = colors.onSurfaceMuted,
                fontSize = 10.sp,
            )
        }
        stage.processingTimeMs?.let {
            Text("Processing ${String.format(Locale.US, "%.3f ms", it)}", color = colors.onSurfaceMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun StageBoundaryMetrics(label: String, metrics: ImmersiveStageDiagnostics, modifier: Modifier = Modifier) {
    val colors = FrostSoulTheme.colors
    Column(modifier = modifier) {
        Text(label, color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        if (!metrics.available) {
            Text("Unavailable", color = colors.onSurfaceMuted, fontSize = 10.sp)
        } else {
            Text("RMS ${String.format(Locale.US, "%.4f", metrics.rms)}  Peak ${String.format(Locale.US, "%.4f", metrics.peak)}", color = colors.onSurface, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("TP ${String.format(Locale.US, "%.4f", metrics.truePeak)}  Clip ${metrics.clippedSamples}", color = colors.onSurfaceMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("NaN ${metrics.nanCount}  Inf ${metrics.infCount}  Frames ${metrics.frames}", color = colors.onSurfaceMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PipelineTelemetryCard(
    diagnostics: ImmersiveAudioDiagnostics,
    activeCapture: ImmersiveActiveCapture?,
    latestCapture: ImmersiveDiagnosticCapture?,
    onCapture: (Int) -> Unit,
    onExport: () -> Unit,
    onResetDiagnostics: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FrostSoulTheme.colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "LIVE PROCESSING BOUNDARIES",
                color = FrostSoulTheme.colors.onSurfaceMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            diagnostics.pipelineStages().forEach { stage ->
                PipelineStageTelemetryRow(stage)
            }

            HorizontalDivider(color = FrostSoulTheme.colors.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryMetricItem("Sample Rate", "${diagnostics.sampleRate} Hz")
                TelemetryMetricItem("Format", if (diagnostics.pcmEncoding == 4) "PCM Float" else "PCM 16-bit")
                TelemetryMetricItem("Quantum", "${diagnostics.quantumFrames} frames")
            }

            HorizontalDivider(color = FrostSoulTheme.colors.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryMetricItem("Backend", diagnostics.backendLabel())
                TelemetryMetricItem("BRIR", if (diagnostics.brirReady) "Ready" else "Unavailable")
                TelemetryMetricItem(
                    "Latency",
                    if (diagnostics.algorithmicLatencySamples > 0 && diagnostics.sampleRate > 0) {
                        String.format(
                            Locale.US,
                            "%.1f ms",
                            diagnostics.algorithmicLatencySamples * 1000.0 / diagnostics.sampleRate,
                        )
                    } else {
                        "Unavailable"
                    },
                )
            }

            HorizontalDivider(color = FrostSoulTheme.colors.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryMetricItem("Avg Render Time", String.format(Locale.US, "%.2f ms", diagnostics.averageProcessingTimeMs))
                TelemetryMetricItem("Processed Blocks", "${diagnostics.totalBlocks}")
                TelemetryMetricItem("Deadline Misses", "${diagnostics.deadlineMisses}")
            }

            HorizontalDivider(color = FrostSoulTheme.colors.outline.copy(alpha = 0.2f))

            // Capture Diagnostics Section
            Text("SIGNAL VERIFICATION CAPTURE", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

            if (activeCapture != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Recording ${String.format(Locale.US, "%.1f", activeCapture.elapsedSeconds)}s / ${activeCapture.durationSeconds}s",
                        color = FrostSoulTheme.colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = { (activeCapture.elapsedSeconds / activeCapture.durationSeconds).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = FrostSoulTheme.colors.accent,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onCapture(10) },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Capture 10s", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onCapture(20) },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Capture 20s", fontSize = 12.sp)
                    }
                }
            }

            if (latestCapture != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Capture ready: ${latestCapture.samples.size} samples",
                        color = FrostSoulTheme.colors.onSurface,
                        fontSize = 12.sp,
                    )
                    Button(
                        onClick = onExport,
                        colors = ButtonDefaults.buttonColors(containerColor = FrostSoulTheme.colors.accent),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Export TXT", color = FrostSoulTheme.colors.background, fontSize = 12.sp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onResetDiagnostics,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Reset Telemetry", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricItem(label: String, value: String) {
    Column {
        Text(label, color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
        Text(value, color = FrostSoulTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

// -------------------------------------------------------------------------
// REUSABLE HELPER UI COMPONENTS
// -------------------------------------------------------------------------

@Composable
private fun SectionTitleHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = FrostSoulTheme.colors.onSurfaceMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Text(
            text = subtitle,
            color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AcousticParamSliderCard(
    label: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FrostSoulTheme.colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostSoulTheme.colors.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = FrostSoulTheme.colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = hint,
                        color = FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 11.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(FrostSoulTheme.colors.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = displayValue,
                        color = FrostSoulTheme.colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            TechnicalSlider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = { onValueChange(it.coerceIn(range.start, range.endInclusive)) },
                valueRange = range,
                label = label,
            )
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
        inactiveTrackColor = colors.onSurface.copy(alpha = 0.12f),
        disabledThumbColor = colors.onSurfaceMuted,
        disabledActiveTrackColor = colors.onSurfaceMuted.copy(alpha = 0.35f),
        disabledInactiveTrackColor = colors.onSurface.copy(alpha = 0.06f),
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
                Modifier
                    .size(20.dp)
                    .background(if (enabled) colors.accent else colors.onSurfaceMuted, CircleShape)
                    .border(3.dp, colors.surfaceRaised, CircleShape),
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                enabled = enabled,
                colors = sliderColors,
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
                modifier = Modifier.height(5.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { contentDescription = label },
    )
}

private fun formatDb(value: Float): String =
    if (!value.isFinite() || value <= 1.0e-9f) "-∞ dB"
    else String.format(Locale.US, "%.1f dB", 20.0 * log10(value.toDouble()))
