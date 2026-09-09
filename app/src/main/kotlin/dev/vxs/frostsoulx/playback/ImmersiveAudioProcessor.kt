package dev.vxs.frostsoulx.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ImmersiveAudioDiagnostics(
    val inputRms: Float = 0f,
    val outputRms: Float = 0f,
    val inputPeak: Float = 0f,
    val outputPeak: Float = 0f,
    val maxAbsDifference: Float = 0f,
    val changedPercentage: Float = 0f,
    val nanCount: Long = 0L,
    val infCount: Long = 0L,
    val processCallCount: Long = 0L,
    val processedFrames: Long = 0L,
    val nativeStatus: Int = 0,
) {
    companion object {
        fun fromNative(values: DoubleArray?): ImmersiveAudioDiagnostics {
            if (values == null || values.size < 9) return ImmersiveAudioDiagnostics()
            return ImmersiveAudioDiagnostics(
                inputRms = values[0].toFloat().takeIf(Float::isFinite) ?: 0f,
                outputRms = values[1].toFloat().takeIf(Float::isFinite) ?: 0f,
                inputPeak = values[2].toFloat().takeIf(Float::isFinite) ?: 0f,
                outputPeak = values[3].toFloat().takeIf(Float::isFinite) ?: 0f,
                maxAbsDifference = values[4].toFloat().takeIf(Float::isFinite) ?: 0f,
                changedPercentage = values[5].toFloat().takeIf(Float::isFinite) ?: 0f,
                nanCount = values[6].toLong().coerceAtLeast(0L),
                infCount = values[7].toLong().coerceAtLeast(0L),
                processCallCount = values[8].toLong().coerceAtLeast(0L),
                processedFrames = values.getOrNull(9)?.toLong()?.coerceAtLeast(0L) ?: 0L,
                nativeStatus = values.getOrNull(10)?.toInt() ?: 0,
            )
        }
    }
}

/** Media3 adapter for the Steam Audio HRTF binaural engine. */
class ImmersiveAudioProcessor : AudioProcessor {
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
        val inputBytes = inputBuffer.remaining()
        val readableBuffer = prepareOutputBuffer(inputBytes)
        readableBuffer.put(inputBuffer)
        readableBuffer.flip()
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
        if (nativeHandle != 0L) nativeSetSpatialBlend(nativeHandle, intensity)
    }

    fun readDiagnostics(): ImmersiveAudioDiagnostics =
        if (nativeHandle == 0L) ImmersiveAudioDiagnostics() else ImmersiveAudioDiagnostics.fromNative(nativeReadDiagnostics(nativeHandle))

    private fun releaseNative() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        init {
            System.loadLibrary("frostsoulx_immersive_jni")
        }

        @JvmStatic private external fun nativeCreate(sampleRate: Int, encoding: Int): Long
        @JvmStatic private external fun nativeRelease(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeSetEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetSpatialBlend(handle: Long, blend: Float)
        @JvmStatic private external fun nativeReadDiagnostics(handle: Long): DoubleArray?
        @JvmStatic private external fun nativeProcess(handle: Long, pcmBuffer: ByteBuffer, frames: Int, encoding: Int)
    }
}

object ImmersiveAudioRuntime {
    @Volatile private var processor: ImmersiveAudioProcessor? = null
    @Volatile private var transitionHandler: ((Boolean) -> Unit)? = null
    @Volatile private var enabled = false
    @Volatile private var intensity = 0.5f

    fun attach(value: ImmersiveAudioProcessor) {
        processor = value
        value.setIntensity(intensity)
        value.setEnabled(enabled)
    }

    fun detachProcessor() {
        processor = null
    }

    fun detach() {
        processor = null
        transitionHandler = null
    }

    fun setTransitionHandler(handler: ((Boolean) -> Unit)?) {
        transitionHandler = handler
    }

    fun setEnabled(value: Boolean) {
        val changed = enabled != value
        enabled = value
        processor?.setEnabled(value)
        if (changed) transitionHandler?.invoke(value)
    }

    fun setIntensity(value: Float) {
        intensity = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        processor?.setIntensity(intensity)
    }

    fun readDiagnostics(): ImmersiveAudioDiagnostics = processor?.readDiagnostics() ?: ImmersiveAudioDiagnostics()

    fun isEnabled(): Boolean = enabled
    fun intensity(): Float = intensity
}
