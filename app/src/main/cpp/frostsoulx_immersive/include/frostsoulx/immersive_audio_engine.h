#pragma once

#include <cstddef>
#include <memory>

#include "frostsoulx/spatial/rir_generator.h"
#include "frostsoulx/spatial/space_profile.h"
#include "frostsoulx/spatial/spatial_source.h"

namespace frostsoulx {

enum class ImmersiveProcessResult {
    NotPrepared,
    Disabled,
    InvalidInput,
    SteamAudioUnavailable,
    InvalidOutput,
    SteamAudioProcessed,
    /// Processed by the built-in HOA/HRTF spatial renderer. Reported when the
    /// Steam Audio backend is not compiled in or failed to initialise.
    NativeSpatialProcessed,
    /// Processed by the full-partitioned linear convolution acoustic space engine.
    FullConvolutionProcessed,
};

/// Which spatialiser `process()` is currently driving.
enum class SpatialBackend {
    None,            ///< not prepared
    SteamAudio,      ///< vendored Steam Audio binaural effect
    Native,          ///< built-in HOA encode -> rotate -> HRTF convolution (low-device fallback)
    FullConvolution, ///< Full physical room acoustic BRIR partitioned linear convolution
};

enum class RoomSimulationPreset {
    Off,
    SmallRoom,
    Studio,
    ConcertHall,
    Cathedral,
    Subway,
};

// UI-friendly normalized controls in [0, 1].
struct SpaceDesignControls {
    float roomSize = 0.5f;
    float dampening = 0.5f;
    float width = 0.5f;
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

    // Additional normalized UI controls (sliders/knobs): [0, 1].
    void setRoomSize(float size) noexcept;
    void setDampening(float dampening) noexcept;
    void setStereoWidth(float width) noexcept;
    SpaceDesignControls spaceDesignControls() const noexcept;

    /// Head orientation for the native renderer's sound-field rotation.
    /// Degrees; yaw+ = turn left, pitch+ = look up, roll+ = tilt right.
    /// Control thread only. Ignored by the Steam Audio backend.
    void setHeadOrientation(float yawDeg, float pitchDeg, float rollDeg) noexcept;

    bool isPrepared() const noexcept;
    int maxFrames() const noexcept;
    ImmersiveProcessResult lastProcessResult() const noexcept;
    int lastEffectState() const noexcept;

    /// Which spatialiser is active after `prepare()`.
    SpatialBackend backend() const noexcept;
    /// Algorithmic latency added by the active spatial stage, in samples.
    int latencySamples() const noexcept;

    bool process(float* interleavedStereo, int frames) noexcept;

    // -------------------------------------------------------------------------
    // True Acoustic Space & Full Partitioned Convolution API (Stage 4 & Stage 7)
    // -------------------------------------------------------------------------
    void setSpatialBackendPreference(SpatialBackend backend) noexcept;
    void setSpacePreset(spatial::SpaceProfile::Preset preset) noexcept;
    void setSpaceProfile(const spatial::SpaceProfile& profile) noexcept;
    void setRoomDimensions(float x, float y, float z) noexcept;
    void setBoundaryMaterial(spatial::RoomSurface surface, const spatial::AcousticMaterial& material) noexcept;
    void setSourcePosition(float x, float y, float z) noexcept; // User coords: X=right/left, Y=up/down, Z=front/back
    void setListenerPosition(float x, float y, float z) noexcept;
    void setListenerOrientation(float yawDeg, float pitchDeg, float rollDeg) noexcept;
    void setSourceTrajectory(const spatial::SourceTrajectory& trajectory) noexcept;
    void setTrajectoryPosition(float timeSeconds) noexcept;
    void setIrLength(std::size_t taps) noexcept;
    void setReflectionDensity(float density) noexcept;

    const spatial::SpaceProfile& activeSpaceProfile() const noexcept;
    const spatial::StereoBrir& activeBrir() const noexcept;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace frostsoulx
