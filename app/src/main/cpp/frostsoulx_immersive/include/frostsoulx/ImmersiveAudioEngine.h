#pragma once

#include <cstddef>
#include <memory>

namespace frostsoulx {

enum class ImmersiveProcessResult {
    NotPrepared,
    Disabled,
    InvalidInput,
    SteamAudioUnavailable,
    SteamAudioProcessed,
};

class ImmersiveAudioEngine final {
public:
    ImmersiveAudioEngine();
    ~ImmersiveAudioEngine();

    ImmersiveAudioEngine(const ImmersiveAudioEngine&) = delete;
    ImmersiveAudioEngine& operator=(const ImmersiveAudioEngine&) = delete;

    bool prepare(int sampleRate, int maxFrames) noexcept;
    void reset() noexcept;
    void setEnabled(bool enabled) noexcept;
    void setSpatialBlend(float blend) noexcept;
    bool isPrepared() const noexcept;
    int maxFrames() const noexcept;
    ImmersiveProcessResult lastProcessResult() const noexcept;
    int lastEffectState() const noexcept;
    bool process(float* interleavedStereo, int frames) noexcept;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace frostsoulx
