#include "frostsoulx/immersive_audio_engine.h"

#include "frostsoulx/dsp/partitioned_convolver.h"
#include "frostsoulx/rt/rt_types.h"
#include "frostsoulx/spatial/spatial_renderer.h"

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
// Keep ordinary HRTF/room peaks out of the dynamics stage. The previous 0.90 threshold
// caused continuous gain modulation on loud passages, which was audible as low-level clipping.
constexpr float kLimiterThreshold = 0.96f;
constexpr float kLimiterMinGain = 0.1f;
constexpr float kZeroEpsilon = 1.0e-12f;

inline float sanitizeInputSample(float sample) noexcept {
    if (!std::isfinite(sample)) return 0.0f;
    return std::clamp(sample, -kInputSanitizeLimit, kInputSanitizeLimit);
}

inline int msToSamples(float milliseconds, int sampleRate) noexcept {
    const float samples = (milliseconds * 0.001f) * static_cast<float>(sampleRate);
    return std::max(1, static_cast<int>(std::lround(samples)));
}

inline float clampUnit(float value) noexcept {
    return std::isfinite(value) ? std::clamp(value, 0.0f, 1.0f) : 0.0f;
}

} // namespace

struct ImmersiveAudioEngine::Impl {
    // Keep the effect block below 10 ms at 48 kHz for low-latency playback.
    static constexpr int kSteamAudioFrameSize = 384;
    static constexpr float kMaxReverbTimeSeconds = 8.0f;
    static constexpr float kMinReverbTimeSeconds = 0.2f;

    int sampleRate = 0;
    int maxFrames = 0;
    int frameCapacity = 0;
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

    // Derived room shaping values.
    float delayScale = 1.0f;
    float decorrelationSkew = 1.13f;
    float reflectionCrossFeed = 0.15f;

    std::vector<float> inputLeft;
    std::vector<float> inputRight;
    std::vector<float> outputLeft;
    std::vector<float> outputRight;
    float* inputChannels[2] = {nullptr, nullptr};
    float* outputChannels[2] = {nullptr, nullptr};

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
    float reverbLowpassL = 0.0f;
    float reverbLowpassR = 0.0f;

    // Lightweight stereo peak limiter state to prevent residual clipping.
    float limiterGain = 1.0f;
    float limiterReleaseCoeff = 0.9996f;
    float limiterAttackCoeff = 0.98f;

    // Built-in spatialiser used when Steam Audio is unavailable. Owns the
    // HRTF/HRIR set, the HOA bus and the partitioned binaural convolution.
    spatial::SpatialRenderer nativeRenderer;
    SpatialBackend backend = SpatialBackend::None;
    SpatialBackend backendPreference = SpatialBackend::Native;

    // True Acoustic Space & Full Partitioned Linear Convolution backend
    spatial::SpaceProfile activeSpace = spatial::SpaceProfile::createLivingRoom();
    spatial::RirGenerator rirGenerator;
    spatial::StereoBrir activeBrir;
    dsp::NonUniformConvolver convLeft;
    dsp::NonUniformConvolver convRight;
    bool fullConvolutionReady = false;
    std::size_t irLengthTaps = 16384;
    float reflectionDensity = 0.5f;

    // Preallocated real-time scratch buffers for non-uniform convolution
    std::vector<float> convScratchInL;
    std::vector<float> convScratchInR;
    std::vector<float> convScratchOutL;
    std::vector<float> convScratchOutR;

