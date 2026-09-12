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

enum class ImmersiveRoomPreset(val nativeValue: Int, val label: String) {
    OFF(0, "Off"),
    SMALL_ROOM(1, "Small room"),
    STUDIO(2, "Studio"),
    CONCERT_HALL(3, "Concert hall"),
    CATHEDRAL(4, "Cathedral"),
    SUBWAY(5, "Subway"),
    ;

    companion object {
        fun fromNative(value: Int): ImmersiveRoomPreset =
            entries.firstOrNull { it.nativeValue == value } ?: STUDIO
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
    @Volatile private var roomPreset = ImmersiveRoomPreset.STUDIO
    @Volatile private var roomMix = 0.18f
    @Volatile private var reflectionAmount = 0.28f
    @Volatile private var reverbTimeSeconds = 1.35f
    @Volatile private var roomSize = 0.5f
    @Volatile private var dampening = 0.5f
    @Volatile private var stereoWidth = 0.5f

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
            setRoomPreset(roomPreset)
            setRoomMix(roomMix)
            setReflectionAmount(reflectionAmount)
            setReverbTimeSeconds(reverbTimeSeconds)
            setRoomSize(roomSize)
            setDampening(dampening)
            setStereoWidth(stereoWidth)
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

    fun setRoomPreset(value: ImmersiveRoomPreset) {
        roomPreset = value
        if (nativeHandle != 0L) nativeSetRoomPreset(nativeHandle, value.nativeValue)
    }

    fun setRoomMix(value: Float) {
        roomMix = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        if (nativeHandle != 0L) nativeSetRoomMix(nativeHandle, roomMix)
    }

    fun setReflectionAmount(value: Float) {
        reflectionAmount = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        if (nativeHandle != 0L) nativeSetReflectionAmount(nativeHandle, reflectionAmount)
    }

    fun setReverbTimeSeconds(value: Float) {
        reverbTimeSeconds = value.takeIf(Float::isFinite)?.coerceIn(0.2f, 8f) ?: 1.35f
        if (nativeHandle != 0L) nativeSetReverbTimeSeconds(nativeHandle, reverbTimeSeconds)
    }

    fun setRoomSize(value: Float) {
        roomSize = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
        if (nativeHandle != 0L) nativeSetRoomSize(nativeHandle, roomSize)
    }

    fun setDampening(value: Float) {
        dampening = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
        if (nativeHandle != 0L) nativeSetDampening(nativeHandle, dampening)
    }

    fun setStereoWidth(value: Float) {
        stereoWidth = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
        if (nativeHandle != 0L) nativeSetStereoWidth(nativeHandle, stereoWidth)
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
        @JvmStatic private external fun nativeSetRoomPreset(handle: Long, preset: Int)
        @JvmStatic private external fun nativeSetRoomMix(handle: Long, wetMix: Float)
        @JvmStatic private external fun nativeSetReflectionAmount(handle: Long, amount: Float)
        @JvmStatic private external fun nativeSetReverbTimeSeconds(handle: Long, seconds: Float)
        @JvmStatic private external fun nativeSetRoomSize(handle: Long, size: Float)
        @JvmStatic private external fun nativeSetDampening(handle: Long, dampening: Float)
        @JvmStatic private external fun nativeSetStereoWidth(handle: Long, width: Float)
        @JvmStatic private external fun nativeReadDiagnostics(handle: Long): DoubleArray?
        @JvmStatic private external fun nativeProcess(handle: Long, pcmBuffer: ByteBuffer, frames: Int, encoding: Int)
    }
}

object ImmersiveAudioRuntime {
    @Volatile private var processor: ImmersiveAudioProcessor? = null
    @Volatile private var transitionHandler: ((Boolean) -> Unit)? = null
    @Volatile private var enabled = false
    @Volatile private var intensity = 0.5f
    @Volatile private var roomPreset = ImmersiveRoomPreset.STUDIO
    @Volatile private var roomMix = 0.18f
    @Volatile private var reflectionAmount = 0.28f
    @Volatile private var reverbTimeSeconds = 1.35f
    @Volatile private var roomSize = 0.5f
    @Volatile private var dampening = 0.5f
    @Volatile private var stereoWidth = 0.5f

    fun attach(value: ImmersiveAudioProcessor) {
        processor = value
        value.setIntensity(intensity)
        value.setRoomPreset(roomPreset)
        value.setRoomMix(roomMix)
        value.setReflectionAmount(reflectionAmount)
        value.setReverbTimeSeconds(reverbTimeSeconds)
        value.setRoomSize(roomSize)
        value.setDampening(dampening)
        value.setStereoWidth(stereoWidth)
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

    fun setRoomPreset(value: ImmersiveRoomPreset) {
        roomPreset = value
        processor?.setRoomPreset(value)
    }

    fun setRoomMix(value: Float) {
        roomMix = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        processor?.setRoomMix(roomMix)
    }

    fun setReflectionAmount(value: Float) {
        reflectionAmount = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        processor?.setReflectionAmount(reflectionAmount)
    }

    fun setReverbTimeSeconds(value: Float) {
        reverbTimeSeconds = value.takeIf(Float::isFinite)?.coerceIn(0.2f, 8f) ?: 1.35f
        processor?.setReverbTimeSeconds(reverbTimeSeconds)
    }

    fun setRoomSize(value: Float) {
        roomSize = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
        processor?.setRoomSize(roomSize)
    }

    fun setDampening(value: Float) {
        dampening = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
        processor?.setDampening(dampening)
    }

    fun setStereoWidth(value: Float) {
        stereoWidth = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
        processor?.setStereoWidth(stereoWidth)
    }

    fun readDiagnostics(): ImmersiveAudioDiagnostics = processor?.readDiagnostics() ?: ImmersiveAudioDiagnostics()

    fun isEnabled(): Boolean = enabled
    fun intensity(): Float = intensity
    fun roomPreset(): ImmersiveRoomPreset = roomPreset
    fun roomMix(): Float = roomMix
    fun reflectionAmount(): Float = reflectionAmount
    fun reverbTimeSeconds(): Float = reverbTimeSeconds
    fun roomSize(): Float = roomSize
    fun dampening(): Float = dampening
    fun stereoWidth(): Float = stereoWidth
}
