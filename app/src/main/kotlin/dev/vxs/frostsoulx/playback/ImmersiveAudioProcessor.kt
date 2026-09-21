package dev.vxs.frostsoulx.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ImmersiveAudioDiagnostics(
    val inputRmsL: Float = 0f,
    val inputRmsR: Float = 0f,
    val outputRmsL: Float = 0f,
    val outputRmsR: Float = 0f,
    val inputPeakL: Float = 0f,
    val inputPeakR: Float = 0f,
    val outputPeakL: Float = 0f,
    val outputPeakR: Float = 0f,
    val inputTruePeakL: Float = 0f,
    val inputTruePeakR: Float = 0f,
    val outputTruePeakL: Float = 0f,
    val outputTruePeakR: Float = 0f,
    val inputMinL: Float = 0f,
    val inputMinR: Float = 0f,
    val inputMaxL: Float = 0f,
    val inputMaxR: Float = 0f,
    val outputMinL: Float = 0f,
    val outputMinR: Float = 0f,
    val outputMaxL: Float = 0f,
    val outputMaxR: Float = 0f,
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
    val averageAbsDifference: Float = 0f,
    val clippedInput: Long = 0L,
    val clippedOutput: Long = 0L,
    val totalBlocks: Long = 0L,
    val processingTimeMs: Double = 0.0,
    val averageProcessingTimeMs: Double = 0.0,
    val maxProcessingTimeMs: Double = 0.0,
    val deadlineMisses: Long = 0L,
    val nativeProcessFailures: Long = 0L,
    val sampleRate: Int = 0,
    val hostCallbackFrames: Int = 0,
    val quantumFrames: Int = 384,
    val processorEnabled: Boolean = false,
    val pcmEncoding: Int = 0,
) {
    fun truePeakWarningSource(): String {
        val inputL = inputTruePeakL > TRUE_PEAK_WARNING_LIMIT
        val inputR = inputTruePeakR > TRUE_PEAK_WARNING_LIMIT
        val outputL = outputTruePeakL > TRUE_PEAK_WARNING_LIMIT
        val outputR = outputTruePeakR > TRUE_PEAK_WARNING_LIMIT
        val inputChannels = buildList {
            if (inputL) add("L")
            if (inputR) add("R")
        }
        val outputChannels = buildList {
            if (outputL) add("L")
            if (outputR) add("R")
        }
        return when {
            inputChannels.isNotEmpty() && outputChannels.isNotEmpty() ->
                "BOTH (input ${inputChannels.joinToString("/")}, output ${outputChannels.joinToString("/")})"
            inputChannels.isNotEmpty() -> "INPUT (${inputChannels.joinToString("/")})"
            outputChannels.isNotEmpty() -> "OUTPUT (${outputChannels.joinToString("/")})"
            else -> "NONE"
        }
    }

    fun clippingSource(): String = when {
        clippedInput > 0L && clippedOutput > 0L -> "BOTH (input $clippedInput, output $clippedOutput samples)"
        clippedInput > 0L -> "INPUT ($clippedInput samples at native DSP input)"
        clippedOutput > 0L -> "OUTPUT ($clippedOutput samples after native DSP)"
        else -> "NONE"
    }

    companion object {
        const val TRUE_PEAK_WARNING_LIMIT = 0.988553f

        fun fromNative(values: DoubleArray?): ImmersiveAudioDiagnostics {
            if (values == null || values.size < 41) return ImmersiveAudioDiagnostics()
            fun f(index: Int): Float = values[index].toFloat().takeIf(Float::isFinite) ?: 0f
            fun l(index: Int): Long = values[index].toLong().coerceAtLeast(0L)
            return ImmersiveAudioDiagnostics(
                inputRmsL = f(0), inputRmsR = f(1), outputRmsL = f(2), outputRmsR = f(3),
                inputPeakL = f(4), inputPeakR = f(5), outputPeakL = f(6), outputPeakR = f(7),
                inputTruePeakL = f(8), inputTruePeakR = f(9), outputTruePeakL = f(10), outputTruePeakR = f(11),
                inputMinL = f(12), inputMinR = f(13), inputMaxL = f(14), inputMaxR = f(15),
                outputMinL = f(16), outputMinR = f(17), outputMaxL = f(18), outputMaxR = f(19),
                inputRms = (f(0) + f(1)) / 2f, outputRms = (f(2) + f(3)) / 2f,
                inputPeak = maxOf(f(4), f(5)), outputPeak = maxOf(f(6), f(7)),
                maxAbsDifference = f(20), changedPercentage = f(22),
                nanCount = l(23), infCount = l(24), processCallCount = l(27), processedFrames = l(28), nativeStatus = values[29].toInt(),
                averageAbsDifference = f(21), clippedInput = l(25), clippedOutput = l(26), totalBlocks = l(30),
                processingTimeMs = values[31].takeIf(Double::isFinite) ?: 0.0,
                averageProcessingTimeMs = values[32].takeIf(Double::isFinite) ?: 0.0,
                maxProcessingTimeMs = values[33].takeIf(Double::isFinite) ?: 0.0,
                deadlineMisses = l(34), nativeProcessFailures = l(35), sampleRate = values[36].toInt().coerceAtLeast(0),
                hostCallbackFrames = values[37].toInt().coerceAtLeast(0), quantumFrames = values[38].toInt().coerceIn(96, 2048),
                processorEnabled = values[39] > 0.5,
                pcmEncoding = values[40].toInt(),
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
    @Volatile private var quantumFrames = DEFAULT_QUANTUM_FRAMES

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
            setQuantumFrames(quantumFrames)
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

    fun setQuantumFrames(value: Int) {
        quantumFrames = value.coerceIn(MIN_QUANTUM_FRAMES, MAX_QUANTUM_FRAMES)
        if (nativeHandle != 0L) nativeSetQuantumFrames(nativeHandle, quantumFrames)
    }

    fun quantumFrames(): Int = quantumFrames

    fun readDiagnostics(): ImmersiveAudioDiagnostics =
        if (nativeHandle == 0L) ImmersiveAudioDiagnostics() else ImmersiveAudioDiagnostics.fromNative(nativeReadDiagnostics(nativeHandle))

    fun resetDiagnostics() {
        if (nativeHandle != 0L) nativeResetDiagnostics(nativeHandle)
    }

    private fun releaseNative() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    companion object {
        const val DEFAULT_QUANTUM_FRAMES = 384
        const val MIN_QUANTUM_FRAMES = 96
        const val MAX_QUANTUM_FRAMES = 2048
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        init {
            System.loadLibrary("frostsoulx_immersive_jni")
        }

        @JvmStatic private external fun nativeCreate(sampleRate: Int, encoding: Int): Long
        @JvmStatic private external fun nativeRelease(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeResetDiagnostics(handle: Long)
        @JvmStatic private external fun nativeSetEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetSpatialBlend(handle: Long, blend: Float)
        @JvmStatic private external fun nativeSetRoomPreset(handle: Long, preset: Int)
        @JvmStatic private external fun nativeSetRoomMix(handle: Long, wetMix: Float)
        @JvmStatic private external fun nativeSetReflectionAmount(handle: Long, amount: Float)
        @JvmStatic private external fun nativeSetReverbTimeSeconds(handle: Long, seconds: Float)
        @JvmStatic private external fun nativeSetRoomSize(handle: Long, size: Float)
        @JvmStatic private external fun nativeSetDampening(handle: Long, dampening: Float)
        @JvmStatic private external fun nativeSetStereoWidth(handle: Long, width: Float)
        @JvmStatic private external fun nativeSetQuantumFrames(handle: Long, quantumFrames: Int)
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
    @Volatile private var quantumFrames = ImmersiveAudioProcessor.DEFAULT_QUANTUM_FRAMES

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
        value.setQuantumFrames(quantumFrames)
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
    fun setQuantumFrames(value: Int) {
        quantumFrames = value.coerceIn(ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES, ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES)
        processor?.setQuantumFrames(quantumFrames)
    }

    fun readDiagnostics(): ImmersiveAudioDiagnostics = processor?.readDiagnostics() ?: ImmersiveAudioDiagnostics()
    fun resetDiagnostics() { processor?.resetDiagnostics() }

    fun isEnabled(): Boolean = enabled
    fun intensity(): Float = intensity
    fun roomPreset(): ImmersiveRoomPreset = roomPreset
    fun roomMix(): Float = roomMix
    fun reflectionAmount(): Float = reflectionAmount
    fun reverbTimeSeconds(): Float = reverbTimeSeconds
    fun roomSize(): Float = roomSize
    fun dampening(): Float = dampening
    fun stereoWidth(): Float = stereoWidth
    fun quantumFrames(): Int = quantumFrames
}
