package dev.vxs.frostsoulx.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.DoubleAdder

data class ImmersiveStageDiagnostics(
    val available: Boolean = false,
    val rms: Float = 0f,
    val peak: Float = 0f,
    val truePeak: Float = 0f,
    val clippedSamples: Long = 0L,
    val nanCount: Long = 0L,
    val infCount: Long = 0L,
    val frames: Long = 0L,
    val sampleRate: Int = 0,
    val encoding: Int = 0,
)

data class ImmersivePipelineStageTelemetry(
    val id: String,
    val name: String,
    val available: Boolean,
    val input: ImmersiveStageDiagnostics = ImmersiveStageDiagnostics(),
    val output: ImmersiveStageDiagnostics = ImmersiveStageDiagnostics(),
    val processingTimeMs: Double? = null,
    val unavailableReason: String? = null,
)

private fun unavailableStage(id: String, name: String, reason: String) =
    ImmersivePipelineStageTelemetry(id, name, available = false, unavailableReason = reason)

private fun observedStage(id: String, name: String, output: ImmersiveStageDiagnostics) =
    ImmersivePipelineStageTelemetry(id, name, available = output.available, output = output)

/** Transparent Media3 processor used only to observe a real PCM boundary. */
class ImmersiveStageMeterAudioProcessor(private val stage: ImmersiveStageMeter) : AudioProcessor {
    private var format = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var ended = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        format = inputAudioFormat
        stage.configure(inputAudioFormat)
        return inputAudioFormat
    }

    override fun isActive(): Boolean = format != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val readable = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val byteCount = inputBuffer.remaining()
        if (outputBuffer.capacity() < byteCount) {
            outputBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        outputBuffer.limit(byteCount)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
        stage.observe(readable)
    }

    override fun queueEndOfStream() { ended = true }
    override fun getOutput(): ByteBuffer = outputBuffer
    override fun isEnded(): Boolean = ended && !outputBuffer.hasRemaining()
    override fun flush() { outputBuffer = EMPTY_BUFFER; ended = false; stage.reset() }
    override fun reset() { flush(); format = AudioProcessor.AudioFormat.NOT_SET; stage.reset() }

    private companion object {
        val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}

class ImmersiveStageMeter {
    private val sumSquares = DoubleAdder()
    private val peakBits = AtomicLong(java.lang.Float.floatToRawIntBits(0f).toLong())
    private val truePeakBits = AtomicLong(java.lang.Float.floatToRawIntBits(0f).toLong())
    private val clipped = AtomicLong(0L)
    private val nan = AtomicLong(0L)
    private val inf = AtomicLong(0L)
    private val frames = AtomicLong(0L)
    @Volatile private var sampleRate = 0
    @Volatile private var encoding = 0
    @Volatile private var previous = 0f

    fun configure(format: AudioProcessor.AudioFormat) {
        sampleRate = format.sampleRate
        encoding = format.encoding
    }

