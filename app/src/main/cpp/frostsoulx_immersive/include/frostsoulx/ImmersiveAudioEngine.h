#pragma once

#include <cstddef>
#include <memory>

namespace frostsoulx {

enum class ImmersiveProcessResult {
    NotPrepared,
    Disabled,
    InvalidInput,
    SteamAudioUnavailable,
    InvalidOutput,
    SteamAudioProcessed,
};

enum class RoomSimulationPreset {
    Off,
    SmallRoom,
    Studio,
    ConcertHall,
    Cathedral,
    Subway,
};

class ImmersiveAudioEngine final {
public:
    ImmersiveAudioEngine();
    ~ImmersiveAudioEngine();

    // Recommended host callback quantum for low-latency processing at common
    // sample rates. The engine still accepts any positive max frame count.
    static constexpr int kPreferredQuantumFrames = 384;

    ImmersiveAudioEngine(const ImmersiveAudioEngine&) = delete;
    ImmersiveAudioEngine& operator=(const ImmersiveAudioEngine&) = delete;

    bool prepare(int sampleRate, int maxFrames) noexcept;
    void reset() noexcept;
    void setEnabled(bool enabled) noexcept;
    void setSpatialBlend(float blend) noexcept;

    // Space simulation controls (control thread only).
    void setRoomSimulationPreset(RoomSimulationPreset preset) noexcept;
    void setRoomMix(float wetMix) noexcept;
    void setReflectionAmount(float amount) noexcept;
    void setReverbTimeSeconds(float seconds) noexcept;

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
