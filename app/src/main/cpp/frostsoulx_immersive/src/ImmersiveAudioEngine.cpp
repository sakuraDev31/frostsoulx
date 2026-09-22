#include "frostsoulx/ImmersiveAudioEngine.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <vector>

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
#include <phonon.h>
#endif

namespace frostsoulx {

namespace {
constexpr float kInputSanitizeLimit = 2.0f;
constexpr float kOutputCeiling = 0.98f;
constexpr float kZeroEpsilon = 1.0e-12f;
// Flush denormals. Sustained denormal arithmetic inside the reverb tank is a
// known source of CPU spikes on mobile cores, which shows up as crackle.
constexpr float kDenormalFloor = 1.0e-18f;

inline float sanitizeInputSample(float sample) noexcept {
    if (!std::isfinite(sample)) return 0.0f;
    return std::clamp(sample, -kInputSanitizeLimit, kInputSanitizeLimit);
}

inline float flushDenormal(float value) noexcept {
    return std::fabs(value) < kDenormalFloor ? 0.0f : value;
}

inline int msToSamples(float milliseconds, int sampleRate) noexcept {
    const float samples = (milliseconds * 0.001f) * static_cast<float>(sampleRate);
    return std::max(1, static_cast<int>(std::lround(samples)));
}

inline float clampUnit(float value) noexcept {
    return std::isfinite(value) ? std::clamp(value, 0.0f, 1.0f) : 0.0f;
}

inline float dbToLinear(float db) noexcept {
    return std::pow(10.0f, db / 20.0f);
}

inline float linearToDb(float linear) noexcept {
    return linear > 1.0e-7f ? 20.0f * std::log10(linear) : 0.0f;
}

// Raised-cosine shaping turns a linear 0..1 ramp into a click-free fade with
// zero slope at both ends.
inline float smoothRamp(float linearPosition) noexcept {
    const float clamped = std::clamp(linearPosition, 0.0f, 1.0f);
    return 0.5f - 0.5f * std::cos(clamped * 3.14159265358979323846f);
}

/**
 * Stereo peak limiter with a smoothed gain envelope.
 *
 * Every gain-producing stage owns one of these, so a boost applied in one stage
 * is contained by that same stage instead of being dumped on a single shared
 * limiter at the very end of the chain.
 */
struct StageLimiter {
    float threshold = 0.96f;
    float gain = 1.0f;
    float attackCoeff = 0.0f;
    float releaseCoeff = 0.0f;
    float minGain = 0.05f;
    float maxReduction = 0.0f;

    void configure(int sampleRate, float thresholdValue, float attackSeconds, float releaseSeconds) noexcept {
        threshold = thresholdValue;
        const float rate = static_cast<float>(std::max(sampleRate, 8000));
        attackCoeff = std::exp(-1.0f / (attackSeconds * rate));
        releaseCoeff = std::exp(-1.0f / (releaseSeconds * rate));
        gain = 1.0f;
        maxReduction = 0.0f;
    }

    void reset() noexcept {
        gain = 1.0f;
        maxReduction = 0.0f;
    }

    void process(float& left, float& right) noexcept {
        const float peak = std::max(std::fabs(left), std::fabs(right));
        const float target = peak > threshold ? std::max(threshold / peak, minGain) : 1.0f;
        if (target < gain) {
            gain += (target - gain) * (1.0f - attackCoeff);
        } else {
            gain += (1.0f - gain) * (1.0f - releaseCoeff);
            gain = std::min(gain, 1.0f);
        }
        gain = std::isfinite(gain) ? std::clamp(gain, minGain, 1.0f) : 1.0f;
        maxReduction = std::max(maxReduction, 1.0f - gain);
        left *= gain;
        right *= gain;
    }

    float reductionDb() const noexcept {
        return maxReduction <= 1.0e-6f ? 0.0f : -linearToDb(1.0f - maxReduction);
    }
};

/** Transposed direct-form II biquad, one instance per channel. */
struct Biquad {
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f, a1 = 0.0f, a2 = 0.0f;
    float z1 = 0.0f, z2 = 0.0f;

    void reset() noexcept {
        z1 = 0.0f;
        z2 = 0.0f;
    }

    void setBypass() noexcept {
        b0 = 1.0f; b1 = 0.0f; b2 = 0.0f; a1 = 0.0f; a2 = 0.0f;
    }

    void setLowShelf(float sampleRate, float frequency, float gainDb, float slope) noexcept {
        const float a = std::pow(10.0f, gainDb / 40.0f);
        const float w0 = 2.0f * 3.14159265358979323846f * frequency / sampleRate;
        const float cosW0 = std::cos(w0);
        const float sinW0 = std::sin(w0);
        const float alpha = sinW0 * 0.5f * std::sqrt((a + 1.0f / a) * (1.0f / slope - 1.0f) + 2.0f);
        const float twoSqrtAAlpha = 2.0f * std::sqrt(a) * alpha;
        const float a0 = (a + 1.0f) + (a - 1.0f) * cosW0 + twoSqrtAAlpha;
        if (!std::isfinite(a0) || std::fabs(a0) < 1.0e-12f) { setBypass(); return; }
        const float inv = 1.0f / a0;
        b0 = a * ((a + 1.0f) - (a - 1.0f) * cosW0 + twoSqrtAAlpha) * inv;
        b1 = 2.0f * a * ((a - 1.0f) - (a + 1.0f) * cosW0) * inv;
        b2 = a * ((a + 1.0f) - (a - 1.0f) * cosW0 - twoSqrtAAlpha) * inv;
        a1 = -2.0f * ((a - 1.0f) + (a + 1.0f) * cosW0) * inv;
        a2 = ((a + 1.0f) + (a - 1.0f) * cosW0 - twoSqrtAAlpha) * inv;
    }

