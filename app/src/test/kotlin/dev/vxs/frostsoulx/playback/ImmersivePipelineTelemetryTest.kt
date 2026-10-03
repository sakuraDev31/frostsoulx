package dev.vxs.frostsoulx.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePipelineTelemetryTest {
    private fun measured(rms: Float = 0.25f) = ImmersiveStageDiagnostics(
        available = true,
        rms = rms,
        peak = 0.5f,
        truePeak = 0.55f,
        clippedSamples = 0,
        frames = 384,
        sampleRate = 48_000,
        encoding = 2,
    )

    @Test
    fun pipelineExposesExactlySevenBoundaries() {
        val diagnostics = ImmersiveAudioDiagnostics(
            processorEnabled = true,
            inputRms = 0.2f,
            inputPeak = 0.4f,
            inputTruePeakL = 0.45f,
            inputTruePeakR = 0.44f,
            outputRms = 0.18f,
            outputPeak = 0.36f,
            outputTruePeakL = 0.4f,
            outputTruePeakR = 0.39f,
            b1AfterSilenceSkipping = measured(),
            b2AfterSonic = measured(0.24f),
            b3BeforeNativeDsp = measured(0.23f),
            b5AfterNativeDsp = measured(0.18f),
        )

        val stages = diagnostics.pipelineStages()
        assertEquals(listOf("B1", "B2", "B3", "B4", "B5", "B6", "B7"), stages.map { it.id })
        assertTrue(stages[3].available)
        assertEquals(0.18f, stages[4].output.rms, 0.0001f)
        assertFalse(stages[6].available)
    }

    @Test
    fun unavailableDeviceBoundariesNeverInventMeasurements() {
        val stages = ImmersiveAudioDiagnostics().pipelineStages()
        assertFalse(stages[5].output.available)
        assertFalse(stages[6].input.available)
        assertFalse(stages[6].output.available)
        assertTrue(stages[5].unavailableReason!!.contains("AudioTrack"))
        assertTrue(stages[6].unavailableReason!!.contains("DAC"))
    }

    @Test
    fun b4UsesPreEngineConversionMeasurement() {
        val diagnostics = ImmersiveAudioDiagnostics(
            processorEnabled = true,
            preEngineRmsL = 0.11f,
            preEngineRmsR = 0.13f,
            preEnginePeakL = 0.21f,
            preEnginePeakR = 0.23f,
            preEngineTruePeakL = 0.24f,
            preEngineTruePeakR = 0.26f,
            preEngineClipped = 2L,
        )
        val b4 = diagnostics.pipelineStages()[3]
        assertEquals("PCM conversion → native DSP", b4.name)
        assertEquals(0.12f, b4.input.rms, 0.0001f)
        assertEquals(0.23f, b4.input.peak, 0.0001f)
        assertEquals(2L, b4.input.clippedSamples)
    }

    @Test
    fun exportContainsBoundarySection() {
        val report = ImmersiveDiagnosticCapture(
            processorOn = true,
            durationSeconds = 1,
            startedAtMillis = 0L,
            samples = emptyList(),
            finalDiagnostics = ImmersiveAudioDiagnostics(),
        ).toText("test", "test", "test", 384)

        assertTrue(report.contains("B1–B7 SIGNAL BOUNDARIES"))
        assertTrue(report.contains("B6 AudioTrack enqueue"))
        assertTrue(report.contains("B7 Physical device output"))
    }

    @Test
    fun oldShortTelemetryPayloadDoesNotCrashNewFields() {
        val values = DoubleArray(41)
        values[38] = 384.0
        val d = ImmersiveAudioDiagnostics.fromNative(values)
        assertEquals(0f, d.brirRms, 0f)
        assertEquals(0f, d.brirNormalizationGain, 0f)
        assertEquals("Off / unavailable", d.backendLabel())
    }

    @Test
    fun allPhysicalPresetIdsAreRetained() {
        for (id in 0..12) assertEquals(id, ImmersiveRoomPreset.fromNative(id).nativeValue)
    }

    @Test
    fun controlsClampInvalidTargetsToEngineRanges() {
        val c = ImmersiveControls(azimuth = Float.NaN, elevation = 200f, distance = -3f,
            bassGainDb = 12f, bassWidth = Float.POSITIVE_INFINITY, outputGainDb = 12f).sanitized()
        assertEquals(0f, c.azimuth, 0f)
        assertEquals(90f, c.elevation, 0f)
        assertEquals(0.2f, c.distance, 0f)
        assertEquals(6f, c.bassGainDb, 0f)
        assertEquals(1f, c.bassWidth, 0f)
        assertEquals(0f, c.outputGainDb, 0f)
    }

    @Test
    fun legacyPresetMigrationAndFullControlRoundTrip() {
        val legacy = ImmersiveAudioPreset.fromJson(org.json.JSONObject("""{"name":"Old studio","roomPreset":2}"""))!!
        assertEquals(1f, legacy.toControls().distance, 0f)
        assertEquals(0f, legacy.toControls().azimuth, 0f)
        val controls = ImmersiveControls(enabled = true, azimuth = 65f, elevation = -25f,
            distance = 2.4f, bassWidth = 0.7f, bassGainDb = 3f, outputGainDb = -4f,
            roomPreset = ImmersiveRoomPreset.CAVE, carFader = -0.4f)
        val decoded = ImmersiveAudioPreset.fromJson(ImmersiveAudioPreset.fromControls("Cave", controls).toJson())!!
        assertEquals(controls, decoded.toControls())
    }

    @Test
    fun unifiedEngineReportRetainsSafetyAndMatrixMetrics() {
        val report = ImmersiveDiagnosticCapture(processorOn = true, durationSeconds = 1,
            startedAtMillis = 0, samples = emptyList(), finalDiagnostics = ImmersiveAudioDiagnostics(
                processorEnabled = true, activeBackend = 3, algorithmicLatencySamples = 210,
                brirReady = true, brirRms = 0.1f,
            )).toText("test", "test", "test", 384)
        assertTrue(report.contains("FrostSoulX unified convolution"))
        assertTrue(report.contains("Latency: 210 samples"))
        assertTrue(report.contains("Transfer matrix RMS="))
        assertTrue(report.contains("Safety detector peak="))
    }
}
