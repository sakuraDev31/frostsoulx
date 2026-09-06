#include "frostsoulx/dsp/early_reflections.h"

#include <algorithm>
#include <cmath>

namespace frostsoulx::dsp {

void EarlyReflectionProcessor::prepare(Format format) noexcept {
    format_ = format;
    if (!std::isfinite(format_.sampleRate) || format_.sampleRate < 8000.0) {
        format_.sampleRate = 48000.0;
    }
    reset();
}

void EarlyReflectionProcessor::reset() noexcept {
    inputHistory_.fill(0.0f);
    historyIndex_ = 0;
}

bool EarlyReflectionProcessor::setImpulseResponse(ImpulseResponse response) noexcept {
    if (response.left == nullptr || response.right == nullptr || response.taps == 0) {
        return false;
    }

    taps_ = std::min(response.taps, kMaxTaps);
    for (std::size_t i = 0; i < taps_; ++i) {
        leftRir_[i] = std::isfinite(response.left[i]) ? response.left[i] : 0.0f;
        rightRir_[i] = std::isfinite(response.right[i]) ? response.right[i] : 0.0f;
    }
    for (std::size_t i = taps_; i < kMaxTaps; ++i) {
        leftRir_[i] = 0.0f;
        rightRir_[i] = 0.0f;
    }
    reset();
    return true;
}

void EarlyReflectionProcessor::setEnabled(bool enabled) noexcept {
    enabled_ = enabled;
}

void EarlyReflectionProcessor::setMix(float mix) noexcept {
    mix_ = std::clamp(std::isfinite(mix) ? mix : 0.0f, 0.0f, 1.0f);
}

void EarlyReflectionProcessor::setRoomGeometry(RoomGeometry geometry) noexcept {
    geometry.roomDimensions.x = std::clamp(std::isfinite(geometry.roomDimensions.x) ? geometry.roomDimensions.x : 10.0f, 0.1f, 1000.0f);
    geometry.roomDimensions.y = std::clamp(std::isfinite(geometry.roomDimensions.y) ? geometry.roomDimensions.y : 10.0f, 0.1f, 1000.0f);
    geometry.roomDimensions.z = std::clamp(std::isfinite(geometry.roomDimensions.z) ? geometry.roomDimensions.z : 3.0f, 0.1f, 1000.0f);
    geometry.referenceDistance = std::clamp(std::isfinite(geometry.referenceDistance) ? geometry.referenceDistance : 1.0f, 0.01f, 1000.0f);
    geometry.maxDistance = std::clamp(std::isfinite(geometry.maxDistance) ? geometry.maxDistance : 100.0f, geometry.referenceDistance, 10000.0f);
    geometry.directDistanceExponent = std::clamp(std::isfinite(geometry.directDistanceExponent) ? geometry.directDistanceExponent : 1.0f, 0.0f, 2.0f);
    geometry.reflectionDistanceExponent = std::clamp(std::isfinite(geometry.reflectionDistanceExponent) ? geometry.reflectionDistanceExponent : 0.5f, 0.0f, 2.0f);
    geometry.reflectionBalance = std::clamp(std::isfinite(geometry.reflectionBalance) ? geometry.reflectionBalance : 1.0f, 0.0f, 4.0f);
    roomGeometry_ = geometry;
    sourceDistance_ = roomDistance(roomGeometry_);
    reflectionGain_ = roomGeometry_.reflectionBalance * roomDistanceGain(
        sourceDistance_,
        roomGeometry_.referenceDistance,
        roomGeometry_.reflectionDistanceExponent,
        roomGeometry_.maxDistance);
}

void EarlyReflectionProcessor::setDistanceBalanceEnabled(bool enabled) noexcept {
    distanceBalanceEnabled_ = enabled;
}

bool EarlyReflectionProcessor::enabled() const noexcept {
    return enabled_;
}

float EarlyReflectionProcessor::mix() const noexcept {
    return mix_;
}

float EarlyReflectionProcessor::reflectionGain() const noexcept {
    return reflectionGain_;
}

float EarlyReflectionProcessor::sourceDistance() const noexcept {
    return sourceDistance_;
}

std::size_t EarlyReflectionProcessor::tapCount() const noexcept {
    return taps_;
}

void EarlyReflectionProcessor::process(
    const float* monoInput,
    float* interleavedStereoOutput,
    std::size_t frames) noexcept {
    if (monoInput == nullptr || interleavedStereoOutput == nullptr || frames == 0) {
        return;
    }

    for (std::size_t frame = 0; frame < frames; ++frame) {
        const float input = std::isfinite(monoInput[frame]) ? monoInput[frame] : 0.0f;
        inputHistory_[historyIndex_] = input;
        float left = 0.0f;
        float right = 0.0f;
        if (enabled_ && taps_ > 0 && mix_ > 0.0f) {
            std::size_t history = historyIndex_;
            for (std::size_t tap = 0; tap < taps_; ++tap) {
                left += inputHistory_[history] * leftRir_[tap];
                right += inputHistory_[history] * rightRir_[tap];
                history = history == 0 ? kMaxTaps - 1U : history - 1U;
            }
            const float geometryGain = distanceBalanceEnabled_ ? reflectionGain_ : 1.0f;
            left *= mix_ * geometryGain;
            right *= mix_ * geometryGain;
        }
        interleavedStereoOutput[frame * 2U] = left;
        interleavedStereoOutput[frame * 2U + 1U] = right;
        historyIndex_ = (historyIndex_ + 1U) % kMaxTaps;
    }
}

}  // namespace frostsoulx::dsp