    void setHighShelf(float sampleRate, float frequency, float gainDb, float slope) noexcept {
        const float a = std::pow(10.0f, gainDb / 40.0f);
        const float w0 = 2.0f * 3.14159265358979323846f * frequency / sampleRate;
        const float cosW0 = std::cos(w0);
        const float sinW0 = std::sin(w0);
        const float alpha = sinW0 * 0.5f * std::sqrt((a + 1.0f / a) * (1.0f / slope - 1.0f) + 2.0f);
        const float twoSqrtAAlpha = 2.0f * std::sqrt(a) * alpha;
        const float a0 = (a + 1.0f) - (a - 1.0f) * cosW0 + twoSqrtAAlpha;
        if (!std::isfinite(a0) || std::fabs(a0) < 1.0e-12f) { setBypass(); return; }
        const float inv = 1.0f / a0;
        b0 = a * ((a + 1.0f) + (a - 1.0f) * cosW0 + twoSqrtAAlpha) * inv;
        b1 = -2.0f * a * ((a - 1.0f) + (a + 1.0f) * cosW0) * inv;
        b2 = a * ((a + 1.0f) + (a - 1.0f) * cosW0 - twoSqrtAAlpha) * inv;
        a1 = 2.0f * ((a - 1.0f) - (a + 1.0f) * cosW0) * inv;
        a2 = ((a + 1.0f) - (a - 1.0f) * cosW0 - twoSqrtAAlpha) * inv;
    }

    inline float process(float input) noexcept {
        const float output = b0 * input + z1;
        z1 = flushDenormal(b1 * input - a1 * output + z2);
        z2 = flushDenormal(b2 * input - a2 * output);
        return output;
    }
};

} // namespace

struct ImmersiveAudioEngine::Impl {
    // Steam Audio binaural effects require a fixed block length. Every call to
    // iplBinauralEffectApply must therefore receive exactly this many frames.
    static constexpr int kSteamAudioFrameSize = 384;
    static constexpr float kMaxReverbTimeSeconds = 8.0f;
    static constexpr float kMinReverbTimeSeconds = 0.2f;
    // Bypass <-> processed crossfade length.
    static constexpr float kTransitionSeconds = 0.120f;

    int sampleRate = 0;
    int maxFrames = 0;
    bool prepared = false;
    bool enabled = false;
    float spatialBlend = 1.0f;
    ImmersiveProcessResult lastResult = ImmersiveProcessResult::NotPrepared;
    int lastState = -1;

    RoomSimulationPreset roomPreset = RoomSimulationPreset::Studio;
    float roomMix = 0.18f;
    float reflectionAmount = 0.28f;
    float reverbTimeSeconds = 1.35f;
    float damping = 0.42f;

    // Normalized UI controls exposed to sliders/knobs.
    float roomSizeNorm = 0.5f;
    float dampeningNorm = 0.5f;
    float widthNorm = 0.5f;

    // Tone stage controls.
    float bassGainDbValue = 0.0f;
    float trebleGainDbValue = 0.0f;
    float outputGainDbValue = 0.0f;
    float outputGainLinear = 1.0f;
    float smoothedOutputGain = 1.0f;
    float gainSmoothCoeff = 0.0f;
    Biquad bassFilterL, bassFilterR;
    Biquad trebleFilterL, trebleFilterR;
    StageLimiter bassLimiter;
    StageLimiter trebleLimiter;
    StageLimiter outputLimiter;
    StageLimiter spatialLimiter;

    // Derived room shaping values.
    float delayScale = 1.0f;
    float decorrelationSkew = 1.13f;
    float reflectionCrossFeed = 0.15f;

    // Fixed-size Steam Audio scratch (deinterleaved).
    std::array<float, kSteamAudioFrameSize> inputLeft{};
    std::array<float, kSteamAudioFrameSize> inputRight{};
    std::array<float, kSteamAudioFrameSize> outputLeft{};
    std::array<float, kSteamAudioFrameSize> outputRight{};
    float* inputChannels[2] = {nullptr, nullptr};
    float* outputChannels[2] = {nullptr, nullptr};

    // ---------------------------------------------------------------------
    // Block alignment FIFOs.
    //
    // Media3 hands us arbitrary buffer sizes (commonly 1024 or 1152 frames).
    // The previous implementation zero-padded every leftover partial chunk up
    // to 384 frames and discarded the effect tail, which injected a step
    // discontinuity into the HRTF convolution on every single block. That is
    // the continuous ~8 ms crackle. These FIFOs guarantee the effect only ever
    // sees complete, gapless 384-frame blocks.
    // ---------------------------------------------------------------------
    std::array<float, kSteamAudioFrameSize * 2> pendingInput{};
    int pendingInputFrames = 0;
    std::vector<float> outputFifo; // interleaved stereo
    int outputFifoRead = 0;
    int outputFifoWrite = 0;
    int outputFifoFrames = 0;
    int outputFifoCapacityFrames = 0;


    // Lightweight room/reflection/reverb simulation buffers.
    std::vector<float> reflectionDelayLeft;
    std::vector<float> reflectionDelayRight;
    std::vector<float> reverbDelayLeft;
    std::vector<float> reverbDelayRight;
    int reflectionWriteIndex = 0;
    int reverbWriteIndex = 0;

    std::array<int, 6> reflectionTapsL{};
    std::array<int, 6> reflectionTapsR{};
    std::array<float, 6> reflectionGains{};
    int reflectionTapCount = 0;

