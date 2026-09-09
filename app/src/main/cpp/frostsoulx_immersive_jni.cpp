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
};

struct Handle {
    frostsoulx::ImmersiveAudioEngine engine;
    bool enabled = false;
    std::array<float, kStereoSamples> scratch{};
    std::array<float, kStereoSamples> inputSnapshot{};
    Diagnostics diagnostics{};
};

float sampleRms(const float* values, int samples, float* peak, int* nanCount, int* infCount) noexcept {
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
        *peak = std::max(*peak, std::fabs(value));
        sumSquares += static_cast<double>(value) * static_cast<double>(value);
        ++finiteSamples;
    }
    return finiteSamples > 0 ? static_cast<float>(std::sqrt(sumSquares / finiteSamples)) : 0.0f;
}

void publishDiagnostics(Handle& handle, const float* input, const float* output, int samples) noexcept {
    float inputPeak = 0.0f;
    float outputPeak = 0.0f;
    int inputNan = 0;
    int inputInf = 0;
    int outputNan = 0;
    int outputInf = 0;
    handle.diagnostics.inputRms = sampleRms(input, samples, &inputPeak, &inputNan, &inputInf);
    handle.diagnostics.outputRms = sampleRms(output, samples, &outputPeak, &outputNan, &outputInf);
    handle.diagnostics.inputPeak = inputPeak;
    handle.diagnostics.outputPeak = outputPeak;
    handle.diagnostics.maxAbsDifference = 0.0f;
    int changedSamples = 0;
    for (int index = 0; index < samples; ++index) {
        const float inputValue = input[index];
        const float outputValue = output[index];
        const float difference =
            (std::isfinite(inputValue) && std::isfinite(outputValue))
                ? std::fabs(outputValue - inputValue)
                : (inputValue == outputValue ? 0.0f : 1.0f);
        handle.diagnostics.maxAbsDifference = std::max(handle.diagnostics.maxAbsDifference, difference);
        if (difference > 1.0e-7f) ++changedSamples;
    }
    handle.diagnostics.changedPercentage = samples > 0
        ? 100.0f * static_cast<float>(changedSamples) / static_cast<float>(samples)
        : 0.0f;
    handle.diagnostics.nanCount = inputNan + outputNan;
    handle.diagnostics.infCount = inputInf + outputInf;
    ++handle.diagnostics.processCallCount;
}

float readPcm16(std::int16_t sample) noexcept {
    return static_cast<float>(sample) / 32768.0f;
}

std::int16_t writePcm16(float sample) noexcept {
    const float bounded = std::clamp(sample, -1.0f, 0.9999695f);
    const auto scaled = static_cast<int>(bounded * 32768.0f);
    return static_cast<std::int16_t>(std::clamp(scaled, -32768, 32767));
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeCreate(
    JNIEnv*, jclass, jint sampleRate, jint) {
    auto handle = std::make_unique<Handle>();
    if (!handle->engine.prepare(sampleRate, kMaxFrames)) return 0L;
    handle->engine.setEnabled(false);
    handle->engine.setSpatialBlend(0.0f);
    return reinterpret_cast<jlong>(handle.release());
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
        handle->diagnostics = Diagnostics{};
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetEnabled(
    JNIEnv*, jclass, jlong address, jboolean enabled) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->enabled = enabled == JNI_TRUE;
        handle->engine.setEnabled(handle->enabled);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetSpatialBlend(
    JNIEnv*, jclass, jlong address, jfloat blend) {
    if (auto* handle = reinterpret_cast<Handle*>(address)) {
        handle->engine.setSpatialBlend(std::isfinite(blend) ? std::clamp(blend, 0.0f, 1.0f) : 0.0f);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeReadDiagnostics(
    JNIEnv* env, jclass, jlong address) {
    const auto* handle = reinterpret_cast<const Handle*>(address);
    const jdouble values[9] = {
        handle != nullptr ? handle->diagnostics.inputRms : 0.0,
        handle != nullptr ? handle->diagnostics.outputRms : 0.0,
        handle != nullptr ? handle->diagnostics.inputPeak : 0.0,
        handle != nullptr ? handle->diagnostics.outputPeak : 0.0,
        handle != nullptr ? handle->diagnostics.maxAbsDifference : 0.0,
        handle != nullptr ? handle->diagnostics.changedPercentage : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.nanCount) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.infCount) : 0.0,
        handle != nullptr ? static_cast<jdouble>(handle->diagnostics.processCallCount) : 0.0,
    };
    const jdoubleArray result = env->NewDoubleArray(9);
    if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 9, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeProcess(
    JNIEnv* env, jclass, jlong address, jobject pcmBuffer, jint frames, jint encoding) {
    auto* handle = reinterpret_cast<Handle*>(address);
    if (handle == nullptr || pcmBuffer == nullptr || frames <= 0 || !handle->enabled) return;

    auto* bytes = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(pcmBuffer));
    if (bytes == nullptr) return;
    const int boundedFrames = std::min(frames, kMaxFrames);
    const int samples = boundedFrames * 2;

    if (encoding == 4) {
        auto* samplesFloat = reinterpret_cast<float*>(bytes);
        std::copy(samplesFloat, samplesFloat + samples, handle->inputSnapshot.begin());
        std::copy(samplesFloat, samplesFloat + samples, handle->scratch.begin());
        handle->engine.process(handle->scratch.data(), boundedFrames);
        std::copy(handle->scratch.begin(), handle->scratch.begin() + samples, samplesFloat);
        publishDiagnostics(*handle, handle->inputSnapshot.data(), samplesFloat, samples);
        return;
    }

    if (encoding == 2) {
        auto* samples16 = reinterpret_cast<std::int16_t*>(bytes);
        for (int frame = 0; frame < boundedFrames; ++frame) {
            handle->scratch[frame * 2] = readPcm16(samples16[frame * 2]);
            handle->scratch[frame * 2 + 1] = readPcm16(samples16[frame * 2 + 1]);
        }
        std::copy(handle->scratch.begin(), handle->scratch.begin() + samples, handle->inputSnapshot.begin());
        handle->engine.process(handle->scratch.data(), boundedFrames);
        publishDiagnostics(*handle, handle->inputSnapshot.data(), handle->scratch.data(), samples);
        for (int frame = 0; frame < boundedFrames; ++frame) {
            samples16[frame * 2] = writePcm16(handle->scratch[frame * 2]);
            samples16[frame * 2 + 1] = writePcm16(handle->scratch[frame * 2 + 1]);
        }
    }
}