    void reloadBrir() noexcept {
        if (sampleRate <= 0) return;
        spatial::RirGeneratorConfig rcfg;
        rcfg.sampleRate = static_cast<double>(sampleRate);
        rcfg.maxTaps = irLengthTaps;
        rcfg.maxIsmOrder = 2;
        rcfg.enableDiffuseTail = true;
        rcfg.diffuseEnergyRatio = reflectionDensity;
        rirGenerator.setConfig(rcfg);
        activeBrir = rirGenerator.generateBrir(activeSpace);
        if (activeBrir.valid() && fullConvolutionReady) {
            convLeft.loadIr(activeBrir.left.data(), activeBrir.taps);
            convRight.loadIr(activeBrir.right.data(), activeBrir.taps);
        }
    }

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    IPLContext context = nullptr;
    IPLHRTF hrtf = nullptr;
    IPLBinauralEffect effect = nullptr;
#endif

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
        // chords) can't push reflectionL/R past unity before the room-mix crossfade. Several
        // presets' raw tap gains already sum above 1.0 before the room-size multiplier below —
        // that structural over-unity stacking, not the final limiter, is the actual source of
        // the "consistent, low-level" clipping: it happens on ordinary loud passages, not just
        // peaks.
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
        limiterGain = 1.0f;
        // ~80 ms release for transparent recovery, ~2 ms attack so gain reduction ramps
        // instead of snapping instantly — the instant-cut attack was adding its own grainy
        // edge on top of the softclip/limiter overlap.
        limiterReleaseCoeff = std::exp(-1.0f / (0.080f * static_cast<float>(sampleRate)));
        limiterAttackCoeff = std::exp(-1.0f / (0.002f * static_cast<float>(sampleRate)));
        updateRoomModel();
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
        limiterGain = 1.0f;
    }

    void releaseSteamAudio() noexcept {
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
        if (effect != nullptr) {
            iplBinauralEffectRelease(&effect);
            effect = nullptr;
        }
        if (hrtf != nullptr) {
            iplHRTFRelease(&hrtf);
            hrtf = nullptr;
        }
        if (context != nullptr) {
            iplContextRelease(&context);
            context = nullptr;
        }
#endif
    }

    void release() noexcept {
        releaseSteamAudio();
        backend = SpatialBackend::None;
        prepared = false;
        sampleRate = 0;
        maxFrames = 0;
        frameCapacity = 0;

        inputLeft.clear();
        inputRight.clear();
        outputLeft.clear();
        outputRight.clear();
        inputChannels[0] = nullptr;
        inputChannels[1] = nullptr;
        outputChannels[0] = nullptr;
        outputChannels[1] = nullptr;

        reflectionDelayLeft.clear();
        reflectionDelayRight.clear();
        reverbDelayLeft.clear();
        reverbDelayRight.clear();
        reverbLowpassL = 0.0f;
        reverbLowpassR = 0.0f;
        reflectionWriteIndex = 0;
        reverbWriteIndex = 0;
        limiterGain = 1.0f;

        lastResult = ImmersiveProcessResult::NotPrepared;
        lastState = -1;
    }

    void applyOutputLimiter(float& left, float& right) noexcept {
        const float peak = std::max(std::fabs(left), std::fabs(right));
        const float targetGain = peak > kLimiterThreshold
            ? std::max(kLimiterThreshold / peak, kLimiterMinGain)
            : 1.0f;

        if (targetGain < limiterGain) {
            limiterGain = limiterGain + (targetGain - limiterGain) * (1.0f - limiterAttackCoeff);
        } else {
            limiterGain = std::min(1.0f, limiterGain + (1.0f - limiterGain) * (1.0f - limiterReleaseCoeff));
        }

        left *= limiterGain;
        right *= limiterGain;
        left = std::clamp(left, -kOutputCeiling, kOutputCeiling);
        right = std::clamp(right, -kOutputCeiling, kOutputCeiling);
    }

    void applyRoomModel(float& left, float& right) noexcept {
        // Room processing must be transparent when disabled or when spatial intensity is
        // effectively zero. Applying softClipSample here used to distort ordinary loud
        // samples continuously, even though the room stage was visually set to Off.
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

        reverbLowpassL += damping * (delayedRevL - reverbLowpassL);
        reverbLowpassR += damping * (delayedRevR - reverbLowpassR);

        const float monoInput = 0.5f * (left + right);
        const float revInputL = monoInput + (reflectionL * reflectionAmount);
        const float revInputR = monoInput + (reflectionR * reflectionAmount);

        reverbDelayLeft[static_cast<std::size_t>(reverbWriteIndex)] = revInputL + reverbLowpassL * reverbFeedback;
        reverbDelayRight[static_cast<std::size_t>(reverbWriteIndex)] = revInputR + reverbLowpassR * reverbFeedback;

        reflectionDelayLeft[static_cast<std::size_t>(reflectionWriteIndex)] = left + reflectionCrossFeed * right;
        reflectionDelayRight[static_cast<std::size_t>(reflectionWriteIndex)] = right + reflectionCrossFeed * left;

        reflectionWriteIndex = (reflectionWriteIndex + 1) % reflectionRing;
        reverbWriteIndex = (reverbWriteIndex + 1) % reverbRing;

        const float wetL = reflectionL + (0.70f * reverbLowpassL);
        const float wetR = reflectionR + (0.70f * reverbLowpassR);

        const float dryMix = 1.0f - effectiveRoomMix;
        left = dryMix * left + effectiveRoomMix * wetL;
        right = dryMix * right + effectiveRoomMix * wetR;

        if (std::fabs(left) < kZeroEpsilon) left = 0.0f;
        if (std::fabs(right) < kZeroEpsilon) right = 0.0f;
    }

    /// Native spatial path: sanitise -> SpatialRenderer (HOA encode -> HOA
    /// rotation -> HRTF/HRIR partitioned convolution, with VBAP available for
    /// object/discrete panning) -> room processing -> safety limiter.
    ///
    /// Real-time safe: operates in place on the caller's interleaved buffer
    /// and on storage reserved by prepare(). No allocation, locking, I/O or
    /// logging.
    bool processNative(float* interleavedStereo, int frames) noexcept {
        if (!nativeRenderer.ready()) {
            lastResult = ImmersiveProcessResult::SteamAudioUnavailable;
            return false;
        }

        const std::size_t samples = static_cast<std::size_t>(frames) * 2u;

        // 1. Sanitise in place so no NaN/Inf ever reaches the convolver state.
        for (std::size_t i = 0; i < samples; ++i) {
            interleavedStereo[i] = sanitizeInputSample(interleavedStereo[i]);
        }

        // 2. Spatialise in place. The renderer owns its own block adapter, so
        //    `frames` may be any value in [1, maxFrames]; it applies the
        //    delay-matched dry/wet spatial blend and its own unity-gain
        //    normalisation, so no extra headroom trim is needed here (that
        //    keeps blend=0 + room Off bit-transparent apart from latency).
        nativeRenderer.process(interleavedStereo, frames);

        // 3. Room processing, then the safety limiter as the final stage.
        for (int frame = 0; frame < frames; ++frame) {
            const std::size_t li = static_cast<std::size_t>(frame) * 2u;
            float outL = interleavedStereo[li];
            float outR = interleavedStereo[li + 1];

            if (!std::isfinite(outL) || !std::isfinite(outR)) {
                lastResult = ImmersiveProcessResult::InvalidOutput;
                return false;
            }

            applyRoomModel(outL, outR);
            applyOutputLimiter(outL, outR);

            if (!std::isfinite(outL) || !std::isfinite(outR)) {
                lastResult = ImmersiveProcessResult::InvalidOutput;
                return false;
            }

            interleavedStereo[li] = outL;
            interleavedStereo[li + 1] = outR;
        }

        // Note: unlike the Steam Audio path there is deliberately no
        // "input energy implies output energy" check here. The renderer has
        // one block of algorithmic latency (`latencySamples()`), so the first
        // block after prepare()/reset() is legitimately silent.
        lastState = 0;
        lastResult = ImmersiveProcessResult::NativeSpatialProcessed;
        return true;
    }

    /// Full-Partitioned Linear Convolution Path (Stage 4).
    /// Convolves input PCM with pure physical Room Impulse Response (BRIR)
    /// across 4 non-uniform partition tiers with zero audio-thread allocations,
    /// zero locks, zero syscalls, and exact linear convolution y[n] = (x * h)[n].
    bool processFullConvolution(float* interleavedStereo, int frames) noexcept {
        if (!fullConvolutionReady) {
            lastResult = ImmersiveProcessResult::NotPrepared;
            return false;
        }

        const int maxN = std::min(frames, frameCapacity);

        // 1. Sanitise input into preallocated scratch channels
        for (int i = 0; i < maxN; ++i) {
            convScratchInL[static_cast<std::size_t>(i)] = sanitizeInputSample(interleavedStereo[2 * i]);
            convScratchInR[static_cast<std::size_t>(i)] = sanitizeInputSample(interleavedStereo[2 * i + 1]);
        }

        // 2. Multi-tier partitioned linear convolution (128-sample head block)
        constexpr int kBlock = 128;
        for (int offset = 0; offset < maxN; offset += kBlock) {
            const int chunk = std::min(kBlock, maxN - offset);
            if (chunk == kBlock) {
                convLeft.processBlock(convScratchInL.data() + offset, convScratchOutL.data() + offset);
                convRight.processBlock(convScratchInR.data() + offset, convScratchOutR.data() + offset);
            } else {
                float inChunkL[kBlock] = {};
                float inChunkR[kBlock] = {};
                float outChunkL[kBlock] = {};
                float outChunkR[kBlock] = {};
                std::copy_n(convScratchInL.data() + offset, chunk, inChunkL);
                std::copy_n(convScratchInR.data() + offset, chunk, inChunkR);
                convLeft.processBlock(inChunkL, outChunkL);
                convRight.processBlock(inChunkR, outChunkR);
                std::copy_n(outChunkL, chunk, convScratchOutL.data() + offset);
                std::copy_n(outChunkR, chunk, convScratchOutR.data() + offset);
            }
        }

        // 3. Dry/wet blend & peak limiter
        for (int i = 0; i < maxN; ++i) {
            const float dryL = convScratchInL[static_cast<std::size_t>(i)];
            const float dryR = convScratchInR[static_cast<std::size_t>(i)];
            const float wetL = convScratchOutL[static_cast<std::size_t>(i)];
            const float wetR = convScratchOutR[static_cast<std::size_t>(i)];

            float outL = (1.0f - spatialBlend) * dryL + spatialBlend * wetL;
            float outR = (1.0f - spatialBlend) * dryR + spatialBlend * wetR;

            applyOutputLimiter(outL, outR);

            if (!std::isfinite(outL) || !std::isfinite(outR)) {
                lastResult = ImmersiveProcessResult::InvalidOutput;
                return false;
            }

            interleavedStereo[2 * i] = outL;
            interleavedStereo[2 * i + 1] = outR;
        }

        lastState = 0;
        lastResult = ImmersiveProcessResult::FullConvolutionProcessed;
        return true;
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
    impl_->frameCapacity = std::max(maxFrames, Impl::kSteamAudioFrameSize);
    impl_->inputLeft.resize(static_cast<std::size_t>(impl_->frameCapacity));
    impl_->inputRight.resize(static_cast<std::size_t>(impl_->frameCapacity));
    impl_->outputLeft.resize(static_cast<std::size_t>(impl_->frameCapacity));
    impl_->outputRight.resize(static_cast<std::size_t>(impl_->frameCapacity));
    impl_->inputChannels[0] = impl_->inputLeft.data();
    impl_->inputChannels[1] = impl_->inputRight.data();
    impl_->outputChannels[0] = impl_->outputLeft.data();
    impl_->outputChannels[1] = impl_->outputRight.data();
    impl_->initializeRoomBuffers();

    // Native spatial renderer: HOA encode -> sound-field rotation -> HRTF
    // convolution. This is the fallback path when Steam Audio is not
    // available, and it is what makes the engine's spatial chain testable on
    // the host where the vendored Android .so cannot be loaded.
    bool nativeReady = false;
    {
        spatial::SpatialRendererConfig scfg;
        scfg.ambisonicOrder = 2;
        scfg.array = spatial::VirtualArray::Dodeca12;
        scfg.hrirTaps = 128;
        scfg.renderBlock = 128;
        nativeReady = impl_->nativeRenderer.prepare(static_cast<double>(sampleRate),
                                                    maxFrames, scfg);
        if (nativeReady) {
            impl_->nativeRenderer.setStereoWidth(impl_->widthNorm);
            impl_->nativeRenderer.setSpatialBlend(impl_->spatialBlend);
        }
    }

    // Full Partitioned Linear Convolver preparation (4 non-uniform tiers)
    {
        dsp::NonUniformConvolver::Config convCfg;
        convCfg.headBlock = 128;
        convCfg.maxTaps = 32768;
        convCfg.growth = 4;
        convCfg.maxTiers = 4;
        convCfg.crossfadeBlocks = 8;
        impl_->fullConvolutionReady = impl_->convLeft.prepare(convCfg) && impl_->convRight.prepare(convCfg);
        if (impl_->fullConvolutionReady) {
            impl_->convScratchInL.resize(static_cast<std::size_t>(impl_->frameCapacity), 0.0f);
            impl_->convScratchInR.resize(static_cast<std::size_t>(impl_->frameCapacity), 0.0f);
            impl_->convScratchOutL.resize(static_cast<std::size_t>(impl_->frameCapacity), 0.0f);
            impl_->convScratchOutR.resize(static_cast<std::size_t>(impl_->frameCapacity), 0.0f);
            impl_->reloadBrir();
        }
    }

    if (impl_->backendPreference == SpatialBackend::FullConvolution && impl_->fullConvolutionReady) {
        impl_->backend = SpatialBackend::FullConvolution;
    } else {
        impl_->backend = nativeReady ? SpatialBackend::Native : SpatialBackend::None;
    }

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    // Try Steam Audio. If any stage fails we tear down only the Steam objects
    // and keep running on the native renderer — a missing/!broken libphonon is
    // not a reason to fail closed when the built-in spatialiser is ready.
    const bool steamReady = [&]() noexcept {
        IPLContextSettings contextSettings{};
        contextSettings.version = STEAMAUDIO_VERSION;
        if (iplContextCreate(&contextSettings, &impl_->context) != IPL_STATUS_SUCCESS || impl_->context == nullptr) {
            return false;
        }

        IPLAudioSettings audioSettings{};
        audioSettings.samplingRate = sampleRate;
        // Steam Audio effects use a fixed frame size; process() pads/splits
        // variable Media3 blocks before applying the effect.
        audioSettings.frameSize = Impl::kSteamAudioFrameSize;

        IPLHRTFSettings hrtfSettings{};
        hrtfSettings.type = IPL_HRTFTYPE_DEFAULT;
        hrtfSettings.volume = 1.0f;
        if (iplHRTFCreate(impl_->context, &audioSettings, &hrtfSettings, &impl_->hrtf) != IPL_STATUS_SUCCESS || impl_->hrtf == nullptr) {
            return false;
        }

        IPLBinauralEffectSettings effectSettings{};
        effectSettings.hrtf = impl_->hrtf;
        if (iplBinauralEffectCreate(impl_->context, &audioSettings, &effectSettings, &impl_->effect) != IPL_STATUS_SUCCESS || impl_->effect == nullptr) {
            return false;
        }
        return true;
    }();

    if (steamReady) {
        impl_->backend = SpatialBackend::SteamAudio;
    } else {
        impl_->releaseSteamAudio();
        if (!nativeReady) {
            impl_->release();
            return false;
        }
        impl_->backend = SpatialBackend::Native;
    }
#else
    // No Steam Audio: run the built-in renderer rather than failing closed.
    if (!nativeReady) {
        impl_->release();
        return false;
    }
#endif

    if (impl_->backend == SpatialBackend::None) {
        impl_->release();
        return false;
    }

    impl_->prepared = true;
    impl_->lastResult = ImmersiveProcessResult::Disabled;
    return true;
}

