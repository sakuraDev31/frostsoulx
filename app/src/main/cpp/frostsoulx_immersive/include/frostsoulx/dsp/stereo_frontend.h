#pragma once
#include <algorithm>
#include <cmath>

namespace frostsoulx::dsp {
// Orthonormal M/S. The complementary split reconstructs exactly (high=x-low),
// not the phase-mismatched sum of two independently designed IIR crossovers.
class StereoFrontend {
public:
    void prepare(double sampleRate) noexcept {
        split_ = static_cast<float>(1.0 - std::exp(-6.283185307179586 * 180.0 / sampleRate));
        smooth_ = static_cast<float>(1.0 - std::exp(-1.0 / (0.015 * sampleRate)));
        reset();
    }
    void reset() noexcept { lowM_ = lowS_ = 0.0f; bass_ = width_ = high_ = 1.0f; }
    void process(float& l, float& r, float bassGain, float bassWidth, float highWidth) noexcept {
        constexpr float invSqrt2 = 0.7071067811865475f;
        bass_ += smooth_ * (bassGain - bass_);
        width_ += smooth_ * (bassWidth - width_);
        high_ += smooth_ * (highWidth - high_);
        const float m = (l + r) * invSqrt2;
        const float s = (l - r) * invSqrt2;
        lowM_ += split_ * (m - lowM_);
        lowS_ += split_ * (s - lowS_);
        // A conservative induced peak bound for the complementary band matrix:
        // y = A_high*x + (A_low-A_high)*LP(x), ||LP||_1 = 1.
        // Width <=1 is contractive; boosts consume explicit headroom, never rely
        // on a limiter to hide an over-unity band reconstruction.
        const float lowSide = bass_ * width_;
        const float a = 0.5f * (bass_ + lowSide);
        const float b = 0.5f * (bass_ - lowSide);
        const float c = 0.5f * (1.0f + high_);
        const float d = 0.5f * (1.0f - high_);
        const float bound = std::fabs(c) + std::fabs(d) + std::fabs(a-c) + std::fabs(b-d);
        const float gain = 1.0f / std::max(1.0f, bound);
        const float outM = m + (bass_ - 1.0f) * lowM_;
        const float outS = high_ * s + (lowSide - high_) * lowS_;
        l = (outM + outS) * invSqrt2 * gain;
        r = (outM - outS) * invSqrt2 * gain;
    }
private:
    float split_ = 0.0f, smooth_ = 0.0f;
    float lowM_ = 0.0f, lowS_ = 0.0f;
    float bass_ = 1.0f, width_ = 1.0f, high_ = 1.0f;
};
} // namespace frostsoulx::dsp
