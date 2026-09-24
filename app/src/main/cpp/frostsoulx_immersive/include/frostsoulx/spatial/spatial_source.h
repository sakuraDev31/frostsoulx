#pragma once

#include "frostsoulx/rt/rt_types.h"
#include "frostsoulx/spatial/geometry.h"
#include "frostsoulx/spatial/space_profile.h"

#include <algorithm>
#include <cmath>
#include <string>
#include <vector>

namespace frostsoulx::spatial {

/// ----------------------------------------------------------------------------
/// 3D Spatial Coordinate System.
///
/// External / User Coordinate Convention:
///   X : Left (-) to Right (+)
///   Y : Down (-) to Up (+)
///   Z : Back (-) to Front (+)
///
/// Internal Engine Acoustic Convention (right-handed listener-centric):
///   +X : Forward (Front)
///   +Y : Left
///   +Z : Up
/// ----------------------------------------------------------------------------
struct Coord3D {
    float x = 0.0f; ///< Left (-) to Right (+) in metres
    float y = 0.0f; ///< Down (-) to Up (+) in metres
    float z = 0.0f; ///< Back (-) to Front (+) in metres

    constexpr Coord3D() = default;
    constexpr Coord3D(float x_, float y_, float z_) : x(x_), y(y_), z(z_) {}

    /// Convert from User 3D coordinates (X=right, Y=up, Z=front) to internal
    /// engine coordinates (+X=front, +Y=left, +Z=up).
    constexpr Vec3 toEngineCoords() const noexcept {
        return Vec3{z, -x, y};
    }

    /// Convert from internal engine coordinates (+X=front, +Y=left, +Z=up)
    /// to User 3D coordinates (X=right, Y=up, Z=front).
    static constexpr Coord3D fromEngineCoords(const Vec3& v) noexcept {
        return Coord3D{-v.y, v.z, v.x};
    }

    constexpr Coord3D operator+(const Coord3D& o) const noexcept {
        return {x + o.x, y + o.y, z + o.z};
    }
    constexpr Coord3D operator-(const Coord3D& o) const noexcept {
        return {x - o.x, y - o.y, z - o.z};
    }
    constexpr Coord3D operator*(float s) const noexcept {
        return {x * s, y * s, z * s};
    }

    float length() const noexcept {
        return std::sqrt(x * x + y * y + z * z);
    }
    constexpr float lengthSquared() const noexcept {
        return x * x + y * y + z * z;
    }
};

/// ----------------------------------------------------------------------------
/// 3D Source Model.
///
/// Models 3D spatial properties of an audio source relative to a listener:
/// - Azimuth, Elevation, Distance
/// - Distance attenuation (1/d physical spherical spreading)
/// - Direct-path propagation delay
/// - Interaural Time Difference (ITD) via Woodworth spherical head model
/// - Interaural Level Difference (ILD) via frequency-dependent head shadow
/// ----------------------------------------------------------------------------
class Source3D {
public:
    Source3D() = default;
    Source3D(Coord3D position, Coord3D listenerPos = {})
        : position_(position), listenerPos_(listenerPos) {}

    const Coord3D& position() const noexcept { return position_; }
    void setPosition(const Coord3D& pos) noexcept { position_ = pos; }

    const Coord3D& listenerPosition() const noexcept { return listenerPos_; }
    void setListenerPosition(const Coord3D& pos) noexcept { listenerPos_ = pos; }

    const HeadOrientation& listenerOrientation() const noexcept { return listenerOrient_; }
    void setListenerOrientation(const HeadOrientation& o) noexcept { listenerOrient_ = o; }

    float referenceDistance() const noexcept { return refDistance_; }
    void setReferenceDistance(float d) noexcept { refDistance_ = std::max(0.1f, d); }

    float minDistance() const noexcept { return minDistance_; }
    void setMinDistance(float d) noexcept { minDistance_ = std::max(0.01f, d); }

    float soundSpeed() const noexcept { return soundSpeed_; }
    void setSoundSpeed(float c) noexcept { soundSpeed_ = std::max(100.0f, c); }

    float headRadius() const noexcept { return headRadius_; }
    void setHeadRadius(float r) noexcept { headRadius_ = std::max(0.01f, r); }

    /// Distance from listener to source in metres.
    float distance() const noexcept {
        return (position_ - listenerPos_).length();
    }

    /// Listener-relative spherical coordinates (taking head orientation into account).
    SphericalCoord localSpherical() const noexcept {
        ListenerFrame frame;
        frame.setPosition(listenerPos_.toEngineCoords());
        frame.setOrientation(listenerOrient_);
        return frame.toLocalSpherical(position_.toEngineCoords());
    }

    /// Azimuth angle in degrees: 0 = front, +90 = left, -90 or +270 = right.
    float azimuthDeg() const noexcept {
        return localSpherical().azimuthDeg;
    }

    /// Elevation angle in degrees: 0 = horizontal, +90 = overhead, -90 = below.
    float elevationDeg() const noexcept {
        return localSpherical().elevationDeg;
    }

    /// Physical 1/d spherical distance attenuation factor in [0, 1].
    float distanceAttenuation() const noexcept {
        const float d = std::max(distance(), minDistance_);
        return std::clamp(refDistance_ / d, 0.0f, 1.0f);
    }

    /// Direct-path acoustic propagation delay in seconds.
    float directPathDelaySeconds() const noexcept {
        return distance() / soundSpeed_;
    }

