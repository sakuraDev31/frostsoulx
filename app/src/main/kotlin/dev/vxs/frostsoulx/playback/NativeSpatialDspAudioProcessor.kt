package dev.vxs.frostsoulx.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 adapter for the connected FrostSoulX native sound-field DSP.
 * The processor is inactive by default, so existing playback remains bit-for-bit untouched
 * until the user enables the mic/DSP control.
 */
class NativeSpatialDspAudioProcessor : AudioProcessor {
    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false
    private var nativeHandle = 0L
    private var enabled = false
    private var preset = Preset.NATURAL
    private var parameters = Parameters()

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            this.inputAudioFormat = inputAudioFormat
            outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (this.inputAudioFormat != inputAudioFormat) {
            releaseNative()
            nativeHandle = nativeCreate(inputAudioFormat.sampleRate)
            this.inputAudioFormat = inputAudioFormat
            setPreset(preset)
            setParameters(parameters)
            setEnabled(enabled)
        }
        outputAudioFormat = inputAudioFormat
        return outputAudioFormat
    }

    // Keep the processor active once configured so the UI can toggle DSP at runtime without
    // forcing a player rebuild. Disabled mode is a zero-cost native bypass.
    override fun isActive(): Boolean = nativeHandle != 0L

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.remaining() == 0) return
        val frameBytes = inputBuffer.remaining()
        if (enabled && nativeHandle != 0L && inputBuffer.isDirect && frameBytes >= BYTES_PER_FRAME) {
            nativeProcess(nativeHandle, inputBuffer, frameBytes / BYTES_PER_FRAME)
        }
        // Preserve the readable range before consuming the input buffer. Assigning the original
        // buffer and then advancing its position makes getOutput() appear empty and mutes audio.
        outputBuffer = inputBuffer.slice().order(inputBuffer.order())
        inputBuffer.position(inputBuffer.limit())
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

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

    fun setPreset(value: Preset) {
        preset = value
        if (nativeHandle != 0L) nativeSetPreset(nativeHandle, value.nativeValue)
    }

    fun setParameters(value: Parameters) {
        parameters = value
        if (nativeHandle != 0L) {
            nativeSetParameters(
                nativeHandle,
                value.intensity,
                value.width,
                value.crossfeed,
                value.lowFrequencyProtection,
                value.surround,
                value.reverbMix,
                value.reverbRoomSize,
                value.reverbDecay,
                value.outputGainDb,
                value.limiterCeilingDb,
            )
        }
    }

    private fun releaseNative() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    data class Parameters(
        val intensity: Float = 0.5f,
        val width: Float = 1.0f,
        val crossfeed: Float = 0.0f,
        val lowFrequencyProtection: Float = 0.85f,
        val surround: Float = 0.0f,
        val reverbMix: Float = 0.0f,
        val reverbRoomSize: Float = 0.5f,
        val reverbDecay: Float = 0.45f,
        val outputGainDb: Float = 0.0f,
        val limiterCeilingDb: Float = -1.0f,
    )

    enum class Preset(val nativeValue: Int) {
        NATURAL(0), LIVE(1), WIDE(2), IMMERSIVE(3), CUSTOM(4)
    }

    companion object {
        private const val BYTES_PER_FRAME = 4
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        init {
            System.loadLibrary("frostsoulx_dsp_jni")
        }

        @JvmStatic private external fun nativeCreate(sampleRate: Int): Long
        @JvmStatic private external fun nativeRelease(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeSetEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetPreset(handle: Long, preset: Int)
        @JvmStatic private external fun nativeSetParameters(
            handle: Long,
            intensity: Float,
            width: Float,
            crossfeed: Float,
            lowFrequencyProtection: Float,
            surround: Float,
            reverbMix: Float,
            reverbRoomSize: Float,
            reverbDecay: Float,
            outputGainDb: Float,
            limiterCeilingDb: Float,
        )
        @JvmStatic private external fun nativeProcess(handle: Long, pcmBuffer: ByteBuffer, frames: Int)
    }
}


/** Shared bridge used by both full-player layouts; the service remains the DSP owner. */
object NativeSpatialDspRuntime {
    @Volatile private var processor: NativeSpatialDspAudioProcessor? = null
    @Volatile private var enabled = false
    @Volatile private var preset = NativeSpatialDspAudioProcessor.Preset.NATURAL
    @Volatile private var parameters = NativeSpatialDspAudioProcessor.Parameters()

    fun attach(value: NativeSpatialDspAudioProcessor) {
        processor = value
        value.setPreset(preset)
        value.setParameters(parameters)
        value.setEnabled(enabled)
    }

    fun detach(value: NativeSpatialDspAudioProcessor) {
        if (processor === value) processor = null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        processor?.setEnabled(value)
    }

    fun setPreset(value: NativeSpatialDspAudioProcessor.Preset) {
        preset = value
        processor?.setPreset(value)
    }

    fun setParameters(value: NativeSpatialDspAudioProcessor.Parameters) {
        parameters = value
        processor?.setParameters(value)
    }
}
