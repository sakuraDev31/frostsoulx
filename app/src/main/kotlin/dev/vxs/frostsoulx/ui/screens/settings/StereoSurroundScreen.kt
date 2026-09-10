package dev.vxs.frostsoulx.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import dev.vxs.frostsoulx.playback.ImmersiveAudioRuntime
import dev.vxs.frostsoulx.playback.ImmersiveRoomPreset
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.Locale
import dev.vxs.frostsoulx.playback.ImmersiveAudioDiagnostics

private enum class ImmersiveSettingsPage { Default, Advanced }

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
    val persistedRoomPreset by roomPresetPreference
    val persistedRoomMix by roomMixPreference
    val persistedReflectionAmount by reflectionPreference
    val persistedReverbTime by reverbTimePreference

    var selectedPage by remember { mutableStateOf(ImmersiveSettingsPage.Default) }
    var draftIntensity by remember { mutableFloatStateOf(persistedIntensity.coerceIn(0f, 1f)) }
    var draftRoomMix by remember { mutableFloatStateOf(persistedRoomMix.coerceIn(0f, 1f)) }
    var draftReflectionAmount by remember { mutableFloatStateOf(persistedReflectionAmount.coerceIn(0f, 1f)) }
    var draftReverbTime by remember { mutableFloatStateOf(persistedReverbTime.coerceIn(0.2f, 8f)) }
    var isDragging by remember { mutableStateOf(false) }
    var showDevelopmentWarning by remember { mutableStateOf(true) }
    var diagnostics by remember { mutableStateOf(ImmersiveAudioDiagnostics()) }

    LaunchedEffect(persistedIntensity) {
        if (!isDragging) {
            draftIntensity = persistedIntensity.coerceIn(0f, 1f)
            ImmersiveAudioRuntime.setIntensity(draftIntensity)
        }
    }

    LaunchedEffect(persistedRoomPreset, persistedRoomMix, persistedReflectionAmount, persistedReverbTime) {
        ImmersiveAudioRuntime.setRoomPreset(ImmersiveRoomPreset.fromNative(persistedRoomPreset))
        draftRoomMix = persistedRoomMix.coerceIn(0f, 1f)
        draftReflectionAmount = persistedReflectionAmount.coerceIn(0f, 1f)
        draftReverbTime = persistedReverbTime.coerceIn(0.2f, 8f)
        ImmersiveAudioRuntime.setRoomMix(draftRoomMix)
        ImmersiveAudioRuntime.setReflectionAmount(draftReflectionAmount)
        ImmersiveAudioRuntime.setReverbTimeSeconds(draftReverbTime)
    }

    LaunchedEffect(enabled) {
        ImmersiveAudioRuntime.setEnabled(enabled)
        while (enabled) {
            diagnostics = ImmersiveAudioRuntime.readDiagnostics()
            delay(250)
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
                    onRoomPresetChange = { roomPresetPreference.value = it.nativeValue },
                    onRoomMixChange = { draftRoomMix = it; roomMixPreference.value = it; ImmersiveAudioRuntime.setRoomMix(it) },
                    onReflectionChange = { draftReflectionAmount = it; reflectionPreference.value = it; ImmersiveAudioRuntime.setReflectionAmount(it) },
                    onReverbTimeChange = { draftReverbTime = it; reverbTimePreference.value = it; ImmersiveAudioRuntime.setReverbTimeSeconds(it) },
                    onResetRoom = {
                        roomPresetPreference.value = ImmersiveRoomPreset.STUDIO.nativeValue
                        roomMixPreference.value = 0.18f
                        reflectionPreference.value = 0.28f
                        reverbTimePreference.value = 1.35f
                    },
                    diagnostics = diagnostics,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDevelopmentWarning) {
        AlertDialog(
            onDismissRequest = { showDevelopmentWarning = false },
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
                Button(onClick = { showDevelopmentWarning = false }) {
                    Text("Continue")
                }
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        ImmersivePageTab(
            label = "DEFAULT",
            selected = selectedPage == ImmersiveSettingsPage.Default,
            onClick = { onPageSelected(ImmersiveSettingsPage.Default) },
        )
        ImmersivePageTab(
            label = "ADVANCED",
            selected = selectedPage == ImmersiveSettingsPage.Advanced,
            onClick = { onPageSelected(ImmersiveSettingsPage.Advanced) },
        )
    }
}

@Composable
private fun ImmersivePageTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .wrapContentWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = label,
            color = if (selected) FrostSoulTheme.colors.onSurface else FrostSoulTheme.colors.onSurfaceMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 1.4.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(2.dp)
                .background(
                    if (selected) FrostSoulTheme.colors.onSurface else Color.Transparent,
                ),
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
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
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
private fun AdvancedImmersivePage(
    enabled: Boolean,
    intensity: Float,
    roomPreset: ImmersiveRoomPreset,
    roomMix: Float,
    reflectionAmount: Float,
    reverbTimeSeconds: Float,
    diagnostics: ImmersiveAudioDiagnostics,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onIntensityFinished: () -> Unit,
    onRoomPresetChange: (ImmersiveRoomPreset) -> Unit,
    onRoomMixChange: (Float) -> Unit,
    onReflectionChange: (Float) -> Unit,
    onReverbTimeChange: (Float) -> Unit,
    onResetRoom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
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
        )
        StatusLine("Interpolation", "Bilinear is fixed by the current native engine.")
        StatusLine("Source direction", "Forward-facing source at (0, 0, 1). Runtime direction control is not connected.")
        HorizontalDivider(color = FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.18f))
        ImmersiveSectionLabel("ROOM MODEL")
        Text(
            text = "Preset",
            color = FrostSoulTheme.colors.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ImmersiveRoomPreset.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { preset ->
                        Button(
                            onClick = { onRoomPresetChange(preset) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(preset.label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        RoomParameterSlider("Room mix", roomMix, 0f..1f, "${(roomMix * 100).roundToInt()}%", onRoomMixChange)
        RoomParameterSlider("Reflections", reflectionAmount, 0f..1f, "${(reflectionAmount * 100).roundToInt()}%", onReflectionChange)
        RoomParameterSlider("Reverb time", reverbTimeSeconds, 0.2f..8f, String.format(Locale.US, "%.1fs", reverbTimeSeconds), onReverbTimeChange)
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
private fun RoomParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = FrostSoulTheme.colors.onSurface, fontSize = 14.sp)
            Text(valueLabel, color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChange(it.coerceIn(range.start, range.endInclusive)) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spatial blend", color = FrostSoulTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(intensity * 100f).roundToInt()}%",
                color = if (enabled) FrostSoulTheme.colors.onSurface else FrostSoulTheme.colors.onSurfaceMuted,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = intensity,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..1f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
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
