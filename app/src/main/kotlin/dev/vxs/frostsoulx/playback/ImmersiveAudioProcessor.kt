package dev.vxs.frostsoulx.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.DoubleAdder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private var previousL = 0f
    private var previousR = 0f
    @Volatile private var channels = 0

    fun configure(format: AudioProcessor.AudioFormat) {
        sampleRate = format.sampleRate
        encoding = format.encoding
        channels = format.channelCount
    }

    fun observe(buffer: ByteBuffer) {
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_FLOAT -> 4
            C.ENCODING_PCM_16BIT -> 2
            else -> return
        }
        if (channels != 2 || buffer.remaining() < bytesPerSample * 2) return
        val frameCount = buffer.remaining() / (bytesPerSample * 2)
        repeat(frameCount) {
            val left = readSample(buffer, bytesPerSample)
            val right = readSample(buffer, bytesPerSample)
            observeSample(left)
            observeSample(right)
            if (left.isFinite()) {
                updateMax(truePeakBits, maxOf(kotlin.math.abs(left), kotlin.math.abs((previousL + left) * 0.5f)))
                previousL = left
            }
            if (right.isFinite()) {
                updateMax(truePeakBits, maxOf(kotlin.math.abs(right), kotlin.math.abs((previousR + right) * 0.5f)))
                previousR = right
            }
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
        clipped.set(0L); nan.set(0L); inf.set(0L); frames.set(0L)
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
    fun backendLabel(): String = if (!processorEnabled) "Off / unavailable" else when (activeBackend) {
        3 -> "FrostSoulX unified convolution"
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
            fun f(index: Int): Float = values.getOrNull(index)?.toFloat()?.takeIf(Float::isFinite) ?: 0f
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
    OFF(0, "Anechoic / no room"),
    SMALL_ROOM(1, "Bathroom"),
    STUDIO(2, "Living room"),
    CONCERT_HALL(3, "Concert hall"),
    CATHEDRAL(4, "Large hall"),
    SUBWAY(5, "Long subway tunnel"),
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

/** Immutable, validated targets; names of persisted keys remain migration-compatible. */
data class ImmersiveControls(
    val enabled: Boolean = false,
    val intensity: Float = 0.5f,
    val roomPreset: ImmersiveRoomPreset = ImmersiveRoomPreset.STUDIO,
    val roomMix: Float = 0.18f,
    val reflectionAmount: Float = 0.28f,
    val reverbTimeSeconds: Float = 1.35f,
    val roomSize: Float = 0.5f,
    val dampening: Float = 0.5f,
    val stereoWidth: Float = 0.5f,
    val bassWidth: Float = 1f,
    val bassGainDb: Float = 0f,
    val outputGainDb: Float = 0f,
    val carFader: Float = 0f,
    val azimuth: Float = 0f,
    val elevation: Float = 0f,
    val distance: Float = 1f,
    val orbitEnabled: Boolean = false,
    val quantumFrames: Int = 384,
) {
    fun sanitized(): ImmersiveControls {
        fun safe(v: Float, min: Float, max: Float, default: Float) =
            v.takeIf(Float::isFinite)?.coerceIn(min, max) ?: default
        return copy(
            intensity = safe(intensity, 0f, 1f, 0.5f),
            roomMix = safe(roomMix, 0f, 1f, 0.18f),
            reflectionAmount = safe(reflectionAmount, 0f, 1f, 0.28f),
            reverbTimeSeconds = safe(reverbTimeSeconds, 0.2f, 8f, 1.35f),
            roomSize = safe(roomSize, 0f, 1f, 0.5f), dampening = safe(dampening, 0f, 1f, 0.5f),
            stereoWidth = safe(stereoWidth, 0f, 1f, 0.5f), bassWidth = safe(bassWidth, 0f, 2f, 1f),
            bassGainDb = safe(bassGainDb, -12f, 6f, 0f), outputGainDb = safe(outputGainDb, -24f, 0f, 0f),
            carFader = safe(carFader, -1f, 1f, 0f), azimuth = safe(azimuth, -180f, 180f, 0f),
            elevation = safe(elevation, -90f, 90f, 0f), distance = safe(distance, 0.2f, 10f, 1f),
            quantumFrames = quantumFrames.coerceIn(96, 2048),
        )
    }
}

/** Media3 owns lifecycle/PCM. One shared control producer builds IRs off the UI/audio thread. */
class ImmersiveAudioProcessor : AudioProcessor {
    private var format = AudioProcessor.AudioFormat.NOT_SET
    private var reusableBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false
    private var hasInput = false
    private var algorithmicLatency = 0
    private var nativeHandle = 0L // playback lifecycle thread, or under controlLock
    private val controlLock = Any()
    @Volatile private var desired = ImmersiveControls()
    private var applied: ImmersiveControls? = null
    @Volatile private var diagnostics = ImmersiveAudioDiagnostics()
    @Volatile private var desiredCustomIr: WavImpulseResponse? = null
    private val updatePending = AtomicBoolean(false)

    fun updateControls(value: ImmersiveControls) {
        desired = value.sanitized()
        if (!updatePending.compareAndSet(false, true)) return
        CONTROL_EXECUTOR.schedule({
            synchronized(controlLock) {
                updatePending.set(false)
                if (nativeHandle != 0L) applyControls(desired)
            }
        }, 40, TimeUnit.MILLISECONDS)
    }

    fun updateCustomIr(value: WavImpulseResponse?) {
        desiredCustomIr = value
        CONTROL_EXECUTOR.execute { synchronized(controlLock) { if (nativeHandle != 0L) applyCustomIrLocked() } }
    }
    private fun applyCustomIrLocked(targetRate: Int = format.sampleRate) {
        val h = nativeHandle
        if (h == 0L) return
        val source = desiredCustomIr
        if (source == null) { nativeClearCustomIr(h); return }
        val (left, right) = source.resampled(targetRate.takeIf { it > 0 } ?: source.sampleRate, 32768)
        nativeSetCustomIr(h, left, right)
    }
    private fun applyControls(c: ImmersiveControls) {
        val h = nativeHandle
        val old = applied
        val roomChanged = old?.roomPreset != c.roomPreset
        if (roomChanged) nativeSetRoomPreset(h, c.roomPreset.nativeValue)
        if (roomChanged || old?.roomSize != c.roomSize) nativeSetRoomSize(h, c.roomSize)
        if (roomChanged || old?.dampening != c.dampening) nativeSetDampening(h, c.dampening)
        if (roomChanged || old?.roomMix != c.roomMix) nativeSetRoomMix(h, c.roomMix)
        if (roomChanged || old?.reflectionAmount != c.reflectionAmount) nativeSetReflectionAmount(h, c.reflectionAmount)
        if (roomChanged || old?.reverbTimeSeconds != c.reverbTimeSeconds) nativeSetReverbTimeSeconds(h, c.reverbTimeSeconds)
        if (roomChanged || old?.orbitEnabled != c.orbitEnabled) nativeSetOrbitEnabled(h, c.orbitEnabled)
        if (roomChanged || old?.azimuth != c.azimuth || old?.elevation != c.elevation || old?.distance != c.distance || old?.orbitEnabled != c.orbitEnabled) {
            nativeSetSource(h, c.azimuth, c.elevation, c.distance)
        }
        if (roomChanged || old?.carFader != c.carFader) nativeSetCarFader(h, c.carFader)
        if (old?.intensity != c.intensity) nativeSetSpatialBlend(h, c.intensity)
        if (old?.stereoWidth != c.stereoWidth) nativeSetStereoWidth(h, c.stereoWidth)
        if (old?.bassWidth != c.bassWidth) nativeSetBassWidth(h, c.bassWidth)
        if (old?.bassGainDb != c.bassGainDb) nativeSetBassGainDb(h, c.bassGainDb)
        if (old?.outputGainDb != c.outputGainDb) nativeSetOutputGainDb(h, c.outputGainDb)
        if (old?.quantumFrames != c.quantumFrames) nativeSetQuantumFrames(h, c.quantumFrames)
        if (old?.enabled != c.enabled) nativeSetEnabled(h, c.enabled)
        applied = c
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.channelCount == 2 &&
            inputAudioFormat.encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)
        if (!supported || !nativeAvailable) {
            releaseNative()
            format = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (format != inputAudioFormat) {
            releaseNative()
            synchronized(controlLock) {
                nativeHandle = nativeCreate(inputAudioFormat.sampleRate, inputAudioFormat.encoding)
                if (nativeHandle != 0L) {
                    applyControls(desired)
                    applyCustomIrLocked(inputAudioFormat.sampleRate)
                    algorithmicLatency = ImmersiveAudioDiagnostics.fromNative(nativeReadDiagnostics(nativeHandle)).algorithmicLatencySamples
                }
            }
        }
        format = inputAudioFormat
        return if (nativeHandle != 0L) format else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = nativeHandle != 0L && desired.enabled
    override fun getDurationAfterProcessorApplied(durationUs: Long): Long =
        if (!isActive() || durationUs <= 0 || format.sampleRate <= 0) durationUs
        else durationUs + algorithmicLatency * 1_000_000L / format.sampleRate

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val result = prepareOutputBuffer(inputBuffer.remaining())
        result.put(inputBuffer).flip()
        val bytesPerFrame = if (format.encoding == C.ENCODING_PCM_FLOAT) 8 else 4
        val frames = result.remaining() / bytesPerFrame
        if (nativeHandle != 0L && frames > 0) {
            nativeProcess(nativeHandle, result, frames, format.encoding)
            hasInput = true
        }
    }

    private fun prepareOutputBuffer(bytes: Int): ByteBuffer {
        if (reusableBuffer.capacity() < bytes) reusableBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        reusableBuffer.clear()
        reusableBuffer.limit(bytes)
        outputBuffer = reusableBuffer
        return outputBuffer
    }

    override fun queueEndOfStream() {
        // Drain the engine's reported algorithmic delay so the final input frames are not lost.
        if (nativeHandle != 0L && hasInput && !inputEnded && desired.enabled) {
            val frames = algorithmicLatency
            val bytesPerFrame = if (format.encoding == C.ENCODING_PCM_FLOAT) 8 else 4
            val result = prepareOutputBuffer(frames * bytesPerFrame)
            repeat(frames * bytesPerFrame) { result.put(0.toByte()) }
            result.flip()
            if (frames > 0) nativeProcess(nativeHandle, result, frames, format.encoding)
        }
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer = outputBuffer.also { outputBuffer = EMPTY_BUFFER }
    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER
    override fun flush() {
        outputBuffer = EMPTY_BUFFER; inputEnded = false; hasInput = false
        synchronized(controlLock) { if (nativeHandle != 0L) nativeReset(nativeHandle) }
        diagnostics = ImmersiveAudioDiagnostics()
    }
    override fun reset() {
        flush(); releaseNative(); format = AudioProcessor.AudioFormat.NOT_SET
        reusableBuffer = EMPTY_BUFFER
    }
    private fun releaseNative() = synchronized(controlLock) {
        if (nativeHandle != 0L) nativeRelease(nativeHandle)
        nativeHandle = 0L; algorithmicLatency = 0; applied = null; diagnostics = ImmersiveAudioDiagnostics()
    }
    fun readDiagnostics(): ImmersiveAudioDiagnostics {
        CONTROL_EXECUTOR.execute {
            synchronized(controlLock) {
                if (nativeHandle != 0L) diagnostics = ImmersiveAudioDiagnostics.fromNative(nativeReadDiagnostics(nativeHandle))
            }
        }
        return diagnostics
    }
    fun resetDiagnostics() {
        CONTROL_EXECUTOR.execute {
            synchronized(controlLock) { if (nativeHandle != 0L) nativeResetDiagnostics(nativeHandle) }
        }
    }

    companion object {
        const val DEFAULT_QUANTUM_FRAMES = 384
        const val MIN_QUANTUM_FRAMES = 96
        const val MAX_QUANTUM_FRAMES = 2048
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private val CONTROL_EXECUTOR = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "FrostSoulX-IR-control").apply { isDaemon = true }
        }
        val nativeAvailable: Boolean = try {
            System.loadLibrary("frostsoulx_immersive_jni"); true
        } catch (_: LinkageError) { false }
        @JvmStatic private external fun nativeCreate(sampleRate: Int, encoding: Int): Long
        @JvmStatic private external fun nativeRelease(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeResetDiagnostics(handle: Long)
        @JvmStatic private external fun nativeSetEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetBassGainDb(handle: Long, gainDb: Float)
        @JvmStatic private external fun nativeSetOutputGainDb(handle: Long, gainDb: Float)
        @JvmStatic private external fun nativeSetCustomIr(handle: Long, left: FloatArray, right: FloatArray): Boolean
        @JvmStatic private external fun nativeClearCustomIr(handle: Long)
        @JvmStatic private external fun nativeSetSpatialBlend(handle: Long, blend: Float)
        @JvmStatic private external fun nativeSetRoomPreset(handle: Long, preset: Int)
        @JvmStatic private external fun nativeSetRoomMix(handle: Long, wetMix: Float)
        @JvmStatic private external fun nativeSetReflectionAmount(handle: Long, amount: Float)
        @JvmStatic private external fun nativeSetReverbTimeSeconds(handle: Long, seconds: Float)
        @JvmStatic private external fun nativeSetRoomSize(handle: Long, size: Float)
        @JvmStatic private external fun nativeSetDampening(handle: Long, dampening: Float)
        @JvmStatic private external fun nativeSetStereoWidth(handle: Long, width: Float)
        @JvmStatic private external fun nativeSetBassWidth(handle: Long, width: Float)
        @JvmStatic private external fun nativeSetSource(handle: Long, azimuth: Float, elevation: Float, distance: Float)
        @JvmStatic private external fun nativeSetOrbitEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetCarFader(handle: Long, fader: Float)
        @JvmStatic private external fun nativeSetQuantumFrames(handle: Long, quantumFrames: Int)
        @JvmStatic private external fun nativeReadDiagnostics(handle: Long): DoubleArray?
        @JvmStatic private external fun nativeProcess(handle: Long, pcmBuffer: ByteBuffer, frames: Int, encoding: Int)
    }
}

object ImmersiveAudioRuntime {
    @Volatile private var processor: ImmersiveAudioProcessor? = null
    @Volatile private var transitionHandler: ((Boolean) -> Unit)? = null
    @Volatile private var controls = ImmersiveControls()
    @Volatile private var customIr: WavImpulseResponse? = null
    @Volatile private var b1Meter: ImmersiveStageMeter? = null
    @Volatile private var b2Meter: ImmersiveStageMeter? = null
    @Volatile private var b3Meter: ImmersiveStageMeter? = null
    @Volatile private var b5Meter: ImmersiveStageMeter? = null

    fun attachStageMeters(b1: ImmersiveStageMeter, b2: ImmersiveStageMeter, b3: ImmersiveStageMeter, b5: ImmersiveStageMeter) {
        b1Meter = b1; b2Meter = b2; b3Meter = b3; b5Meter = b5
    }
    fun attach(value: ImmersiveAudioProcessor) { processor = value; value.updateControls(controls); value.updateCustomIr(customIr) }
    fun detachProcessor() { processor = null }
    fun detach() { processor = null; transitionHandler = null; b1Meter = null; b2Meter = null; b3Meter = null; b5Meter = null }
    fun setTransitionHandler(handler: ((Boolean) -> Unit)?) { transitionHandler = handler }
    @Synchronized fun applyControls(value: ImmersiveControls) {
        val oldEnabled = controls.enabled
        controls = value.sanitized()
        processor?.updateControls(controls)
        if (oldEnabled != controls.enabled) transitionHandler?.invoke(controls.enabled)
    }
    @Synchronized fun setCustomIr(value: WavImpulseResponse?) { customIr = value; processor?.updateCustomIr(value) }
    @Synchronized private fun change(update: (ImmersiveControls) -> ImmersiveControls) = applyControls(update(controls))
    fun currentControls(): ImmersiveControls = controls
    fun setEnabled(value: Boolean) = change { it.copy(enabled = value) }
    fun setIntensity(value: Float) = change { it.copy(intensity = value) }
    fun setRoomPreset(value: ImmersiveRoomPreset) = change { it.copy(roomPreset = value) }
    fun setRoomMix(value: Float) = change { it.copy(roomMix = value) }
    fun setReflectionAmount(value: Float) = change { it.copy(reflectionAmount = value) }
    fun setReverbTimeSeconds(value: Float) = change { it.copy(reverbTimeSeconds = value) }
    fun setRoomSize(value: Float) = change { it.copy(roomSize = value) }
    fun setDampening(value: Float) = change { it.copy(dampening = value) }
    fun setStereoWidth(value: Float) = change { it.copy(stereoWidth = value) }
    fun setBassWidth(value: Float) = change { it.copy(bassWidth = value) }
    fun setCarFader(value: Float) = change { it.copy(carFader = value) }
    fun setAzimuth(value: Float) = change { it.copy(azimuth = value) }
    fun setElevation(value: Float) = change { it.copy(elevation = value) }
    fun setDistance(value: Float) = change { it.copy(distance = value) }
    fun setOrbitEnabled(value: Boolean) = change { it.copy(orbitEnabled = value) }
    fun setQuantumFrames(value: Int) = change { it.copy(quantumFrames = value) }
    fun setBassGainDb(value: Float) = change { it.copy(bassGainDb = value) }
    fun setOutputGainDb(value: Float) = change { it.copy(outputGainDb = value) }
    fun isEnabled(): Boolean = controls.enabled
    fun intensity(): Float = controls.intensity
    fun roomPreset(): ImmersiveRoomPreset = controls.roomPreset
    fun roomMix(): Float = controls.roomMix
    fun reflectionAmount(): Float = controls.reflectionAmount
    fun reverbTimeSeconds(): Float = controls.reverbTimeSeconds
    fun roomSize(): Float = controls.roomSize
    fun dampening(): Float = controls.dampening
    fun stereoWidth(): Float = controls.stereoWidth
    fun quantumFrames(): Int = controls.quantumFrames
    fun readDiagnostics(): ImmersiveAudioDiagnostics {
        val native = processor?.readDiagnostics() ?: ImmersiveAudioDiagnostics()
        return native.copy(
            b1AfterSilenceSkipping = b1Meter?.snapshot() ?: ImmersiveStageDiagnostics(),
            b2AfterSonic = b2Meter?.snapshot() ?: ImmersiveStageDiagnostics(),
            b3BeforeNativeDsp = b3Meter?.snapshot() ?: ImmersiveStageDiagnostics(),
            b5AfterNativeDsp = b5Meter?.snapshot() ?: ImmersiveStageDiagnostics(),
        )
    }
    fun resetDiagnostics() {
        processor?.resetDiagnostics(); b1Meter?.reset(); b2Meter?.reset(); b3Meter?.reset(); b5Meter?.reset()
    }
}