void ImmersiveAudioEngine::reset() noexcept {
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    if (impl_->effect != nullptr) {
        iplBinauralEffectReset(impl_->effect);
    }
#endif
    impl_->nativeRenderer.reset();
    if (impl_->fullConvolutionReady) {
        impl_->convLeft.reset();
        impl_->convRight.reset();
    }
    impl_->clearStateOnly();
    impl_->lastResult = impl_->prepared ? ImmersiveProcessResult::Disabled : ImmersiveProcessResult::NotPrepared;
    impl_->lastState = -1;
}

void ImmersiveAudioEngine::setEnabled(bool enabled) noexcept {
    impl_->enabled = enabled;
    if (!enabled && impl_->prepared) {
        impl_->lastResult = ImmersiveProcessResult::Disabled;
    }
}

void ImmersiveAudioEngine::setSpatialBlend(float blend) noexcept {
    impl_->spatialBlend = std::isfinite(blend) ? std::clamp(blend, 0.0f, 1.0f) : 0.0f;
    // The native renderer performs its own delay-matched dry/wet crossfade.
    impl_->nativeRenderer.setSpatialBlend(impl_->spatialBlend);
}

void ImmersiveAudioEngine::setHeadOrientation(float yawDeg, float pitchDeg, float rollDeg) noexcept {
    spatial::HeadOrientation o;
    o.yawDeg = std::isfinite(yawDeg) ? yawDeg : 0.0f;
    o.pitchDeg = std::isfinite(pitchDeg) ? pitchDeg : 0.0f;
    o.rollDeg = std::isfinite(rollDeg) ? rollDeg : 0.0f;
    impl_->nativeRenderer.setHeadOrientation(o);
    impl_->activeSpace.setListenerOrientation(o);
    if (impl_->backend == SpatialBackend::FullConvolution && impl_->fullConvolutionReady) {
        impl_->reloadBrir();
    }
}

