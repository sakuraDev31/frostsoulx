#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include "frostsoulx/rt/rt_types.h"

namespace frostsoulx::dsp {
// Linked stereo, 4x polyphase windowed-sinc peak detector; 64-sample lookahead.
// A 64-tap detector avoids the short-kernel high-frequency under-read found by
// independent 16x reconstruction. 0.95 detection target leaves interpolation
// and gain-modulation margin beneath the 0.98 reconstructed ceiling.
// The monotonic deque gives amortized O(1) lookahead maxima. No audio is clipped
// or waveshaped: a single gain envelope acts on the delayed original samples.
class TruePeakSafety {
public:
    static constexpr int kLookahead = 64;
    void prepare(double sampleRate) noexcept {
        release_ = static_cast<float>(1.0 - std::exp(-1.0 / (0.080 * sampleRate)));
        for (int p = 0; p < 4; ++p) {
            double sum = 0.0;
            for (int k = 0; k < 64; ++k) {
                const double x = k - (31.0 + p * 0.25);
                const double sinc = std::fabs(x) < 1.0e-12 ? 1.0 : std::sin(3.141592653589793 * x) / (3.141592653589793 * x);
                const double w = 0.42 - 0.5 * std::cos(6.283185307179586 * k / 63.0) + 0.08 * std::cos(12.566370614359172 * k / 63.0);
                kernel_[static_cast<std::size_t>(p)][static_cast<std::size_t>(63-k)] = static_cast<float>(sinc * w);
                sum += sinc * w;
            }
            for (float& v : kernel_[static_cast<std::size_t>(p)]) v /= static_cast<float>(sum);
        }
        reset();
    }
    void reset() noexcept {
        delay_ = {}; history_ = {}; peaks_ = {}; indices_ = {};
        detectedPeak_ = 0.0f;
        clock_ = 0; head_ = tail_ = 0; gain_ = 1.0f; attack_ = 0.0f;
    }
    float gain() const noexcept { return gain_; }
    float detectedPeak() const noexcept { return detectedPeak_; }
    void process(float& l, float& r) noexcept {
        if (!std::isfinite(l)) l = 0.0f;
        if (!std::isfinite(r)) r = 0.0f;
        const std::size_t h = static_cast<std::size_t>(clock_ % 64);
        // Mirror the ring so every FIR dot product is a contiguous SIMD span.
        history_[0][h] = history_[0][h+64] = l;
        history_[1][h] = history_[1][h+64] = r;
        float peak = std::max(std::fabs(l), std::fabs(r));
        for (int c = 0; c < 2; ++c) {
            for (int p = 1; p < 4; ++p) {
                const float value = rt::dotProduct(kernel_[static_cast<std::size_t>(p)].data(),
                    history_[static_cast<std::size_t>(c)].data() + h + 1, 64);
                peak = std::max(peak, std::fabs(value));
            }
        }
        // Keep detector support around the sample leaving the lookahead ring.
        while (head_ != tail_ && clock_ - indices_[head_ % 128] > 100) ++head_;
        while (head_ != tail_ && peaks_[(tail_ - 1) % 128] <= peak) --tail_;
        peaks_[tail_ % 128] = peak; indices_[tail_ % 128] = clock_; ++tail_;
        const float maxPeak = peaks_[head_ % 128];
        detectedPeak_ = maxPeak;
        const float target = maxPeak > 0.95f ? 0.95f / maxPeak : 1.0f;
        if (target < gain_) {
            attack_ = std::max(attack_, (gain_ - target) / 24.0f);
            gain_ = std::max(target, gain_ - attack_);
        } else {
            attack_ = 0.0f;
            gain_ += release_ * (target - gain_);
        }
        const std::size_t d = static_cast<std::size_t>(clock_ % kLookahead);
        const float outL = delay_[0][d], outR = delay_[1][d];
        delay_[0][d] = l; delay_[1][d] = r;
        // Fail-safe for grossly invalid upstream levels, not a nonlinear clip.
        const float outPeak = std::max(std::fabs(outL), std::fabs(outR));
        if (outPeak * gain_ > 0.98f) gain_ = 0.98f / outPeak;
        l = outL * gain_; r = outR * gain_; ++clock_;
    }
private:
    std::array<std::array<float, 64>, 4> kernel_{};
    std::array<std::array<float, 128>, 2> history_{};
    std::array<std::array<float, kLookahead>, 2> delay_{};
    std::array<float, 128> peaks_{};
    std::array<std::uint64_t, 128> indices_{};
    std::uint64_t clock_ = 0, head_ = 0, tail_ = 0;
    float detectedPeak_ = 0.0f;
    float gain_ = 1.0f, attack_ = 0.0f, release_ = 0.0f;
};
} // namespace frostsoulx::dsp