    int reverbTapL = 1;
    int reverbTapR = 1;
    float reverbFeedback = 0.72f;
    float reverbInputScale = 0.28f;
    float reverbLowpassL = 0.0f;
    float reverbLowpassR = 0.0f;

    // Bypass crossfade state.
    float transitionPosition = 0.0f; // linear 0..1
    float transitionStep = 0.0f;
    // True while process() is taking the real-bypass early return. Used to
    // re-prime the DSP exactly once, on the audio thread, when playback
    // re-enters the processed path.
    bool bypassed = true;

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    IPLContext context = nullptr;
    IPLHRTF hrtf = nullptr;
    IPLBinauralEffect effect = nullptr;
#endif

    bool transitionActive() const noexcept {
        const float target = enabled ? 1.0f : 0.0f;
        return std::fabs(transitionPosition - target) > 1.0e-4f;
    }

    void updateToneStage() noexcept {
        if (sampleRate <= 0) return;
        const float rate = static_cast<float>(sampleRate);
        const float bassFrequency = std::min(200.0f, rate * 0.25f);
        const float trebleFrequency = std::min(4000.0f, rate * 0.45f);

        if (std::fabs(bassGainDbValue) < 0.05f) {
            bassFilterL.setBypass();
            bassFilterR.setBypass();
        } else {
            bassFilterL.setLowShelf(rate, bassFrequency, bassGainDbValue, 0.7f);
            bassFilterR.setLowShelf(rate, bassFrequency, bassGainDbValue, 0.7f);
        }

        if (std::fabs(trebleGainDbValue) < 0.05f) {
            trebleFilterL.setBypass();
            trebleFilterR.setBypass();
        } else {
            trebleFilterL.setHighShelf(rate, trebleFrequency, trebleGainDbValue, 0.7f);
            trebleFilterR.setHighShelf(rate, trebleFrequency, trebleGainDbValue, 0.7f);
        }

        outputGainLinear = dbToLinear(std::clamp(outputGainDbValue, kMinOutputGainDb, kMaxOutputGainDb));
    }

