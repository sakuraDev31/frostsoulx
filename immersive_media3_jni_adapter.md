# FrostSoulX Media3 → JNI → Steam Audio Adapter

## Important diagnosis

The current Kotlin adapter copies the incoming Media3 PCM into `outputBuffer` **before** calling native processing. If native processing fails, the original PCM copy remains in `outputBuffer`; therefore this adapter does not intentionally replace failed processing with silence. The observed ON-only silence is more likely in the live player/AudioSink replacement lifecycle or in native initialization (`nativeCreate()` returning `0`), and must be confirmed with the native status diagnostics.

The current JNI `nativeProcess()` is `void`, so the Kotlin adapter cannot directly observe whether processing succeeded. The diagnostics array is the available status channel. Native status values should be surfaced in logs outside the real-time callback, not logged from `nativeProcess()` itself.

## Current `ImmersiveAudioProcessor.kt`

```kotlin
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

    override fun queueEndOfStream() { inputEnded = true }
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
        if (nativeHandle == 0L) ImmersiveAudioDiagnostics()
        else ImmersiveAudioDiagnostics.fromNative(nativeReadDiagnostics(nativeHandle))

    private fun releaseNative() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        init { System.loadLibrary("frostsoulx_immersive_jni") }

        @JvmStatic private external fun nativeCreate(sampleRate: Int, encoding: Int): Long
        @JvmStatic private external fun nativeRelease(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeSetEnabled(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeSetSpatialBlend(handle: Long, blend: Float)
        @JvmStatic private external fun nativeReadDiagnostics(handle: Long): DoubleArray?
        @JvmStatic private external fun nativeProcess(
            handle: Long,
            pcmBuffer: ByteBuffer,
            frames: Int,
            encoding: Int,
        )
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

    fun detachProcessor() { processor = null }
    fun detach() { processor = null; transitionHandler = null }
    fun setTransitionHandler(handler: ((Boolean) -> Unit)?) { transitionHandler = handler }

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

    fun readDiagnostics(): ImmersiveAudioDiagnostics =
        processor?.readDiagnostics() ?: ImmersiveAudioDiagnostics()

    fun isEnabled(): Boolean = enabled
    fun intensity(): Float = intensity
}
```

## Current JNI bridge

