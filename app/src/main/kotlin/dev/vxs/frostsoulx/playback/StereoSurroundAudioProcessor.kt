package dev.vxs.frostsoulx.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class StereoSurroundTuningParameters(
    val lowFrequencyCutoffHz: Float = 180f,
    val sideExtractionGain: Float = 0.5f,
    val delayASamples: Int = 37,
    val delayBSamples: Int = 59,
    val decorrelationAInputCoefficient: Float = 0.28f,
    val decorrelationBInputCoefficient: Float = 0.32f,
    val sideHighMixBase: Float = 0.35f,
    val sideHighMixIntensitySpan: Float = 0.45f,
    val ambienceDecorrelatedAWeight: Float = 0.55f,
    val rearAmbienceWeight: Float = 0.45f,
    val maxRearContribution: Float = 0.22f,
) {
    fun validated(): StereoSurroundTuningParameters = copy(
        lowFrequencyCutoffHz = lowFrequencyCutoffHz.takeIf(Float::isFinite)?.coerceIn(20f, 2000f) ?: 180f,
        sideExtractionGain = sideExtractionGain.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
        delayASamples = delayASamples.coerceIn(1, 255),
        delayBSamples = delayBSamples.coerceIn(1, 255),
        decorrelationAInputCoefficient = decorrelationAInputCoefficient.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.28f,
        decorrelationBInputCoefficient = decorrelationBInputCoefficient.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.32f,
        sideHighMixBase = sideHighMixBase.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.35f,
        sideHighMixIntensitySpan = sideHighMixIntensitySpan.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.45f,
        ambienceDecorrelatedAWeight = ambienceDecorrelatedAWeight.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.55f,
        rearAmbienceWeight = rearAmbienceWeight.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.45f,
        maxRearContribution = maxRearContribution.takeIf(Float::isFinite)?.coerceIn(0f, 0.5f) ?: 0.22f,
    )

    companion object {
        val DEFAULT = StereoSurroundTuningParameters()
    }
}

data class StereoSurroundDiagnostics(
    val inputRms: Float = 0f,
    val outputRms: Float = 0f,
    val inputPeak: Float = 0f,
    val outputPeak: Float = 0f,
    val maxAbsDifference: Float = 0f,
    val changedPercentage: Float = 0f,
    val nanCount: Long = 0L,
    val infCount: Long = 0L,
    val processCallCount: Long = 0L,
) {
    companion object {
        fun fromNative(values: DoubleArray?): StereoSurroundDiagnostics {
            if (values == null || values.size < 9) return StereoSurroundDiagnostics()
            return StereoSurroundDiagnostics(
                inputRms = values[0].toFloat().takeIf(Float::isFinite) ?: 0f,
                outputRms = values[1].toFloat().takeIf(Float::isFinite) ?: 0f,
                inputPeak = values[2].toFloat().takeIf(Float::isFinite) ?: 0f,
                outputPeak = values[3].toFloat().takeIf(Float::isFinite) ?: 0f,
                maxAbsDifference = values[4].toFloat().takeIf(Float::isFinite) ?: 0f,
                changedPercentage = values[5].toFloat().takeIf(Float::isFinite) ?: 0f,
                nanCount = values[6].toLong().coerceAtLeast(0L),
                infCount = values[7].toLong().coerceAtLeast(0L),
                processCallCount = values[8].toLong().coerceAtLeast(0L),
            )
        }
    }
}

/**
 * Media3 adapter for the standalone V1 stereo-surround processor.
 * Disabled mode is a content-preserving bypass: the native processor is not called and the
 * PCM bytes are copied unchanged into a dedicated output buffer owned by this processor.
 */