    fun observe(buffer: ByteBuffer) {
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_FLOAT -> 4
            C.ENCODING_PCM_16BIT -> 2
            else -> return
        }
        if (buffer.remaining() < bytesPerSample * 2) return
        val frameCount = buffer.remaining() / (bytesPerSample * 2)
        repeat(frameCount) {
            val left = readSample(buffer, bytesPerSample)
            val right = readSample(buffer, bytesPerSample)
            observeSample(left)
            observeSample(right)
            val interpolated = maxOf(kotlin.math.abs(previous), kotlin.math.abs((previous + left) * 0.5f))
            updateMax(truePeakBits, interpolated)
            previous = left
        }
        frames.addAndGet(frameCount.toLong())
    }

    private fun readSample(buffer: ByteBuffer, bytes: Int): Float = when (bytes) {
        4 -> buffer.float
        else -> buffer.short / 32768f
    }

    private fun observeSample(value: Float) {
        when {
            value.isNaN() -> nan.incrementAndGet()
            value.isInfinite() -> inf.incrementAndGet()
            else -> {
                sumSquares.add(value.toDouble() * value.toDouble())
                updateMax(peakBits, kotlin.math.abs(value))
                if (kotlin.math.abs(value) >= 1f) clipped.incrementAndGet()
            }
        }
    }

    private fun updateMax(target: AtomicLong, value: Float) {
        val bits = java.lang.Float.floatToRawIntBits(value).toLong()
        while (true) {
            val old = target.get()
            if (java.lang.Float.intBitsToFloat(old.toInt()) >= value || target.compareAndSet(old, bits)) return
        }
    }

    fun snapshot(): ImmersiveStageDiagnostics {
        val frameCount = frames.get()
        val sum = sumSquares.sum()
        return ImmersiveStageDiagnostics(
            available = frameCount > 0,
            rms = if (frameCount > 0) kotlin.math.sqrt(sum / (frameCount * 2.0)).toFloat() else 0f,
            peak = java.lang.Float.intBitsToFloat(peakBits.get().toInt()),
            truePeak = java.lang.Float.intBitsToFloat(truePeakBits.get().toInt()),
            clippedSamples = clipped.get(), nanCount = nan.get(), infCount = inf.get(),
            frames = frameCount, sampleRate = sampleRate, encoding = encoding,
        )
    }

    fun reset() {
        sumSquares.reset(); peakBits.set(0L); truePeakBits.set(0L)
        clipped.set(0L); nan.set(0L); inf.set(0L); frames.set(0L); previous = 0f
    }
}

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
    val activeBackend: Int = 0,
    val algorithmicLatencySamples: Int = 0,
    val brirReady: Boolean = false,
    val preLimiterTruePeak: Float = 0f,
    val limiterGainReductionDb: Float = 0f,
    val gainBudgetDb: Float = 0f,
    val preEngineRmsL: Float = 0f,
    val preEngineRmsR: Float = 0f,
    val preEnginePeakL: Float = 0f,
    val preEnginePeakR: Float = 0f,
    val preEngineTruePeakL: Float = 0f,
    val preEngineTruePeakR: Float = 0f,
    val preEngineClipped: Long = 0L,
    val preEngineNan: Long = 0L,
    val preEngineInf: Long = 0L,
    val brirRms: Float = 0f,
    val brirPeak: Float = 0f,
    val brirDirectRms: Float = 0f,
    val brirLateRms: Float = 0f,
    val brirNormalizationGain: Float = 0f,
    val b1AfterSilenceSkipping: ImmersiveStageDiagnostics = ImmersiveStageDiagnostics(),
    val b2AfterSonic: ImmersiveStageDiagnostics = ImmersiveStageDiagnostics(),
    val b3BeforeNativeDsp: ImmersiveStageDiagnostics = ImmersiveStageDiagnostics(),
    val b5AfterNativeDsp: ImmersiveStageDiagnostics = ImmersiveStageDiagnostics(),
    val b5AudioTrack: ImmersiveStageDiagnostics = ImmersiveStageDiagnostics(),
) {
    fun backendLabel(): String = when (activeBackend) {
        1 -> "Steam Audio"
        2 -> "Native HOA/HRTF"
        3 -> "Full Convolution"
        else -> if (processorEnabled) "Unavailable" else "Off / unavailable"
    }

    fun pipelineStages(): List<ImmersivePipelineStageTelemetry> = listOf(
        ImmersivePipelineStageTelemetry("B1", "After silence skipping", b1AfterSilenceSkipping.available,
            output = b1AfterSilenceSkipping,
            unavailableReason = if (!b1AfterSilenceSkipping.available) "Input boundary is before Media3 silence skipping" else null),
        ImmersivePipelineStageTelemetry("B2", "After Sonic time/pitch", b2AfterSonic.available,
            input = b1AfterSilenceSkipping, output = b2AfterSonic,
            unavailableReason = if (!b2AfterSonic.available) "Waiting for PCM" else null),
        ImmersivePipelineStageTelemetry("B3", "Before native DSP", b3BeforeNativeDsp.available,
            input = b2AfterSonic, output = b3BeforeNativeDsp,
            unavailableReason = if (!b3BeforeNativeDsp.available) "Waiting for PCM" else null),
        ImmersivePipelineStageTelemetry("B4", "PCM conversion → native DSP", processorEnabled,
            input = ImmersiveStageDiagnostics(processorEnabled, (preEngineRmsL + preEngineRmsR) / 2f, maxOf(preEnginePeakL, preEnginePeakR), maxOf(preEngineTruePeakL, preEngineTruePeakR), preEngineClipped, preEngineNan, preEngineInf, processedFrames, sampleRate, pcmEncoding),
            output = ImmersiveStageDiagnostics(processorEnabled, outputRms, outputPeak, maxOf(outputTruePeakL, outputTruePeakR), clippedOutput, nanCount, infCount, processedFrames, sampleRate, pcmEncoding),
            processingTimeMs = averageProcessingTimeMs,
            unavailableReason = if (!processorEnabled) "Processor off" else null),
        ImmersivePipelineStageTelemetry("B5", "After native DSP", b5AfterNativeDsp.available,
            input = ImmersiveStageDiagnostics(processorEnabled, outputRms, outputPeak, maxOf(outputTruePeakL, outputTruePeakR), clippedOutput, nanCount, infCount, processedFrames, sampleRate, pcmEncoding),
            output = b5AfterNativeDsp,
            unavailableReason = if (!b5AfterNativeDsp.available) "Waiting for PCM" else null),
        ImmersivePipelineStageTelemetry("B6", "AudioTrack enqueue", false, input = b5AfterNativeDsp,
            unavailableReason = "AudioTrack internal PCM is not observable from the app"),
        unavailableStage("B7", "Physical device output", "DAC/speaker output is not observable by Android app code"),
    )

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
                hostCallbackFrames = values[37].toInt().coerceAtLeast(0), quantumFrames = values[38].toInt().coerceIn(1, 1_000_000),
                processorEnabled = values[39] > 0.5,
                pcmEncoding = values[40].toInt(),
                activeBackend = values.getOrNull(41)?.toInt() ?: 0,
                algorithmicLatencySamples = values.getOrNull(42)?.toInt()?.coerceAtLeast(0) ?: 0,
                brirReady = values.getOrNull(43)?.let { it > 0.5 } ?: false,
                preLimiterTruePeak = values.getOrNull(44)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                limiterGainReductionDb = values.getOrNull(45)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                gainBudgetDb = values.getOrNull(46)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                preEngineRmsL = values.getOrNull(47)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                preEngineRmsR = values.getOrNull(48)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                preEnginePeakL = values.getOrNull(49)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                preEnginePeakR = values.getOrNull(50)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                preEngineTruePeakL = values.getOrNull(51)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                preEngineTruePeakR = values.getOrNull(52)?.toFloat()?.takeIf(Float::isFinite) ?: 0f,
                preEngineClipped = values.getOrNull(53)?.toLong()?.coerceAtLeast(0L) ?: 0L,
                preEngineNan = values.getOrNull(54)?.toLong()?.coerceAtLeast(0L) ?: 0L,
                preEngineInf = values.getOrNull(55)?.toLong()?.coerceAtLeast(0L) ?: 0L,
                brirRms = f(56),
                brirPeak = f(57),
                brirDirectRms = f(58),
                brirLateRms = f(59),
                brirNormalizationGain = f(60),
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
    CLOSED_CAR(6, "Closed car"),
    MEDIUM_HALL(7, "Medium hall"),
    SUBWAY_PLATFORM(8, "Subway platform"),
    LONG_TUNNEL(9, "Long tunnel"),
    OPEN_ROAD(10, "Open road"),
    CAVE(11, "Cave"),
    STADIUM(12, "Stadium"),
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
    @Volatile private var carFader = 0f
    @Volatile private var quantumFrames = DEFAULT_QUANTUM_FRAMES
    @Volatile private var limiterEnabled = true
    @Volatile private var bassGainDb = 0f
    @Volatile private var trebleGainDb = 0f
    @Volatile private var outputGainDb = 0f

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!nativeAvailable) { this.inputAudioFormat = inputAudioFormat; outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET; return AudioProcessor.AudioFormat.NOT_SET }
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
            setCarFader(carFader)
            setQuantumFrames(quantumFrames)
            setLimiterEnabled(limiterEnabled)
            setBassGainDb(bassGainDb)
            setTrebleGainDb(trebleGainDb)
            setOutputGainDb(outputGainDb)
            setEnabled(enabled)
        }
        outputAudioFormat = inputAudioFormat
        return outputAudioFormat
    }

    override fun isActive(): Boolean = nativeAvailable && nativeHandle != 0L

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

    fun setCarFader(value: Float) {
        carFader = value.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        if (nativeHandle != 0L) nativeSetCarFader(nativeHandle, carFader)
    }

    fun setQuantumFrames(value: Int) {
        quantumFrames = value.coerceIn(MIN_QUANTUM_FRAMES, MAX_QUANTUM_FRAMES)
        if (nativeHandle != 0L) nativeSetQuantumFrames(nativeHandle, quantumFrames)
    }

    fun quantumFrames(): Int = quantumFrames

    fun setLimiterEnabled(value: Boolean) {
        limiterEnabled = value
        if (nativeHandle != 0L) nativeSetLimiterEnabled(nativeHandle, value)
    }

    fun setBassGainDb(value: Float) {
        bassGainDb = value.takeIf(Float::isFinite)?.coerceIn(-12f, 12f) ?: 0f
        if (nativeHandle != 0L) nativeSetBassGainDb(nativeHandle, bassGainDb)
    }

    fun setTrebleGainDb(value: Float) {
        trebleGainDb = value.takeIf(Float::isFinite)?.coerceIn(-12f, 12f) ?: 0f
        if (nativeHandle != 0L) nativeSetTrebleGainDb(nativeHandle, trebleGainDb)
    }

    fun setOutputGainDb(value: Float) {
        outputGainDb = value.takeIf(Float::isFinite)?.coerceIn(-24f, 12f) ?: 0f
        if (nativeHandle != 0L) nativeSetOutputGainDb(nativeHandle, outputGainDb)
    }

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

        private val nativeAvailable: Boolean = runCatching { System.loadLibrary("frostsoulx_immersive_jni") }.isSuccess

        @JvmStatic private external fun nativeCreate(sampleRate: Int, encoding: Int): Long
        @JvmStatic private external fun nativeRelease(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeResetDiagnostics(handle: Long)
        @JvmStatic private external fun nativeSetEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetLimiterEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetBassGainDb(handle: Long, gainDb: Float)
        @JvmStatic private external fun nativeSetTrebleGainDb(handle: Long, gainDb: Float)
        @JvmStatic private external fun nativeSetOutputGainDb(handle: Long, gainDb: Float)
        @JvmStatic private external fun nativeSetSpatialBlend(handle: Long, blend: Float)
        @JvmStatic private external fun nativeSetRoomPreset(handle: Long, preset: Int)
        @JvmStatic private external fun nativeSetRoomMix(handle: Long, wetMix: Float)
        @JvmStatic private external fun nativeSetReflectionAmount(handle: Long, amount: Float)
        @JvmStatic private external fun nativeSetReverbTimeSeconds(handle: Long, seconds: Float)
        @JvmStatic private external fun nativeSetRoomSize(handle: Long, size: Float)
        @JvmStatic private external fun nativeSetDampening(handle: Long, dampening: Float)
        @JvmStatic private external fun nativeSetStereoWidth(handle: Long, width: Float)
        @JvmStatic private external fun nativeSetCarFader(handle: Long, fader: Float)
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
    @Volatile private var carFader = 0f
    @Volatile private var quantumFrames = ImmersiveAudioProcessor.DEFAULT_QUANTUM_FRAMES
    @Volatile private var limiterEnabled = true
    @Volatile private var bassGainDb = 0f
    @Volatile private var trebleGainDb = 0f
    @Volatile private var outputGainDb = 0f
    @Volatile private var b1Meter: ImmersiveStageMeter? = null
    @Volatile private var b2Meter: ImmersiveStageMeter? = null
    @Volatile private var b3Meter: ImmersiveStageMeter? = null
    @Volatile private var b5Meter: ImmersiveStageMeter? = null

    fun attachStageMeters(
        b1: ImmersiveStageMeter,
        b2: ImmersiveStageMeter,
        b3: ImmersiveStageMeter,
        b5: ImmersiveStageMeter,
    ) {
        b1Meter = b1
        b2Meter = b2
        b3Meter = b3
        b5Meter = b5
    }

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
        value.setCarFader(carFader)
        value.setQuantumFrames(quantumFrames)
        value.setLimiterEnabled(limiterEnabled)
        value.setBassGainDb(bassGainDb)
        value.setTrebleGainDb(trebleGainDb)
        value.setOutputGainDb(outputGainDb)
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

    fun setCarFader(value: Float) {
        carFader = value.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        processor?.setCarFader(carFader)
    }
    fun setQuantumFrames(value: Int) {
        quantumFrames = value.coerceIn(ImmersiveAudioProcessor.MIN_QUANTUM_FRAMES, ImmersiveAudioProcessor.MAX_QUANTUM_FRAMES)
        processor?.setQuantumFrames(quantumFrames)
    }

    fun readDiagnostics(): ImmersiveAudioDiagnostics {
        val native = processor?.readDiagnostics() ?: ImmersiveAudioDiagnostics()
        return native.copy(
            b1AfterSilenceSkipping = b1Meter?.snapshot() ?: ImmersiveStageDiagnostics(),
            b2AfterSonic = b2Meter?.snapshot() ?: ImmersiveStageDiagnostics(),
            b3BeforeNativeDsp = b3Meter?.snapshot() ?: ImmersiveStageDiagnostics(),
            b5AfterNativeDsp = b5Meter?.snapshot() ?: ImmersiveStageDiagnostics(),
            // AudioTrack and physical device output are intentionally unavailable.
            b5AudioTrack = ImmersiveStageDiagnostics(),
        )
    }
    fun resetDiagnostics() {
        processor?.resetDiagnostics()
        b1Meter?.reset()
        b2Meter?.reset()
        b3Meter?.reset()
        b5Meter?.reset()
    }

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

    fun setLimiterEnabled(value: Boolean) {
        limiterEnabled = value
        processor?.setLimiterEnabled(value)
    }

    fun setBassGainDb(value: Float) {
        bassGainDb = value.takeIf(Float::isFinite)?.coerceIn(-12f, 12f) ?: 0f
        processor?.setBassGainDb(bassGainDb)
    }

    fun setTrebleGainDb(value: Float) {
        trebleGainDb = value.takeIf(Float::isFinite)?.coerceIn(-12f, 12f) ?: 0f
        processor?.setTrebleGainDb(trebleGainDb)
    }

    fun setOutputGainDb(value: Float) {
        outputGainDb = value.takeIf(Float::isFinite)?.coerceIn(-24f, 12f) ?: 0f
        processor?.setOutputGainDb(outputGainDb)
    }
}
