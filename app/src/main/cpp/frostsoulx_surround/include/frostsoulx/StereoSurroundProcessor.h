#pragma once

#include <array>
#include <cstddef>

namespace frostsoulx {

class StereoSurroundProcessor final {
public:
    struct Parameters {
        float lowFrequencyCutoffHz = 180.0f;
        float sideExtractionGain = 0.5f;
        std::size_t delayASamples = 37;
        std::size_t delayBSamples = 59;
        float decorrelationAInputCoefficient = 0.28f;
        float decorrelationBInputCoefficient = 0.32f;
        float sideHighMixBase = 0.35f;
        float sideHighMixIntensitySpan = 0.45f;
        float ambienceDecorrelatedAWeight = 0.55f;
        float rearAmbienceWeight = 0.45f;
        float maxRearContribution = 0.22f;
    };

    StereoSurroundProcessor() = default;

    void prepare(double sampleRate, int channels, int maxBlockSize) noexcept;
    void reset() noexcept;

    void setEnabled(bool enabled) noexcept;
    void setIntensity(float intensity) noexcept;
    void setParameters(const Parameters& parameters) noexcept;

    [[nodiscard]] bool isEnabled() const noexcept { return enabled_; }
    [[nodiscard]] float intensity() const noexcept { return intensity_; }
    [[nodiscard]] Parameters parameters() const noexcept { return parameters_; }

    // Processes interleaved stereo float PCM: L R L R ... .
    // No allocation, locking, I/O, or logging occurs here.
    void process(float* interleavedStereo, int frames) noexcept;

private:
    static constexpr std::size_t kMaxDelaySamples = 256;

    [[nodiscard]] static float clamp01(float value) noexcept;
    [[nodiscard]] static float finiteOrZero(float value) noexcept;

    bool prepared_ = false;
    bool enabled_ = false;
    float intensity_ = 0.0f;
    double sampleRate_ = 48000.0;
    int channels_ = 2;
    int maxBlockSize_ = 0;
    Parameters parameters_{};

    std::size_t writeIndex_ = 0;
    float sideLowpass_ = 0.0f;
    float decorrelatedA_ = 0.0f;
    float decorrelatedB_ = 0.0f;

    std::array<float, kMaxDelaySamples> delayLineA_{};
    std::array<float, kMaxDelaySamples> delayLineB_{};
};

}  // namespace frostsoulx
