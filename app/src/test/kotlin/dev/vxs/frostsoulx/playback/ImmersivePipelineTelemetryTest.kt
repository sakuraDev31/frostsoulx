package dev.vxs.frostsoulx.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavImpulseResponseTest {
    private fun wav(channels: Int, frames: Int = 1, sample: Float = 0.25f): ByteArray {
        val dataSize = channels * frames * 4
        val buffer = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(3.toShort()).putShort(channels.toShort())
        buffer.putInt(48000).putInt(48000 * channels * 4).putShort((channels * 4).toShort()).putShort(32.toShort())
        buffer.put("data".toByteArray()).putInt(dataSize)
        repeat(frames) { repeat(channels) { channel -> buffer.putFloat(sample * (channel + 1)) } }
        return buffer.array()
    }

    @Test
    fun fourPathWavKeepsAllIndependentTransfers() {
        val ir = WavImpulseResponse.decode(wav(4))
        val paths = ir.resampledMatrix(48000, 32768)
        for (channel in 0..3) assertEquals(0.25f * (channel + 1), paths[channel][0], 0f)
        assertEquals(1, paths[0].size)
    }

    @Test
    fun monoAndStereoWavKeepCrossTransfersSilent() {
        for (channels in 1..2) {
            val paths = WavImpulseResponse.decode(wav(channels)).resampledMatrix(48000, 32768)
            assertEquals(0.25f, paths[0][0], 0f)
            assertEquals(0f, paths[1][0], 0f)
            assertEquals(0f, paths[2][0], 0f)
            assertEquals(if (channels == 1) 0.25f else 0.5f, paths[3][0], 0f)
        }
    }

    @Test
    fun resamplingPreservesAllFourPathsAndCapsLength() {
        val ir = WavImpulseResponse.decode(wav(4, frames = 4))
        val paths = ir.resampledMatrix(96000, 6)
        for (channel in 0..3) {
            assertEquals(6, paths[channel].size)
            assertEquals(0.25f * (channel + 1), paths[channel].last(), 0f)
        }
    }

    @Test
    fun rejectsNonFiniteTruncatedAndUnsupportedWavs() {
        val invalid = listOf(wav(4, sample = Float.NaN), wav(3), wav(2).copyOf(45), wav(1, frames = 32769))
        for (bytes in invalid) assertTrue(runCatching { WavImpulseResponse.decode(bytes) }.isFailure)
    }
}

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
        val decoded = ImmersiveAudioPreset.fromJson(ImmersiveAudioPreset.fromControls("Cave", controls.copy(orbitEnabled = true), "test-ir-id").toJson())!!
        assertEquals(controls.copy(orbitEnabled = true), decoded.toControls())
        assertEquals("test-ir-id", decoded.customIrPresetId)
        assertEquals("", legacy.customIrPresetId)
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
