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
    spatial::SpaceProfile space = spatial::SpaceProfile::createLivingRoom();
    spatial::HrtfDatabase hrtf;
    spatial::RirGenerator generator;
    std::array<spatial::StereoBrir, 2> brir;
    dsp::MimoConvolver convolution;
    dsp::StereoFrontend frontend;
    dsp::TruePeakSafety safety;
    std::array<std::array<float, kBlock>, 2> input{}, wet{}, output{};
    std::vector<float> dryRing;
    std::size_t dryWrite = 0, fill = 0;
    float blendCurrent = 1.0f;
    float normalizationGain = 1.0f;

    // Control-thread spatializer: geometry -> listener-local directions ->
    // ILD/ITD/pinna HRTF for direct + reflected arrivals. Collapse the entire
    // linear spatial model into four filters, rather than running a second
    // competing HOA renderer or doing HRTF convolution twice on the audio thread.
    bool reload() noexcept {
        if (!prepared) return true;
        try {
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
                // Convex, delay-matched blend. Equal power is WRONG for
                // correlated dry/wet and gave a +3 dB boost at half intensity.
                output[c][n] = dry + b * (wet[c][n] - dry);
            }
            dryWrite = (dryWrite + 1) % static_cast<std::size_t>(dryDelay);
        }
        blendCurrent = target;
    }
};

ImmersiveAudioEngine::ImmersiveAudioEngine() : impl_(std::make_unique<Impl>()) {}
ImmersiveAudioEngine::~ImmersiveAudioEngine() = default;

bool ImmersiveAudioEngine::prepare(int sampleRate, int maxFrames) noexcept {
    if (sampleRate < 8000 || sampleRate > 384000 || maxFrames <= 0) return false;
    impl_->prepared = false;
    try {
        impl_->rate = sampleRate; impl_->maximum = maxFrames;
        std::size_t hrirTaps = 128;
        while (hrirTaps < static_cast<std::size_t>(sampleRate / 500)) hrirTaps *= 2;
        if (!impl_->hrtf.buildParametric(sampleRate, hrirTaps)) return false;
        dsp::NonUniformConvolver::Config cfg;
        cfg.headBlock = Impl::kBlock; cfg.maxTaps = 32768;
        cfg.maxTiers = 4; cfg.growth = 4; cfg.crossfadeBlocks = 8;
        if (!impl_->convolution.prepare(2, 2, cfg)) return false;
        impl_->dryDelay = static_cast<int>(std::ceil(rt::kHeadRadius / rt::kSpeedOfSound * static_cast<float>(sampleRate))) + 5;
        impl_->dryRing.assign(static_cast<std::size_t>(2 * impl_->dryDelay), 0.0f);
        impl_->frontend.prepare(sampleRate); impl_->safety.prepare(sampleRate);
        impl_->prepared = true;
        if (!impl_->reload()) { impl_->prepared = false; return false; }
        reset();
        return true;
    } catch (...) { impl_->prepared = false; return false; }
}
void ImmersiveAudioEngine::reset() noexcept {
    if (impl_->prepared) {
        impl_->convolution.reset(); impl_->frontend.reset(); impl_->safety.reset();
        impl_->input = {}; impl_->wet = {}; impl_->output = {};
        std::fill(impl_->dryRing.begin(), impl_->dryRing.end(), 0.0f);
        impl_->dryWrite = impl_->fill = 0;
        impl_->blendCurrent = impl_->blend.load();
    }
    impl_->result.store(impl_->prepared ? ImmersiveProcessResult::Disabled : ImmersiveProcessResult::NotPrepared);
}
void ImmersiveAudioEngine::setEnabled(bool enabled) noexcept { impl_->enabled.store(enabled); }
void ImmersiveAudioEngine::setSpatialBlend(float blend) noexcept { impl_->blend.store(unit(blend)); }
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
    impl_->reload();
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
        impl_->frontend.process(l, r, bass, width, high);
        const std::size_t pos = impl_->fill;
        impl_->input[0][pos] = l; impl_->input[1][pos] = r;
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
    impl_->reload();
}
void ImmersiveAudioEngine::setSpaceProfile(const spatial::SpaceProfile& profile) noexcept { impl_->space = profile; impl_->reload(); }
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
    impl_->space.setSourcePosition(spatial::Coord3D{finite(x), finite(y), finite(z)}.toEngineCoords()); impl_->reload();
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
const spatial::SpaceProfile& ImmersiveAudioEngine::activeSpaceProfile() const noexcept { return impl_->space; }
const spatial::StereoBrir& ImmersiveAudioEngine::activeBrir() const noexcept { return impl_->brir[0]; }
const std::array<spatial::StereoBrir, 2>& ImmersiveAudioEngine::activeTransferMatrix() const noexcept { return impl_->brir; }
float ImmersiveAudioEngine::safetyGain() const noexcept { return impl_->safety.gain(); }
} // namespace frostsoulx

namespace frostsoulx {
float ImmersiveAudioEngine::matrixNormalizationGain() const noexcept { return impl_->normalizationGain; }
float ImmersiveAudioEngine::safetyPeak() const noexcept { return impl_->safety.detectedPeak(); }
}
