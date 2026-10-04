#pragma once

#include <cstddef>
#include <array>
#include <memory>

#include "frostsoulx/spatial/rir_generator.h"
#include "frostsoulx/spatial/space_profile.h"
#include "frostsoulx/spatial/spatial_source.h"

namespace frostsoulx {

enum class ImmersiveProcessResult {
    NotPrepared,
    Disabled,
    InvalidInput,
    InvalidOutput,
    Processed,
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
    bool setCustomImpulseResponse(const float* left, const float* right, std::size_t taps) noexcept;
    void clearCustomImpulseResponse() noexcept;

    // One control producer may update parameters concurrently with process().
    // Geometry/IR generation allocates and performs FFTs on that control thread.
    // prepare/reset/destruction MUST be serialized with both threads.
    // Space/profile/matrix getters are control-thread-only.
    // Space simulation controls (control thread only).
    void setRoomSimulationPreset(RoomSimulationPreset preset) noexcept;
    void setRoomMix(float wetMix) noexcept;
    void setReflectionAmount(float amount) noexcept;
    void setReverbTimeSeconds(float seconds) noexcept;

    // Additional normalized UI controls (sliders/knobs): [0, 1].
    void setRoomSize(float size) noexcept;
    void setDampening(float dampening) noexcept;
    void setStereoWidth(float width) noexcept; // normalized 0.5 = high-band unity
    void setBassGain(float gain) noexcept;     // linear [0,2], unity=1
    void setBassWidth(float width) noexcept;   // independent low-band M/S [0,2]
    void setHighBandWidth(float width) noexcept; // high-band M/S [0,2]
    SpaceDesignControls spaceDesignControls() const noexcept;

    /// Geometry-aware listener orientation; yaw+ left, pitch+ up, roll+ right.
    /// Control thread only; publishes a crossfaded complete HRTF/room matrix.
    void setHeadOrientation(float yawDeg, float pitchDeg, float rollDeg) noexcept;

    bool isPrepared() const noexcept;
    int maxFrames() const noexcept;
    ImmersiveProcessResult lastProcessResult() const noexcept;
    int lastEffectState() const noexcept;

    /// Algorithmic latency added by the active spatial stage, in samples.
    int latencySamples() const noexcept;

    bool process(float* interleavedStereo, int frames) noexcept;

    // -------------------------------------------------------------------------
    // Unified spatializer / physical acoustic space controls
    // -------------------------------------------------------------------------
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

    // 3D orbit mode is a spatial mode layered over the active room preset.
    // 0° = front, +90° = left, -90° = right; elevation is vertical; radius is metres.
    void setOrbitEnabled(bool enabled) noexcept;
    void setOrbitAzimuth(float azimuthDeg) noexcept;
    void setOrbitElevation(float elevationDeg) noexcept;
    void setOrbitRadius(float radiusMetres) noexcept;
    void setOrbitPosition(float azimuthDeg, float elevationDeg, float radiusMetres) noexcept;
    void advanceOrbit(float deltaAzimuthDeg) noexcept;
    bool orbitEnabled() const noexcept;
    float orbitAzimuth() const noexcept;
    float orbitElevation() const noexcept;
    float orbitRadius() const noexcept;

    const spatial::SpaceProfile& activeSpaceProfile() const noexcept;
    const spatial::StereoBrir& activeBrir() const noexcept;
    const std::array<spatial::StereoBrir, 2>& activeTransferMatrix() const noexcept;
    float matrixNormalizationGain() const noexcept; // control-thread matrix attenuation
    float safetyPeak() const noexcept; // audio-thread detector diagnostic
    float safetyGain() const noexcept; // audio-thread diagnostic only

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace frostsoulx