    void updateRoomModel() noexcept {
        // Defaults are intentionally conservative for mobile thermal limits.
        float tapDelaysMs[6] = {14.0f, 23.0f, 37.0f, 51.0f, 0.0f, 0.0f};
        float tapGains[6] = {0.38f, 0.26f, 0.19f, 0.14f, 0.0f, 0.0f};
        reflectionTapCount = 4;
        damping = 0.42f;

        delayScale = 0.60f + 1.00f * roomSizeNorm;
        // Wider rooms should spread channels more; narrow rooms should mono-ish collapse.
        decorrelationSkew = 1.00f + 0.28f * widthNorm;
        reflectionCrossFeed = 0.26f - 0.24f * widthNorm;

        switch (roomPreset) {
            case RoomSimulationPreset::Off:
                break;
            case RoomSimulationPreset::SmallRoom:
                tapDelaysMs[0] = 9.0f;
                tapDelaysMs[1] = 15.0f;
                tapDelaysMs[2] = 23.0f;
                tapDelaysMs[3] = 31.0f;
                tapGains[0] = 0.45f;
                tapGains[1] = 0.33f;
                tapGains[2] = 0.24f;
                tapGains[3] = 0.17f;
                damping = 0.34f;
                break;
            case RoomSimulationPreset::Studio:
                tapDelaysMs[0] = 12.0f;
                tapDelaysMs[1] = 19.0f;
                tapDelaysMs[2] = 29.0f;
                tapDelaysMs[3] = 41.0f;
                tapGains[0] = 0.42f;
                tapGains[1] = 0.29f;
                tapGains[2] = 0.21f;
                tapGains[3] = 0.15f;
                damping = 0.40f;
                break;
            case RoomSimulationPreset::ConcertHall:
                tapDelaysMs[0] = 20.0f;
                tapDelaysMs[1] = 34.0f;
                tapDelaysMs[2] = 51.0f;
                tapDelaysMs[3] = 73.0f;
                tapDelaysMs[4] = 97.0f;
                tapDelaysMs[5] = 123.0f;
                tapGains[0] = 0.34f;
                tapGains[1] = 0.27f;
                tapGains[2] = 0.21f;
                tapGains[3] = 0.17f;
                tapGains[4] = 0.13f;
                tapGains[5] = 0.10f;
                reflectionTapCount = 6;
                damping = 0.50f;
                break;
            case RoomSimulationPreset::Cathedral:
                tapDelaysMs[0] = 28.0f;
                tapDelaysMs[1] = 47.0f;
                tapDelaysMs[2] = 71.0f;
                tapDelaysMs[3] = 101.0f;
                tapDelaysMs[4] = 137.0f;
                tapDelaysMs[5] = 179.0f;
                tapGains[0] = 0.30f;
                tapGains[1] = 0.25f;
                tapGains[2] = 0.20f;
                tapGains[3] = 0.16f;
                tapGains[4] = 0.13f;
                tapGains[5] = 0.11f;
                reflectionTapCount = 6;
                damping = 0.57f;
                break;
            case RoomSimulationPreset::Subway:
                tapDelaysMs[0] = 18.0f;
                tapDelaysMs[1] = 33.0f;
                tapDelaysMs[2] = 56.0f;
                tapDelaysMs[3] = 84.0f;
                tapDelaysMs[4] = 119.0f;
                tapDelaysMs[5] = 158.0f;
                tapGains[0] = 0.36f;
                tapGains[1] = 0.29f;
                tapGains[2] = 0.22f;
                tapGains[3] = 0.17f;
                tapGains[4] = 0.12f;
                tapGains[5] = 0.08f;
                reflectionTapCount = 6;
                damping = 0.48f;
                break;
        }

        // Normalize combined reflection-tap gain so correlated content (sustained bass, held
        // chords) can't push reflectionL/R past unity before the room-mix crossfade.
        constexpr float kTargetReflectionTapSum = 0.65f;
        float tapGainSum = 0.0f;
        for (int i = 0; i < reflectionTapCount; ++i) {
            tapGainSum += tapGains[i];
        }
        if (tapGainSum > kTargetReflectionTapSum) {
            const float tapNorm = kTargetReflectionTapSum / tapGainSum;
            for (int i = 0; i < reflectionTapCount; ++i) {
                tapGains[i] *= tapNorm;
            }
        }

        if (sampleRate <= 0 || reflectionDelayLeft.empty()) {
            return;
        }

        const int ringLength = static_cast<int>(reflectionDelayLeft.size());
        for (int i = 0; i < reflectionTapCount; ++i) {
            // Slightly de-correlate channels with opposite delay offsets.
            reflectionTapsL[static_cast<std::size_t>(i)] =
                std::clamp(msToSamples(tapDelaysMs[i] * delayScale, sampleRate), 1, ringLength - 1);
            reflectionTapsR[static_cast<std::size_t>(i)] =
                std::clamp(msToSamples(tapDelaysMs[i] * delayScale * decorrelationSkew, sampleRate), 1, ringLength - 1);
            reflectionGains[static_cast<std::size_t>(i)] = tapGains[i] * (0.80f + 0.35f * roomSizeNorm);
        }

        const float reverbTapMs = [&]() noexcept {
            switch (roomPreset) {
                case RoomSimulationPreset::Off: return 0.0f;
                case RoomSimulationPreset::SmallRoom: return 37.0f;
                case RoomSimulationPreset::Studio: return 53.0f;
                case RoomSimulationPreset::ConcertHall: return 79.0f;
                case RoomSimulationPreset::Cathedral: return 107.0f;
                case RoomSimulationPreset::Subway: return 86.0f;
            }
            return 53.0f;
        }();

        if (reverbTapMs <= 0.0f) {
            reverbTapL = 1;
            reverbTapR = 1;
            reverbFeedback = 0.0f;
            reverbInputScale = 0.0f;
            return;
        }

        const int reverbRing = static_cast<int>(reverbDelayLeft.size());
        reverbTapL = std::clamp(msToSamples(reverbTapMs * delayScale, sampleRate), 1, reverbRing - 1);
        reverbTapR = std::clamp(msToSamples(reverbTapMs * delayScale * (1.08f + 0.20f * widthNorm), sampleRate), 1, reverbRing - 1);

        const float clampedT60 = std::clamp(reverbTimeSeconds, kMinReverbTimeSeconds, kMaxReverbTimeSeconds);
        const float dampeningFactor = 1.0f - dampeningNorm;
        damping = std::clamp(0.18f + 0.62f * dampeningNorm, 0.18f, 0.80f);
        const float delaySeconds = static_cast<float>(reverbTapL) / static_cast<float>(sampleRate);
        const float gainAtT60 = std::exp((-6.9077553f * delaySeconds) / clampedT60);
        reverbFeedback = std::clamp(gainAtT60 * (0.80f + 0.20f * dampeningFactor), 0.0f, 0.90f);

        // A comb tank with feedback g has a steady-state DC gain of 1/(1-g).
        // With the long reverb times the UI allows, g reaches 0.90, i.e. a 10x
        // build-up on sustained material. Normalizing the tank input keeps the
        // wet signal near unity regardless of the selected reverb time, which
        // is what stops the room stage from permanently slamming the limiter.
        reverbInputScale = std::clamp(1.0f - reverbFeedback, 0.06f, 1.0f);
    }

    void initializeRoomBuffers() noexcept {
        if (sampleRate <= 0) return;

        // Keep memory bounded: reflection ring up to 240 ms, reverb ring up to 1.5 s.
        const int reflectionRing = std::max(2, static_cast<int>(static_cast<float>(sampleRate) * 0.240f));
        const int reverbRing = std::max(2, static_cast<int>(static_cast<float>(sampleRate) * 1.5f));

        reflectionDelayLeft.assign(static_cast<std::size_t>(reflectionRing), 0.0f);
        reflectionDelayRight.assign(static_cast<std::size_t>(reflectionRing), 0.0f);
        reverbDelayLeft.assign(static_cast<std::size_t>(reverbRing), 0.0f);
        reverbDelayRight.assign(static_cast<std::size_t>(reverbRing), 0.0f);

        reflectionWriteIndex = 0;
        reverbWriteIndex = 0;
        reverbLowpassL = 0.0f;
        reverbLowpassR = 0.0f;

        // Per-stage limiters. Bass/treble use slightly slower attacks because
        // shelf boosts are steady-state, while the output stage is the final
        // brick wall and needs a faster grab.
        bassLimiter.configure(sampleRate, 0.97f, 0.006f, 0.120f);
        trebleLimiter.configure(sampleRate, 0.97f, 0.003f, 0.100f);
        spatialLimiter.configure(sampleRate, 0.96f, 0.004f, 0.100f);
        outputLimiter.configure(sampleRate, kOutputCeiling, 0.002f, 0.080f);

        gainSmoothCoeff = std::exp(-1.0f / (0.020f * static_cast<float>(sampleRate)));
        smoothedOutputGain = outputGainLinear;
        transitionStep = 1.0f / std::max(1.0f, kTransitionSeconds * static_cast<float>(sampleRate));

        updateToneStage();
        updateRoomModel();
    }

