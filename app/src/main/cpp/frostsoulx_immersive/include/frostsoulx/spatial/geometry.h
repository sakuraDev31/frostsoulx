#pragma once

// -----------------------------------------------------------------------------
// Spatial geometry primitives.
//
// Coordinate convention (right-handed, listener-centric, matches the usual
// spatial-audio / Ambisonics convention):
//
//     +X : forward  (in front of the listener / nose direction)
//     +Y : left
//     +Z : up
//
//   azimuth   : rotation about +Z, 0 = front, +90 deg = left  (counter-clockwise)
//   elevation : angle above the horizontal plane, +90 deg = directly overhead
//
// The interaural axis is therefore the Y axis, and a source's incidence angle
// relative to the right ear is acos(-y_hat).
//
// Head tracking is applied by rotating world-space source positions into this
// listener-local frame; `HeadOrientation` carries the yaw/pitch/roll needed to
// do so and is consumed by `ListenerFrame::toLocal()`.
// -----------------------------------------------------------------------------

#include "frostsoulx/rt/rt_types.h"

#include <cmath>

namespace frostsoulx::spatial {

struct Vec3 {
    float x = 0.0f, y = 0.0f, z = 0.0f;

    constexpr Vec3() = default;
    constexpr Vec3(float x_, float y_, float z_) : x(x_), y(y_), z(z_) {}

    constexpr Vec3 operator+(const Vec3& o) const noexcept { return {x + o.x, y + o.y, z + o.z}; }
    constexpr Vec3 operator-(const Vec3& o) const noexcept { return {x - o.x, y - o.y, z - o.z}; }
    constexpr Vec3 operator*(float s) const noexcept { return {x * s, y * s, z * s}; }

    float length() const noexcept { return std::sqrt(x * x + y * y + z * z); }
    constexpr float lengthSquared() const noexcept { return x * x + y * y + z * z; }

    Vec3 normalized() const noexcept {
        const float len = length();
        if (len < 1.0e-9f) return {1.0f, 0.0f, 0.0f};
        const float inv = 1.0f / len;
        return {x * inv, y * inv, z * inv};
    }

    bool isFinite() const noexcept {
        return std::isfinite(x) && std::isfinite(y) && std::isfinite(z);
    }
};

constexpr float dot(const Vec3& a, const Vec3& b) noexcept {
    return a.x * b.x + a.y * b.y + a.z * b.z;
}

constexpr Vec3 cross(const Vec3& a, const Vec3& b) noexcept {
    return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x};
}

/// Direction plus distance in listener-local space.
struct SphericalCoord {
    float azimuthDeg = 0.0f;    ///< 0 = front, +90 = left
    float elevationDeg = 0.0f;  ///< +90 = up
    float distanceMeters = 1.0f;

    Vec3 toCartesian() const noexcept {
        const float az = azimuthDeg * rt::kDegToRad;
        const float el = elevationDeg * rt::kDegToRad;
        const float ce = std::cos(el);
        return {distanceMeters * ce * std::cos(az),
                distanceMeters * ce * std::sin(az),
                distanceMeters * std::sin(el)};
    }

    static SphericalCoord fromCartesian(const Vec3& v) noexcept {
        SphericalCoord s;
        const float d = v.length();
        s.distanceMeters = d;
        if (d < 1.0e-9f) return s;
        s.azimuthDeg = std::atan2(v.y, v.x) * rt::kRadToDeg;
        s.elevationDeg = std::asin(std::clamp(v.z / d, -1.0f, 1.0f)) * rt::kRadToDeg;
        return s;
    }
};

/// Wrap an azimuth into [0, 360).
inline float wrapAzimuth(float deg) noexcept {
    if (!std::isfinite(deg)) return 0.0f;
    deg = std::fmod(deg, 360.0f);
    if (deg < 0.0f) deg += 360.0f;
    return deg;
}

