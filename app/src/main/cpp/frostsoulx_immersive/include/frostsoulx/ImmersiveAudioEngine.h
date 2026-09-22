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
    ClosedCar,
};

// UI-friendly normalized controls in [0, 1].
struct SpaceDesignControls {
    float roomSize = 0.5f;
    float dampening = 0.5f;
    float width = 0.5f;
};

// Per-stage limiter telemetry, expressed as positive dB of gain reduction.
struct ToneStageTelemetry {
    float bassGainReductionDb = 0.0f;
    float trebleGainReductionDb = 0.0f;
    float outputGainReductionDb = 0.0f;
    float spatialGainReductionDb = 0.0f;
    // 0 = fully bypassed, 1 = fully processed. Values in between mean an
    // enable/disable crossfade is currently in flight.
    float transitionRamp = 0.0f;
};

class ImmersiveAudioEngine final {
public:
    ImmersiveAudioEngine();
    ~ImmersiveAudioEngine();

    // Recommended host callback quantum for low-latency processing at common
    // sample rates. The engine still accepts any positive max frame count.
    static constexpr int kPreferredQuantumFrames = 384;

    // Tone stage limits shared with the JNI/Kotlin layers.
    static constexpr float kMinShelfGainDb = -12.0f;
    static constexpr float kMaxShelfGainDb = 12.0f;
    static constexpr float kMinOutputGainDb = -24.0f;
    static constexpr float kMaxOutputGainDb = 12.0f;

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

    // Additional normalized UI controls (sliders/knobs): [0, 1].
    void setRoomSize(float size) noexcept;
    void setDampening(float dampening) noexcept;
    void setStereoWidth(float width) noexcept;
    // Front/rear cabin balance: -1 = front, 0 = centered, +1 = rear.
    void setCarFader(float fader) noexcept;
    SpaceDesignControls spaceDesignControls() const noexcept;

    // Tone and level stage. Each of these stages owns a dedicated limiter so a
    // boost in one band can never push a later stage into hard clipping.
    void setBassGainDb(float gainDb) noexcept;
    void setTrebleGainDb(float gainDb) noexcept;
    void setOutputGainDb(float gainDb) noexcept;
    float bassGainDb() const noexcept;
    float trebleGainDb() const noexcept;
    float outputGainDb() const noexcept;
    ToneStageTelemetry toneStageTelemetry() const noexcept;

    bool isPrepared() const noexcept;
    // True while a bypass crossfade is still in flight. The host must keep
    // calling process() until this returns false, otherwise the fade is cut
    // short and the transition becomes audible.
    bool isTransitioning() const noexcept;
    int maxFrames() const noexcept;
    ImmersiveProcessResult lastProcessResult() const noexcept;
    int lastEffectState() const noexcept;
    bool process(float* interleavedStereo, int frames) noexcept;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace frostsoulx