void ImmersiveAudioEngine::setListenerOrientation(float yawDeg, float pitchDeg, float rollDeg) noexcept {
    setHeadOrientation(yawDeg, pitchDeg, rollDeg);
}

SpatialBackend ImmersiveAudioEngine::backend() const noexcept {
    return impl_->backend;
}

int ImmersiveAudioEngine::latencySamples() const noexcept {
    if (!impl_->prepared) return 0;
    if (impl_->backend == SpatialBackend::Native) {
        return static_cast<int>(impl_->nativeRenderer.latencySamples());
    }
    return 0;
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
    if (impl_->fullConvolutionReady) {
        const float scale = 0.5f + impl_->roomSizeNorm * 1.5f;
        impl_->activeSpace.setScale(scale);
        impl_->reloadBrir();
    }
}

void ImmersiveAudioEngine::setDampening(float dampening) noexcept {
    impl_->dampeningNorm = clampUnit(dampening);
    impl_->updateRoomModel();
    if (impl_->fullConvolutionReady) {
        const float alpha = 0.05f + impl_->dampeningNorm * 0.70f;
        spatial::AcousticMaterial mat;
        mat.name = "Damped";
        mat.absorption.fill(alpha);
        mat.scattering = 0.15f;
        for (int s = 0; s < 6; ++s) {
            impl_->activeSpace.setBoundary(static_cast<spatial::RoomSurface>(s),
                                           {static_cast<spatial::RoomSurface>(s), mat, 0.0f});
        }
        impl_->reloadBrir();
    }
}