    void resetFifos() noexcept {
        pendingInput.fill(0.0f);
        pendingInputFrames = 0;
        std::fill(outputFifo.begin(), outputFifo.end(), 0.0f);
        outputFifoRead = 0;
        outputFifoWrite = 0;
        outputFifoFrames = 0;
        // Prime the output FIFO with exactly one effect block of silence. This
        // is what lets us answer every host request immediately while still
        // only ever feeding the effect complete 384-frame blocks.
        if (outputFifoCapacityFrames >= kSteamAudioFrameSize) {
            outputFifoWrite = kSteamAudioFrameSize * 2;
            outputFifoFrames = kSteamAudioFrameSize;
        }
    }

    void clearStateOnly() noexcept {
        std::fill(reflectionDelayLeft.begin(), reflectionDelayLeft.end(), 0.0f);
        std::fill(reflectionDelayRight.begin(), reflectionDelayRight.end(), 0.0f);
        std::fill(reverbDelayLeft.begin(), reverbDelayLeft.end(), 0.0f);
        std::fill(reverbDelayRight.begin(), reverbDelayRight.end(), 0.0f);
        reverbLowpassL = 0.0f;
        reverbLowpassR = 0.0f;
        reflectionWriteIndex = 0;
        reverbWriteIndex = 0;
        bassLimiter.reset();
        trebleLimiter.reset();
        spatialLimiter.reset();
        outputLimiter.reset();
        bassFilterL.reset(); bassFilterR.reset();
        trebleFilterL.reset(); trebleFilterR.reset();
        smoothedOutputGain = outputGainLinear;
        // Land directly on the steady-state value so a flush/seek does not
        // replay a fade-in over the first block of the new position.
        transitionPosition = enabled ? 1.0f : 0.0f;
        resetFifos();
    }

    void release() noexcept {
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
        if (effect != nullptr) {
            iplBinauralEffectRelease(&effect);
        }
        if (hrtf != nullptr) {
            iplHRTFRelease(&hrtf);
        }
        if (context != nullptr) {
            iplContextRelease(&context);
        }
#endif
        prepared = false;
        sampleRate = 0;
        maxFrames = 0;

        inputLeft.fill(0.0f);
        inputRight.fill(0.0f);
        outputLeft.fill(0.0f);
        outputRight.fill(0.0f);

        outputFifo.clear();
        outputFifoCapacityFrames = 0;
        outputFifoRead = 0;
        outputFifoWrite = 0;
        outputFifoFrames = 0;
        pendingInputFrames = 0;

        reflectionDelayLeft.clear();
        reflectionDelayRight.clear();
        reverbDelayLeft.clear();
        reverbDelayRight.clear();
        reverbLowpassL = 0.0f;
        reverbLowpassR = 0.0f;
        reflectionWriteIndex = 0;
        reverbWriteIndex = 0;
        transitionPosition = 0.0f;

        lastResult = ImmersiveProcessResult::NotPrepared;
        lastState = -1;
    }

    void applyRoomModel(float& left, float& right) noexcept {
        const float effectiveRoomMix = roomMix * spatialBlend;
        if (roomPreset == RoomSimulationPreset::Off || effectiveRoomMix <= kZeroEpsilon) {
            return;
        }
        if (reflectionDelayLeft.empty() || reverbDelayLeft.empty()) {
            return;
        }

        const int reflectionRing = static_cast<int>(reflectionDelayLeft.size());
        const int reverbRing = static_cast<int>(reverbDelayLeft.size());

        float reflectionL = 0.0f;
        float reflectionR = 0.0f;
        for (int i = 0; i < reflectionTapCount; ++i) {
            const int readL = (reflectionWriteIndex - reflectionTapsL[static_cast<std::size_t>(i)] + reflectionRing) % reflectionRing;
            const int readR = (reflectionWriteIndex - reflectionTapsR[static_cast<std::size_t>(i)] + reflectionRing) % reflectionRing;
            const float gain = reflectionGains[static_cast<std::size_t>(i)];
            reflectionL += reflectionDelayLeft[static_cast<std::size_t>(readL)] * gain;
            reflectionR += reflectionDelayRight[static_cast<std::size_t>(readR)] * gain;
        }

        const int revReadL = (reverbWriteIndex - reverbTapL + reverbRing) % reverbRing;
        const int revReadR = (reverbWriteIndex - reverbTapR + reverbRing) % reverbRing;
        const float delayedRevL = reverbDelayLeft[static_cast<std::size_t>(revReadL)];
        const float delayedRevR = reverbDelayRight[static_cast<std::size_t>(revReadR)];

        reverbLowpassL = flushDenormal(reverbLowpassL + damping * (delayedRevL - reverbLowpassL));
        reverbLowpassR = flushDenormal(reverbLowpassR + damping * (delayedRevR - reverbLowpassR));

        const float monoInput = 0.5f * (left + right);
        // Scale the tank input by (1 - feedback) so the recirculating sum stays
        // near unity instead of building up to 1/(1-feedback).
        const float revInputL = (monoInput + (reflectionL * reflectionAmount)) * reverbInputScale;
        const float revInputR = (monoInput + (reflectionR * reflectionAmount)) * reverbInputScale;

        reverbDelayLeft[static_cast<std::size_t>(reverbWriteIndex)] = flushDenormal(revInputL + reverbLowpassL * reverbFeedback);
        reverbDelayRight[static_cast<std::size_t>(reverbWriteIndex)] = flushDenormal(revInputR + reverbLowpassR * reverbFeedback);

        reflectionDelayLeft[static_cast<std::size_t>(reflectionWriteIndex)] = flushDenormal(left + reflectionCrossFeed * right);
        reflectionDelayRight[static_cast<std::size_t>(reflectionWriteIndex)] = flushDenormal(right + reflectionCrossFeed * left);

        reflectionWriteIndex = (reflectionWriteIndex + 1) % reflectionRing;
        reverbWriteIndex = (reverbWriteIndex + 1) % reverbRing;

        const float wetL = reflectionL + (0.70f * reverbLowpassL);
        const float wetR = reflectionR + (0.70f * reverbLowpassR);

        const float dryMix = 1.0f - effectiveRoomMix;
        left = dryMix * left + effectiveRoomMix * wetL;
        right = dryMix * right + effectiveRoomMix * wetR;

        left = flushDenormal(left);
        right = flushDenormal(right);
    }

