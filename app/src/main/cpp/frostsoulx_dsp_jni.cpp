#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <cmath>
#include <memory>
#include <new>

#include "frostsoulx/dsp/sound_field.h"

namespace {
constexpr char kTag[] = "FrostSoulX-DSP";
constexpr std::size_t kChunkFrames = 1024;

struct NativeDsp final {
    frostsoulx::dsp::SoundFieldProcessor processor;
    std::atomic<bool> enabled{false};
    std::atomic<int> preset{0};
    std::atomic<float> intensity{0.5f};
    std::atomic<float> width{1.0f};
    std::atomic<float> crossfeed{0.0f};
    std::atomic<float> lowFrequencyProtection{0.85f};
    std::atomic<float> surround{0.0f};
    std::atomic<float> reverbMix{0.0f};
    std::atomic<float> reverbRoomSize{0.5f};
    std::atomic<float> reverbDecay{0.45f};
    std::atomic<float> outputGainDb{0.0f};
    std::atomic<float> limiterCeilingDb{-1.0f};
    std::atomic<bool> hrtfEnabled{false};
    std::atomic<float> hrtfMix{0.85f};
    std::atomic<float> hrtfAzimuth{0.0f};
    std::atomic<float> hrtfElevation{0.0f};
    // Audio-thread-owned diagnostics; emitted at most once per ~1 second of PCM.
    std::uint64_t diagnosticsFrames = 0;
    std::uint64_t diagnosticsLimiterChunks = 0;
    float diagnosticsInputPeak = 0.0f;
    float diagnosticsOutputPeak = 0.0f;

