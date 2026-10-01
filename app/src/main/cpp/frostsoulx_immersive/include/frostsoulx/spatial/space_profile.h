#pragma once

#include "frostsoulx/rt/rt_types.h"
#include "frostsoulx/spatial/geometry.h"

#include <array>
#include <cmath>
#include <string>
#include <vector>

namespace frostsoulx::spatial {

// -----------------------------------------------------------------------------
// Octave-band frequency representation for acoustic absorption.
// Standard octave bands: 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz.
// -----------------------------------------------------------------------------
inline constexpr std::size_t kNumAcousticBands = 6;
inline constexpr std::array<float, kNumAcousticBands> kOctaveBandFrequencies = {
    125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f
};

/// Acoustic material properties with multi-band absorption and scattering.
struct AcousticMaterial {
    std::string name = "Generic";
    /// Absorption coefficient alpha in [0, 1] per octave band.
    std::array<float, kNumAcousticBands> absorption = {0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f};
    /// Scattering (diffuse reflection) coefficient in [0, 1].
    float scattering = 0.15f;

    static AcousticMaterial Concrete();
    static AcousticMaterial Brick();
    static AcousticMaterial WoodPanel();
    static AcousticMaterial Glass();
    static AcousticMaterial Plasterboard();
    static AcousticMaterial Carpet();
    static AcousticMaterial Marble();
    static AcousticMaterial AcousticDrapes();
    static AcousticMaterial OpenAir();
    static AcousticMaterial CeramicTile();
    static AcousticMaterial RoughStone();
    static AcousticMaterial CarInterior();
    static AcousticMaterial Asphalt();
    static AcousticMaterial AudienceSeating();
};

/// Boundary surface index in a shoebox room.
enum class RoomSurface {
    Floor = 0,
    Ceiling = 1,
    Front = 2,
    Back = 3,
    Left = 4,
    Right = 5
};

/// A physical boundary plane of an acoustic space.
struct BoundarySurface {
    RoomSurface surface = RoomSurface::Floor;
    AcousticMaterial material = AcousticMaterial::Concrete();
    /// Fraction in [0, 1] of the boundary surface open to outside (e.g. windows, open air).
    float openness = 0.0f;
};

/// Obstacle or aperture in the space.
struct AcousticObstacle {
    Vec3 position{0.0f, 0.0f, 0.0f};
    Vec3 size{1.0f, 1.0f, 1.0f};
    AcousticMaterial material = AcousticMaterial::WoodPanel();
    /// Acoustic transmission loss in dB (transmission through obstacle).
    float transmissionLossDb = 20.0f;
};

/// Trajectory waypoint for moving sources.
struct TrajectoryWaypoint {
    float timeSeconds = 0.0f;
    Vec3 position{0.0f, 0.0f, 0.0f};
    HeadOrientation orientation{};
};

/// Continuous trajectory for audio source motion.
struct SourceTrajectory {
    std::vector<TrajectoryWaypoint> waypoints;

    Vec3 positionAt(float timeSeconds) const noexcept;
    HeadOrientation orientationAt(float timeSeconds) const noexcept;
};

/// Full physical space profile describing geometry, materials, and kinematics.
class SpaceProfile {
public:
    SpaceProfile() = default;
    SpaceProfile(std::string name, Vec3 dimensions);

    const std::string& name() const noexcept { return name_; }
    void setName(std::string name) { name_ = std::move(name); }

    const Vec3& dimensions() const noexcept { return dimensions_; }
    void setDimensions(const Vec3& dims) noexcept;

    float scale() const noexcept { return scale_; }
    void setScale(float scale) noexcept { scale_ = std::clamp(scale, 0.1f, 10.0f); }

    float openness() const noexcept { return openness_; }
    void setOpenness(float o) noexcept { openness_ = std::clamp(o, 0.0f, 1.0f); }

    const Vec3& sourcePosition() const noexcept { return sourcePos_; }
    void setSourcePosition(const Vec3& pos) noexcept { sourcePos_ = clampToRoom(pos); }

