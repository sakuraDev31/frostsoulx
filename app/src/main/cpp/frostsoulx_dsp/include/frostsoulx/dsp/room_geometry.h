#pragma once

#include <algorithm>
#include <cmath>

namespace frostsoulx::dsp {

struct RoomVector3 {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
};

struct RoomGeometry {
    RoomVector3 sourcePosition{0.0f, 0.0f, 1.0f};
    RoomVector3 listenerPosition{0.0f, 0.0f, 0.0f};
    RoomVector3 roomDimensions{10.0f, 10.0f, 3.0f};
    float referenceDistance = 1.0f;
    float maxDistance = 100.0f;
    float directDistanceExponent = 1.0f;
    float reflectionDistanceExponent = 0.5f;
    float reflectionBalance = 1.0f;
};

inline float roomDistance(const RoomGeometry& geometry) noexcept {
    const float dx = geometry.sourcePosition.x - geometry.listenerPosition.x;
    const float dy = geometry.sourcePosition.y - geometry.listenerPosition.y;
    const float dz = geometry.sourcePosition.z - geometry.listenerPosition.z;
    return std::sqrt(dx * dx + dy * dy + dz * dz);
}

inline float roomDistanceGain(float distance, float referenceDistance, float exponent, float maxDistance) noexcept {
    const float safeReference = std::max(std::isfinite(referenceDistance) ? referenceDistance : 1.0f, 0.01f);
    const float safeDistance = std::clamp(std::isfinite(distance) ? distance : safeReference, safeReference, std::max(maxDistance, safeReference));
    const float safeExponent = std::clamp(std::isfinite(exponent) ? exponent : 1.0f, 0.0f, 2.0f);
    return std::pow(safeReference / safeDistance, safeExponent);
}

}  // namespace frostsoulx::dsp
