#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <memory>

#include "frostsoulx/StereoSurroundProcessor.h"

namespace {
struct SurroundHandle {
    frostsoulx::StereoSurroundProcessor processor;
    std::atomic<bool> enabled{false};
    std::atomic<float> intensity{0.0f};
};

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
    handle->processor.prepare(static_cast<double>(sampleRate), 2, 4096);
    handle->processor.setEnabled(false);
    handle->processor.setIntensity(0.0f);
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
        const float bounded = std::clamp(intensity, 0.0f, 1.0f);
        handle->intensity.store(bounded, std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_StereoSurroundAudioProcessor_nativeProcess(
    JNIEnv* env, jclass, jlong address, jobject pcmBuffer, jint frames, jint encoding) {
    auto* handle = reinterpret_cast<SurroundHandle*>(address);
    if (handle == nullptr || pcmBuffer == nullptr || frames <= 0 ||
        !handle->enabled.load(std::memory_order_relaxed) ||
        handle->intensity.load(std::memory_order_relaxed) <= 0.0f) {
        return;
    }

    auto* bytes = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(pcmBuffer));
    if (bytes == nullptr) return;

    const int boundedFrames = std::min(frames, 8192);
    handle->processor.setEnabled(true);
    handle->processor.setIntensity(handle->intensity.load(std::memory_order_relaxed));

    if (encoding == 4) { // Media3 C.ENCODING_PCM_FLOAT
        handle->processor.process(reinterpret_cast<float*>(bytes), boundedFrames);
        return;
    }

    if (encoding == 2) { // Media3 C.ENCODING_PCM_16BIT
        auto* samples = reinterpret_cast<std::int16_t*>(bytes);
        constexpr int kMaxFrames = 8192;
        float scratch[kMaxFrames * 2]{};
        for (int frame = 0; frame < boundedFrames; ++frame) {
            scratch[frame * 2] = readPcm16(samples[frame * 2]);
            scratch[frame * 2 + 1] = readPcm16(samples[frame * 2 + 1]);
        }
        handle->processor.process(scratch, boundedFrames);
        for (int frame = 0; frame < boundedFrames; ++frame) {
            samples[frame * 2] = writePcm16(scratch[frame * 2]);
            samples[frame * 2 + 1] = writePcm16(scratch[frame * 2 + 1]);
        }
    }
}