    /** Bass -> treble -> output gain, each with a dedicated limiter. */
    void applyToneStage(float& left, float& right) noexcept {
        if (bassGainDbValue > 0.05f || bassGainDbValue < -0.05f) {
            left = bassFilterL.process(left);
            right = bassFilterR.process(right);
            if (bassGainDbValue > 0.0f) bassLimiter.process(left, right);
        }
        if (trebleGainDbValue > 0.05f || trebleGainDbValue < -0.05f) {
            left = trebleFilterL.process(left);
            right = trebleFilterR.process(right);
            if (trebleGainDbValue > 0.0f) trebleLimiter.process(left, right);
        }

        smoothedOutputGain += (outputGainLinear - smoothedOutputGain) * (1.0f - gainSmoothCoeff);
        left *= smoothedOutputGain;
        right *= smoothedOutputGain;
        outputLimiter.process(left, right);
    }

    /** Runs one complete 384-frame effect block. Returns false on hard failure. */
    bool runEffectBlock() noexcept {
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
        constexpr int steamFrames = kSteamAudioFrameSize;
        for (int frame = 0; frame < steamFrames; ++frame) {
            inputLeft[static_cast<std::size_t>(frame)] = pendingInput[static_cast<std::size_t>(frame * 2)];
            inputRight[static_cast<std::size_t>(frame)] = pendingInput[static_cast<std::size_t>(frame * 2 + 1)];
        }
        std::fill(outputLeft.begin(), outputLeft.end(), 0.0f);
        std::fill(outputRight.begin(), outputRight.end(), 0.0f);

        IPLAudioBuffer input{};
        input.numChannels = 2;
        input.numSamples = steamFrames;
        input.data = inputChannels;
        IPLAudioBuffer output{};
        output.numChannels = 2;
        output.numSamples = steamFrames;
        output.data = outputChannels;

        IPLBinauralEffectParams params{};
        params.direction = IPLVector3{0.0f, 0.0f, 1.0f};
        params.interpolation = IPL_HRTFINTERPOLATION_BILINEAR;
        params.spatialBlend = spatialBlend;
        params.hrtf = hrtf;
        params.peakDelays = nullptr;

        const IPLAudioEffectState state = iplBinauralEffectApply(effect, &params, &input, &output);
        lastState = static_cast<int>(state);
        if (state != IPL_AUDIOEFFECTSTATE_TAILCOMPLETE && state != IPL_AUDIOEFFECTSTATE_TAILREMAINING) {
            lastResult = ImmersiveProcessResult::SteamAudioUnavailable;
            return false;
        }

        // -6 dB of fixed headroom before the room stage so ordinary HRTF peaks
        // never reach the limiters.
        constexpr float kSteamAudioOutputGain = 0.50118723f;
        for (int frame = 0; frame < steamFrames; ++frame) {
            float wetL = outputLeft[static_cast<std::size_t>(frame)] * kSteamAudioOutputGain;
            float wetR = outputRight[static_cast<std::size_t>(frame)] * kSteamAudioOutputGain;
            if (!std::isfinite(wetL)) wetL = 0.0f;
            if (!std::isfinite(wetR)) wetR = 0.0f;

            applyRoomModel(wetL, wetR);
            spatialLimiter.process(wetL, wetR);
            applyToneStage(wetL, wetR);

            if (!std::isfinite(wetL)) wetL = 0.0f;
            if (!std::isfinite(wetR)) wetR = 0.0f;

            outputFifo[static_cast<std::size_t>(outputFifoWrite)] = wetL;
            outputFifo[static_cast<std::size_t>(outputFifoWrite + 1)] = wetR;
            outputFifoWrite = (outputFifoWrite + 2) % static_cast<int>(outputFifo.size());
        }
        outputFifoFrames += steamFrames;
        return true;
#else
        return false;
#endif
    }

