#pragma once

#include <array>
#include <cstddef>

#include "frostsoulx/dsp/room_geometry.h"

namespace frostsoulx::dsp {

class HrtfBinauralProcessor final {
public:
    static constexpr std::size_t kMaxTaps = 2048;
    static constexpr std::size_t kMaxDirections = 64;
    static constexpr std::size_t kPartitionSize = 64;
    static constexpr std::size_t kFftSize = kPartitionSize * 2;
    static constexpr std::size_t kMaxPartitions = (kMaxTaps + kPartitionSize - 1U) / kPartitionSize;

    struct Format {
        double sampleRate = 48000.0;
    };

    struct ImpulseResponse {
        const float* left = nullptr;
        const float* right = nullptr;
        std::size_t taps = 0;
    };

    struct DirectionalImpulseResponse {
        float azimuthDegrees = 0.0f;
        float elevationDegrees = 0.0f;
        ImpulseResponse response{};
    };

    void prepare(Format format) noexcept;
    void reset() noexcept;

    // Copies one measured HRIR pair into the active response.
    bool setImpulseResponse(ImpulseResponse response) noexcept;

    // Copies a fixed directional HRIR set. Coordinates are degrees.
    bool setDirectionalResponses(const DirectionalImpulseResponse* responses, std::size_t count) noexcept;
    // Loads a validated .fhrtf asset generated from a licensed SOFA dataset.
    // File I/O occurs only here, never inside process().
    bool loadFhrtfAsset(const char* path) noexcept;
    bool setDirection(float azimuthDegrees, float elevationDegrees) noexcept;
    void setCrossfadeSamples(std::size_t samples) noexcept;
    void setRoomGeometry(RoomGeometry geometry) noexcept;
    void setDistanceAttenuationEnabled(bool enabled) noexcept;
    [[nodiscard]] float sourceDistance() const noexcept;

    void setEnabled(bool enabled) noexcept;
    [[nodiscard]] bool enabled() const noexcept;
    [[nodiscard]] std::size_t tapCount() const noexcept;
    [[nodiscard]] std::size_t directionCount() const noexcept;
    [[nodiscard]] float azimuthDegrees() const noexcept;
    [[nodiscard]] float elevationDegrees() const noexcept;

    // Renders mono input to interleaved stereo headphone output.
    // No allocation, locking, or file I/O occurs here.
    void process(const float* monoInput, float* interleavedStereoOutput, std::size_t frames) noexcept;

private:
    struct Complex {
        float real = 0.0f;
        float imaginary = 0.0f;
    };

    struct StoredDirection {
        float azimuthDegrees = 0.0f;
        float elevationDegrees = 0.0f;
        std::array<float, kMaxTaps> left{};
        std::array<float, kMaxTaps> right{};
    };

    void clearResponses() noexcept;
    bool copyResponse(std::array<float, kMaxTaps>& left, std::array<float, kMaxTaps>& right, ImpulseResponse response) noexcept;
    bool interpolateDirection(float azimuthDegrees, float elevationDegrees) noexcept;
    [[nodiscard]] float convolve(const std::array<float, kMaxTaps>& response) const noexcept;
    void preparePartitionedFilters() noexcept;
    void processPartitionedBlock() noexcept;
    void fft(std::array<Complex, kFftSize>& values, bool inverse) const noexcept;
    void resetPartitionedState() noexcept;
    [[nodiscard]] float wrapAzimuth(float degrees) const noexcept;
    [[nodiscard]] float clampElevation(float degrees) const noexcept;

    Format format_{};
    std::array<float, kMaxTaps> leftHrir_{};
    std::array<float, kMaxTaps> rightHrir_{};
    std::array<float, kMaxTaps> targetLeftHrir_{};
    std::array<float, kMaxTaps> targetRightHrir_{};
    std::array<float, kMaxTaps> inputHistory_{};
    std::array<StoredDirection, kMaxDirections> directions_{};
    std::size_t taps_ = 0;
    std::size_t historyIndex_ = 0;
    std::size_t directionCount_ = 0;
    std::size_t crossfadeSamples_ = 256;
    std::size_t crossfadePosition_ = 256;
    float azimuthDegrees_ = 0.0f;
    float elevationDegrees_ = 0.0f;
    bool enabled_ = false;
    RoomGeometry roomGeometry_{};
    float sourceDistance_ = 1.0f;
    bool distanceAttenuationEnabled_ = true;
    bool hasTarget_ = false;
    bool usePartitioned_ = false;
    std::array<Complex, kFftSize> fftScratch_{};
    std::array<std::array<Complex, kFftSize>, kMaxPartitions> inputSpectra_{};
    std::array<std::array<Complex, kFftSize>, kMaxPartitions> currentLeftPartitions_{};
    std::array<std::array<Complex, kFftSize>, kMaxPartitions> currentRightPartitions_{};
    std::array<std::array<Complex, kFftSize>, kMaxPartitions> targetLeftPartitions_{};
    std::array<std::array<Complex, kFftSize>, kMaxPartitions> targetRightPartitions_{};
    std::array<float, kPartitionSize> partitionInput_{};
    std::array<float, kPartitionSize> partitionOutputLeft_{};
    std::array<float, kPartitionSize> partitionOutputRight_{};
    std::array<float, kPartitionSize> targetPartitionOutputLeft_{};
    std::array<float, kPartitionSize> targetPartitionOutputRight_{};
    std::array<float, kPartitionSize> overlapLeft_{};
    std::array<float, kPartitionSize> overlapRight_{};
    std::array<float, kPartitionSize> targetOverlapLeft_{};
    std::array<float, kPartitionSize> targetOverlapRight_{};
    std::size_t partitionInputFrames_ = 0;
    std::size_t partitionOutputFrames_ = 0;
    std::size_t inputPartitionIndex_ = 0;
};

}  // namespace frostsoulx::dsp
