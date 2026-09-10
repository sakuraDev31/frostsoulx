#include "frostsoulx/ImmersiveAudioEngine.h"

#include <algorithm>
#include <cmath>
#include <vector>

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
#include <phonon.h>
#endif

namespace frostsoulx {

struct ImmersiveAudioEngine::Impl {
    // Keep the Steam Audio block below 10 ms at 48 kHz to reduce audible
    // latency and the amount of audio affected by a transient glitch.
    static constexpr int kSteamAudioFrameSize = 384;
    int sampleRate = 0;
    int maxFrames = 0;
    bool prepared = false;
    bool enabled = false;
    float spatialBlend = 1.0f;
    ImmersiveProcessResult lastResult = ImmersiveProcessResult::NotPrepared;
    int lastState = -1;

    std::vector<float> inputLeft;
    std::vector<float> inputRight;
    std::vector<float> outputLeft;
    std::vector<float> outputRight;
    float* inputChannels[2] = {nullptr, nullptr};
    float* outputChannels[2] = {nullptr, nullptr};

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    IPLContext context = nullptr;
    IPLHRTF hrtf = nullptr;
    IPLBinauralEffect effect = nullptr;
#endif

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
        inputLeft.clear();
        inputRight.clear();
        outputLeft.clear();
        outputRight.clear();
        inputChannels[0] = nullptr;
        inputChannels[1] = nullptr;
        outputChannels[0] = nullptr;
        outputChannels[1] = nullptr;
        lastResult = ImmersiveProcessResult::NotPrepared;
        lastState = -1;
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
    impl_->inputLeft.resize(static_cast<std::size_t>(maxFrames));
    impl_->inputRight.resize(static_cast<std::size_t>(maxFrames));
    impl_->outputLeft.resize(static_cast<std::size_t>(maxFrames));
    impl_->outputRight.resize(static_cast<std::size_t>(maxFrames));
    impl_->inputChannels[0] = impl_->inputLeft.data();
    impl_->inputChannels[1] = impl_->inputRight.data();
    impl_->outputChannels[0] = impl_->outputLeft.data();
    impl_->outputChannels[1] = impl_->outputRight.data();

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    IPLContextSettings contextSettings{};
    contextSettings.version = STEAMAUDIO_VERSION;
    if (iplContextCreate(&contextSettings, &impl_->context) != IPL_STATUS_SUCCESS || impl_->context == nullptr) {
        impl_->release();
        return false;
    }

    IPLAudioSettings audioSettings{};
    audioSettings.samplingRate = sampleRate;
    // Steam Audio effects are configured for a fixed frame size. Media3 may
    // deliver smaller or larger buffers, so process() pads/splits them.
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
    impl_->lastResult = ImmersiveProcessResult::Disabled;
    return true;
}

void ImmersiveAudioEngine::reset() noexcept {
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    if (impl_->effect != nullptr) {
        iplBinauralEffectReset(impl_->effect);
    }
#endif
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

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
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
            impl_->inputLeft[static_cast<std::size_t>(frame)] = interleavedStereo[(frameOffset + frame) * 2];
            impl_->inputRight[static_cast<std::size_t>(frame)] = interleavedStereo[(frameOffset + frame) * 2 + 1];
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
            impl_->lastResult = ImmersiveProcessResult::SteamAudioUnavailable;
            return false;
        }

        bool inputHasEnergy = false;
        bool outputHasEnergy = false;
        for (int frame = 0; frame < activeFrames; ++frame) {
            const float inputLeft = impl_->inputLeft[static_cast<std::size_t>(frame)];
            const float inputRight = impl_->inputRight[static_cast<std::size_t>(frame)];
            const float outputLeft = impl_->outputLeft[static_cast<std::size_t>(frame)];
            const float outputRight = impl_->outputRight[static_cast<std::size_t>(frame)];
            if (!std::isfinite(outputLeft) || !std::isfinite(outputRight)) {
                impl_->lastResult = ImmersiveProcessResult::InvalidOutput;
                return false;
            }
            inputHasEnergy = inputHasEnergy || std::fabs(inputLeft) > 1.0e-8f || std::fabs(inputRight) > 1.0e-8f;
            outputHasEnergy = outputHasEnergy || std::fabs(outputLeft) > 1.0e-8f || std::fabs(outputRight) > 1.0e-8f;
        }
        if (inputHasEnergy && !outputHasEnergy) {
            impl_->lastResult = ImmersiveProcessResult::InvalidOutput;
            return false;
        }
        anyInputEnergy = anyInputEnergy || inputHasEnergy;
        anyOutputEnergy = anyOutputEnergy || outputHasEnergy;
        // Binaural channel summing can add peak energy. Use fixed headroom
        // rather than per-block normalization: a changing block gain causes
        // audible pumping and can sound like crackling on sustained bass.
        // This stage exists exclusively on the enabled path, so OFF remains
        // a byte-for-byte bypass through Media3.
        constexpr float kSteamAudioOutputGain = 0.70710678f; // -3 dB
        for (int frame = 0; frame < activeFrames; ++frame) {
            interleavedStereo[(frameOffset + frame) * 2] =
                impl_->outputLeft[static_cast<std::size_t>(frame)] * kSteamAudioOutputGain;
            interleavedStereo[(frameOffset + frame) * 2 + 1] =
                impl_->outputRight[static_cast<std::size_t>(frame)] * kSteamAudioOutputGain;
        }
        frameOffset += activeFrames;
    }
    if (anyInputEnergy && !anyOutputEnergy) {
        impl_->lastResult = ImmersiveProcessResult::InvalidOutput;
        return false;
    }
    impl_->lastResult = ImmersiveProcessResult::SteamAudioProcessed;
    return true;
#else
    impl_->lastResult = ImmersiveProcessResult::SteamAudioUnavailable;
    return false;
#endif
}

} // namespace frostsoulx