    const Vec3& listenerPosition() const noexcept { return listenerPos_; }
    void setListenerPosition(const Vec3& pos) noexcept { listenerPos_ = clampToRoom(pos); }

    const HeadOrientation& listenerOrientation() const noexcept { return listenerOrient_; }
    void setListenerOrientation(const HeadOrientation& o) noexcept { listenerOrient_ = o; }

    const HeadOrientation& sourceOrientation() const noexcept { return sourceOrient_; }
    void setSourceOrientation(const HeadOrientation& o) noexcept { sourceOrient_ = o; }

    const SourceTrajectory& trajectory() const noexcept { return trajectory_; }
    SourceTrajectory& trajectory() noexcept { return trajectory_; }
    void setTrajectory(SourceTrajectory traj) { trajectory_ = std::move(traj); }

    const BoundarySurface& boundary(RoomSurface surface) const noexcept {
        return boundaries_[static_cast<std::size_t>(surface)];
    }
    void setBoundary(RoomSurface surface, const BoundarySurface& b) noexcept {
        boundaries_[static_cast<std::size_t>(surface)] = b;
    }

    const std::vector<AcousticObstacle>& obstacles() const noexcept { return obstacles_; }
    void addObstacle(const AcousticObstacle& obstacle) { obstacles_.push_back(obstacle); }
    void clearObstacles() noexcept { obstacles_.clear(); }

    /// Geometric room volume (scaled), in m^3.
    float volume() const noexcept;

    /// Total surface area of the 6 bounding walls (scaled), in m^2.
    float totalSurfaceArea() const noexcept;

    /// Surface area for a specific boundary plane, in m^2.
    float surfaceArea(RoomSurface surface) const noexcept;

    /// Average absorption coefficient per octave band across all boundaries.
    std::array<float, kNumAcousticBands> averageAbsorption() const noexcept;

    /// Sabine/Eyring reverberation time T60 per octave band, in seconds.
    std::array<float, kNumAcousticBands> reverberationTimeT60() const noexcept;

    // -------------------------------------------------------------------------
    // Factory space profile presets (12 physical environments):
    // -------------------------------------------------------------------------
    static SpaceProfile createBathroom();
    static SpaceProfile createLivingRoom();
    static SpaceProfile createMediumHall();
    static SpaceProfile createLargeHall();
    static SpaceProfile createConcertHall();
    static SpaceProfile createSubwayPlatform();
    static SpaceProfile createLongSubwayTunnel();
    static SpaceProfile createLongTunnel();
    static SpaceProfile createClosedCar();
    static SpaceProfile createOpenRoad();
    static SpaceProfile createCave();
    static SpaceProfile createStadium();

    enum class Preset {
        Bathroom,
        LivingRoom,
        MediumHall,
        LargeHall,
        ConcertHall,
        SubwayPlatform,
        LongSubwayTunnel,
        LongTunnel,
        ClosedCar,
        OpenRoad,
        Cave,
        Stadium
    };

    static SpaceProfile createPreset(Preset preset);

    // Compatibility aliases:
    static SpaceProfile createSmallRoom() { return createBathroom(); }
    static SpaceProfile createStudio() { return createLivingRoom(); }
    static SpaceProfile createCathedral() { return createLargeHall(); }
    static SpaceProfile createSubway() { return createSubwayPlatform(); }
    static SpaceProfile createCourtyard() { return createOpenRoad(); }

private:
    Vec3 effectiveDimensions() const noexcept { return dimensions_ * scale_; }
    Vec3 clampToRoom(const Vec3& p) const noexcept;

    std::string name_ = "Default Room";
    Vec3 dimensions_{6.0f, 4.0f, 2.8f};
    float scale_ = 1.0f;
    float openness_ = 0.0f;

    Vec3 sourcePos_{2.0f, 0.0f, 1.2f};
    Vec3 listenerPos_{0.0f, 0.0f, 1.2f};
    HeadOrientation listenerOrient_{};
    HeadOrientation sourceOrient_{};
    SourceTrajectory trajectory_{};

    std::array<BoundarySurface, 6> boundaries_{};
    std::vector<AcousticObstacle> obstacles_;
};

} // namespace frostsoulx::spatial