```cpp
#include <jni.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <memory>
#include "frostsoulx/ImmersiveAudioEngine.h"

namespace {
constexpr int kMaxFrames = 8192;
constexpr int kStereoSamples = kMaxFrames * 2;

struct Diagnostics {
    float inputRms = 0.0f;
    float outputRms = 0.0f;
    float inputPeak = 0.0f;
    float outputPeak = 0.0f;
    float maxAbsDifference = 0.0f;
    float changedPercentage = 0.0f;
    int nanCount = 0;
    int infCount = 0;
    int processCallCount = 0;
    int processedFrames = 0;
    int nativeStatus = 0;
};

struct Handle {
    frostsoulx::ImmersiveAudioEngine engine;
    bool enabled = false;
    std::array<float, kStereoSamples> scratch{};
    std::array<float, kStereoSamples> inputSnapshot{};
    Diagnostics diagnostics{};
};

float readPcm16(std::int16_t sample) noexcept {
    return static_cast<float>(sample) / 32768.0f;
}

std::int16_t writePcm16(float sample) noexcept {
    const float bounded = std::clamp(sample, -1.0f, 0.9999695f);
    const auto scaled = static_cast<int>(bounded * 32768.0f);
    return static_cast<std::int16_t>(std::clamp(scaled, -32768, 32767));
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeCreate(
    JNIEnv*, jclass, jint sampleRate, jint) {
    auto handle = std::make_unique<Handle>();
    if (!handle->engine.prepare(sampleRate, kMaxFrames)) return 0L;
    handle->engine.setEnabled(false);
    handle->engine.setSpatialBlend(0.0f);
    handle->diagnostics.nativeStatus =
        static_cast<int>(handle->engine.lastProcessResult());
    return reinterpret_cast<jlong>(handle.release());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeProcess(
    JNIEnv* env, jclass, jlong address, jobject pcmBuffer,
    jint frames, jint encoding) {
    auto* handle = reinterpret_cast<Handle*>(address);
    if (handle == nullptr || pcmBuffer == nullptr || frames <= 0 ||
        !handle->enabled) return;

    auto* bytes = static_cast<std::uint8_t*>(
        env->GetDirectBufferAddress(pcmBuffer));
    if (bytes == nullptr) return;

    const int totalFrames = frames;
    int frameOffset = 0;
    while (frameOffset < totalFrames) {
        const int chunkFrames = std::min(kMaxFrames, totalFrames - frameOffset);
        const int samples = chunkFrames * 2;
        const int sampleOffset = frameOffset * 2;

        if (encoding == 4) { // C.ENCODING_PCM_FLOAT
            auto* samplesFloat = reinterpret_cast<float*>(bytes) + sampleOffset;
            std::copy(samplesFloat, samplesFloat + samples,
                      handle->inputSnapshot.begin());
            std::copy(samplesFloat, samplesFloat + samples,
                      handle->scratch.begin());
            const bool processed = handle->engine.process(
                handle->scratch.data(), chunkFrames);
            handle->diagnostics.nativeStatus = static_cast<int>(
                handle->engine.lastProcessResult());
            if (processed) {
                std::copy(handle->scratch.begin(),
                          handle->scratch.begin() + samples, samplesFloat);
            }
            frameOffset += chunkFrames;
            continue;
        }

        if (encoding == 2) { // C.ENCODING_PCM_16BIT
            auto* samples16 = reinterpret_cast<std::int16_t*>(bytes) + sampleOffset;
            for (int frame = 0; frame < chunkFrames; ++frame) {
                handle->scratch[frame * 2] = readPcm16(samples16[frame * 2]);
                handle->scratch[frame * 2 + 1] = readPcm16(samples16[frame * 2 + 1]);
            }
            std::copy(handle->scratch.begin(),
                      handle->scratch.begin() + samples,
                      handle->inputSnapshot.begin());
            const bool processed = handle->engine.process(
                handle->scratch.data(), chunkFrames);
            handle->diagnostics.nativeStatus = static_cast<int>(
                handle->engine.lastProcessResult());
            if (processed) {
                for (int frame = 0; frame < chunkFrames; ++frame) {
                    samples16[frame * 2] = writePcm16(handle->scratch[frame * 2]);
                    samples16[frame * 2 + 1] = writePcm16(handle->scratch[frame * 2 + 1]);
                }
            }
            frameOffset += chunkFrames;
            continue;
        }

        handle->diagnostics.nativeStatus = static_cast<int>(
            frostsoulx::ImmersiveProcessResult::InvalidInput);
        return;
    }
}
```

## Status interpretation

| Native status | Meaning |
|---:|---|
| `0` | Not prepared |
| `1` | Disabled/bypassed |
| `2` | Invalid input |
| `3` | Steam Audio unavailable or rejected the block |
| `4` | Invalid/non-finite/silent output rejected; original PCM preserved |
| `5` | Steam Audio processed the block |

## Critical next check

After enabling Immersive, read `ImmersiveAudioRuntime.readDiagnostics()` after at least one audio block:

- `nativeHandle == 0` / all diagnostics zero: `nativeCreate()` failed, usually ABI loading or Steam Audio initialization.
- `processCallCount == 0`: the rebuilt AudioSink is not using this processor, or `enabled` was not applied to the newly created processor.
- `nativeStatus == 3`: Steam Audio was loaded but rejected initialization or the process block.
- `nativeStatus == 4`: Steam Audio produced invalid output and the adapter intentionally preserved the original PCM.
- `nativeStatus == 5` with non-zero RMS: the adapter/native path is processing audio; silence then occurs downstream in the replacement AudioSink/player path.

Do not add logging inside `nativeProcess()` or the audio callback. Export/read these values from the service or a developer diagnostics surface outside the real-time callback.