    /// Interaural Time Difference (ITD) in seconds using the Woodworth spherical head model:
    ///   ITD = (r / c) * (sin(theta) + theta)
    /// where theta is the angle of incidence relative to the median plane.
    /// theta = 0 for any direction on the median plane (front, back, overhead, below).
    /// Positive = sound arrives earlier at left ear (source on the left).
    /// Negative = sound arrives earlier at right ear (source on the right).
    float itdSeconds() const noexcept {
        ListenerFrame frame;
        frame.setPosition(listenerPos_.toEngineCoords());
        frame.setOrientation(listenerOrient_);
        const Vec3 local = frame.toLocal(position_.toEngineCoords());
        const float d = local.length();
        if (d < 1.0e-6f) return 0.0f;
        // In local frame: +Y is LEFT ear, -Y is RIGHT ear.
        const float sinTheta = std::clamp(local.y / d, -1.0f, 1.0f);
        const float theta = std::asin(sinTheta); // in [-pi/2, +pi/2]
        return (headRadius_ / soundSpeed_) * (sinTheta + theta);
    }

    /// Interaural Level Difference (ILD) linear gain factor for the left ear.
    /// Accounts for acoustic head shadowing across frequency.
    float ildGainLeft() const noexcept {
        ListenerFrame frame;
        frame.setPosition(listenerPos_.toEngineCoords());
        frame.setOrientation(listenerOrient_);
        const Vec3 local = frame.toLocal(position_.toEngineCoords());
        const float d = local.length();
        if (d < 1.0e-6f) return 1.0f;
        const float sinTheta = std::clamp(local.y / d, -1.0f, 1.0f);
        if (sinTheta >= 0.0f) {
            return 1.0f; // Ipsilateral
        }
        const float shadow = 1.0f / (1.0f + 1.2f * std::fabs(sinTheta));
        return std::clamp(shadow, 0.2f, 1.0f);
    }

    /// Interaural Level Difference (ILD) linear gain factor for the right ear.
    float ildGainRight() const noexcept {
        ListenerFrame frame;
        frame.setPosition(listenerPos_.toEngineCoords());
        frame.setOrientation(listenerOrient_);
        const Vec3 local = frame.toLocal(position_.toEngineCoords());
        const float d = local.length();
        if (d < 1.0e-6f) return 1.0f;
        const float sinTheta = std::clamp(local.y / d, -1.0f, 1.0f);
        if (sinTheta <= 0.0f) {
            return 1.0f; // Ipsilateral
        }
        const float shadow = 1.0f / (1.0f + 1.2f * std::fabs(sinTheta));
        return std::clamp(shadow, 0.2f, 1.0f);
    }

private:
    Coord3D position_{0.0f, 0.0f, 2.0f}; // 2 metres in front by default
    Coord3D listenerPos_{0.0f, 0.0f, 0.0f};
    HeadOrientation listenerOrient_{};
    float refDistance_ = 1.0f;
    float minDistance_ = 0.2f;
    float soundSpeed_ = rt::kSpeedOfSound;
    float headRadius_ = rt::kHeadRadius;
};

/// ----------------------------------------------------------------------------
/// Reusable Trajectory Types & Factory.
/// ----------------------------------------------------------------------------
enum class TrajectoryType {
    LeftToRight,
    FrontToBack,
    BackToFront,
    HorizontalArc,
    VerticalArcOverhead,
    Orbital3D,
    Circular,
    CustomWaypoint,
    CompoundDemo
};

/// Factory for generating continuous 3D source motion trajectories.
class TrajectoryFactory {
public:
    /// A. Left -> Right linear trajectory.
    static SourceTrajectory createLeftToRight(float span = 10.0f, float distanceFront = 2.0f,
                                             float height = 0.0f, float durationSec = 4.0f);

    /// B. Front -> Back linear trajectory.
    static SourceTrajectory createFrontToBack(float span = 10.0f, float xOffset = 0.0f,
                                             float height = 0.0f, float durationSec = 4.0f);

    /// C. Back -> Front linear trajectory.
    static SourceTrajectory createBackToFront(float span = 10.0f, float xOffset = 0.0f,
                                             float height = 0.0f, float durationSec = 4.0f);

    /// D. Horizontal arc trajectory across front hemisphere.
    static SourceTrajectory createHorizontalArc(float radius = 3.0f, float startAzDeg = -80.0f,
                                               float endAzDeg = 80.0f, float height = 0.0f,
                                               float durationSec = 4.0f);

    /// E. Vertical arc overhead trajectory: Front -> Overhead -> Back.
    static SourceTrajectory createVerticalArcOverhead(float radius = 3.0f, float durationSec = 4.0f);

    /// F. 3D orbital trajectory with spatial inclination.
    static SourceTrajectory createOrbital3D(float radius = 3.0f, float inclinationDeg = 35.0f,
                                           float durationSec = 6.0f);

    /// G. 360-degree circular trajectory around listener.
    static SourceTrajectory createCircular(float radius = 3.0f, float height = 0.0f,
                                          float durationSec = 5.0f);

    /// Compound demo trajectory: LEFT -> FRONT -> OVERHEAD -> BACK -> RIGHT.
    static SourceTrajectory createCompoundDemo(float radius = 3.0f, float durationSec = 8.0f);
};

} // namespace frostsoulx::spatial
