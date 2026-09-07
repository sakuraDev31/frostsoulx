#include "frostsoulx/StereoSurroundProcessor.h"

#include <algorithm>
#include <cmath>
#include <limits>

namespace frostsoulx {
namespace {
constexpr float kPi = 3.14159265358979323846f;
constexpr float kDefaultMaxContribution = 0.22f;
constexpr float kMinCutoffHz = 20.0f;
constexpr float kMaxCutoffHz = 2000.0f;
constexpr float kMinDecorInputCoefficient = 0.0f;
constexpr float kMaxDecorInputCoefficient = 1.0f;
constexpr float kMinRearContribution = 0.0f;
constexpr float kMaxRearContribution = 0.5f;
constexpr std::size_t kDefaultDelayA = 37;
constexpr std::size_t kDefaultDelayB = 59;

float clampFinite(float value, float low, float high, float fallback) noexcept {
    if (!std::isfinite(value)) return fallback;
    return std::clamp(value, low, high);
}

std::size_t clampDelay(std::size_t value) noexcept {
    return std::clamp<std::size_t>(value, 1U, 255U);
}
}

void StereoSurroundProcessor::prepare(double sampleRate, int channels, int maxBlockSize) noexcept {
    sampleRate_ = std::isfinite(sampleRate) && sampleRate >= 8000.0 ? sampleRate : 48000.0;
    channels_ = channels;
    maxBlockSize_ = std::max(maxBlockSize, 0);
    prepared_ = channels_ == 2;
    parameters_ = Parameters{};
    parameters_.delayASamples = clampDelay(kDefaultDelayA);
    parameters_.delayBSamples = clampDelay(kDefaultDelayB);
    reset();
}

void StereoSurroundProcessor::reset() noexcept {
    delayLineA_.fill(0.0f);
    delayLineB_.fill(0.0f);
    writeIndex_ = 0;
    sideLowpass_ = 0.0f;
    decorrelatedA_ = 0.0f;
    decorrelatedB_ = 0.0f;
}

void StereoSurroundProcessor::setEnabled(bool enabled) noexcept {
    enabled_ = enabled;
}

void StereoSurroundProcessor::setIntensity(float intensity) noexcept {
    intensity_ = clamp01(intensity);
}

void StereoSurroundProcessor::setParameters(const Parameters& parameters) noexcept {
    parameters_.lowFrequencyCutoffHz =
        clampFinite(parameters.lowFrequencyCutoffHz, kMinCutoffHz, kMaxCutoffHz, Parameters{}.lowFrequencyCutoffHz);
    parameters_.sideExtractionGain =
        clampFinite(parameters.sideExtractionGain, 0.0f, 1.0f, Parameters{}.sideExtractionGain);
    parameters_.delayASamples = clampDelay(parameters.delayASamples);
    parameters_.delayBSamples = clampDelay(parameters.delayBSamples);
    parameters_.decorrelationAInputCoefficient =
        clampFinite(parameters.decorrelationAInputCoefficient, kMinDecorInputCoefficient, kMaxDecorInputCoefficient, Parameters{}.decorrelationAInputCoefficient);
    parameters_.decorrelationBInputCoefficient =
        clampFinite(parameters.decorrelationBInputCoefficient, kMinDecorInputCoefficient, kMaxDecorInputCoefficient, Parameters{}.decorrelationBInputCoefficient);
    parameters_.sideHighMixBase = clamp01(parameters.sideHighMixBase);
    parameters_.sideHighMixIntensitySpan = clamp01(parameters.sideHighMixIntensitySpan);
    parameters_.ambienceDecorrelatedAWeight = clamp01(parameters.ambienceDecorrelatedAWeight);
    parameters_.rearAmbienceWeight = clamp01(parameters.rearAmbienceWeight);
    parameters_.maxRearContribution =
        clampFinite(parameters.maxRearContribution, kMinRearContribution, kMaxRearContribution, kDefaultMaxContribution);
}

float StereoSurroundProcessor::clamp01(float value) noexcept {
    if (!std::isfinite(value)) return 0.0f;
    return std::clamp(value, 0.0f, 1.0f);
}

float StereoSurroundProcessor::finiteOrZero(float value) noexcept {
    return std::isfinite(value) ? value : 0.0f;
}

void StereoSurroundProcessor::process(float* interleavedStereo, int frames) noexcept {
    // This check is intentionally first: disabled mode is a true bit-for-bit
    // bypass and must not touch buffers or state.
    if (!enabled_ || !prepared_ || interleavedStereo == nullptr || frames <= 0) return;
    if (intensity_ <= 0.0f) return;

    const float amount = clamp01(intensity_);
    const float lowAlpha = static_cast<float>(1.0 - std::exp(-2.0 * kPi * parameters_.lowFrequencyCutoffHz / sampleRate_));
    const float sideHighMix =
        clamp01(parameters_.sideHighMixBase + parameters_.sideHighMixIntensitySpan * amount);
    const float rearMix = parameters_.maxRearContribution * amount;

    for (int frame = 0; frame < frames; ++frame) {
        const std::size_t offset = static_cast<std::size_t>(frame) * 2U;
        const float left = finiteOrZero(interleavedStereo[offset]);
        const float right = finiteOrZero(interleavedStereo[offset + 1U]);

        // Center content is preserved because the surround generator consumes
        // only side information. When L == R, side is exactly zero and the
        // direct center image is not widened, rotated, or otherwise processed.
        const float side = parameters_.sideExtractionGain * (left - right);

        // Keep the bass/low-mid portion of the side signal direct. Only the
        // high-passed side contributes strongly to the virtual rear field.
        sideLowpass_ += lowAlpha * (side - sideLowpass_);
        const float sideHigh = side - sideLowpass_;

        const std::size_t delayedAIndex =
            (writeIndex_ + 256U - parameters_.delayASamples) % 256U;
        const std::size_t delayedBIndex =
            (writeIndex_ + 256U - parameters_.delayBSamples) % 256U;
        const float delayedA = delayLineA_[delayedAIndex];
        const float delayedB = delayLineB_[delayedBIndex];
        // Both decorrelation paths receive the same side-derived signal. Their
        // different delays and bounded one-pole coefficients create the
        // distinction; there is no polarity inversion trick.
        delayLineA_[writeIndex_] = sideHigh;
        delayLineB_[writeIndex_] = sideHigh;
        writeIndex_ = (writeIndex_ + 1U) % 256U;

        // Two short, bounded one-pole decorrelators provide ambience without
        // polarity tricks, extreme Haas delay, or runaway feedback.
        decorrelatedA_ =
            (1.0f - parameters_.decorrelationAInputCoefficient) * decorrelatedA_ +
            parameters_.decorrelationAInputCoefficient * delayedA;
        decorrelatedB_ =
            (1.0f - parameters_.decorrelationBInputCoefficient) * decorrelatedB_ +
            parameters_.decorrelationBInputCoefficient * delayedB;
        const float ambience =
            sideHigh * (1.0f - sideHighMix) +
            (parameters_.ambienceDecorrelatedAWeight * decorrelatedA_ +
             (1.0f - parameters_.ambienceDecorrelatedAWeight) * decorrelatedB_) * sideHighMix;

        // Fold virtual rear components back into stereo while preserving the
        // original direct image. Both outputs use same-polarity, side-derived
        // ambience; their decorrelated states remain distinct, so the direct
        // stereo field is retained without a polarity/phase gimmick.
        const float rearLeft = parameters_.rearAmbienceWeight * ambience +
                               (1.0f - parameters_.rearAmbienceWeight) * decorrelatedA_;
        const float rearRight = parameters_.rearAmbienceWeight * ambience +
                                (1.0f - parameters_.rearAmbienceWeight) * decorrelatedB_;
        // Spatial-only reconstruction: preserve the direct samples and add
        // only the generated surround contribution. Master headroom and
        // limiting belong to a later stage outside this processor.
        interleavedStereo[offset] = left + rearLeft * rearMix;
        interleavedStereo[offset + 1U] = right + rearRight * rearMix;
    }
}

}  // namespace frostsoulx
