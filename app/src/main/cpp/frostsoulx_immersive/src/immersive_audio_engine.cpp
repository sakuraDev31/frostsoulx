#include "frostsoulx/immersive_audio_engine.h"
#include "frostsoulx/dsp/partitioned_convolver.h"
#include "frostsoulx/dsp/stereo_frontend.h"
#include "frostsoulx/dsp/true_peak.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <vector>

namespace frostsoulx {
namespace {
float unit(float x) noexcept { return std::isfinite(x) ? std::clamp(x, 0.0f, 1.0f) : 0.0f; }
float finite(float x) noexcept { return std::isfinite(x) ? x : 0.0f; }
float sample(float x) noexcept { return std::clamp(finite(x), -2.0f, 2.0f); }
}

struct ImmersiveAudioEngine::Impl {
    static constexpr std::size_t kBlock = 128; // Three heads per 384-frame callback.
    int rate = 0, maximum = 0, dryDelay = 0;
    bool prepared = false;
    std::atomic<bool> enabled{false};
    std::atomic<float> blend{1.0f}, bassGain{1.0f}, bassWidth{1.0f}, highWidth{1.0f};
    std::atomic<ImmersiveProcessResult> result{ImmersiveProcessResult::NotPrepared};
    SpaceDesignControls controls{};
    RoomSimulationPreset room = RoomSimulationPreset::Studio;
    float roomMix = 0.18f, reflections = 0.28f, reverbTime = 1.35f, density = 0.5f;
    std::size_t irLength = 16384;
    bool customIrActive = false;
    std::array<std::vector<float>, 4> customIr;
    spatial::SpaceProfile space = spatial::SpaceProfile::createLivingRoom();
    spatial::HrtfDatabase hrtf;
    spatial::RirGenerator generator;
    std::array<spatial::StereoBrir, 2> brir;
    dsp::MimoConvolver convolution;
    dsp::StereoFrontend frontend;
    dsp::TruePeakSafety safety;
    std::array<std::array<float, kBlock>, 2> input{}, wet{}, output{}, bassInput{}, bassOutput{};
    std::vector<float> dryRing, bassRing;
    std::size_t dryWrite = 0, fill = 0;
    float blendCurrent = 1.0f;
    float normalizationGain = 1.0f;

    // Orbit geometry/BRIR preparation stays on the control thread. The
    // automatic 8D layer is allocation-free: independent per-ear level/delay
    // motion plus vertical pinna cues, never a mono fold-down. Stereo bass
    // bypasses both this renderer and the four-path room matrix.
    static constexpr float kClassic8dRateHz = 0.09f; // ~11.1 s per cycle
    static constexpr float kClassic8dLowCutHz = 170.0f;
    std::atomic<bool> orbitEnabled{false};
    float orbitMix = 0.0f;
    std::array<std::array<float, 256>, 2> orbitDelay{};
    std::size_t orbitWrite = 0;
    float orbitAzimuthDeg = 0.0f;
    std::atomic<float> orbitPhaseRad{0.0f};
    float orbitLow = 0.0f;
    float orbitSideLow = 0.0f;
    float orbitSplit = 0.0f;
    float orbitElevationDeg = 0.0f;
    // Independent per-ear state for the smooth, elevation-dependent pinna cue.
    // The cue is deliberately an envelope over the existing stereo output,
    // not a second HRTF/convolution backend.
    std::array<float, 2> orbitNotchX1{}, orbitNotchX2{}, orbitNotchY1{}, orbitNotchY2{};
    float orbitNotchHz = 5800.0f;
    float orbitNotchDepth = 0.10f;
    float orbitRadiusMetres = 3.0f;
    spatial::Vec3 preOrbitSourcePosition{0.0f, 0.0f, 0.0f};
    bool orbitBackupValid = false;

    void reloadOrbitPosition() noexcept {
        const float az = orbitAzimuthDeg * rt::kDegToRad;
        const float el = orbitElevationDeg * rt::kDegToRad;
        const float ce = std::cos(el);
        // Engine-local coordinates: +X front, +Y left, +Z up.
        const spatial::Vec3 offset{
            orbitRadiusMetres * ce * std::cos(az),
            orbitRadiusMetres * ce * std::sin(az),
            orbitRadiusMetres * std::sin(el)
        };
        space.setSourcePosition(space.listenerPosition() + offset);
        reload();
    }