void ImmersiveAudioEngine::setStereoWidth(float width) noexcept {
    impl_->widthNorm = clampUnit(width);
    impl_->nativeRenderer.setStereoWidth(impl_->widthNorm);
    impl_->updateRoomModel();
}

SpaceDesignControls ImmersiveAudioEngine::spaceDesignControls() const noexcept {
    return impl_->currentSpaceDesignControls();
}

bool ImmersiveAudioEngine::isPrepared() const noexcept {
    return impl_->prepared;
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
    if (!impl_->enabled) {
        impl_->lastResult = ImmersiveProcessResult::Disabled;
        return false;
    }
    if (interleavedStereo == nullptr || frames <= 0 || frames > impl_->maxFrames) {
        impl_->lastResult = ImmersiveProcessResult::InvalidInput;
        return false;
    }

    // Full-Partitioned Linear Convolution: physical room BRIR convolution.
    if (impl_->backend == SpatialBackend::FullConvolution) {
        return impl_->processFullConvolution(interleavedStereo, frames);
    }

    // Native backend: built-in HOA/HRTF renderer -> room -> limiter.
    if (impl_->backend == SpatialBackend::Native) {
        return impl_->processNative(interleavedStereo, frames);
    }

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    if (impl_->backend != SpatialBackend::SteamAudio || impl_->effect == nullptr) {
        impl_->lastResult = ImmersiveProcessResult::SteamAudioUnavailable;
        return false;
    }

    bool anyInputEnergy = false;
    bool anyOutputEnergy = false;
    int frameOffset = 0;
    while (frameOffset < frames) {
        const int activeFrames = std::min(Impl::kSteamAudioFrameSize, frames - frameOffset);
        const int steamFrames = Impl::kSteamAudioFrameSize;
        std::fill(impl_->inputLeft.begin(), impl_->inputLeft.begin() + steamFrames, 0.0f);
        std::fill(impl_->inputRight.begin(), impl_->inputRight.begin() + steamFrames, 0.0f);
        std::fill(impl_->outputLeft.begin(), impl_->outputLeft.begin() + steamFrames, 0.0f);
        std::fill(impl_->outputRight.begin(), impl_->outputRight.begin() + steamFrames, 0.0f);
        for (int frame = 0; frame < activeFrames; ++frame) {
            impl_->inputLeft[static_cast<std::size_t>(frame)] = sanitizeInputSample(interleavedStereo[(frameOffset + frame) * 2]);
            impl_->inputRight[static_cast<std::size_t>(frame)] = sanitizeInputSample(interleavedStereo[(frameOffset + frame) * 2 + 1]);
        }

        IPLAudioBuffer input{};
        input.numChannels = 2;
        input.numSamples = steamFrames;
        input.data = impl_->inputChannels;
        IPLAudioBuffer output{};
        output.numChannels = 2;
        output.numSamples = steamFrames;
        output.data = impl_->outputChannels;

        IPLBinauralEffectParams params{};
        params.direction = IPLVector3{0.0f, 0.0f, 1.0f};
        params.interpolation = IPL_HRTFINTERPOLATION_BILINEAR;
        params.spatialBlend = impl_->spatialBlend;
        params.hrtf = impl_->hrtf;
        params.peakDelays = nullptr;

        const IPLAudioEffectState state = iplBinauralEffectApply(impl_->effect, &params, &input, &output);
        impl_->lastState = static_cast<int>(state);
        if (state != IPL_AUDIOEFFECTSTATE_TAILCOMPLETE && state != IPL_AUDIOEFFECTSTATE_TAILREMAINING) {
            // Steam Audio stopped working at runtime. Hand the remaining and
            // all subsequent blocks to the built-in renderer rather than
            // dropping spatialisation entirely.
            if (impl_->nativeRenderer.ready()) {
                impl_->backend = SpatialBackend::Native;
                return impl_->processNative(interleavedStereo + static_cast<std::size_t>(frameOffset) * 2u,
                                            frames - frameOffset);
            }
            impl_->lastResult = ImmersiveProcessResult::SteamAudioUnavailable;
            return false;
        }

        bool inputHasEnergy = false;
        bool outputHasEnergy = false;
        // Fixed headroom keeps normal HRTF output below the safety limiter. The limiter is now
        // reserved for exceptional peaks instead of acting as a continuous tone shaper.
        constexpr float kSteamAudioOutputGain = 0.50118723f; // -6 dB
        for (int frame = 0; frame < activeFrames; ++frame) {
            const float inputLeft = impl_->inputLeft[static_cast<std::size_t>(frame)];
            const float inputRight = impl_->inputRight[static_cast<std::size_t>(frame)];
            float outputLeft = impl_->outputLeft[static_cast<std::size_t>(frame)] * kSteamAudioOutputGain;
            float outputRight = impl_->outputRight[static_cast<std::size_t>(frame)] * kSteamAudioOutputGain;
            if (!std::isfinite(outputLeft) || !std::isfinite(outputRight)) {
                impl_->lastResult = ImmersiveProcessResult::InvalidOutput;
                return false;
            }

            impl_->applyRoomModel(outputLeft, outputRight);
            impl_->applyOutputLimiter(outputLeft, outputRight);

            if (!std::isfinite(outputLeft) || !std::isfinite(outputRight)) {
                impl_->lastResult = ImmersiveProcessResult::InvalidOutput;
                return false;
            }

            inputHasEnergy = inputHasEnergy || std::fabs(inputLeft) > 1.0e-8f || std::fabs(inputRight) > 1.0e-8f;
            outputHasEnergy = outputHasEnergy || std::fabs(outputLeft) > 1.0e-8f || std::fabs(outputRight) > 1.0e-8f;

            interleavedStereo[(frameOffset + frame) * 2] = outputLeft;
            interleavedStereo[(frameOffset + frame) * 2 + 1] = outputRight;
        }
        if (inputHasEnergy && !outputHasEnergy) {
            impl_->lastResult = ImmersiveProcessResult::InvalidOutput;
            return false;
        }
        anyInputEnergy = anyInputEnergy || inputHasEnergy;
        anyOutputEnergy = anyOutputEnergy || outputHasEnergy;
        frameOffset += activeFrames;
    }
    if (anyInputEnergy && !anyOutputEnergy) {
        impl_->lastResult = ImmersiveProcessResult::InvalidOutput;
        return false;
    }
    impl_->lastResult = ImmersiveProcessResult::SteamAudioProcessed;
    return true;
#else
    // Prepared with no usable backend: should be unreachable because
    // prepare() fails when neither backend initialises.
    impl_->lastResult = ImmersiveProcessResult::SteamAudioUnavailable;
    return false;
#endif
}

