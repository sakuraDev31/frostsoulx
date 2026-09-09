#include "frostsoulx/ImmersiveAudioEngine.h"

#include <algorithm>
#include <cmath>
#include <vector>

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
#include <phonon.h>
#endif

namespace frostsoulx {

struct ImmersiveAudioEngine::Impl {
    int sampleRate = 0;
    int maxFrames = 0;
    bool prepared = false;
    bool enabled = false;
    float spatialBlend = 1.0f;

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
    if (iplContextCreate(&contextSettings, &impl_->context) != IPL_STATUS_SUCCESS) {
        impl_->release();
        return false;
    }

    IPLAudioSettings audioSettings{};
    audioSettings.samplingRate = sampleRate;
    audioSettings.frameSize = maxFrames;

    IPLHRTFSettings hrtfSettings{};
    hrtfSettings.type = IPL_HRTFTYPE_DEFAULT;
    hrtfSettings.volume = 1.0f;
    if (iplHRTFCreate(impl_->context, &audioSettings, &hrtfSettings, &impl_->hrtf) != IPL_STATUS_SUCCESS) {
        impl_->release();
        return false;
    }

    IPLBinauralEffectSettings effectSettings{};
    effectSettings.hrtf = impl_->hrtf;
    if (iplBinauralEffectCreate(impl_->context, &audioSettings, &effectSettings, &impl_->effect) != IPL_STATUS_SUCCESS) {
        impl_->release();
        return false;
    }
#else
    impl_->release();
    return false;
#endif

    impl_->prepared = true;
    return true;
}

void ImmersiveAudioEngine::reset() noexcept {
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    if (impl_->effect != nullptr) {
        iplBinauralEffectReset(impl_->effect);
    }
#endif
}

void ImmersiveAudioEngine::setEnabled(bool enabled) noexcept {
    impl_->enabled = enabled;
}

void ImmersiveAudioEngine::setSpatialBlend(float blend) noexcept {
    impl_->spatialBlend = std::isfinite(blend) ? std::clamp(blend, 0.0f, 1.0f) : 0.0f;
}

bool ImmersiveAudioEngine::isPrepared() const noexcept {
    return impl_->prepared;
}

bool ImmersiveAudioEngine::process(float* interleavedStereo, int frames) noexcept {
    if (!impl_->prepared || !impl_->enabled || interleavedStereo == nullptr || frames <= 0 || frames > impl_->maxFrames) {
        return false;
    }

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    for (int frame = 0; frame < frames; ++frame) {
        impl_->inputLeft[static_cast<std::size_t>(frame)] = interleavedStereo[frame * 2];
        impl_->inputRight[static_cast<std::size_t>(frame)] = interleavedStereo[frame * 2 + 1];
    }

    IPLAudioBuffer input{};
    input.numChannels = 2;
    input.numSamples = frames;
    input.data = impl_->inputChannels;

    IPLAudioBuffer output{};
    output.numChannels = 2;
    output.numSamples = frames;
    output.data = impl_->outputChannels;

    IPLBinauralEffectParams params{};
    params.direction = IPLVector3{0.0f, 0.0f, 1.0f};
    params.interpolation = IPL_HRTFINTERPOLATION_BILINEAR;
    params.spatialBlend = impl_->spatialBlend;
    params.hrtf = impl_->hrtf;
    params.peakDelays = nullptr;

    (void)iplBinauralEffectApply(impl_->effect, &params, &input, &output);
    for (int frame = 0; frame < frames; ++frame) {
        interleavedStereo[frame * 2] = impl_->outputLeft[static_cast<std::size_t>(frame)];
        interleavedStereo[frame * 2 + 1] = impl_->outputRight[static_cast<std::size_t>(frame)];
    }
    return true;
#endif
    return false;
}

} // namespace frostsoulx
