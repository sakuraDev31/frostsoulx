#pragma once

#include <cstddef>
#include <memory>

namespace frostsoulx {

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
    bool process(float* interleavedStereo, int frames) noexcept;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace frostsoulx