void ImmersiveAudioEngine::setSpatialBackendPreference(SpatialBackend backend) noexcept {
    impl_->backendPreference = backend;
    if (backend == SpatialBackend::FullConvolution && impl_->fullConvolutionReady) {
        impl_->backend = SpatialBackend::FullConvolution;
    } else if (backend == SpatialBackend::Native && impl_->nativeRenderer.ready()) {
        impl_->backend = SpatialBackend::Native;
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    } else if (backend == SpatialBackend::SteamAudio && impl_->effect != nullptr) {
        impl_->backend = SpatialBackend::SteamAudio;
#endif
    } else if (backend == SpatialBackend::None) {
        impl_->backend = SpatialBackend::None;
    }
}

void ImmersiveAudioEngine::setSpacePreset(spatial::SpaceProfile::Preset preset) noexcept {
    impl_->activeSpace = spatial::SpaceProfile::createPreset(preset);
    impl_->reloadBrir();
}

void ImmersiveAudioEngine::setSpaceProfile(const spatial::SpaceProfile& profile) noexcept {
    impl_->activeSpace = profile;
    impl_->reloadBrir();
}

void ImmersiveAudioEngine::setRoomDimensions(float x, float y, float z) noexcept {
    impl_->activeSpace.setDimensions({x, y, z});
    impl_->reloadBrir();
}

