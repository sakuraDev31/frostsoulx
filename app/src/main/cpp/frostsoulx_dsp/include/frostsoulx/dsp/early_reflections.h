#pragma once

#include <array>
#include <cstddef>

#include "frostsoulx/dsp/room_geometry.h"

namespace frostsoulx::dsp {

class EarlyReflectionProcessor final {
public:
    static constexpr std::size_t kMaxTaps = 2048;

    struct Format {
        double sampleRate = 48000.0;
    };

    struct ImpulseResponse {
        const float* left = nullptr;
        const float* right = nullptr;
        std::size_t taps = 0;
    };

    void prepare(Format format) noexcept;
    void reset() noexcept;
    bool setImpulseResponse(ImpulseResponse response) noexcept;
    void setEnabled(bool enabled) noexcept;
    void setMix(float mix) noexcept;
    void setRoomGeometry(RoomGeometry geometry) noexcept;
    void setDistanceBalanceEnabled(bool enabled) noexcept;
    [[nodiscard]] bool enabled() const noexcept;
    [[nodiscard]] float mix() const noexcept;
    [[nodiscard]] float reflectionGain() const noexcept;
    [[nodiscard]] float sourceDistance() const noexcept;
    [[nodiscard]] std::size_t tapCount() const noexcept;

    // Produces wet stereo room reflections only. The host should sum these
    // outputs with the direct HRTF output; this processor does not add dry audio.
    void process(const float* monoInput, float* interleavedStereoOutput, std::size_t frames) noexcept;

private:
    Format format_{};
    std::array<float, kMaxTaps> leftRir_{};
    std::array<float, kMaxTaps> rightRir_{};
    std::array<float, kMaxTaps> inputHistory_{};
    std::size_t taps_ = 0;
    std::size_t historyIndex_ = 0;
    float mix_ = 0.0f;
    RoomGeometry roomGeometry_{};
    float sourceDistance_ = 1.0f;
    float reflectionGain_ = 1.0f;
    bool distanceBalanceEnabled_ = true;
    bool enabled_ = false;
};

}  // namespace frostsoulx::dsp