    SpaceDesignControls currentSpaceDesignControls() const noexcept {
        return SpaceDesignControls{roomSizeNorm, dampeningNorm, widthNorm};
    }
};

ImmersiveAudioEngine::ImmersiveAudioEngine() : impl_(std::make_unique<Impl>()) {}

ImmersiveAudioEngine::~ImmersiveAudioEngine() {
    impl_->release();
}

bool ImmersiveAudioEngine::prepare(int sampleRate, int maxFrames) noexcept {
    if (sampleRate < 8000 || maxFrames <= 0) return false;

    impl_->release();
    impl_->sampleRate = sampleRate;
    impl_->maxFrames = maxFrames;
    impl_->inputChannels[0] = impl_->inputLeft.data();
    impl_->inputChannels[1] = impl_->inputRight.data();
    impl_->outputChannels[0] = impl_->outputLeft.data();
    impl_->outputChannels[1] = impl_->outputRight.data();

    // Worst case the FIFO holds the priming block, one host buffer and one
    // partially consumed effect block.
    impl_->outputFifoCapacityFrames = maxFrames + Impl::kSteamAudioFrameSize * 3;
    impl_->outputFifo.assign(static_cast<std::size_t>(impl_->outputFifoCapacityFrames) * 2, 0.0f);
    impl_->initializeRoomBuffers();
    impl_->resetFifos();

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    IPLContextSettings contextSettings{};
    contextSettings.version = STEAMAUDIO_VERSION;
    if (iplContextCreate(&contextSettings, &impl_->context) != IPL_STATUS_SUCCESS || impl_->context == nullptr) {
        impl_->release();
        return false;
    }

    IPLAudioSettings audioSettings{};
    audioSettings.samplingRate = sampleRate;
    // Steam Audio effects use a fixed frame size; process() now buffers the
    // variable Media3 blocks so the effect always receives exactly this many
    // frames with no zero padding and no discarded tail.
    audioSettings.frameSize = Impl::kSteamAudioFrameSize;

    IPLHRTFSettings hrtfSettings{};
    hrtfSettings.type = IPL_HRTFTYPE_DEFAULT;
    hrtfSettings.volume = 1.0f;
    if (iplHRTFCreate(impl_->context, &audioSettings, &hrtfSettings, &impl_->hrtf) != IPL_STATUS_SUCCESS || impl_->hrtf == nullptr) {
        impl_->release();
        return false;
    }

    IPLBinauralEffectSettings effectSettings{};
    effectSettings.hrtf = impl_->hrtf;
    if (iplBinauralEffectCreate(impl_->context, &audioSettings, &effectSettings, &impl_->effect) != IPL_STATUS_SUCCESS || impl_->effect == nullptr) {
        impl_->release();
        return false;
    }
#else
    impl_->release();
    return false;
#endif

    impl_->prepared = true;
    impl_->transitionPosition = 0.0f;
    impl_->lastResult = ImmersiveProcessResult::Disabled;
    return true;
}

void ImmersiveAudioEngine::reset() noexcept {
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    if (impl_->effect != nullptr) {
        iplBinauralEffectReset(impl_->effect);
    }
#endif
    impl_->clearStateOnly();
    impl_->lastResult = impl_->prepared ? ImmersiveProcessResult::Disabled : ImmersiveProcessResult::NotPrepared;
    impl_->lastState = -1;
}

void ImmersiveAudioEngine::setEnabled(bool enabled) noexcept {
    // Do not touch transitionPosition here: process() ramps towards the new
    // target, which is exactly what makes the toggle inaudible.
    impl_->enabled = enabled;
}

void ImmersiveAudioEngine::setSpatialBlend(float blend) noexcept {
    impl_->spatialBlend = std::isfinite(blend) ? std::clamp(blend, 0.0f, 1.0f) : 0.0f;
}

void ImmersiveAudioEngine::setRoomSimulationPreset(RoomSimulationPreset preset) noexcept {
    impl_->roomPreset = preset;
    impl_->updateRoomModel();
}

void ImmersiveAudioEngine::setRoomMix(float wetMix) noexcept {
    impl_->roomMix = clampUnit(wetMix);
}

void ImmersiveAudioEngine::setReflectionAmount(float amount) noexcept {
    impl_->reflectionAmount = clampUnit(amount);
}

void ImmersiveAudioEngine::setReverbTimeSeconds(float seconds) noexcept {
    impl_->reverbTimeSeconds = std::isfinite(seconds)
        ? std::clamp(seconds, Impl::kMinReverbTimeSeconds, Impl::kMaxReverbTimeSeconds)
        : 1.35f;
    impl_->updateRoomModel();
}

void ImmersiveAudioEngine::setRoomSize(float size) noexcept {
    impl_->roomSizeNorm = clampUnit(size);
    impl_->updateRoomModel();
}

void ImmersiveAudioEngine::setDampening(float dampening) noexcept {
    impl_->dampeningNorm = clampUnit(dampening);
    impl_->updateRoomModel();
}

void ImmersiveAudioEngine::setStereoWidth(float width) noexcept {
    impl_->widthNorm = clampUnit(width);
    impl_->updateRoomModel();
}

void ImmersiveAudioEngine::setBassGainDb(float gainDb) noexcept {
    impl_->bassGainDbValue = std::isfinite(gainDb)
        ? std::clamp(gainDb, kMinShelfGainDb, kMaxShelfGainDb) : 0.0f;
    impl_->updateToneStage();
}

void ImmersiveAudioEngine::setTrebleGainDb(float gainDb) noexcept {
    impl_->trebleGainDbValue = std::isfinite(gainDb)
        ? std::clamp(gainDb, kMinShelfGainDb, kMaxShelfGainDb) : 0.0f;
    impl_->updateToneStage();
}

void ImmersiveAudioEngine::setOutputGainDb(float gainDb) noexcept {
    impl_->outputGainDbValue = std::isfinite(gainDb)
        ? std::clamp(gainDb, kMinOutputGainDb, kMaxOutputGainDb) : 0.0f;
    impl_->updateToneStage();
}

float ImmersiveAudioEngine::bassGainDb() const noexcept { return impl_->bassGainDbValue; }
float ImmersiveAudioEngine::trebleGainDb() const noexcept { return impl_->trebleGainDbValue; }
float ImmersiveAudioEngine::outputGainDb() const noexcept { return impl_->outputGainDbValue; }

ToneStageTelemetry ImmersiveAudioEngine::toneStageTelemetry() const noexcept {
    ToneStageTelemetry telemetry{};
    telemetry.bassGainReductionDb = impl_->bassLimiter.reductionDb();
    telemetry.trebleGainReductionDb = impl_->trebleLimiter.reductionDb();
    telemetry.outputGainReductionDb = impl_->outputLimiter.reductionDb();
    telemetry.spatialGainReductionDb = impl_->spatialLimiter.reductionDb();
    telemetry.transitionRamp = smoothRamp(impl_->transitionPosition);
    return telemetry;
}

SpaceDesignControls ImmersiveAudioEngine::spaceDesignControls() const noexcept {
    return impl_->currentSpaceDesignControls();
}

bool ImmersiveAudioEngine::isPrepared() const noexcept {
    return impl_->prepared;
}

bool ImmersiveAudioEngine::isTransitioning() const noexcept {
    return impl_->prepared && impl_->transitionActive();
}

int ImmersiveAudioEngine::maxFrames() const noexcept {
    return impl_->maxFrames;
}

ImmersiveProcessResult ImmersiveAudioEngine::lastProcessResult() const noexcept {
    return impl_->lastResult;
}

int ImmersiveAudioEngine::lastEffectState() const noexcept {
    return impl_->lastState;
}

bool ImmersiveAudioEngine::process(float* interleavedStereo, int frames) noexcept {
    if (!impl_->prepared) {
        impl_->lastResult = ImmersiveProcessResult::NotPrepared;
        return false;
    }
    // Fully faded out and not asked to fade in: real bypass, zero added latency.
    if (!impl_->enabled && !impl_->transitionActive()) {
        impl_->bypassed = true;
        impl_->lastResult = ImmersiveProcessResult::Disabled;
        return false;
    }
    if (interleavedStereo == nullptr || frames <= 0 || frames > impl_->maxFrames) {
        impl_->lastResult = ImmersiveProcessResult::InvalidInput;
        return false;
    }

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    // Re-entering the processed path. The delay lines, filter state and FIFO
    // still hold audio from before the bypass, which would be replayed as a
    // burst. Clear them here, on the audio thread, so the fade-in starts from
    // true silence. transitionPosition is deliberately preserved.
    if (impl_->bypassed) {
        impl_->bypassed = false;
        const float resumePosition = impl_->transitionPosition;
        impl_->clearStateOnly();
        impl_->transitionPosition = resumePosition;
        if (impl_->effect != nullptr) {
            iplBinauralEffectReset(impl_->effect);
        }
    }

    constexpr int steamFrames = Impl::kSteamAudioFrameSize;
    const int fifoSamples = static_cast<int>(impl_->outputFifo.size());

    for (int frame = 0; frame < frames; ++frame) {
        const float dryL = sanitizeInputSample(interleavedStereo[frame * 2]);
        const float dryR = sanitizeInputSample(interleavedStereo[frame * 2 + 1]);

        // 1. Accumulate into the effect block.
        impl_->pendingInput[static_cast<std::size_t>(impl_->pendingInputFrames * 2)] = dryL;
        impl_->pendingInput[static_cast<std::size_t>(impl_->pendingInputFrames * 2 + 1)] = dryR;
        ++impl_->pendingInputFrames;
        if (impl_->pendingInputFrames == steamFrames) {
            impl_->pendingInputFrames = 0;
            if (!impl_->runEffectBlock()) {
                return false;
            }
        }

        // 2. Pop the matching processed frame. The FIFO was primed with one
        //    block of silence, so it can never underrun.
        float wetL = 0.0f;
        float wetR = 0.0f;
        if (impl_->outputFifoFrames > 0) {
            wetL = impl_->outputFifo[static_cast<std::size_t>(impl_->outputFifoRead)];
            wetR = impl_->outputFifo[static_cast<std::size_t>(impl_->outputFifoRead + 1)];
            impl_->outputFifoRead = (impl_->outputFifoRead + 2) % fifoSamples;
            --impl_->outputFifoFrames;
        }

        // 3. Advance the bypass crossfade and mix against the *live* dry input.
        //    Deliberately not time-aligned to the wet path: at ramp == 0 the
        //    output is then bit-identical to the input, so handing over to the
        //    zero-latency bypass early-return costs no sample jump. Aligning
        //    the dry path instead would make the fade phase-coherent but would
        //    put an 8 ms discontinuity at that hand-off, which is far worse.
        const float target = impl_->enabled ? 1.0f : 0.0f;
        if (impl_->transitionPosition < target) {
            impl_->transitionPosition = std::min(target, impl_->transitionPosition + impl_->transitionStep);
        } else if (impl_->transitionPosition > target) {
            impl_->transitionPosition = std::max(target, impl_->transitionPosition - impl_->transitionStep);
        }
        const float ramp = smoothRamp(impl_->transitionPosition);
        const float dryLevel = 1.0f - ramp;

        float outL = dryL * dryLevel + wetL * ramp;
        float outR = dryR * dryLevel + wetR * ramp;
        if (!std::isfinite(outL)) outL = 0.0f;
        if (!std::isfinite(outR)) outR = 0.0f;

        interleavedStereo[frame * 2] = std::clamp(outL, -kOutputCeiling, kOutputCeiling);
        interleavedStereo[frame * 2 + 1] = std::clamp(outR, -kOutputCeiling, kOutputCeiling);
    }

    impl_->lastResult = ImmersiveProcessResult::SteamAudioProcessed;
    return true;
#else
    impl_->lastResult = ImmersiveProcessResult::SteamAudioUnavailable;
    return false;
#endif
}

} // namespace frostsoulx