    // Control-thread spatializer: geometry -> listener-local directions ->
    // ILD/ITD/pinna HRTF for direct + reflected arrivals. Collapse the entire
    // linear spatial model into four filters, rather than running a second
    // competing HOA renderer or doing HRTF convolution twice on the audio thread.
    bool reload(bool forceCustom = false) noexcept {
        if (!prepared) return true;
        try {
            if (customIrActive && !customIr[0].empty()) {
                if (!forceCustom) return true; // Room knobs do not rebuild an unchanged custom response.
                const float* matrix[4] = {customIr[0].data(), customIr[1].data(), customIr[2].data(), customIr[3].data()};
                return convolution.loadMatrix(matrix, customIr[0].size());
            }
            spatial::RirGeneratorConfig cfg;
            cfg.sampleRate = rate;
            cfg.maxTaps = irLength;
            cfg.maxIsmOrder = room == RoomSimulationPreset::Off ? 0 : 2;
            cfg.enableDiffuseTail = room != RoomSimulationPreset::Off;
            cfg.diffuseEnergyRatio = density * roomMix;
            cfg.reflectionGain = reflections * roomMix;
            cfg.reverbTimeScale = reverbTime / 1.35f;
            cfg.alignDirectArrival = true;
            cfg.normalize = false; // Normalize the COMPLETE transfer matrix below.
            generator.setConfig(cfg);
            std::array<spatial::StereoBrir, 2> next;
            const auto centre = space.sourcePosition() - space.listenerPosition();
            const float az = 30.0f * rt::kDegToRad;
            for (std::size_t i = 0; i < 2; ++i) {
                const float angle = i == 0 ? az : -az;
                const spatial::Vec3 ray{centre.x * std::cos(angle) - centre.y * std::sin(angle),
                                       centre.x * std::sin(angle) + centre.y * std::cos(angle), centre.z};
                auto source = space;
                source.setSourcePosition(space.listenerPosition() + ray);
                next[i] = generator.generateBrir(source, hrtf);
            }
            // Use a power/RMS bound for the transfer matrix. An L1 tap-sum bound
            // is safe but catastrophically conservative for long, sparse BRIRs:
            // it attenuates a car/tunnel response by tens of dB and removes the
            // audible reflections. The final true-peak limiter still protects
            // arbitrary programme transients after convolution.
            double rmsBound = 0.0;
            for (int ear = 0; ear < 2; ++ear) {
                double power = 0.0;
                for (const auto& source : next) {
                    const auto& taps = ear == 0 ? source.left : source.right;
                    for (float x : taps) {
                        if (!std::isfinite(x)) return false;
                        power += static_cast<double>(x) * static_cast<double>(x);
                    }
                }
                rmsBound = std::max(rmsBound, std::sqrt(power));
            }
            const float gain = static_cast<float>(std::min(1.0, 0.95 / std::max(rmsBound, 0.95)));
            for (auto& source : next) {
                for (float& x : source.left) x *= gain;
                for (float& x : source.right) x *= gain;
            }
            const float* matrix[4] = {next[0].left.data(), next[0].right.data(), next[1].left.data(), next[1].right.data()};
            if (!convolution.loadMatrix(matrix, irLength)) return false;
            brir = std::move(next);
            normalizationGain = gain;
            return true;
        } catch (...) { return false; } // Retain the previous valid IR on control OOM.
    }

