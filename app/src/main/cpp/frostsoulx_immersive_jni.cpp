#include <jni.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <chrono>
#include <limits>
#include <memory>

#include "frostsoulx/ImmersiveAudioEngine.h"

namespace {
// 384 frames is the low-latency default. The engine internally subdivides larger
// host quanta into Steam Audio's fixed 384-frame effect blocks.
constexpr int kDefaultQuantumFrames = 384;
constexpr int kMinQuantumFrames = 96;
constexpr int kMaxQuantumFrames = 2048;
constexpr int kStereoSamples = kMaxQuantumFrames * 2;

struct Diagnostics {
    std::atomic<double> inputSumSquaresL{0.0};
    std::atomic<double> inputSumSquaresR{0.0};
    std::atomic<double> outputSumSquaresL{0.0};
    std::atomic<double> outputSumSquaresR{0.0};
    std::atomic<float> inputPeakL{0.0f};
    std::atomic<float> inputPeakR{0.0f};
    std::atomic<float> outputPeakL{0.0f};
    std::atomic<float> outputPeakR{0.0f};
    std::atomic<float> inputTruePeakL{0.0f};
    std::atomic<float> inputTruePeakR{0.0f};
    std::atomic<float> outputTruePeakL{0.0f};
    std::atomic<float> outputTruePeakR{0.0f};
    std::atomic<float> inputMinL{std::numeric_limits<float>::infinity()};
    std::atomic<float> inputMinR{std::numeric_limits<float>::infinity()};
    std::atomic<float> outputMinL{std::numeric_limits<float>::infinity()};
    std::atomic<float> outputMinR{std::numeric_limits<float>::infinity()};
    std::atomic<float> inputMaxL{-std::numeric_limits<float>::infinity()};
    std::atomic<float> inputMaxR{-std::numeric_limits<float>::infinity()};
    std::atomic<float> outputMaxL{-std::numeric_limits<float>::infinity()};
    std::atomic<float> outputMaxR{-std::numeric_limits<float>::infinity()};
    std::atomic<float> maxAbsDifference{0.0f};
    std::atomic<double> sumAbsDifference{0.0};
    std::atomic<uint64_t> changedSamples{0};
    std::atomic<uint64_t> nanCount{0};
    std::atomic<uint64_t> infCount{0};
    std::atomic<uint64_t> clippedInput{0};
    std::atomic<uint64_t> clippedOutput{0};
    std::atomic<uint64_t> processCallCount{0};
    std::atomic<uint64_t> processedFrames{0};
    std::atomic<uint64_t> totalBlocks{0};
    std::atomic<uint64_t> nativeProcessFailures{0};
    std::atomic<uint64_t> processingTimeNanos{0};
    std::atomic<uint64_t> maxProcessingTimeNanos{0};
    std::atomic<uint64_t> deadlineMisses{0};
    std::atomic<int> nativeStatus{0};
    std::atomic<float> bassGainReductionDb{0.0f};
    std::atomic<float> trebleGainReductionDb{0.0f};
    std::atomic<float> outputGainReductionDb{0.0f};
    std::atomic<float> spatialGainReductionDb{0.0f};
    std::atomic<float> transitionRamp{0.0f};

