package dev.vxs.frostsoulx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.playback.StereoSurroundDiagnostics
import dev.vxs.frostsoulx.playback.StereoSurroundRuntime
import dev.vxs.frostsoulx.playback.StereoSurroundTuningParameters
import kotlinx.coroutines.delay

private data class TuningSliderSpec(
    val title: String,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val value: (StereoSurroundTuningParameters) -> Float,
    val update: (StereoSurroundTuningParameters, Float) -> StereoSurroundTuningParameters,
    val format: (Float) -> String = { "%.3f".format(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StereoSurroundTuningScreen(navController: NavController) {
    var enabled by remember { mutableStateOf(StereoSurroundRuntime.isEnabled()) }
    var intensity by remember { mutableStateOf(StereoSurroundRuntime.intensity()) }
    var tuning by remember { mutableStateOf(StereoSurroundRuntime.tuning().validated()) }
    var diagnostics by remember { mutableStateOf(StereoSurroundDiagnostics()) }

    LaunchedEffect(Unit) {
        while (true) {
            diagnostics = StereoSurroundRuntime.readDiagnostics()
            delay(250L)
        }
    }

    val specs = remember {
        listOf(
            TuningSliderSpec(
                title = "Low-frequency side cutoff (Hz)",
                range = 20f..2000f,
                steps = 197,
                value = { it.lowFrequencyCutoffHz },
                update = { value, next -> value.copy(lowFrequencyCutoffHz = next) },
                format = { "%.0f".format(it) },
            ),
            TuningSliderSpec(
                title = "Side extraction gain", range = 0f..1f, steps = 99,
                value = { it.sideExtractionGain },
                update = { value, next -> value.copy(sideExtractionGain = next) },
            ),
            TuningSliderSpec(
                title = "Delay A (samples)",
                range = 1f..255f,
                steps = 253,
                value = { it.delayASamples.toFloat() },
                update = { value, next -> value.copy(delayASamples = next.toInt()) },
                format = { "%.0f".format(it) },
            ),
            TuningSliderSpec(
                title = "Delay B (samples)",
                range = 1f..255f,
                steps = 253,
                value = { it.delayBSamples.toFloat() },
                update = { value, next -> value.copy(delayBSamples = next.toInt()) },
                format = { "%.0f".format(it) },
            ),
            TuningSliderSpec(
                title = "Decorrelation A coefficient", range = 0f..1f, steps = 99,
                value = { it.decorrelationAInputCoefficient },
                update = { value, next -> value.copy(decorrelationAInputCoefficient = next) },
            ),
            TuningSliderSpec(
                title = "Decorrelation B coefficient", range = 0f..1f, steps = 99,
                value = { it.decorrelationBInputCoefficient },
                update = { value, next -> value.copy(decorrelationBInputCoefficient = next) },
            ),
            TuningSliderSpec(
                title = "Side-high mix base", range = 0f..1f, steps = 99,
                value = { it.sideHighMixBase },
                update = { value, next -> value.copy(sideHighMixBase = next) },
            ),
            TuningSliderSpec(
                title = "Side-high mix intensity span", range = 0f..1f, steps = 99,
                value = { it.sideHighMixIntensitySpan },
                update = { value, next -> value.copy(sideHighMixIntensitySpan = next) },
            ),
            TuningSliderSpec(
                title = "Ambience decorrelated-A weight", range = 0f..1f, steps = 99,
                value = { it.ambienceDecorrelatedAWeight },
                update = { value, next -> value.copy(ambienceDecorrelatedAWeight = next) },
            ),
            TuningSliderSpec(
                title = "Rear ambience weight", range = 0f..1f, steps = 99,
                value = { it.rearAmbienceWeight },
                update = { value, next -> value.copy(rearAmbienceWeight = next) },
            ),
            TuningSliderSpec(
                title = "Maximum rear contribution", range = 0f..0.5f, steps = 99,
                value = { it.maxRearContribution },
                update = { value, next -> value.copy(maxRearContribution = next) },
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer DSP tuning") },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Temporary V1 Stereo Surround diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This panel changes only existing processor parameters. It does not add a second DSP path or change the production Surround screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enabled", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (enabled) "V1 processor requested on" else "Original renderer-level OFF state",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        StereoSurroundRuntime.setEnabled(it)
                    },
                )
            }

            TuningSlider(
                title = "Intensity",
                value = intensity,
                range = 0f..1f,
                steps = 99,
                valueText = "%.3f".format(intensity),
                onValueChange = {
                    intensity = it.coerceIn(0f, 1f)
                    StereoSurroundRuntime.setIntensity(intensity)
                },
                onReset = {
                    intensity = 0.5f
                    StereoSurroundRuntime.setIntensity(intensity)
                },
            )

            HorizontalDivider()
            Text("Current algorithm parameters", style = MaterialTheme.typography.titleMedium)
            specs.forEach { spec ->
                val value = spec.value(tuning)
                TuningSlider(
                    title = spec.title,
                    value = value,
                    range = spec.range,
                    steps = spec.steps,
                    valueText = spec.format(value),
                    onValueChange = { next ->
                        tuning = spec.update(tuning, next).validated()
                        StereoSurroundRuntime.setTuning(tuning)
                    },
                    onReset = {
                        tuning = spec.update(tuning, spec.value(StereoSurroundTuningParameters.DEFAULT)).validated()
                        StereoSurroundRuntime.setTuning(tuning)
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        intensity = 0.5f
                        tuning = StereoSurroundTuningParameters.DEFAULT
                        StereoSurroundRuntime.setIntensity(intensity)
                        StereoSurroundRuntime.setTuning(tuning)
                    },
                ) { Text("Reset all DSP parameters") }
                OutlinedButton(
                    onClick = {
                        enabled = false
                        StereoSurroundRuntime.setEnabled(false)
                    },
                ) { Text("Force off") }
            }

            HorizontalDivider()
            Text("Live diagnostics", style = MaterialTheme.typography.titleMedium)
            DiagnosticLine("Processor enabled", enabled.toString())
            DiagnosticLine("Intensity", "%.3f".format(intensity))
            DiagnosticLine("Input RMS", "%.6f".format(diagnostics.inputRms))
            DiagnosticLine("Output RMS", "%.6f".format(diagnostics.outputRms))
            DiagnosticLine("Input peak", "%.6f".format(diagnostics.inputPeak))
            DiagnosticLine("Output peak", "%.6f".format(diagnostics.outputPeak))
            DiagnosticLine("Maximum |input-output|", "%.6f".format(diagnostics.maxAbsDifference))
            DiagnosticLine("Samples changed", "%.3f%%".format(diagnostics.changedPercentage))
            DiagnosticLine("NaN count", diagnostics.nanCount.toString())
            DiagnosticLine("Inf count", diagnostics.infCount.toString())
            DiagnosticLine("Process call count", diagnostics.processCallCount.toString())

            Text(
                text = "Parameters are applied through atomics and committed on the audio thread before processing. No allocation, lock, file I/O, or logging is performed by the callback. The renderer-level OFF bypass remains unchanged when the player is constructed with Surround disabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TuningSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(valueText, style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onReset) {
                    Icon(painterResource(R.drawable.replay), contentDescription = "Reset $title")
                }
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