void ImmersiveAudioEngine::setBoundaryMaterial(spatial::RoomSurface surface, const spatial::AcousticMaterial& material) noexcept {
    impl_->activeSpace.setBoundary(surface, {surface, material, 0.0f});
    impl_->reloadBrir();
}

void ImmersiveAudioEngine::setSourcePosition(float x, float y, float z) noexcept {
    spatial::Coord3D userPos{x, y, z};
    impl_->activeSpace.setSourcePosition(userPos.toEngineCoords());
    impl_->reloadBrir();
}

void ImmersiveAudioEngine::setListenerPosition(float x, float y, float z) noexcept {
    spatial::Coord3D userPos{x, y, z};
    impl_->activeSpace.setListenerPosition(userPos.toEngineCoords());
    impl_->reloadBrir();
}

void ImmersiveAudioEngine::setSourceTrajectory(const spatial::SourceTrajectory& trajectory) noexcept {
    impl_->activeSpace.setTrajectory(trajectory);
}

void ImmersiveAudioEngine::setTrajectoryPosition(float timeSeconds) noexcept {
    const spatial::Vec3 pos = impl_->activeSpace.trajectory().positionAt(timeSeconds);
    impl_->activeSpace.setSourcePosition(pos);
    impl_->reloadBrir();
}

void ImmersiveAudioEngine::setIrLength(std::size_t taps) noexcept {
    impl_->irLengthTaps = std::clamp<std::size_t>(taps, 512, 32768);
    impl_->reloadBrir();
}

void ImmersiveAudioEngine::setReflectionDensity(float density) noexcept {
    impl_->reflectionDensity = std::clamp(density, 0.0f, 1.0f);
    impl_->reloadBrir();
}

const spatial::SpaceProfile& ImmersiveAudioEngine::activeSpaceProfile() const noexcept {
    return impl_->activeSpace;
}

const spatial::StereoBrir& ImmersiveAudioEngine::activeBrir() const noexcept {
    return impl_->activeBrir;
}

} // namespace frostsoulx
