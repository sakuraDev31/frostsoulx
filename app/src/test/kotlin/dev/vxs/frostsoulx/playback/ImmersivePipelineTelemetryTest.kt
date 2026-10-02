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
}
