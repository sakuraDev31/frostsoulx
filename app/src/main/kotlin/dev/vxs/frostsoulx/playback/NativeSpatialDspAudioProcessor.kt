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
    @Volatile private var enabled = false
    @Volatile private var surroundEnabled = false
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
            setSurroundEnabled(surroundEnabled)
        }
        outputAudioFormat = inputAudioFormat
        return outputAudioFormat
    }

    // Keep the processor active once configured so the UI can toggle DSP at runtime without
    // forcing a player rebuild. Disabled mode is a zero-cost native bypass.
    override fun isActive(): Boolean = nativeHandle != 0L

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.remaining() == 0) return
        // JNI GetDirectBufferAddress points at the direct buffer base, not the current
        // ByteBuffer position. Always pass a zero-based readable slice to native code;
        // otherwise Media3 buffers with a non-zero position can be processed/written at
        // the wrong PCM region, producing corruption, clicks, or apparent clipping.
        val readableBuffer = inputBuffer.slice().order(inputBuffer.order())
        val frameBytes = readableBuffer.remaining()
        if (nativeHandle != 0L && readableBuffer.isDirect && frameBytes >= BYTES_PER_FRAME) {
            // Native process() returns immediately when DSP is disabled, leaving the
            // buffer bit-for-bit untouched. The JNI boundary only applies a per-sample
            // safety clamp to [-1, 1] — no block-level rescale — so normal loud/mastered
            // audio isn't gain-stepped on every ~23ms chunk.
            nativeProcess(nativeHandle, readableBuffer, frameBytes / BYTES_PER_FRAME)
        }
        outputBuffer = readableBuffer
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

    fun setSurroundEnabled(value: Boolean) {
        surroundEnabled = value
        if (nativeHandle != 0L) nativeSetSurroundEnabled(nativeHandle, value)
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
                value.hrtfEnabled,
                value.hrtfMix,
                value.hrtfAzimuth,
                value.hrtfElevation,
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
        val hrtfEnabled: Boolean = false,
        val hrtfMix: Float = 0.85f,
        val hrtfAzimuth: Float = 0.0f,
        val hrtfElevation: Float = 0.0f,
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
        @JvmStatic private external fun nativeSetSurroundEnabled(handle: Long, enabled: Boolean)
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
            hrtfEnabled: Boolean,
            hrtfMix: Float,
            hrtfAzimuth: Float,
            hrtfElevation: Float,
        )
        @JvmStatic private external fun nativeProcess(handle: Long, pcmBuffer: ByteBuffer, frames: Int)
    }
}


/** Shared bridge used by both full-player layouts; the service remains the DSP owner. */
object NativeSpatialDspRuntime {
    @Volatile private var processor: NativeSpatialDspAudioProcessor? = null
    @Volatile private var enabled = false
    @Volatile private var surroundEnabled = false
    @Volatile private var preset = NativeSpatialDspAudioProcessor.Preset.NATURAL
    @Volatile private var parameters = NativeSpatialDspAudioProcessor.Parameters()

    fun attach(value: NativeSpatialDspAudioProcessor) {
        processor = value
        value.setPreset(preset)
        value.setParameters(parameters)
        value.setEnabled(enabled)
        value.setSurroundEnabled(surroundEnabled)
    }

    fun detach(value: NativeSpatialDspAudioProcessor) {
        if (processor === value) processor = null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        processor?.setEnabled(value)
    }

    fun setSurroundEnabled(value: Boolean) {
        surroundEnabled = value
        processor?.setSurroundEnabled(value)
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