class StereoSurroundAudioProcessor : AudioProcessor {
    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false
    private var nativeHandle = 0L
    @Volatile private var enabled = false
    @Volatile private var intensity = 0.0f
    @Volatile private var tuning = StereoSurroundTuningParameters.DEFAULT

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supportedEncoding =
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supportedEncoding || inputAudioFormat.channelCount != 2) {
            this.inputAudioFormat = inputAudioFormat
            outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (this.inputAudioFormat != inputAudioFormat) {
            releaseNative()
            nativeHandle = nativeCreate(inputAudioFormat.sampleRate, inputAudioFormat.encoding)
            this.inputAudioFormat = inputAudioFormat
            setTuning(tuning)
            setIntensity(intensity)
            setEnabled(enabled)
        }
        outputAudioFormat = inputAudioFormat
        return outputAudioFormat
    }

    override fun isActive(): Boolean = nativeHandle != 0L

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val inputBytes = inputBuffer.remaining()
        val readableBuffer = prepareOutputBuffer(inputBytes)
        // Copy the original PCM bytes exactly before any optional processing. This avoids
        // returning a view into Media3's input buffer, which the sink may recycle immediately.
        readableBuffer.put(inputBuffer)
        readableBuffer.flip()

        // Strict OFF bypass: the output bytes are identical to the input bytes and the
        // native integration is not called at all. With the processor enabled at intensity
        // zero, JNI may collect identity diagnostics but never changes the PCM bytes.
        if (!enabled) return

        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * 2
        val frames = readableBuffer.remaining() / frameBytes
        if (nativeHandle != 0L && frames > 0) {
            nativeProcess(nativeHandle, readableBuffer, frames, inputAudioFormat.encoding)
        }
    }

    private fun prepareOutputBuffer(byteCount: Int): ByteBuffer {
        if (outputBuffer.capacity() < byteCount) {
            outputBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        outputBuffer.limit(byteCount)
        return outputBuffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer = outputBuffer

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        if (nativeHandle != 0L) nativeReset(nativeHandle)
    }

    override fun reset() {
        flush()
        releaseNative()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (nativeHandle != 0L) nativeSetEnabled(nativeHandle, value)
    }

    fun setIntensity(value: Float) {
        intensity = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        if (nativeHandle != 0L) nativeSetIntensity(nativeHandle, intensity)
    }

    fun setTuning(value: StereoSurroundTuningParameters) {
        tuning = value.validated()
        if (nativeHandle != 0L) {
            nativeSetParameters(
                nativeHandle,
                tuning.lowFrequencyCutoffHz,
                tuning.sideExtractionGain,
                tuning.delayASamples,
                tuning.delayBSamples,
                tuning.decorrelationAInputCoefficient,
                tuning.decorrelationBInputCoefficient,
                tuning.sideHighMixBase,
                tuning.sideHighMixIntensitySpan,
                tuning.ambienceDecorrelatedAWeight,
                tuning.rearAmbienceWeight,
                tuning.maxRearContribution,
            )
        }
    }

    fun readDiagnostics(): StereoSurroundDiagnostics =
        if (nativeHandle == 0L) StereoSurroundDiagnostics() else StereoSurroundDiagnostics.fromNative(nativeReadDiagnostics(nativeHandle))

    private fun releaseNative() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        init {
            System.loadLibrary("frostsoulx_surround_jni")
        }

        @JvmStatic private external fun nativeCreate(sampleRate: Int, encoding: Int): Long
        @JvmStatic private external fun nativeRelease(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeSetEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetIntensity(handle: Long, intensity: Float)
        @JvmStatic private external fun nativeSetParameters(
            handle: Long,
            lowCutoffHz: Float,
            sideGain: Float,
            delayA: Int,
            delayB: Int,
            decorA: Float,
            decorB: Float,
            sideBase: Float,
            sideSpan: Float,
            ambienceAWeight: Float,
            rearAmbienceWeight: Float,
            maxRearContribution: Float,
        )
        @JvmStatic private external fun nativeReadDiagnostics(handle: Long): DoubleArray?
        @JvmStatic private external fun nativeProcess(handle: Long, pcmBuffer: ByteBuffer, frames: Int, encoding: Int)
    }
}

/** Service-owned runtime state shared by the quick toggle, production controls, and dev panel. */
object StereoSurroundRuntime {
    @Volatile private var processor: StereoSurroundAudioProcessor? = null
    @Volatile private var enabled = false
    @Volatile private var intensity = 0.5f
    @Volatile private var tuning = StereoSurroundTuningParameters.DEFAULT

    fun attach(value: StereoSurroundAudioProcessor) {
        processor = value
        value.setTuning(tuning)
        value.setIntensity(intensity)
        value.setEnabled(enabled)
    }

    fun detach() {
        processor = null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        processor?.setEnabled(value)
    }

    fun setIntensity(value: Float) {
        intensity = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        processor?.setIntensity(intensity)
    }

    fun setTuning(value: StereoSurroundTuningParameters) {
        tuning = value.validated()
        processor?.setTuning(tuning)
    }

    fun readDiagnostics(): StereoSurroundDiagnostics = processor?.readDiagnostics() ?: StereoSurroundDiagnostics()

    fun isEnabled(): Boolean = enabled
    fun intensity(): Float = intensity
    fun tuning(): StereoSurroundTuningParameters = tuning
}