    void render() noexcept {
        // Room/HRTF convolution remains the spatial/acoustic layer.
        // The 8D layer below provides the familiar slow YouTube-style motion.

        const float* in[2] = {input[0].data(), input[1].data()};
        float* out[2] = {wet[0].data(), wet[1].data()};
        convolution.processBlock(in, out);
        const float target = blend.load(std::memory_order_relaxed);
        const float step = (target - blendCurrent) / static_cast<float>(kBlock);
        for (std::size_t n = 0; n < kBlock; ++n) {
            const float b = blendCurrent + step * static_cast<float>(n);
            for (std::size_t c = 0; c < 2; ++c) {
                const std::size_t pos = 2 * dryWrite + c;
                const float dry = dryRing[pos];
                dryRing[pos] = input[c][n];
                bassOutput[c][n] = bassRing[pos];
                bassRing[pos] = bassInput[c][n];
                // Convex, delay-matched blend. Equal power is WRONG for
                // correlated dry/wet and gave a +3 dB boost at half intensity.
                output[c][n] = dry + b * (wet[c][n] - dry);
            }
            dryWrite = (dryWrite + 1) % static_cast<std::size_t>(dryDelay);
        }
        blendCurrent = target;

        const bool orbitOn = orbitEnabled.load(std::memory_order_relaxed);
        if (orbitOn || orbitMix > 0.00001f) {
            constexpr float kTwoPi = 6.2831853071795864769f;
            const float phaseStep = kTwoPi * kClassic8dRateHz / static_cast<float>(rate);
            float phase = orbitPhaseRad.load(std::memory_order_relaxed);
            const float motionSmoothing = 1.0f - std::exp(-1.0f / (0.025f * static_cast<float>(rate)));

            // Elevation is carried by pinna-like spectral contrast, not by a
            // left/right pan. Research places important elevation-dependent
            // HRTF peaks/notches around 6-9 kHz. Smooth targets at block rate
            // avoid zipper noise while keeping all filter work allocation-free.
            const float elevationNow = std::fabs(std::sin(phase));
            const float frontRear = std::cos(phase);
            const float requestedNotchHz = 5800.0f + 2500.0f * elevationNow
                                         + (frontRear < 0.0f ? 350.0f : 0.0f);
            // Elevation cues are strongest in the pinna-sensitive upper bands
            // (~6-9 kHz), but keep the filter below Nyquist on low-rate streams.
            // RBJ notch coefficients become invalid if sin(omega) crosses into
            // the aliased region, so clamp the *state* as well as the target.
            const float maxNotchHz = 0.45f * static_cast<float>(rate);
            const float targetNotchHz = std::clamp(requestedNotchHz, 1200.0f, maxNotchHz);
            const float targetDepth = 0.10f + 0.35f * elevationNow;
            // A time-constant-based one-pole smoother makes the trajectory's
            // response independent of sample rate and the internal 128-frame
            // processing quantum (about an 18 ms time constant).
            constexpr float kCueSmoothingSeconds = 0.018f;
            const float cueSmoothing = 1.0f - std::exp(
                -static_cast<float>(kBlock) /
                (kCueSmoothingSeconds * static_cast<float>(rate)));
            orbitNotchHz = std::clamp(
                orbitNotchHz + cueSmoothing * (targetNotchHz - orbitNotchHz),
                1200.0f, maxNotchHz);
            orbitNotchDepth += cueSmoothing * (targetDepth - orbitNotchDepth);

            const float omega = kTwoPi * orbitNotchHz / static_cast<float>(rate);
            const float cosine = std::cos(omega);
            const float sine = std::sin(omega);
            constexpr float kNotchQ = 2.2f;
            const float alpha = sine / (2.0f * kNotchQ);
            const float invA0 = 1.0f / (1.0f + alpha);
            const float b0 = invA0;
            const float b1 = -2.0f * cosine * invA0;
            const float b2 = invA0;
            const float a1 = b1;
            const float a2 = (1.0f - alpha) * invA0;

            for (std::size_t n = 0; n < kBlock; ++n) {
                // Reversible M/S encoding retains the original stereo Side.
                // Never fold the complete signal to mono: anti-phase/wide
                // stereo material must remain audible throughout the orbit.
                const float mid = 0.5f * (output[0][n] + output[1][n]);
                const float side = 0.5f * (output[0][n] - output[1][n]);
                orbitLow += orbitSplit * (mid - orbitLow);
                orbitSideLow += orbitSplit * (side - orbitSideLow);
                const float midHigh = mid - orbitLow;
                const float sideHigh = side - orbitSideLow;
                const float elevation = std::fabs(std::sin(phase));
                const float midHighGain = 1.0f - 0.02f * elevation;
                const float sideHighGain = 1.0f - 0.18f * elevation;
                const float outMid = orbitLow + midHigh * midHighGain;
                const float outSide = orbitSideLow + sideHigh * sideHighGain;
                const float stereo[2] = {outMid + outSide, outMid - outSide};

                // Same smoothly moving pinna notch on each ear. Independent
                // histories preserve channel detail and stereo image cues.
                for (std::size_t c = 0; c < 2; ++c) {
                    const float x = stereo[c];
                    const float y = b0 * x + b1 * orbitNotchX1[c]
                                  + b2 * orbitNotchX2[c] - a1 * orbitNotchY1[c]
                                  - a2 * orbitNotchY2[c];
                    orbitNotchX2[c] = orbitNotchX1[c];
                    orbitNotchX1[c] = x;
                    orbitNotchY2[c] = orbitNotchY1[c];
                    orbitNotchY1[c] = rt::flushDenormal(y);
                    const float shaped = x + orbitNotchDepth * (y - x);
                    // Independent fractional per-ear delay and level cues trace
                    // a complete horizontal circle without summing stereo to mono.
                    orbitDelay[c][orbitWrite] = shaped;
                    const float lateral = std::sin(phase) * (c == 0 ? -1.0f : 1.0f);
                    const float delay = 0.000325f * static_cast<float>(rate) * (1.0f + lateral);
                    const auto whole = static_cast<std::size_t>(delay);
                    const float fraction = delay - static_cast<float>(whole);
                    const auto a = (orbitWrite + 256 - whole) % 256;
                    const auto b = (a + 255) % 256;
                    const float delayed = orbitDelay[c][a] + fraction * (orbitDelay[c][b] - orbitDelay[c][a]);
                    const float level = std::sqrt(0.5f * (1.0f - 0.85f * lateral));
                    output[c][n] += orbitMix * (delayed * level - output[c][n]);
                }

                orbitWrite = (orbitWrite + 1) % 256;
                orbitMix += motionSmoothing *
                            ((orbitOn ? 1.0f : 0.0f) - orbitMix);
                phase += phaseStep;
                if (phase >= kTwoPi) phase -= kTwoPi;
            }
            orbitPhaseRad = phase;
        }
        // Bass never enters the room/custom-IR matrix or the orbit renderer.
        // Match its delay to the dry high band before the shared safety limiter.
        for (std::size_t c = 0; c < 2; ++c)
            for (std::size_t n = 0; n < kBlock; ++n) output[c][n] += bassOutput[c][n];
    }
};

ImmersiveAudioEngine::ImmersiveAudioEngine() : impl_(std::make_unique<Impl>()) {}
ImmersiveAudioEngine::~ImmersiveAudioEngine() = default;

bool ImmersiveAudioEngine::prepare(int sampleRate, int maxFrames) noexcept {
    if (sampleRate < 8000 || sampleRate > 384000 || maxFrames <= 0) return false;
    impl_->prepared = false;
    try {
        impl_->rate = sampleRate; impl_->maximum = maxFrames;
        impl_->orbitSplit = 1.0f - std::exp(
            -2.0f * 3.14159265358979323846f * Impl::kClassic8dLowCutHz /
            static_cast<float>(sampleRate));
        std::size_t hrirTaps = 128;
        while (hrirTaps < static_cast<std::size_t>(sampleRate / 500)) hrirTaps *= 2;
        if (!impl_->hrtf.buildParametric(sampleRate, hrirTaps)) return false;
        impl_->dryDelay = static_cast<int>(std::ceil(rt::kHeadRadius / rt::kSpeedOfSound * static_cast<float>(sampleRate))) + 5;
        dsp::NonUniformConvolver::Config cfg;
        cfg.headBlock = Impl::kBlock; cfg.maxTaps = 32768 + static_cast<std::size_t>(impl_->dryDelay);
        cfg.maxTiers = 4; cfg.growth = 4; cfg.crossfadeBlocks = 8;
        if (!impl_->convolution.prepare(2, 2, cfg)) return false;
        impl_->dryDelay = static_cast<int>(std::ceil(rt::kHeadRadius / rt::kSpeedOfSound * static_cast<float>(sampleRate))) + 5;
        impl_->dryRing.assign(static_cast<std::size_t>(2 * impl_->dryDelay), 0.0f);
        impl_->bassRing.assign(impl_->dryRing.size(), 0.0f);
        impl_->frontend.prepare(sampleRate); impl_->safety.prepare(sampleRate);
        impl_->prepared = true;
        if (!impl_->reload(true)) { impl_->prepared = false; return false; }
        reset();
        return true;
    } catch (...) { impl_->prepared = false; return false; }
}
void ImmersiveAudioEngine::reset() noexcept {
    if (impl_->prepared) {
        impl_->convolution.reset(); impl_->frontend.reset(); impl_->safety.reset();
        impl_->input = {}; impl_->wet = {}; impl_->output = {};
        impl_->bassInput = {}; impl_->bassOutput = {}; impl_->orbitDelay = {};
        impl_->orbitWrite = 0; impl_->orbitMix = 0.0f; impl_->orbitPhaseRad = 0.0f;
        std::fill(impl_->dryRing.begin(), impl_->dryRing.end(), 0.0f);
        std::fill(impl_->bassRing.begin(), impl_->bassRing.end(), 0.0f);
        impl_->dryWrite = impl_->fill = 0;
        impl_->blendCurrent = impl_->blend.load();
        impl_->orbitLow = impl_->orbitSideLow = 0.0f;
        impl_->orbitNotchX1 = {}; impl_->orbitNotchX2 = {};
        impl_->orbitNotchY1 = {}; impl_->orbitNotchY2 = {};
        impl_->orbitNotchHz = 5800.0f; impl_->orbitNotchDepth = 0.10f;
    }
    impl_->result.store(impl_->prepared ? ImmersiveProcessResult::Disabled : ImmersiveProcessResult::NotPrepared);
}
void ImmersiveAudioEngine::setEnabled(bool enabled) noexcept { impl_->enabled.store(enabled); }
void ImmersiveAudioEngine::setSpatialBlend(float blend) noexcept { impl_->blend.store(unit(blend)); }
bool ImmersiveAudioEngine::setCustomImpulseResponse(const float* left, const float* right, std::size_t taps) noexcept {
    const float* matrix[4] = {left, nullptr, nullptr, right};
    return left && right && setCustomTransferMatrix(matrix, taps);
}
bool ImmersiveAudioEngine::setCustomTransferMatrix(const float* const paths[4], std::size_t taps) noexcept {
    if (!impl_->prepared || !paths || taps == 0 || taps > 32768) return false;
    try {
        std::array<std::vector<float>, 4> next;
        double bound = 0.0;
        for (std::size_t p = 0; p < 4; ++p) {
            next[p].assign(taps + static_cast<std::size_t>(impl_->dryDelay), 0.0f);
            for (std::size_t i = 0; i < taps; ++i) {
                const float value = paths[p] ? paths[p][i] : 0.0f;
                if (!std::isfinite(value)) return false;
                next[p][i + static_cast<std::size_t>(impl_->dryDelay)] = value;
            }
        }
        // Bound the complete per-ear matrix, not each filter independently.
        for (std::size_t ear = 0; ear < 2; ++ear) {
            double power = 0.0;
            for (std::size_t source = 0; source < 2; ++source)
                for (float value : next[source * 2 + ear]) power += static_cast<double>(value) * value;
            bound = std::max(bound, std::sqrt(power));
        }
        const float gain = static_cast<float>(1.0 / std::max(1.0, bound));
        for (auto& path : next) for (float& value : path) value *= gain;
        const float* matrix[4] = {next[0].data(), next[1].data(), next[2].data(), next[3].data()};
        if (!impl_->convolution.loadMatrix(matrix, next[0].size())) return false;
        impl_->customIr = std::move(next); impl_->customIrActive = true;
        impl_->normalizationGain = gain;
        return true;
    } catch (...) { return false; }
}
void ImmersiveAudioEngine::clearCustomImpulseResponse() noexcept {
    impl_->customIrActive = false;
    for (auto& path : impl_->customIr) path.clear();
    impl_->reload();
}
void ImmersiveAudioEngine::setBassGain(float gain) noexcept { impl_->bassGain.store(std::clamp(finite(gain), 0.0f, 2.0f)); }
void ImmersiveAudioEngine::setBassWidth(float width) noexcept { impl_->bassWidth.store(std::clamp(finite(width), 0.0f, 2.0f)); }
void ImmersiveAudioEngine::setHighBandWidth(float width) noexcept { impl_->highWidth.store(std::clamp(finite(width), 0.0f, 2.0f)); }
void ImmersiveAudioEngine::setStereoWidth(float width) noexcept {
    impl_->controls.width = unit(width);
    setHighBandWidth(2.0f * impl_->controls.width); // normalized 0.5 is unity
}
void ImmersiveAudioEngine::setHeadOrientation(float yaw, float pitch, float roll) noexcept {
    impl_->space.setListenerOrientation({finite(yaw), finite(pitch), finite(roll)}); impl_->reload();
}
void ImmersiveAudioEngine::setListenerOrientation(float yaw, float pitch, float roll) noexcept { setHeadOrientation(yaw, pitch, roll); }
int ImmersiveAudioEngine::latencySamples() const noexcept {
    return impl_->prepared ? static_cast<int>(Impl::kBlock) + impl_->dryDelay + dsp::TruePeakSafety::kLookahead : 0;
}
void ImmersiveAudioEngine::setRoomSimulationPreset(RoomSimulationPreset preset) noexcept {
    impl_->room = preset;
    switch (preset) {
        case RoomSimulationPreset::Off: break;
        case RoomSimulationPreset::SmallRoom: impl_->space = spatial::SpaceProfile::createBathroom(); break;
        case RoomSimulationPreset::Studio: impl_->space = spatial::SpaceProfile::createLivingRoom(); break;
        case RoomSimulationPreset::ConcertHall: impl_->space = spatial::SpaceProfile::createConcertHall(); break;
        case RoomSimulationPreset::Cathedral: impl_->space = spatial::SpaceProfile::createLargeHall(); break;
        case RoomSimulationPreset::Subway: impl_->space = spatial::SpaceProfile::createLongSubwayTunnel(); break;
    }
    if (impl_->orbitEnabled) impl_->reloadOrbitPosition();
    else impl_->reload();
}
void ImmersiveAudioEngine::setRoomMix(float mix) noexcept { impl_->roomMix = unit(mix); impl_->reload(); }
void ImmersiveAudioEngine::setReflectionAmount(float amount) noexcept { impl_->reflections = unit(amount); impl_->reload(); }
void ImmersiveAudioEngine::setReverbTimeSeconds(float seconds) noexcept {
    impl_->reverbTime = std::clamp(finite(seconds), 0.2f, 8.0f); impl_->reload();
}
void ImmersiveAudioEngine::setRoomSize(float size) noexcept {
    impl_->controls.roomSize = unit(size); impl_->space.setScale(0.5f + 1.5f * impl_->controls.roomSize); impl_->reload();
}
void ImmersiveAudioEngine::setDampening(float dampening) noexcept {
    impl_->controls.dampening = unit(dampening);
    spatial::AcousticMaterial mat;
    mat.absorption.fill(0.05f + 0.70f * impl_->controls.dampening);
    for (int s = 0; s < 6; ++s) impl_->space.setBoundary(static_cast<spatial::RoomSurface>(s), {static_cast<spatial::RoomSurface>(s), mat, 0.0f});
    impl_->reload();
}
SpaceDesignControls ImmersiveAudioEngine::spaceDesignControls() const noexcept { return impl_->controls; }
bool ImmersiveAudioEngine::isPrepared() const noexcept { return impl_->prepared; }
int ImmersiveAudioEngine::maxFrames() const noexcept { return impl_->maximum; }
ImmersiveProcessResult ImmersiveAudioEngine::lastProcessResult() const noexcept { return impl_->result.load(); }
int ImmersiveAudioEngine::lastEffectState() const noexcept { return impl_->prepared ? 0 : -1; }

bool ImmersiveAudioEngine::process(float* pcm, int frames) noexcept {
    if (!impl_->prepared) { impl_->result.store(ImmersiveProcessResult::NotPrepared); return false; }
    if (!impl_->enabled.load(std::memory_order_relaxed)) { impl_->result.store(ImmersiveProcessResult::Disabled); return false; }
    if (!pcm || frames <= 0 || frames > impl_->maximum) { impl_->result.store(ImmersiveProcessResult::InvalidInput); return false; }
    const rt::ScopedDenormalDisable denormals;
    const float bass = impl_->bassGain.load(std::memory_order_relaxed);
    const float width = impl_->bassWidth.load(std::memory_order_relaxed);
    const float high = impl_->highWidth.load(std::memory_order_relaxed);
    for (int n = 0; n < frames; ++n) {
        float l = sample(pcm[2 * n]), r = sample(pcm[2 * n + 1]);
        const auto bands = impl_->frontend.split(l, r, bass, width, high);
        const std::size_t pos = impl_->fill;
        impl_->input[0][pos] = bands.highL; impl_->input[1][pos] = bands.highR;
        impl_->bassInput[0][pos] = bands.lowL; impl_->bassInput[1][pos] = bands.lowR;
        l = impl_->output[0][pos]; r = impl_->output[1][pos];
        impl_->safety.process(l, r);
        pcm[2 * n] = l; pcm[2 * n + 1] = r;
        if (++impl_->fill == Impl::kBlock) { impl_->render(); impl_->fill = 0; }
    }
    impl_->result.store(ImmersiveProcessResult::Processed);
    return true;
}

void ImmersiveAudioEngine::setSpacePreset(spatial::SpaceProfile::Preset preset) noexcept {
    impl_->space = spatial::SpaceProfile::createPreset(preset);
    impl_->room = RoomSimulationPreset::Studio;
    impl_->irLength = preset == spatial::SpaceProfile::Preset::ClosedCar ? 8192 : 32768;
    if (impl_->orbitEnabled) impl_->reloadOrbitPosition();
    else impl_->reload();
}
void ImmersiveAudioEngine::setSpaceProfile(const spatial::SpaceProfile& profile) noexcept {
    impl_->space = profile;
    if (impl_->orbitEnabled) impl_->reloadOrbitPosition();
    else impl_->reload();
}
void ImmersiveAudioEngine::setRoomDimensions(float x, float y, float z) noexcept { impl_->space.setDimensions({finite(x), finite(y), finite(z)}); impl_->reload(); }
void ImmersiveAudioEngine::setBoundaryMaterial(spatial::RoomSurface surface, const spatial::AcousticMaterial& material) noexcept {
    const int s = static_cast<int>(surface);
    if (s < 0 || s >= 6) return;
    auto safe = material;
    for (float& alpha : safe.absorption) alpha = unit(alpha);
    safe.scattering = unit(safe.scattering);
    impl_->space.setBoundary(surface, {surface, safe, 0.0f}); impl_->reload();
}
void ImmersiveAudioEngine::setSourcePosition(float x, float y, float z) noexcept {
    const auto position = spatial::Coord3D{finite(x), finite(y), finite(z)}.toEngineCoords();
    impl_->space.setSourcePosition(position);
    if (!impl_->orbitEnabled) impl_->reload();
}
void ImmersiveAudioEngine::setListenerPosition(float x, float y, float z) noexcept {
    impl_->space.setListenerPosition(spatial::Coord3D{finite(x), finite(y), finite(z)}.toEngineCoords()); impl_->reload();
}
void ImmersiveAudioEngine::setSourceTrajectory(const spatial::SourceTrajectory& trajectory) noexcept { impl_->space.setTrajectory(trajectory); }
void ImmersiveAudioEngine::setTrajectoryPosition(float seconds) noexcept {
    impl_->space.setSourcePosition(impl_->space.trajectory().positionAt(finite(seconds))); impl_->reload();
}
void ImmersiveAudioEngine::setIrLength(std::size_t taps) noexcept { impl_->irLength = std::clamp<std::size_t>(taps, 512, 32768); impl_->reload(); }
void ImmersiveAudioEngine::setReflectionDensity(float density) noexcept { impl_->density = unit(density); impl_->reload(); }
void ImmersiveAudioEngine::setOrbitEnabled(bool enabled) noexcept {
    if (enabled == impl_->orbitEnabled) return;
    if (enabled) {
        const auto listener = impl_->space.listenerPosition();
        const auto source = impl_->space.sourcePosition();
        const auto offset = source - listener;
        const float radius = std::max(offset.length(), 0.1f);
        impl_->preOrbitSourcePosition = source;
        impl_->orbitBackupValid = true;
        impl_->orbitRadiusMetres = radius;
        impl_->orbitAzimuthDeg = spatial::wrapAzimuth(std::atan2(offset.y, offset.x) * 57.29577951308232f);
        impl_->orbitElevationDeg = spatial::clampElevation(std::asin(std::clamp(offset.z / radius, -1.0f, 1.0f)) * 57.29577951308232f);
        // Audio-thread motion/filter histories are never mutated here.
        impl_->orbitEnabled = true;
        impl_->reloadOrbitPosition();
    } else {
        impl_->orbitEnabled = false;
        if (impl_->orbitBackupValid) impl_->space.setSourcePosition(impl_->preOrbitSourcePosition);
        impl_->reload();
    }
}
void ImmersiveAudioEngine::setOrbitAzimuth(float azimuthDeg) noexcept {
    if (!std::isfinite(azimuthDeg)) return;
    impl_->orbitAzimuthDeg = spatial::wrapAzimuth(azimuthDeg);
    if (impl_->orbitEnabled) impl_->reloadOrbitPosition();
}
void ImmersiveAudioEngine::setOrbitElevation(float elevationDeg) noexcept {
    if (!std::isfinite(elevationDeg)) return;
    impl_->orbitElevationDeg = spatial::clampElevation(elevationDeg);
    if (impl_->orbitEnabled) impl_->reloadOrbitPosition();
}
void ImmersiveAudioEngine::setOrbitRadius(float radiusMetres) noexcept {
    if (!std::isfinite(radiusMetres)) return;
    impl_->orbitRadiusMetres = std::clamp(radiusMetres, 0.1f, 1000.0f);
    if (impl_->orbitEnabled) impl_->reloadOrbitPosition();
}
void ImmersiveAudioEngine::setOrbitPosition(float azimuthDeg, float elevationDeg, float radiusMetres) noexcept {
    if (!std::isfinite(azimuthDeg) || !std::isfinite(elevationDeg) || !std::isfinite(radiusMetres)) return;
    if (!impl_->orbitEnabled) {
        impl_->preOrbitSourcePosition = impl_->space.sourcePosition();
        impl_->orbitBackupValid = true;
    }
    impl_->orbitAzimuthDeg = spatial::wrapAzimuth(azimuthDeg);
    impl_->orbitElevationDeg = spatial::clampElevation(elevationDeg);
    impl_->orbitRadiusMetres = std::clamp(radiusMetres, 0.1f, 1000.0f);
    impl_->orbitEnabled = true;
    impl_->reloadOrbitPosition();
}
void ImmersiveAudioEngine::advanceOrbit(float deltaAzimuthDeg) noexcept {
    if (!impl_->orbitEnabled || !std::isfinite(deltaAzimuthDeg)) return;
    impl_->orbitAzimuthDeg = spatial::wrapAzimuth(impl_->orbitAzimuthDeg + deltaAzimuthDeg);
    impl_->reloadOrbitPosition();
}
bool ImmersiveAudioEngine::orbitEnabled() const noexcept { return impl_->orbitEnabled; }
float ImmersiveAudioEngine::orbitAzimuth() const noexcept {
    return spatial::wrapAzimuth(impl_->orbitAzimuthDeg + (impl_->orbitEnabled.load() ?
        impl_->orbitPhaseRad.load(std::memory_order_relaxed) / rt::kDegToRad : 0.0f));
}
float ImmersiveAudioEngine::orbitElevation() const noexcept { return impl_->orbitElevationDeg; }
float ImmersiveAudioEngine::orbitRadius() const noexcept { return impl_->orbitRadiusMetres; }

const spatial::SpaceProfile& ImmersiveAudioEngine::activeSpaceProfile() const noexcept { return impl_->space; }
const spatial::StereoBrir& ImmersiveAudioEngine::activeBrir() const noexcept { return impl_->brir[0]; }
const std::array<spatial::StereoBrir, 2>& ImmersiveAudioEngine::activeTransferMatrix() const noexcept { return impl_->brir; }
float ImmersiveAudioEngine::safetyGain() const noexcept { return impl_->safety.gain(); }
} // namespace frostsoulx

namespace frostsoulx {
float ImmersiveAudioEngine::matrixNormalizationGain() const noexcept { return impl_->normalizationGain; }
float ImmersiveAudioEngine::safetyPeak() const noexcept { return impl_->safety.detectedPeak(); }
}
