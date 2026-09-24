#include "frostsoulx/spatial/spatial_source.h"

#include <cmath>

namespace frostsoulx::spatial {

SourceTrajectory TrajectoryFactory::createLeftToRight(float span, float distanceFront,
                                                     float height, float durationSec) {
    SourceTrajectory traj;
    constexpr int kSteps = 32;
    traj.waypoints.reserve(kSteps + 1);

    for (int i = 0; i <= kSteps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(kSteps);
        const float time = t * durationSec;
        const float x = -0.5f * span + t * span; // Left (-) to Right (+)
        const Coord3D userPos{x, height, distanceFront};

        TrajectoryWaypoint wp;
        wp.timeSeconds = time;
        wp.position = userPos.toEngineCoords();
        traj.waypoints.push_back(wp);
    }
    return traj;
}

SourceTrajectory TrajectoryFactory::createFrontToBack(float span, float xOffset,
                                                     float height, float durationSec) {
    SourceTrajectory traj;
    constexpr int kSteps = 32;
    traj.waypoints.reserve(kSteps + 1);

    for (int i = 0; i <= kSteps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(kSteps);
        const float time = t * durationSec;
        const float z = 0.5f * span - t * span; // Front (+) to Back (-)
        const Coord3D userPos{xOffset, height, z};

        TrajectoryWaypoint wp;
        wp.timeSeconds = time;
        wp.position = userPos.toEngineCoords();
        traj.waypoints.push_back(wp);
    }
    return traj;
}

SourceTrajectory TrajectoryFactory::createBackToFront(float span, float xOffset,
                                                     float height, float durationSec) {
    SourceTrajectory traj;
    constexpr int kSteps = 32;
    traj.waypoints.reserve(kSteps + 1);

    for (int i = 0; i <= kSteps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(kSteps);
        const float time = t * durationSec;
        const float z = -0.5f * span + t * span; // Back (-) to Front (+)
        const Coord3D userPos{xOffset, height, z};

        TrajectoryWaypoint wp;
        wp.timeSeconds = time;
        wp.position = userPos.toEngineCoords();
        traj.waypoints.push_back(wp);
    }
    return traj;
}

SourceTrajectory TrajectoryFactory::createHorizontalArc(float radius, float startAzDeg,
                                                       float endAzDeg, float height,
                                                       float durationSec) {
    SourceTrajectory traj;
    constexpr int kSteps = 32;
    traj.waypoints.reserve(kSteps + 1);

    for (int i = 0; i <= kSteps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(kSteps);
        const float time = t * durationSec;
        const float az = (startAzDeg + t * (endAzDeg - startAzDeg)) * rt::kDegToRad;

        // In user coordinates: x = R*sin(az), z = R*cos(az)
        const float x = radius * std::sin(az);
        const float z = radius * std::cos(az);
        const Coord3D userPos{x, height, z};

        TrajectoryWaypoint wp;
        wp.timeSeconds = time;
        wp.position = userPos.toEngineCoords();
        traj.waypoints.push_back(wp);
    }
    return traj;
}

SourceTrajectory TrajectoryFactory::createVerticalArcOverhead(float radius, float durationSec) {
    SourceTrajectory traj;
    constexpr int kSteps = 32;
    traj.waypoints.reserve(kSteps + 1);

    // Front (z = +R, y = 0) -> Overhead (z = 0, y = +R) -> Back (z = -R, y = 0)
    for (int i = 0; i <= kSteps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(kSteps);
        const float time = t * durationSec;
        const float angle = t * rt::kPi; // 0 to pi

        const float z = radius * std::cos(angle);
        const float y = radius * std::sin(angle);
        const Coord3D userPos{0.0f, y, z};

        TrajectoryWaypoint wp;
        wp.timeSeconds = time;
        wp.position = userPos.toEngineCoords();
        traj.waypoints.push_back(wp);
    }
    return traj;
}

SourceTrajectory TrajectoryFactory::createOrbital3D(float radius, float inclinationDeg,
                                                   float durationSec) {
    SourceTrajectory traj;
    constexpr int kSteps = 48;
    traj.waypoints.reserve(kSteps + 1);

    const float inc = inclinationDeg * rt::kDegToRad;
    const float cosInc = std::cos(inc);
    const float sinInc = std::sin(inc);

    for (int i = 0; i <= kSteps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(kSteps);
        const float time = t * durationSec;
        const float phi = t * rt::kTwoPi;

        const float x = radius * std::sin(phi);
        const float zPlane = radius * std::cos(phi);
        const float z = zPlane * cosInc;
        const float y = zPlane * sinInc;
        const Coord3D userPos{x, y, z};

        TrajectoryWaypoint wp;
        wp.timeSeconds = time;
        wp.position = userPos.toEngineCoords();
        traj.waypoints.push_back(wp);
    }
    return traj;
}

SourceTrajectory TrajectoryFactory::createCircular(float radius, float height,
                                                  float durationSec) {
    SourceTrajectory traj;
    constexpr int kSteps = 36;
    traj.waypoints.reserve(kSteps + 1);

    for (int i = 0; i <= kSteps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(kSteps);
        const float time = t * durationSec;
        const float phi = t * rt::kTwoPi;

        const float x = radius * std::sin(phi);
        const float z = radius * std::cos(phi);
        const Coord3D userPos{x, height, z};

        TrajectoryWaypoint wp;
        wp.timeSeconds = time;
        wp.position = userPos.toEngineCoords();
        traj.waypoints.push_back(wp);
    }
    return traj;
}

SourceTrajectory TrajectoryFactory::createCompoundDemo(float radius, float durationSec) {
    // Compound sequence:
    // 0. LEFT:     x = -radius, y = 0, z = 0
    // 1. FRONT:    x = 0, y = 0, z = +radius
    // 2. OVERHEAD: x = 0, y = +radius, z = 0
    // 3. BACK:     x = 0, y = 0, z = -radius
    // 4. RIGHT:    x = +radius, y = 0, z = 0
    SourceTrajectory traj;
    const std::vector<Coord3D> keypoints = {
        Coord3D{-radius, 0.0f, 0.0f},   // LEFT
        Coord3D{0.0f, 0.0f, radius},    // FRONT
        Coord3D{0.0f, radius, 0.0f},    // OVERHEAD
        Coord3D{0.0f, 0.0f, -radius},   // BACK
        Coord3D{radius, 0.0f, 0.0f}     // RIGHT
    };

    const float segDuration = durationSec / static_cast<float>(keypoints.size() - 1);
    constexpr int kStepsPerSeg = 12;

    for (std::size_t seg = 0; seg < keypoints.size() - 1; ++seg) {
        const Coord3D& p0 = keypoints[seg];
        const Coord3D& p1 = keypoints[seg + 1];

        for (int step = 0; step < kStepsPerSeg; ++step) {
            const float u = static_cast<float>(step) / static_cast<float>(kStepsPerSeg);
            const float time = (seg + u) * segDuration;
            const Coord3D p = p0 + (p1 - p0) * u;

            TrajectoryWaypoint wp;
            wp.timeSeconds = time;
            wp.position = p.toEngineCoords();
            traj.waypoints.push_back(wp);
        }
    }

    // Add final keypoint
    TrajectoryWaypoint finalWp;
    finalWp.timeSeconds = durationSec;
    finalWp.position = keypoints.back().toEngineCoords();
    traj.waypoints.push_back(finalWp);

    return traj;
}

} // namespace frostsoulx::spatial
