#include "frostsoulx/dsp/sound_field.h"

#include <algorithm>
#include <cmath>

namespace frostsoulx::dsp {
namespace {
constexpr float kMinIntensity = 0.0f;
constexpr float kMaxIntensity = 1.0f;
constexpr float kMinWidth = 0.0f;
constexpr float kMaxWidth = 2.0f;
constexpr float kMinCrossfeed = 0.0f;
constexpr float kMaxCrossfeed = 0.35f;
constexpr float kMinProtection = 0.0f;
constexpr float kMaxProtection = 1.0f;
constexpr float kMinSurround = 0.0f;
constexpr float kMaxSurround = 1.0f;
constexpr float kMinReverbMix = 0.0f;
constexpr float kMaxReverbMix = 0.35f;
constexpr float kMinRoomSize = 0.0f;
constexpr float kMaxRoomSize = 1.0f;
constexpr float kMinReverbDecay = 0.0f;
constexpr float kMaxReverbDecay = 0.85f;
constexpr float kMinGainDb = -24.0f;
constexpr float kMaxGainDb = 6.0f;
constexpr float kMinCeilingDb = -12.0f;
constexpr float kMaxCeilingDb = -0.1f;

float dbToLinear(float db) noexcept {
    return std::pow(10.0f, db / 20.0f);
}

float sanitize(float value, float fallback) noexcept {
    return std::isfinite(value) ? value : fallback;
}

}  // namespace

void SoundFieldProcessor::prepare(SoundFieldFormat format) noexcept {
    format_ = format;
    if (!std::isfinite(format_.sampleRate) || format_.sampleRate < 8000.0) {
        format_.sampleRate = 48000.0;
    }
    if (format_.channels != 2) {
        format_.channels = 2;
    }
    hrtf_.prepare({format_.sampleRate});
    configureFallbackHrtf();
    reset();
}

void SoundFieldProcessor::configureFallbackHrtf() noexcept {
    constexpr std::size_t kTaps = 32;
    constexpr std::size_t kDirections = 5;
    std::array<std::array<float, kTaps>, kDirections> left{};
    std::array<std::array<float, kTaps>, kDirections> right{};
    std::array<HrtfBinauralProcessor::DirectionalImpulseResponse, kDirections> responses{};
    constexpr std::array<float, kDirections> azimuths{-90.0f, -45.0f, 0.0f, 45.0f, 90.0f};
    for (std::size_t index = 0; index < kDirections; ++index) {
        const float pan = azimuths[index] / 90.0f;
        const float leftGain = 0.78f - 0.22f * pan;
        const float rightGain = 0.78f + 0.22f * pan;
        const std::size_t leftDelay = pan > 0.0f ? 4U : 1U;
        const std::size_t rightDelay = pan < 0.0f ? 4U : 1U;
        left[index][leftDelay] = leftGain;
        right[index][rightDelay] = rightGain;
        responses[index] = {
            azimuths[index],
            0.0f,
            {left[index].data(), right[index].data(), kTaps},
        };
    }
    hrtf_.setDirectionalResponses(responses.data(), responses.size());
    hrtf_.setCrossfadeSamples(512U);
    hrtf_.setDirection(0.0f, 0.0f);
    hrtf_.setEnabled(false);
}

void SoundFieldProcessor::reset() noexcept {
    lowLeft_ = 0.0f;
    lowRight_ = 0.0f;
    crossfeedLeft_ = 0.0f;
    crossfeedRight_ = 0.0f;
    reverbLeft_.fill(0.0f);
    reverbRight_.fill(0.0f);
    hrtfMono_.fill(0.0f);
    hrtfStereo_.fill(0.0f);
    hrtf_.reset();
    lastHrtfAzimuth_ = 9999.0f;
    lastHrtfElevation_ = 9999.0f;
    reverbIndex_ = 0;
}

void SoundFieldProcessor::setParameters(const SoundFieldParameters& parameters) noexcept {
    parameters_ = parameters;
    parameters_.intensity = std::clamp(sanitize(parameters_.intensity, 0.5f), kMinIntensity, kMaxIntensity);
    parameters_.width = std::clamp(sanitize(parameters_.width, 1.0f), kMinWidth, kMaxWidth);
    parameters_.crossfeed = std::clamp(sanitize(parameters_.crossfeed, 0.0f), kMinCrossfeed, kMaxCrossfeed);
    parameters_.lowFrequencyProtection = std::clamp(sanitize(parameters_.lowFrequencyProtection, 0.85f), kMinProtection, kMaxProtection);
    parameters_.surround = std::clamp(sanitize(parameters_.surround, 0.0f), kMinSurround, kMaxSurround);
    parameters_.reverbMix = std::clamp(sanitize(parameters_.reverbMix, 0.0f), kMinReverbMix, kMaxReverbMix);
    parameters_.reverbRoomSize = std::clamp(sanitize(parameters_.reverbRoomSize, 0.5f), kMinRoomSize, kMaxRoomSize);
    parameters_.reverbDecay = std::clamp(sanitize(parameters_.reverbDecay, 0.45f), kMinReverbDecay, kMaxReverbDecay);
    parameters_.outputGainDb = std::clamp(sanitize(parameters_.outputGainDb, 0.0f), kMinGainDb, kMaxGainDb);
    parameters_.limiterCeilingDb = std::clamp(sanitize(parameters_.limiterCeilingDb, -1.0f), kMinCeilingDb, kMaxCeilingDb);
    parameters_.hrtfMix = std::clamp(sanitize(parameters_.hrtfMix, 0.85f), 0.0f, 1.0f);
    parameters_.hrtfAzimuth = std::clamp(sanitize(parameters_.hrtfAzimuth, 0.0f), -180.0f, 180.0f);
    parameters_.hrtfElevation = std::clamp(sanitize(parameters_.hrtfElevation, 0.0f), -90.0f, 90.0f);
}

const SoundFieldParameters& SoundFieldProcessor::parameters() const noexcept {
    return parameters_;
}

SoundFieldParameters SoundFieldProcessor::effectiveParameters() const noexcept {
    SoundFieldParameters result = parameters_;
    applyPresetDefaults(result);
    result.intensity = std::clamp(result.intensity, kMinIntensity, kMaxIntensity);
    result.width = std::clamp(result.width, kMinWidth, kMaxWidth);
    result.crossfeed = std::clamp(result.crossfeed, kMinCrossfeed, kMaxCrossfeed);
    result.lowFrequencyProtection = std::clamp(result.lowFrequencyProtection, kMinProtection, kMaxProtection);
    result.surround = std::clamp(result.surround, kMinSurround, kMaxSurround);
    result.reverbMix = std::clamp(result.reverbMix, kMinReverbMix, kMaxReverbMix);
    result.reverbRoomSize = std::clamp(result.reverbRoomSize, kMinRoomSize, kMaxRoomSize);
    result.reverbDecay = std::clamp(result.reverbDecay, kMinReverbDecay, kMaxReverbDecay);
    result.outputGainDb = std::clamp(result.outputGainDb, kMinGainDb, kMaxGainDb);
    result.limiterCeilingDb = std::clamp(result.limiterCeilingDb, kMinCeilingDb, kMaxCeilingDb);
    result.hrtfMix = std::clamp(result.hrtfMix, 0.0f, 1.0f);
    result.hrtfAzimuth = std::clamp(result.hrtfAzimuth, -180.0f, 180.0f);
    result.hrtfElevation = std::clamp(result.hrtfElevation, -90.0f, 90.0f);
    return result;
}

void SoundFieldProcessor::applyPresetDefaults(SoundFieldParameters& parameters) const noexcept {
    if (parameters.preset == SoundFieldPreset::Custom) {
        return;
    }

    switch (parameters.preset) {
    case SoundFieldPreset::Natural:
        parameters.width = 1.08f;
        parameters.crossfeed = 0.06f;
        parameters.surround = 0.08f;
        parameters.reverbMix = 0.04f;
        parameters.reverbRoomSize = 0.25f;
        parameters.reverbDecay = 0.25f;
        parameters.lowFrequencyProtection = 0.95f;
        break;
    case SoundFieldPreset::Live:
        parameters.width = 1.28f;
        parameters.crossfeed = 0.12f;
        parameters.surround = 0.25f;
        parameters.reverbMix = 0.12f;
        parameters.reverbRoomSize = 0.50f;
        parameters.reverbDecay = 0.45f;
        parameters.lowFrequencyProtection = 0.90f;
        break;
    case SoundFieldPreset::Wide:
        parameters.width = 1.55f;
        parameters.crossfeed = 0.08f;
        parameters.surround = 0.42f;
        parameters.reverbMix = 0.16f;
        parameters.reverbRoomSize = 0.65f;
        parameters.reverbDecay = 0.55f;
        parameters.lowFrequencyProtection = 0.88f;
        break;
    case SoundFieldPreset::Immersive:
        parameters.width = 1.75f;
        parameters.crossfeed = 0.16f;
        parameters.surround = 0.60f;
        parameters.reverbMix = 0.22f;
        parameters.reverbRoomSize = 0.80f;
        parameters.reverbDecay = 0.65f;
        parameters.lowFrequencyProtection = 0.84f;
        break;
    case SoundFieldPreset::Custom:
        break;
    }
}

float SoundFieldProcessor::lowPass(float input, float& state) const noexcept {
    const float cutoffHz = 180.0f;
    const float sampleRate = static_cast<float>(format_.sampleRate);
    const float alpha = 1.0f - std::exp(-2.0f * 3.14159265358979323846f * cutoffHz / sampleRate);
    state += alpha * (input - state);
    return state;
}

float SoundFieldProcessor::readDelay(const std::array<float, 4096>& buffer, std::size_t delay) const noexcept {
    const std::size_t boundedDelay = std::min<std::size_t>(delay, buffer.size() - 1U);
    const std::size_t index = (reverbIndex_ + buffer.size() - boundedDelay) % buffer.size();
    return buffer[index];
}

float SoundFieldProcessor::clampSample(float sample) const noexcept {
    const float ceiling = dbToLinear(effectiveParameters().limiterCeilingDb);
    const float magnitude = std::fabs(sample);
    if (magnitude <= ceiling) {
        return sample;
    }
    const float excess = magnitude - ceiling;
    const float softened = ceiling + std::tanh(excess / std::max(ceiling, 0.001f)) * (1.0f - ceiling);
    return std::copysign(std::min(softened, 1.0f), sample);
}

void SoundFieldProcessor::process(float* interleavedStereo, std::size_t frames) noexcept {
    if (interleavedStereo == nullptr || frames == 0 || !parameters_.enabled) {
        return;
    }

    const SoundFieldParameters effective = effectiveParameters();
    const float intensity = effective.intensity;
    const float width = 1.0f + (effective.width - 1.0f) * intensity;
    const float crossfeed = effective.crossfeed * intensity;
    const float surround = effective.surround * intensity;
    const float protection = effective.lowFrequencyProtection;
    const float reverbMix = effective.reverbMix * intensity;
    const float roomSize = effective.reverbRoomSize;
    const float reverbDecay = effective.reverbDecay;
    const float gain = dbToLinear(effective.outputGainDb);

    for (std::size_t frame = 0; frame < frames; ++frame) {
        float left = interleavedStereo[frame * 2U];
        float right = interleavedStereo[frame * 2U + 1U];

        const float lowLeft = lowPass(left, lowLeft_);
        const float lowRight = lowPass(right, lowRight_);
        const float highLeft = left - lowLeft;
        const float highRight = right - lowRight;

        const float mid = (highLeft + highRight) * 0.5f;
        const float side = (highLeft - highRight) * 0.5f * (width + 0.75f * surround);
        float processedLeft = (mid + side) + lowLeft * protection;
        float processedRight = (mid - side) + lowRight * protection;

        const float earlyDelayLeft = static_cast<float>(format_.sampleRate) * (0.010f + 0.010f * roomSize);
        const float earlyDelayRight = static_cast<float>(format_.sampleRate) * (0.013f + 0.012f * roomSize);
        const float lateDelayLeft = static_cast<float>(format_.sampleRate) * (0.030f + 0.025f * roomSize);
        const float lateDelayRight = static_cast<float>(format_.sampleRate) * (0.037f + 0.029f * roomSize);
        const std::size_t earlyL = static_cast<std::size_t>(std::clamp(earlyDelayLeft, 1.0f, 4000.0f));
        const std::size_t earlyR = static_cast<std::size_t>(std::clamp(earlyDelayRight, 1.0f, 4000.0f));
        const std::size_t lateL = static_cast<std::size_t>(std::clamp(lateDelayLeft, 1.0f, 4000.0f));
        const std::size_t lateR = static_cast<std::size_t>(std::clamp(lateDelayRight, 1.0f, 4000.0f));
        const float wetInput = (highLeft + highRight) * 0.5f;
        const float earlyLeft = readDelay(reverbLeft_, earlyL);
        const float earlyRight = readDelay(reverbRight_, earlyR);
        const float lateLeft = readDelay(reverbLeft_, lateL);
        const float lateRight = readDelay(reverbRight_, lateR);
        const float wetLeft = 0.55f * earlyLeft + 0.45f * lateLeft;
        const float wetRight = 0.55f * earlyRight + 0.45f * lateRight;
        reverbLeft_[reverbIndex_] = wetInput + 0.35f * reverbDecay * (wetRight - wetInput);
        reverbRight_[reverbIndex_] = wetInput + 0.35f * reverbDecay * (wetLeft - wetInput);
        if (reverbMix > 0.0f) {
            processedLeft += wetLeft * reverbMix;
            processedRight += wetRight * reverbMix;
        }
        reverbIndex_ = (reverbIndex_ + 1U) % reverbLeft_.size();

        const float previousLeft = crossfeedLeft_;
        const float previousRight = crossfeedRight_;
        crossfeedLeft_ = processedLeft;
        crossfeedRight_ = processedRight;
        processedLeft = processedLeft * (1.0f - crossfeed) + previousRight * crossfeed;
        processedRight = processedRight * (1.0f - crossfeed) + previousLeft * crossfeed;

        interleavedStereo[frame * 2U] = clampSample(processedLeft * gain);
        interleavedStereo[frame * 2U + 1U] = clampSample(processedRight * gain);
    }

    if (effective.hrtfEnabled) {
        hrtf_.setEnabled(true);
        if (std::fabs(effective.hrtfAzimuth - lastHrtfAzimuth_) > 0.01f ||
            std::fabs(effective.hrtfElevation - lastHrtfElevation_) > 0.01f) {
            hrtf_.setDirection(effective.hrtfAzimuth, effective.hrtfElevation);
            lastHrtfAzimuth_ = effective.hrtfAzimuth;
            lastHrtfElevation_ = effective.hrtfElevation;
        }
        const std::size_t boundedFrames = std::min(frames, hrtfMono_.size());
        for (std::size_t frame = 0; frame < boundedFrames; ++frame) {
            hrtfMono_[frame] = 0.5f * (interleavedStereo[frame * 2U] + interleavedStereo[frame * 2U + 1U]);
        }
        hrtf_.process(hrtfMono_.data(), hrtfStereo_.data(), boundedFrames);
        const float mix = effective.hrtfMix;
        const float dry = 1.0f - mix;
        for (std::size_t frame = 0; frame < boundedFrames; ++frame) {
            interleavedStereo[frame * 2U] = clampSample(interleavedStereo[frame * 2U] * dry + hrtfStereo_[frame * 2U] * mix);
            interleavedStereo[frame * 2U + 1U] = clampSample(interleavedStereo[frame * 2U + 1U] * dry + hrtfStereo_[frame * 2U + 1U] * mix);
        }
    } else {
        hrtf_.setEnabled(false);
    }
}

}  // namespace frostsoulx::dsp