    void reset() noexcept {
        inputSumSquaresL.store(0.0); inputSumSquaresR.store(0.0);
        outputSumSquaresL.store(0.0); outputSumSquaresR.store(0.0);
        inputPeakL.store(0.0f); inputPeakR.store(0.0f);
        outputPeakL.store(0.0f); outputPeakR.store(0.0f);
        inputTruePeakL.store(0.0f); inputTruePeakR.store(0.0f);
        outputTruePeakL.store(0.0f); outputTruePeakR.store(0.0f);
        inputMinL.store(std::numeric_limits<float>::infinity());
        inputMinR.store(std::numeric_limits<float>::infinity());
        outputMinL.store(std::numeric_limits<float>::infinity());
        outputMinR.store(std::numeric_limits<float>::infinity());
        inputMaxL.store(-std::numeric_limits<float>::infinity());
        inputMaxR.store(-std::numeric_limits<float>::infinity());
        outputMaxL.store(-std::numeric_limits<float>::infinity());
        outputMaxR.store(-std::numeric_limits<float>::infinity());
        maxAbsDifference.store(0.0f); sumAbsDifference.store(0.0);
        changedSamples.store(0); nanCount.store(0); infCount.store(0);
        clippedInput.store(0); clippedOutput.store(0); processCallCount.store(0);
        processedFrames.store(0); totalBlocks.store(0); nativeProcessFailures.store(0);
        processingTimeNanos.store(0); maxProcessingTimeNanos.store(0); deadlineMisses.store(0);
        nativeStatus.store(0);
        bassGainReductionDb.store(0.0f); trebleGainReductionDb.store(0.0f);
        outputGainReductionDb.store(0.0f); spatialGainReductionDb.store(0.0f);
        transitionRamp.store(0.0f);
    }
};

struct Handle {
    frostsoulx::ImmersiveAudioEngine engine;
    std::atomic<bool> enabled{false};
    int sampleRate = 0;
    int encoding = 0;
    std::atomic<int> lastHostCallbackFrames{0};
    std::atomic<int> quantumFrames{kDefaultQuantumFrames};
    std::array<float, kStereoSamples> scratch{};
    std::array<float, kStereoSamples> inputSnapshot{};
    Diagnostics diagnostics{};
    float previousInputL = 0.0f;
    float previousInputR = 0.0f;
    float previousOutputL = 0.0f;
    float previousOutputR = 0.0f;
    bool hasPreviousSample = false;
};

void atomicAdd(std::atomic<double>& target, double value) noexcept {
    double current = target.load(std::memory_order_relaxed);
    while (!target.compare_exchange_weak(current, current + value,
                                          std::memory_order_relaxed,
                                          std::memory_order_relaxed)) {}
}

void atomicMax(std::atomic<float>& target, float value) noexcept {
    float current = target.load(std::memory_order_relaxed);
    while (current < value && !target.compare_exchange_weak(current, value,
                                                            std::memory_order_relaxed,
                                                            std::memory_order_relaxed)) {}
}

void atomicMin(std::atomic<float>& target, float value) noexcept {
    float current = target.load(std::memory_order_relaxed);
    while (current > value && !target.compare_exchange_weak(current, value,
                                                            std::memory_order_relaxed,
                                                            std::memory_order_relaxed)) {}
}

void atomicMax(std::atomic<uint64_t>& target, uint64_t value) noexcept {
    uint64_t current = target.load(std::memory_order_relaxed);
    while (current < value && !target.compare_exchange_weak(current, value,
                                                            std::memory_order_relaxed,
                                                            std::memory_order_relaxed)) {}
}

float truePeak(float previous, float current) noexcept {
    return std::max({std::fabs(current), std::fabs(previous + current) * 0.5f,
                     std::fabs(previous * 0.75f + current * 0.25f),
                     std::fabs(previous * 0.25f + current * 0.75f)});
}

void publishDiagnostics(Handle& handle, const float* input, const float* output, int frames) noexcept {
    uint64_t changed = 0;
    for (int frame = 0; frame < frames; ++frame) {
        const float inL = input[frame * 2];
        const float inR = input[frame * 2 + 1];
        const float outL = output[frame * 2];
        const float outR = output[frame * 2 + 1];
        const float values[] = {inL, inR, outL, outR};
        for (float value : values) {
            if (std::isnan(value)) handle.diagnostics.nanCount.fetch_add(1, std::memory_order_relaxed);
            if (std::isinf(value)) handle.diagnostics.infCount.fetch_add(1, std::memory_order_relaxed);
        }
        if (std::isfinite(inL)) {
            atomicAdd(handle.diagnostics.inputSumSquaresL, static_cast<double>(inL) * inL);
            atomicMax(handle.diagnostics.inputPeakL, std::fabs(inL));
            atomicMin(handle.diagnostics.inputMinL, inL);
            atomicMax(handle.diagnostics.inputMaxL, inL);
            atomicMax(handle.diagnostics.inputTruePeakL, truePeak(handle.previousInputL, inL));
            if (std::fabs(inL) >= 1.0f) handle.diagnostics.clippedInput.fetch_add(1, std::memory_order_relaxed);
        }
        if (std::isfinite(inR)) {
            atomicAdd(handle.diagnostics.inputSumSquaresR, static_cast<double>(inR) * inR);
            atomicMax(handle.diagnostics.inputPeakR, std::fabs(inR));
            atomicMin(handle.diagnostics.inputMinR, inR);
            atomicMax(handle.diagnostics.inputMaxR, inR);
            atomicMax(handle.diagnostics.inputTruePeakR, truePeak(handle.previousInputR, inR));
            if (std::fabs(inR) >= 1.0f) handle.diagnostics.clippedInput.fetch_add(1, std::memory_order_relaxed);
        }
        if (std::isfinite(outL)) {
            atomicAdd(handle.diagnostics.outputSumSquaresL, static_cast<double>(outL) * outL);
            atomicMax(handle.diagnostics.outputPeakL, std::fabs(outL));
            atomicMin(handle.diagnostics.outputMinL, outL);
            atomicMax(handle.diagnostics.outputMaxL, outL);
            atomicMax(handle.diagnostics.outputTruePeakL, truePeak(handle.previousOutputL, outL));
            if (std::fabs(outL) >= 1.0f) handle.diagnostics.clippedOutput.fetch_add(1, std::memory_order_relaxed);
        }
        if (std::isfinite(outR)) {
            atomicAdd(handle.diagnostics.outputSumSquaresR, static_cast<double>(outR) * outR);
            atomicMax(handle.diagnostics.outputPeakR, std::fabs(outR));
            atomicMin(handle.diagnostics.outputMinR, outR);
            atomicMax(handle.diagnostics.outputMaxR, outR);
            atomicMax(handle.diagnostics.outputTruePeakR, truePeak(handle.previousOutputR, outR));
            if (std::fabs(outR) >= 1.0f) handle.diagnostics.clippedOutput.fetch_add(1, std::memory_order_relaxed);
        }
        const float differenceL = std::isfinite(inL) && std::isfinite(outL) ? std::fabs(outL - inL) : (inL == outL ? 0.0f : 1.0f);
        const float differenceR = std::isfinite(inR) && std::isfinite(outR) ? std::fabs(outR - inR) : (inR == outR ? 0.0f : 1.0f);
        atomicMax(handle.diagnostics.maxAbsDifference, std::max(differenceL, differenceR));
        atomicAdd(handle.diagnostics.sumAbsDifference, static_cast<double>(differenceL + differenceR));
        changed += differenceL > 1.0e-7f ? 1U : 0U;
        changed += differenceR > 1.0e-7f ? 1U : 0U;
        handle.previousInputL = inL;
        handle.previousInputR = inR;
        handle.previousOutputL = outL;
        handle.previousOutputR = outR;
        handle.hasPreviousSample = true;
    }
    handle.diagnostics.changedSamples.fetch_add(changed, std::memory_order_relaxed);
    handle.diagnostics.processedFrames.fetch_add(static_cast<uint64_t>(frames), std::memory_order_relaxed);
    handle.diagnostics.totalBlocks.fetch_add(1, std::memory_order_relaxed);
    handle.diagnostics.processCallCount.fetch_add(1, std::memory_order_relaxed);
}

float readPcm16(std::int16_t sample) noexcept {
    return static_cast<float>(sample) / 32768.0f;
}

std::int16_t writePcm16(float sample) noexcept {
    const float bounded = std::clamp(sample, -1.0f, 0.9999695f);
    const auto scaled = static_cast<int>(bounded * 32768.0f);
    return static_cast<std::int16_t>(std::clamp(scaled, -32768, 32767));
}

int resultCode(frostsoulx::ImmersiveProcessResult result) noexcept {
    return static_cast<int>(result);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeCreate(
    JNIEnv*, jclass, jint sampleRate, jint encoding) {
    auto handle = std::make_unique<Handle>();
    handle->sampleRate = sampleRate;
    handle->encoding = encoding;
    if (!handle->engine.prepare(sampleRate, kMaxQuantumFrames)) return 0L;
    handle->engine.setEnabled(false);
    handle->engine.setSpatialBlend(0.0f);
    handle->diagnostics.nativeStatus.store(resultCode(handle->engine.lastProcessResult()), std::memory_order_relaxed);
    return reinterpret_cast<jlong>(handle.release());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetQuantumFrames(
    JNIEnv*, jclass, jlong address, jint quantumFrames) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->quantumFrames.store(
            std::clamp(static_cast<int>(quantumFrames), kMinQuantumFrames, kMaxQuantumFrames),
            std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeRelease(
    JNIEnv*, jclass, jlong address) {
    delete reinterpret_cast<Handle*>(address);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeReset(
    JNIEnv*, jclass, jlong address) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.reset();
        handle->diagnostics.reset();
        handle->diagnostics.nativeStatus.store(resultCode(handle->engine.lastProcessResult()), std::memory_order_relaxed);
        handle->previousInputL = 0.0f;
        handle->previousInputR = 0.0f;
        handle->previousOutputL = 0.0f;
        handle->previousOutputR = 0.0f;
        handle->hasPreviousSample = false;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeResetDiagnostics(
    JNIEnv*, jclass, jlong address) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->diagnostics.reset();
        handle->diagnostics.nativeStatus.store(
            handle->enabled.load(std::memory_order_relaxed)
                ? resultCode(handle->engine.lastProcessResult())
                : resultCode(frostsoulx::ImmersiveProcessResult::Disabled),
            std::memory_order_relaxed);
        handle->previousInputL = 0.0f;
        handle->previousInputR = 0.0f;
        handle->previousOutputL = 0.0f;
        handle->previousOutputR = 0.0f;
        handle->hasPreviousSample = false;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetEnabled(
    JNIEnv*, jclass, jlong address, jboolean enabled) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        const bool value = enabled == JNI_TRUE;
        handle->enabled.store(value, std::memory_order_relaxed);
        handle->engine.setEnabled(value);
        handle->diagnostics.nativeStatus.store(resultCode(handle->engine.lastProcessResult()), std::memory_order_relaxed);
    }
}

// Kept for compatibility with older Kotlin processor builds. The current
// engine applies its safety limiter as part of the output stage, so there is
// no separate runtime switch to forward here.
extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetLimiterEnabled(
    JNIEnv*, jclass, jlong, jboolean) {}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetSpatialBlend(
    JNIEnv*, jclass, jlong address, jfloat blend) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setSpatialBlend(std::isfinite(blend) ? std::clamp(blend, 0.0f, 1.0f) : 0.0f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetRoomPreset(
    JNIEnv*, jclass, jlong address, jint preset) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        const int safePreset = std::clamp(static_cast<int>(preset), 0, 6);
        handle->engine.setRoomSimulationPreset(
            static_cast<frostsoulx::RoomSimulationPreset>(safePreset));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetRoomMix(
    JNIEnv*, jclass, jlong address, jfloat wetMix) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setRoomMix(std::isfinite(wetMix) ? std::clamp(wetMix, 0.0f, 1.0f) : 0.0f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetReflectionAmount(
    JNIEnv*, jclass, jlong address, jfloat amount) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setReflectionAmount(std::isfinite(amount) ? std::clamp(amount, 0.0f, 1.0f) : 0.0f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetReverbTimeSeconds(
    JNIEnv*, jclass, jlong address, jfloat seconds) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        const float safeSeconds = std::isfinite(seconds) ? std::clamp(seconds, 0.2f, 8.0f) : 1.35f;
        handle->engine.setReverbTimeSeconds(safeSeconds);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetRoomSize(
    JNIEnv*, jclass, jlong address, jfloat size) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setRoomSize(std::isfinite(size) ? std::clamp(size, 0.0f, 1.0f) : 0.5f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetDampening(
    JNIEnv*, jclass, jlong address, jfloat dampening) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setDampening(std::isfinite(dampening) ? std::clamp(dampening, 0.0f, 1.0f) : 0.5f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetStereoWidth(
    JNIEnv*, jclass, jlong address, jfloat width) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setStereoWidth(std::isfinite(width) ? std::clamp(width, 0.0f, 1.0f) : 0.5f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetCarFader(
    JNIEnv*, jclass, jlong address, jfloat fader) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setCarFader(std::isfinite(fader) ? std::clamp(fader, -1.0f, 1.0f) : 0.0f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetBassGainDb(
    JNIEnv*, jclass, jlong address, jfloat gainDb) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setBassGainDb(std::isfinite(gainDb) ? std::clamp(gainDb, -12.0f, 12.0f) : 0.0f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetTrebleGainDb(
    JNIEnv*, jclass, jlong address, jfloat gainDb) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setTrebleGainDb(std::isfinite(gainDb) ? std::clamp(gainDb, -12.0f, 12.0f) : 0.0f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetOutputGainDb(
    JNIEnv*, jclass, jlong address, jfloat gainDb) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setOutputGainDb(std::isfinite(gainDb) ? std::clamp(gainDb, -24.0f, 12.0f) : 0.0f);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeReadDiagnostics(
    JNIEnv* env, jclass, jlong address) {
    const auto* handle = reinterpret_cast<const Handle*>(address);
    const uint64_t frames = handle != nullptr ? handle->diagnostics.processedFrames.load(std::memory_order_relaxed) : 0;
    const double denominator = frames > 0 ? static_cast<double>(frames) : 1.0;
    const double changedDenominator = frames > 0 ? static_cast<double>(frames) * 2.0 : 1.0;
    const jdouble values[41] = {
        handle != nullptr ? std::sqrt(handle->diagnostics.inputSumSquaresL.load() / denominator) : 0.0,
        handle != nullptr ? std::sqrt(handle->diagnostics.inputSumSquaresR.load() / denominator) : 0.0,
        handle != nullptr ? std::sqrt(handle->diagnostics.outputSumSquaresL.load() / denominator) : 0.0,
        handle != nullptr ? std::sqrt(handle->diagnostics.outputSumSquaresR.load() / denominator) : 0.0,
        handle != nullptr ? handle->diagnostics.inputPeakL.load() : 0.0,
        handle != nullptr ? handle->diagnostics.inputPeakR.load() : 0.0,
        handle != nullptr ? handle->diagnostics.outputPeakL.load() : 0.0,
        handle != nullptr ? handle->diagnostics.outputPeakR.load() : 0.0,
        handle != nullptr ? handle->diagnostics.inputTruePeakL.load() : 0.0,
        handle != nullptr ? handle->diagnostics.inputTruePeakR.load() : 0.0,
        handle != nullptr ? handle->diagnostics.outputTruePeakL.load() : 0.0,
        handle != nullptr ? handle->diagnostics.outputTruePeakR.load() : 0.0,
        handle != nullptr ? handle->diagnostics.inputMinL.load() : 0.0,
        handle != nullptr ? handle->diagnostics.inputMinR.load() : 0.0,
        handle != nullptr ? handle->diagnostics.inputMaxL.load() : 0.0,
        handle != nullptr ? handle->diagnostics.inputMaxR.load() : 0.0,
        handle != nullptr ? handle->diagnostics.outputMinL.load() : 0.0,
        handle != nullptr ? handle->diagnostics.outputMinR.load() : 0.0,
        handle != nullptr ? handle->diagnostics.outputMaxL.load() : 0.0,
        handle != nullptr ? handle->diagnostics.outputMaxR.load() : 0.0,
        handle != nullptr ? handle->diagnostics.maxAbsDifference.load() : 0.0,
        handle != nullptr ? handle->diagnostics.sumAbsDifference.load() / changedDenominator : 0.0,
        handle != nullptr ? 100.0 * static_cast<double>(handle->diagnostics.changedSamples.load()) / changedDenominator : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.nanCount.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.infCount.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.clippedInput.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.clippedOutput.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.processCallCount.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(frames) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.nativeStatus.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.totalBlocks.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.processingTimeNanos.load()) / 1000000.0 : 0.0,
        handle != nullptr && handle->diagnostics.totalBlocks.load() > 0
            ? static_cast<jdouble>(handle->diagnostics.processingTimeNanos.load()) / 1000000.0 / handle->diagnostics.totalBlocks.load() : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.maxProcessingTimeNanos.load()) / 1000000.0 : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.deadlineMisses.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.nativeProcessFailures.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->sampleRate) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->lastHostCallbackFrames.load()) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->quantumFrames.load()) : 0.0,
        handle != nullptr && handle->enabled.load() ? 1.0 : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->encoding) : 0.0,
    };
    const jdoubleArray result = env->NewDoubleArray(41);
    if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 41, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeProcess(
    JNIEnv* env, jclass, jlong address, jobject pcmBuffer, jint frames, jint encoding) {
    auto* handle = reinterpret_cast<Handle*>(address);
    if (handle == nullptr || pcmBuffer == nullptr || frames <= 0) return;

    auto* bytes = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(pcmBuffer));
    if (bytes == nullptr) return;

    const int totalFrames = frames;
    handle->lastHostCallbackFrames.store(totalFrames, std::memory_order_relaxed);
    int frameOffset = 0;
    const int quantumFrames = handle->quantumFrames.load(std::memory_order_relaxed);
    while (frameOffset < totalFrames) {
        const int chunkFrames = std::min(quantumFrames, totalFrames - frameOffset);
        const int samples = chunkFrames * 2;
        const int sampleOffset = frameOffset * 2;

        if (encoding == 4) {
            auto* samplesFloat = reinterpret_cast<float*>(bytes) + sampleOffset;
            std::copy(samplesFloat, samplesFloat + samples, handle->inputSnapshot.begin());
            std::copy(samplesFloat, samplesFloat + samples, handle->scratch.begin());
            const auto started = std::chrono::steady_clock::now();
            const bool processed = handle->enabled.load(std::memory_order_relaxed) && handle->engine.process(handle->scratch.data(), chunkFrames);
            const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - started).count();
            if (handle->enabled.load(std::memory_order_relaxed) && !processed) handle->diagnostics.nativeProcessFailures.fetch_add(1, std::memory_order_relaxed);
            if (processed) std::copy(handle->scratch.begin(), handle->scratch.begin() + samples, samplesFloat);
            else std::copy(handle->inputSnapshot.begin(), handle->inputSnapshot.begin() + samples, samplesFloat);
            handle->diagnostics.nativeStatus.store(handle->enabled.load() ? resultCode(handle->engine.lastProcessResult()) : resultCode(frostsoulx::ImmersiveProcessResult::Disabled), std::memory_order_relaxed);
            if (processed) {
                handle->diagnostics.processingTimeNanos.fetch_add(static_cast<uint64_t>(elapsed), std::memory_order_relaxed);
                atomicMax(handle->diagnostics.maxProcessingTimeNanos, static_cast<uint64_t>(elapsed));
                if (handle->sampleRate > 0 && elapsed > (1000000000LL * chunkFrames) / handle->sampleRate) {
                    handle->diagnostics.deadlineMisses.fetch_add(1, std::memory_order_relaxed);
                }
            }
            publishDiagnostics(*handle, handle->inputSnapshot.data(), samplesFloat, chunkFrames);
            frameOffset += chunkFrames;
            continue;
        }

        if (encoding == 2) {
            auto* samples16 = reinterpret_cast<std::int16_t*>(bytes) + sampleOffset;
            for (int frame = 0; frame < chunkFrames; ++frame) {
                handle->scratch[frame * 2] = readPcm16(samples16[frame * 2]);
                handle->scratch[frame * 2 + 1] = readPcm16(samples16[frame * 2 + 1]);
            }
            std::copy(handle->scratch.begin(), handle->scratch.begin() + samples, handle->inputSnapshot.begin());
            const auto started = std::chrono::steady_clock::now();
            const bool processed = handle->enabled.load(std::memory_order_relaxed) && handle->engine.process(handle->scratch.data(), chunkFrames);
            const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - started).count();
            if (handle->enabled.load(std::memory_order_relaxed) && !processed) handle->diagnostics.nativeProcessFailures.fetch_add(1, std::memory_order_relaxed);
            if (!processed) std::copy(handle->inputSnapshot.begin(), handle->inputSnapshot.begin() + samples, handle->scratch.begin());
            handle->diagnostics.nativeStatus.store(handle->enabled.load() ? resultCode(handle->engine.lastProcessResult()) : resultCode(frostsoulx::ImmersiveProcessResult::Disabled), std::memory_order_relaxed);
            if (processed) {
                handle->diagnostics.processingTimeNanos.fetch_add(static_cast<uint64_t>(elapsed), std::memory_order_relaxed);
                atomicMax(handle->diagnostics.maxProcessingTimeNanos, static_cast<uint64_t>(elapsed));
                if (handle->sampleRate > 0 && elapsed > (1000000000LL * chunkFrames) / handle->sampleRate) {
                    handle->diagnostics.deadlineMisses.fetch_add(1, std::memory_order_relaxed);
                }
            }
            publishDiagnostics(*handle, handle->inputSnapshot.data(), handle->scratch.data(), chunkFrames);
            for (int frame = 0; frame < chunkFrames; ++frame) {
                samples16[frame * 2] = writePcm16(handle->scratch[frame * 2]);
                samples16[frame * 2 + 1] = writePcm16(handle->scratch[frame * 2 + 1]);
            }
            frameOffset += chunkFrames;
            continue;
        }

        handle->diagnostics.nativeStatus.store(resultCode(frostsoulx::ImmersiveProcessResult::InvalidInput), std::memory_order_relaxed);
        return;
    }
}
