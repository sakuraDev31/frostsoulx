#pragma once

#include "frostsoulx/spatial/geometry.h"
#include "frostsoulx/spatial/hrtf.h"
#include "frostsoulx/spatial/space_profile.h"

#include <cstddef>
#include <vector>

namespace frostsoulx::spatial {

/// Complete stereo Binaural Room Impulse Response (BRIR).
struct StereoBrir {
    std::vector<float> left;
    std::vector<float> right;
    double sampleRate = 48000.0;
    std::size_t taps = 0;

    bool valid() const noexcept {
        return taps > 0 && left.size() >= taps && right.size() >= taps;
    }
};

/// Configuration for physical RIR / BRIR generation.
struct RirGeneratorConfig {
    double sampleRate = 48000.0;
    /// Total length of the generated BRIR in samples (e.g. 16384, 32768, 48000).
    std::size_t maxTaps = 32768;
    /// Maximum reflection order for the 3D Image Source Method (ISM).
    int maxIsmOrder = 3;
    /// Enable physically modeled diffuse reverberant tail.
    bool enableDiffuseTail = true;
    /// Energy factor of the diffuse reverberation tail in [0, 1].
    float diffuseEnergyRatio = 0.5f;
    /// Head radius for ITD calculation (metres).
    float headRadius = rt::kHeadRadius;
};

/// A discrete physical acoustic propagation path (direct sound or geometric reflection).
struct GeometricReflectionPath {
    int order = 0;                  ///< 0 = direct path, 1 = first-order reflection, >=2 = higher order
    float distanceMeters = 0.0f;    ///< Total acoustic path distance
    float delaySeconds = 0.0f;      ///< Propagation delay tau = distance / c
    SphericalCoord arrivalDirection;///< Angle of arrival at listener in local coordinates
    std::array<float, kNumAcousticBands> bandGains = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f}; ///< Frequency-dependent absorption
    float broadbandGain = 1.0f;     ///< 1/d spherical spreading and reflection loss
    bool isScattered = false;       ///< Diffuse reflection flag for irregular surfaces
};

/// Physical Room Impulse Response (RIR) and Binaural RIR (BRIR) generator.
///
/// Implements:
/// 1. Offline geometric reflection path generator (direct path, 1st order,
///    higher-order paths, distance, delay, absorption, angle of arrival).
/// 2. 3D Image Source Method (ISM) for early specular reflections with
///    frequency-dependent wall absorption and air attenuation.
/// 3. Binaural spatialization: each reflection is spatialized through the
///    anechoic HRTF database matching the direction of arrival at the listener.
/// 4. Physically-modeled statistical diffuse tail derived from Eyring/Sabine
///    reverberation times and room volume.
///
/// The output is a pure FIR stereo impulse response for direct consumption
/// by NonUniformConvolver / MimoConvolver (pure linear convolution).
class RirGenerator {
public:
    explicit RirGenerator(const RirGeneratorConfig& cfg = {});

    const RirGeneratorConfig& config() const noexcept { return cfg_; }
    void setConfig(const RirGeneratorConfig& cfg) { cfg_ = cfg; }

    /// Calculate all geometric reflection paths (direct + specular + scattered).
    std::vector<GeometricReflectionPath> calculateReflectionPaths(const SpaceProfile& space) const;

    /// Generate full stereo BRIR for a given space profile and HRTF database.
    StereoBrir generateBrir(const SpaceProfile& space, const HrtfDatabase& hrtf) const;

    /// Generate full stereo BRIR using the internal default parametric HRTF set.
    StereoBrir generateBrir(const SpaceProfile& space) const;

private:
    RirGeneratorConfig cfg_{};
};

} // namespace frostsoulx::spatial
