#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace frostsoulx::dsp {

enum class SoundFieldPreset : std::uint8_t {
    Natural = 0,
    Live,
    Wide,
    Immersive,
    Custom,
};

struct SoundFieldParameters {
    bool enabled = false;
    SoundFieldPreset preset = SoundFieldPreset::Natural;
    float intensity = 0.5f;
    float width = 1.0f;
    float crossfeed = 0.0f;
    float lowFrequencyProtection = 0.85f;
    float surround = 0.0f;
    float reverbMix = 0.0f;
    float reverbRoomSize = 0.5f;
    float reverbDecay = 0.45f;
    float outputGainDb = 0.0f;
    float limiterCeilingDb = -1.0f;
};

struct SoundFieldFormat {
    double sampleRate = 48000.0;
    std::size_t channels = 2;
};

class SoundFieldProcessor final {
public:
    SoundFieldProcessor() = default;

    void prepare(SoundFieldFormat format) noexcept;
    void reset() noexcept;

    void setParameters(const SoundFieldParameters& parameters) noexcept;
    [[nodiscard]] const SoundFieldParameters& parameters() const noexcept;
    [[nodiscard]] SoundFieldParameters effectiveParameters() const noexcept;

    // Processes interleaved stereo float PCM in-place.
    // No memory allocation occurs in this method.
    void process(float* interleavedStereo, std::size_t frames) noexcept;

private:
    void applyPresetDefaults(SoundFieldParameters& parameters) const noexcept;
    [[nodiscard]] float lowPass(float input, float& state) const noexcept;
    [[nodiscard]] float clampSample(float sample) const noexcept;
    [[nodiscard]] float readDelay(const std::array<float, 4096>& buffer, std::size_t delay) const noexcept;

    SoundFieldFormat format_{};
    SoundFieldParameters parameters_{};
    float lowLeft_ = 0.0f;
    float lowRight_ = 0.0f;
    float crossfeedLeft_ = 0.0f;
    float crossfeedRight_ = 0.0f;
    std::array<float, 4096> reverbLeft_{};
    std::array<float, 4096> reverbRight_{};
    std::size_t reverbIndex_ = 0;
};

}  // namespace frostsoulx::dsp