/// Clamp an elevation into [-90, 90].
inline float clampElevation(float deg) noexcept {
    return std::isfinite(deg) ? std::clamp(deg, -90.0f, 90.0f) : 0.0f;
}

/// Shortest signed angular difference b - a, in [-180, 180).
inline float angleDelta(float a, float b) noexcept {
    float d = std::fmod(b - a + 180.0f, 360.0f);
    if (d < 0.0f) d += 360.0f;
    return d - 180.0f;
}

/// Great-circle angle between two directions, degrees.
inline float angularDistanceDeg(const SphericalCoord& a, const SphericalCoord& b) noexcept {
    const float a1 = a.elevationDeg * rt::kDegToRad;
    const float a2 = b.elevationDeg * rt::kDegToRad;
    const float dAz = (b.azimuthDeg - a.azimuthDeg) * rt::kDegToRad;
    const float c = std::sin(a1) * std::sin(a2) + std::cos(a1) * std::cos(a2) * std::cos(dAz);
    return std::acos(std::clamp(c, -1.0f, 1.0f)) * rt::kRadToDeg;
}

/// Head orientation in degrees; consumed by `ListenerFrame`.
struct HeadOrientation {
    float yawDeg = 0.0f;    ///< positive = turn left  (about +Z)
    float pitchDeg = 0.0f;  ///< positive = look up    (about +Y)
    float rollDeg = 0.0f;   ///< positive = tilt right (about +X)
};

/// Listener position + orientation. Transforms world coordinates into the
/// head-local frame used by the HRTF renderer, which is what makes the engine
/// head-tracking ready: feed sensor yaw/pitch/roll here and every source's
/// relative direction updates without touching the source definitions.
class ListenerFrame {
public:
    void setPosition(const Vec3& p) noexcept { position_ = p; }
    void setOrientation(const HeadOrientation& o) noexcept {
        orientation_ = o;
        rebuild();
    }

    const Vec3& position() const noexcept { return position_; }
    const HeadOrientation& orientation() const noexcept { return orientation_; }

    /// World-space point -> listener-local cartesian.
    Vec3 toLocal(const Vec3& world) const noexcept {
        const Vec3 d = world - position_;
        // Rows of the inverse (transpose) rotation.
        return {dot(d, fwd_), dot(d, left_), dot(d, up_)};
    }

    /// World-space point -> listener-local spherical.
    SphericalCoord toLocalSpherical(const Vec3& world) const noexcept {
        return SphericalCoord::fromCartesian(toLocal(world));
    }

    const Vec3& forward() const noexcept { return fwd_; }
    const Vec3& left() const noexcept { return left_; }
    const Vec3& up() const noexcept { return up_; }

private:
    void rebuild() noexcept {
        const float y = orientation_.yawDeg * rt::kDegToRad;
        const float p = orientation_.pitchDeg * rt::kDegToRad;
        const float r = orientation_.rollDeg * rt::kDegToRad;

        const float cy = std::cos(y), sy = std::sin(y);
        // +Y is LEFT in this frame, so a right-handed rotation about +Y by a
        // positive angle tilts the nose DOWN. `pitchDeg` is defined as
        // positive = look UP, hence Ry(-pitch).
        const float cp = std::cos(p), sp = -std::sin(p);
        const float cr = std::cos(r), sr = std::sin(r);

        // R = Rz(yaw) * Ry(-pitch) * Rx(roll), columns are the head axes.
        fwd_ = {cy * cp, sy * cp, -sp};
        left_ = {cy * sp * sr - sy * cr, sy * sp * sr + cy * cr, cp * sr};
        up_ = {cy * sp * cr + sy * sr, sy * sp * cr - cy * sr, cp * cr};
    }

    Vec3 position_{0.0f, 0.0f, 0.0f};
    HeadOrientation orientation_{};
    Vec3 fwd_{1.0f, 0.0f, 0.0f};
    Vec3 left_{0.0f, 1.0f, 0.0f};
    Vec3 up_{0.0f, 0.0f, 1.0f};
};

} // namespace frostsoulx::spatial
