#include <jni.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <memory>

#include "frostsoulx/StereoSurroundProcessor.h"

namespace {
constexpr int kMaxFrames = 8192;
constexpr int kStereoSamples = kMaxFrames * 2;

static_assert(std::atomic<float>::is_always_lock_free, "Surround float controls must be lock-free");
static_assert(std::atomic<int>::is_always_lock_free, "Surround diagnostics must be lock-free");

struct SurroundDiagnostics {
    std::atomic<float> inputRms{0.0f};
    std::atomic<float> outputRms{0.0f};
    std::atomic<float> inputPeak{0.0f};
    std::atomic<float> outputPeak{0.0f};
    std::atomic<float> maxAbsDifference{0.0f};
    std::atomic<float> changedPercentage{0.0f};
    std::atomic<int> nanCount{0};
    std::atomic<int> infCount{0};
    std::atomic<int> processCallCount{0};
};

struct SurroundHandle {
    frostsoulx::StereoSurroundProcessor processor;
    std::atomic<bool> enabled{false};
    std::atomic<float> intensity{0.0f};
    std::atomic<float> lowFrequencyCutoffHz{180.0f};
    std::atomic<float> sideExtractionGain{0.5f};
    std::atomic<int> delayASamples{37};
    std::atomic<int> delayBSamples{59};
    std::atomic<float> decorrelationAInputCoefficient{0.28f};
    std::atomic<float> decorrelationBInputCoefficient{0.32f};
    std::atomic<float> sideHighMixBase{0.35f};
    std::atomic<float> sideHighMixIntensitySpan{0.45f};
    std::atomic<float> ambienceDecorrelatedAWeight{0.55f};
    std::atomic<float> rearAmbienceWeight{0.45f};
    std::atomic<float> maxRearContribution{0.22f};
    SurroundDiagnostics diagnostics;
    std::array<float, kStereoSamples> scratch{};
    std::array<float, kStereoSamples> inputSnapshot{};
};

float finiteClamp(float value, float low, float high, float fallback) noexcept {
    if (!std::isfinite(value)) return fallback;
    return std::clamp(value, low, high);
}

int clampDelay(jint value) noexcept {
    return std::clamp(value, 1, 255);
}

void applyParameters(SurroundHandle& handle) noexcept {
    frostsoulx::StereoSurroundProcessor::Parameters parameters;
    parameters.lowFrequencyCutoffHz =
        finiteClamp(handle.lowFrequencyCutoffHz.load(std::memory_order_relaxed), 20.0f, 2000.0f, 180.0f);
    parameters.sideExtractionGain =
        finiteClamp(handle.sideExtractionGain.load(std::memory_order_relaxed), 0.0f, 1.0f, 0.5f);
    parameters.delayASamples = static_cast<std::size_t>(clampDelay(handle.delayASamples.load(std::memory_order_relaxed)));
    parameters.delayBSamples = static_cast<std::size_t>(clampDelay(handle.delayBSamples.load(std::memory_order_relaxed)));
    parameters.decorrelationAInputCoefficient =
        finiteClamp(handle.decorrelationAInputCoefficient.load(std::memory_order_relaxed), 0.0f, 1.0f, 0.28f);
    parameters.decorrelationBInputCoefficient =
        finiteClamp(handle.decorrelationBInputCoefficient.load(std::memory_order_relaxed), 0.0f, 1.0f, 0.32f);
    parameters.sideHighMixBase =
        finiteClamp(handle.sideHighMixBase.load(std::memory_order_relaxed), 0.0f, 1.0f, 0.35f);
    parameters.sideHighMixIntensitySpan =
        finiteClamp(handle.sideHighMixIntensitySpan.load(std::memory_order_relaxed), 0.0f, 1.0f, 0.45f);
    parameters.ambienceDecorrelatedAWeight =
        finiteClamp(handle.ambienceDecorrelatedAWeight.load(std::memory_order_relaxed), 0.0f, 1.0f, 0.55f);
    parameters.rearAmbienceWeight =
        finiteClamp(handle.rearAmbienceWeight.load(std::memory_order_relaxed), 0.0f, 1.0f, 0.45f);
    parameters.maxRearContribution =
        finiteClamp(handle.maxRearContribution.load(std::memory_order_relaxed), 0.0f, 0.5f, 0.22f);
    handle.processor.setParameters(parameters);
}

float sampleInputRms(const float* values, int samples, float* peak,     int* nanCount, int* infCount) noexcept {

    double sumSquares = 0.0;
    int finiteSamples = 0;
    *peak = 0.0f;
    *nanCount = 0;
    *infCount = 0;
    for (int index = 0; index < samples; ++index) {
        const float value = values[index];
        if (std::isnan(value)) {
            ++*nanCount;
            continue;
        }
        if (std::isinf(value)) {
            ++*infCount;
            continue;
        }
        const float absolute = std::fabs(value);
        *peak = std::max(*peak, absolute);
        sumSquares += static_cast<double>(value) * static_cast<double>(value);
        ++finiteSamples;
    }
    return finiteSamples > 0 ? static_cast<float>(std::sqrt(sumSquares / finiteSamples)) : 0.0f;
}

void publishDiagnostics(
    SurroundHandle& handle,
    const float* input,
    const float* output,
    int samples) noexcept {
    float inputPeak = 0.0f;
    float outputPeak = 0.0f;
    int inputNan = 0;
    int inputInf = 0;
    int outputNan = 0;
    int outputInf = 0;
    const float inputRms = sampleInputRms(input, samples, &inputPeak, &inputNan, &inputInf);
    const float outputRms = sampleInputRms(output, samples, &outputPeak, &outputNan, &outputInf);
    float maximumDifference = 0.0f;
    long long changedSamples = 0;
    for (int index = 0; index < samples; ++index) {
        const float inputValue = input[index];
        const float outputValue = output[index];
        const float difference =
            (std::isfinite(inputValue) && std::isfinite(outputValue))
                ? std::fabs(outputValue - inputValue)
                : (inputValue == outputValue ? 0.0f : 1.0f);
        maximumDifference = std::max(maximumDifference, difference);
        if (difference > 1.0e-7f) ++changedSamples;
    }
    handle.diagnostics.inputRms.store(inputRms, std::memory_order_relaxed);
    handle.diagnostics.outputRms.store(outputRms, std::memory_order_relaxed);
    handle.diagnostics.inputPeak.store(inputPeak, std::memory_order_relaxed);
    handle.diagnostics.outputPeak.store(outputPeak, std::memory_order_relaxed);
    handle.diagnostics.maxAbsDifference.store(maximumDifference, std::memory_order_relaxed);
    handle.diagnostics.changedPercentage.store(
        samples > 0 ? (100.0f * static_cast<float>(changedSamples) / static_cast<float>(samples)) : 0.0f,
        std::memory_order_relaxed);
    handle.diagnostics.nanCount.store(inputNan + outputNan, std::memory_order_relaxed);
    handle.diagnostics.infCount.store(inputInf + outputInf, std::memory_order_relaxed);
    handle.diagnostics.processCallCount.fetch_add(1, std::memory_order_relaxed);
}

float readPcm16(const std::int16_t sample) noexcept {
    return static_cast<float>(sample) / 32768.0f;
}

std::int16_t writePcm16(float sample) noexcept {
    const float bounded = std::clamp(sample, -1.0f, 0.9999695f);
    const auto scaled = static_cast<int>(bounded * 32768.0f);
    return static_cast<std::int16_t>(std::clamp(scaled, -32768, 32767));
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeCreate(
    JNIEnv*, jclass, jint sampleRate, jint encoding) {
    auto* handle = new SurroundHandle();
    handle->processor.prepare(static_cast<double>(sampleRate), 2, kMaxFrames);
    handle->processor.setEnabled(false);
    handle->processor.setIntensity(0.0f);
    applyParameters(*handle);
    (void)encoding;
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeRelease(
    JNIEnv*, jclass, jlong address) {
    delete reinterpret_cast<SurroundHandle*>(address);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeReset(
    JNIEnv*, jclass, jlong address) {
    if (auto* handle = reinterpret_cast<SurroundHandle*>(address)) {
        handle->processor.reset();
        handle->diagnostics.inputRms.store(0.0f, std::memory_order_relaxed);
        handle->diagnostics.outputRms.store(0.0f, std::memory_order_relaxed);
        handle->diagnostics.inputPeak.store(0.0f, std::memory_order_relaxed);
        handle->diagnostics.outputPeak.store(0.0f, std::memory_order_relaxed);
        handle->diagnostics.maxAbsDifference.store(0.0f, std::memory_order_relaxed);
        handle->diagnostics.changedPercentage.store(0.0f, std::memory_order_relaxed);
        handle->diagnostics.nanCount.store(0, std::memory_order_relaxed);
        handle->diagnostics.infCount.store(0, std::memory_order_relaxed);
        handle->diagnostics.processCallCount.store(0, std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeSetEnabled(
    JNIEnv*, jclass, jlong address, jboolean enabled) {
    if (auto* handle = reinterpret_cast<SurroundHandle*>(address)) {
        handle->enabled.store(enabled == JNI_TRUE, std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeSetIntensity(
    JNIEnv*, jclass, jlong address, jfloat intensity) {
    if (auto* handle = reinterpret_cast<SurroundHandle*>(address)) {
        handle->intensity.store(finiteClamp(intensity, 0.0f, 1.0f, 0.0f), std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeSetParameters(
    JNIEnv*, jclass, jlong address,     jfloat lowCutoffHz, jfloat sideGain, jint delayA, jint delayB,

    jfloat decorA, jfloat decorB, jfloat sideBase, jfloat sideSpan,
    jfloat ambienceAWeight, jfloat rearAmbienceWeight, jfloat maxRearContribution) {
    if (auto* handle = reinterpret_cast<SurroundHandle*>(address)) {
        handle->lowFrequencyCutoffHz.store(finiteClamp(lowCutoffHz, 20.0f, 2000.0f, 180.0f), std::memory_order_relaxed);
        handle->sideExtractionGain.store(finiteClamp(sideGain, 0.0f, 1.0f, 0.5f), std::memory_order_relaxed);
        handle->delayASamples.store(clampDelay(delayA), std::memory_order_relaxed);
        handle->delayBSamples.store(clampDelay(delayB), std::memory_order_relaxed);
        handle->decorrelationAInputCoefficient.store(finiteClamp(decorA, 0.0f, 1.0f, 0.28f), std::memory_order_relaxed);
        handle->decorrelationBInputCoefficient.store(finiteClamp(decorB, 0.0f, 1.0f, 0.32f), std::memory_order_relaxed);
        handle->sideHighMixBase.store(finiteClamp(sideBase, 0.0f, 1.0f, 0.35f), std::memory_order_relaxed);
        handle->sideHighMixIntensitySpan.store(finiteClamp(sideSpan, 0.0f, 1.0f, 0.45f), std::memory_order_relaxed);
        handle->ambienceDecorrelatedAWeight.store(finiteClamp(ambienceAWeight, 0.0f, 1.0f, 0.55f), std::memory_order_relaxed);
        handle->rearAmbienceWeight.store(finiteClamp(rearAmbienceWeight, 0.0f, 1.0f, 0.45f), std::memory_order_relaxed);
        handle->maxRearContribution.store(finiteClamp(maxRearContribution, 0.0f, 0.5f, 0.22f), std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeReadDiagnostics(
    JNIEnv* env, jclass, jlong address) {
    const auto* handle = reinterpret_cast<const SurroundHandle*>(address);
    jdouble values[9] = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    if (handle != nullptr) {
        values[0] = handle->diagnostics.inputRms.load(std::memory_order_relaxed);
        values[1] = handle->diagnostics.outputRms.load(std::memory_order_relaxed);
        values[2] = handle->diagnostics.inputPeak.load(std::memory_order_relaxed);
        values[3] = handle->diagnostics.outputPeak.load(std::memory_order_relaxed);
        values[4] = handle->diagnostics.maxAbsDifference.load(std::memory_order_relaxed);
        values[5] = handle->diagnostics.changedPercentage.load(std::memory_order_relaxed);
        values[6] = static_cast<jdouble>(handle->diagnostics.nanCount.load(std::memory_order_relaxed));
        values[7] = static_cast<jdouble>(handle->diagnostics.infCount.load(std::memory_order_relaxed));
        values[8] = static_cast<jdouble>(handle->diagnostics.processCallCount.load(std::memory_order_relaxed));
    }
    const jdoubleArray result = env->NewDoubleArray(9);
    if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 9, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeProcess(
    JNIEnv* env, jclass, jlong address, jobject pcmBuffer, jint frames, jint encoding) {
    auto* handle = reinterpret_cast<SurroundHandle*>(address);
    if (handle == nullptr || pcmBuffer == nullptr || frames <= 0 ||
        !handle->enabled.load(std::memory_order_relaxed)) {
        return;
    }

    auto* bytes = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(pcmBuffer));
    if (bytes == nullptr) return;

    const int boundedFrames = std::min(frames, kMaxFrames);
    const int samples = boundedFrames * 2;
    const float currentIntensity = handle->intensity.load(std::memory_order_relaxed);
    applyParameters(*handle);
    handle->processor.setEnabled(true);
    handle->processor.setIntensity(currentIntensity);

    if (encoding == 4) { // Media3 C.ENCODING_PCM_FLOAT
        auto* samplesFloat = reinterpret_cast<float*>(bytes);
        std::copy(samplesFloat, samplesFloat + samples, handle->inputSnapshot.begin());
        if (currentIntensity > 0.0f) {
            handle->processor.process(samplesFloat, boundedFrames);
        }
        publishDiagnostics(*handle, handle->inputSnapshot.data(), samplesFloat, samples);
        return;
    }

    if (encoding == 2) { // Media3 C.ENCODING_PCM_16BIT
        auto* samples16 = reinterpret_cast<std::int16_t*>(bytes);
        for (int frame = 0; frame < boundedFrames; ++frame) {
            handle->scratch[frame * 2] = readPcm16(samples16[frame * 2]);
            handle->scratch[frame * 2 + 1] = readPcm16(samples16[frame * 2 + 1]);
        }
        std::copy(handle->scratch.begin(), handle->scratch.begin() + samples, handle->inputSnapshot.begin());
        if (currentIntensity > 0.0f) {
            handle->processor.process(handle->scratch.data(), boundedFrames);
        }
        publishDiagnostics(*handle, handle->inputSnapshot.data(), handle->scratch.data(), samples);
        for (int frame = 0; frame < boundedFrames; ++frame) {
            samples16[frame * 2] = writePcm16(handle->scratch[frame * 2]);
            samples16[frame * 2 + 1] = writePcm16(handle->scratch[frame * 2 + 1]);
        }
    }
}
