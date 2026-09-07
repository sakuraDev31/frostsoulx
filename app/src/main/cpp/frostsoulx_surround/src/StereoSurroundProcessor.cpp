#include "frostsoulx/StereoSurroundProcessor.h"

#include <algorithm>
#include <cmath>
#include <limits>

namespace frostsoulx {
namespace {
constexpr float kPi = 3.14159265358979323846f;
constexpr float kMaxContribution = 0.22f;
}

void StereoSurroundProcessor::prepare(double sampleRate, int channels, int maxBlockSize) noexcept {
    sampleRate_ = std::isfinite(sampleRate) && sampleRate >= 8000.0 ? sampleRate : 48000.0;
    channels_ = channels;
    maxBlockSize_ = std::max(maxBlockSize, 0);
    prepared_ = channels_ == 2;

    const auto boundedDelay = [](double seconds, double rate) -> std::size_t {
        const auto samples = static_cast<long long>(seconds * rate);
        return static_cast<std::size_t>(std::clamp(samples, 1LL, static_cast<long long>(kMaxDelaySamples - 1)));
    };
    delayA_ = boundedDelay(0.00077, sampleRate_);
    delayB_ = boundedDelay(0.00123, sampleRate_);
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
    const float lowAlpha = static_cast<float>(1.0 - std::exp(-2.0 * kPi * 180.0 / sampleRate_));
    const float sideHighMix = 0.35f + 0.45f * amount;
    const float rearMix = kMaxContribution * amount;

    for (int frame = 0; frame < frames; ++frame) {
        const std::size_t offset = static_cast<std::size_t>(frame) * 2U;
        const float left = finiteOrZero(interleavedStereo[offset]);
        const float right = finiteOrZero(interleavedStereo[offset + 1U]);

        // Center content is preserved because the surround generator consumes
        // only side information. When L == R, side is exactly zero and the
        // direct center image is not widened, rotated, or otherwise processed.
        const float side = 0.5f * (left - right);

        // Keep the bass/low-mid portion of the side signal direct. Only the
        // high-passed side contributes strongly to the virtual rear field.
        sideLowpass_ += lowAlpha * (side - sideLowpass_);
        const float sideHigh = side - sideLowpass_;

        const float delayedA = delayLineA_[(writeIndex_ + kMaxDelaySamples - delayA_) % kMaxDelaySamples];
        const float delayedB = delayLineB_[(writeIndex_ + kMaxDelaySamples - delayB_) % kMaxDelaySamples];
        // Both decorrelation paths receive the same side-derived signal. Their
        // different delays and bounded one-pole coefficients create the
        // distinction; there is no polarity inversion trick.
        delayLineA_[writeIndex_] = sideHigh;
        delayLineB_[writeIndex_] = sideHigh;
        writeIndex_ = (writeIndex_ + 1U) % kMaxDelaySamples;

        // Two short, bounded one-pole decorrelators provide ambience without
        // polarity tricks, extreme Haas delay, or runaway feedback.
        decorrelatedA_ = 0.72f * decorrelatedA_ + 0.28f * delayedA;
        decorrelatedB_ = 0.68f * decorrelatedB_ + 0.32f * delayedB;
        const float ambience = sideHigh * (1.0f - sideHighMix) +
                               (0.55f * decorrelatedA_ + 0.45f * decorrelatedB_) * sideHighMix;

        // Fold virtual rear components back into stereo while preserving the
        // original direct image. Both outputs use same-polarity, side-derived
        // ambience; their decorrelated states remain distinct, so the direct
        // stereo field is retained without a polarity/phase gimmick.
        const float rearLeft = 0.45f * ambience + 0.55f * decorrelatedA_;
        const float rearRight = 0.45f * ambience + 0.55f * decorrelatedB_;
        // Spatial-only reconstruction: preserve the direct samples and add
        // only the generated surround contribution. Master headroom and
        // limiting belong to a later stage outside this processor.
        interleavedStereo[offset] = left + rearLeft * rearMix;
        interleavedStereo[offset + 1U] = right + rearRight * rearMix;
    }
}

}  // namespace frostsoulx