    void applyParameters() noexcept {
        using namespace frostsoulx::dsp;
        SoundFieldParameters parameters;
        parameters.enabled = enabled.load(std::memory_order_relaxed);
        parameters.preset = static_cast<SoundFieldPreset>(std::clamp(preset.load(std::memory_order_relaxed), 0, 4));
        parameters.intensity = intensity.load(std::memory_order_relaxed);
        parameters.width = width.load(std::memory_order_relaxed);
        parameters.crossfeed = crossfeed.load(std::memory_order_relaxed);
        parameters.lowFrequencyProtection = lowFrequencyProtection.load(std::memory_order_relaxed);
        parameters.surround = surround.load(std::memory_order_relaxed);
        parameters.reverbMix = reverbMix.load(std::memory_order_relaxed);
        parameters.reverbRoomSize = reverbRoomSize.load(std::memory_order_relaxed);
        parameters.reverbDecay = reverbDecay.load(std::memory_order_relaxed);
        parameters.outputGainDb = outputGainDb.load(std::memory_order_relaxed);
        parameters.limiterCeilingDb = limiterCeilingDb.load(std::memory_order_relaxed);
        parameters.hrtfEnabled = hrtfEnabled.load(std::memory_order_relaxed);
        parameters.hrtfMix = hrtfMix.load(std::memory_order_relaxed);
        parameters.hrtfAzimuth = hrtfAzimuth.load(std::memory_order_relaxed);
        parameters.hrtfElevation = hrtfElevation.load(std::memory_order_relaxed);
        processor.setParameters(parameters);
    }
};

NativeDsp* fromHandle(jlong handle) noexcept {
    return reinterpret_cast<NativeDsp*>(handle);
}

void setFloat(std::atomic<float>& target, jfloat value) noexcept {
    if (std::isfinite(value)) target.store(value, std::memory_order_relaxed);
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_vxs_frostsoulx_playback_NativeSpatialDspAudioProcessor_nativeCreate(
    JNIEnv*,
    jobject,
    jint sampleRate
) {
    auto* dsp = new (std::nothrow) NativeDsp();
    if (dsp == nullptr) return 0;
    dsp->processor.prepare({static_cast<double>(std::max(sampleRate, 8000)), 2});
    dsp->applyParameters();
    __android_log_print(ANDROID_LOG_INFO, kTag, "DSP created at %d Hz", sampleRate);
    return reinterpret_cast<jlong>(dsp);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_NativeSpatialDspAudioProcessor_nativeRelease(
    JNIEnv*, jobject, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_NativeSpatialDspAudioProcessor_nativeReset(
    JNIEnv*, jobject, jlong handle) {
    if (auto* dsp = fromHandle(handle)) {
        dsp->processor.reset();
        dsp->applyParameters();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_NativeSpatialDspAudioProcessor_nativeSetEnabled(
    JNIEnv*, jobject, jlong handle, jboolean value) {
    if (auto* dsp = fromHandle(handle)) dsp->enabled.store(value == JNI_TRUE, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_NativeSpatialDspAudioProcessor_nativeSetPreset(
    JNIEnv*, jobject, jlong handle, jint value) {
    if (auto* dsp = fromHandle(handle)) dsp->preset.store(std::clamp(value, 0, 4), std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_NativeSpatialDspAudioProcessor_nativeSetParameters(
    JNIEnv*, jobject, jlong handle, jfloat intensity, jfloat width, jfloat crossfeed,
    jfloat lowFrequencyProtection, jfloat surround, jfloat reverbMix, jfloat reverbRoomSize,
    jfloat reverbDecay, jfloat outputGainDb, jfloat limiterCeilingDb,
    jboolean hrtfEnabled, jfloat hrtfMix, jfloat hrtfAzimuth, jfloat hrtfElevation) {
    auto* dsp = fromHandle(handle);
    if (dsp == nullptr) return;
    setFloat(dsp->intensity, intensity);
    setFloat(dsp->width, width);
    setFloat(dsp->crossfeed, crossfeed);
    setFloat(dsp->lowFrequencyProtection, lowFrequencyProtection);
    setFloat(dsp->surround, surround);
    setFloat(dsp->reverbMix, reverbMix);
    setFloat(dsp->reverbRoomSize, reverbRoomSize);
    setFloat(dsp->reverbDecay, reverbDecay);
    setFloat(dsp->outputGainDb, outputGainDb);
    setFloat(dsp->limiterCeilingDb, limiterCeilingDb);
    dsp->hrtfEnabled.store(hrtfEnabled == JNI_TRUE, std::memory_order_relaxed);
    setFloat(dsp->hrtfMix, hrtfMix);
    setFloat(dsp->hrtfAzimuth, hrtfAzimuth);
    setFloat(dsp->hrtfElevation, hrtfElevation);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vxs_frostsoulx_playback_NativeSpatialDspAudioProcessor_nativeProcess(
    JNIEnv* env, jobject, jlong handle, jobject pcmBuffer, jint frames) {
    auto* dsp = fromHandle(handle);
    auto* samples = static_cast<std::int16_t*>(env->GetDirectBufferAddress(pcmBuffer));
    const jlong capacity = env->GetDirectBufferCapacity(pcmBuffer);
    if (dsp == nullptr || samples == nullptr || frames <= 0 || capacity < static_cast<jlong>(frames) * 4) return;

    dsp->applyParameters();
    std::array<float, kChunkFrames * 2> work{};
    int remaining = frames;
    int offset = 0;
    while (remaining > 0) {
        const int chunk = std::min(remaining, static_cast<int>(kChunkFrames));
        float inputPeak = 0.0f;
        for (int i = 0; i < chunk * 2; ++i) {
            work[static_cast<std::size_t>(i)] = static_cast<float>(samples[offset * 2 + i]) / 32768.0f;
            inputPeak = std::max(inputPeak, std::fabs(work[static_cast<std::size_t>(i)]));
        }
        dsp->processor.process(work.data(), static_cast<std::size_t>(chunk));
        constexpr float kSafePeak = 0.8912509f; // -1 dBFS headroom before the platform output stage.
        float peak = 0.0f;
        for (int i = 0; i < chunk * 2; ++i) {
            peak = std::max(peak, std::fabs(work[static_cast<std::size_t>(i)]));
        }
        const bool limiterApplied = peak > kSafePeak;
        if (limiterApplied) {
            const float scale = kSafePeak / peak;
            for (int i = 0; i < chunk * 2; ++i) {
                work[static_cast<std::size_t>(i)] *= scale;
            }
            ++dsp->diagnosticsLimiterChunks;
        }
        dsp->diagnosticsFrames += static_cast<std::uint64_t>(chunk);
        dsp->diagnosticsInputPeak = std::max(dsp->diagnosticsInputPeak, inputPeak);
        dsp->diagnosticsOutputPeak = std::max(dsp->diagnosticsOutputPeak, peak);
        if (dsp->diagnosticsFrames >= 48000U) {
            __android_log_print(
                ANDROID_LOG_INFO,
                kTag,
                "audio_diag frames=%llu dsp=%d input_peak=%.5f output_peak=%.5f limiter_chunks=%llu",
                static_cast<unsigned long long>(dsp->diagnosticsFrames),
                dsp->enabled.load(std::memory_order_relaxed) ? 1 : 0,
                dsp->diagnosticsInputPeak,
                dsp->diagnosticsOutputPeak,
                static_cast<unsigned long long>(dsp->diagnosticsLimiterChunks));
            dsp->diagnosticsFrames = 0;
            dsp->diagnosticsLimiterChunks = 0;
            dsp->diagnosticsInputPeak = 0.0f;
            dsp->diagnosticsOutputPeak = 0.0f;
        }
        for (int i = 0; i < chunk * 2; ++i) {
            const float clamped = std::clamp(work[static_cast<std::size_t>(i)], -1.0f, 0.999969f);
            samples[offset * 2 + i] = static_cast<std::int16_t>(clamped * 32767.0f);
        }
        offset += chunk;
        remaining -= chunk;
    }
}
