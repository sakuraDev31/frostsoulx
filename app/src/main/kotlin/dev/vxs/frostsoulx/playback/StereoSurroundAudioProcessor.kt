package dev.vxs.frostsoulx.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 adapter for the standalone V1 stereo-surround processor.
 * Disabled mode is a true bypass: the native processor is not called and the PCM buffer is untouched.
 */
class StereoSurroundAudioProcessor : AudioProcessor {
    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false
    private var nativeHandle = 0L
    @Volatile private var enabled = false
    @Volatile private var intensity = 0.0f

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
            setIntensity(intensity)
            setEnabled(enabled)
        }
        outputAudioFormat = inputAudioFormat
        return outputAudioFormat
    }

    override fun isActive(): Boolean = nativeHandle != 0L

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val readableBuffer = inputBuffer.slice().order(inputBuffer.order())
        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * 2
        val frames = readableBuffer.remaining() / frameBytes
        if (nativeHandle != 0L && readableBuffer.isDirect && frames > 0) {
            nativeProcess(nativeHandle, readableBuffer, frames, inputAudioFormat.encoding)
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

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        if (nativeHandle != 0L) nativeSetIntensity(nativeHandle, intensity)
    }

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
        @JvmStatic private external fun nativeProcess(handle: Long, pcmBuffer: ByteBuffer, frames: Int, encoding: Int)
    }
}

/** Service-owned runtime state shared by the quick toggle and the full-screen controls. */
object StereoSurroundRuntime {
    @Volatile private var processor: StereoSurroundAudioProcessor? = null
    @Volatile private var enabled = false
    @Volatile private var intensity = 0.5f

    fun attach(value: StereoSurroundAudioProcessor) {
        processor = value
        value.setIntensity(intensity)
        value.setEnabled(enabled)
    }

    fun detach(value: StereoSurroundAudioProcessor) {
        if (processor === value) processor = null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        processor?.setEnabled(value)
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        processor?.setIntensity(intensity)
    }

    fun isEnabled(): Boolean = enabled
    fun intensity(): Float = intensity
}
