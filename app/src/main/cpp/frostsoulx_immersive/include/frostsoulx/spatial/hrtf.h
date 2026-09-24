#pragma once

// -----------------------------------------------------------------------------
// Parametric HRTF / HRIR synthesis and interpolation.
//
// The engine must work with no external SOFA asset, so the default HRTF set is
// synthesised from a physically-motivated structural model rather than shipped
// as data. The model is the classical "structural HRTF" decomposition
// (Brown & Duda 1998, extended with Duda & Martens near-field range
// dependence and Algazi's elevation cues):
//
//   HRIR(azimuth, elevation, range, ear)
//       = ITD delay  (Woodworth-Schlosberg spherical head)
//       * head shadow (single-pole/zero spherical diffraction filter)
//       * near-field range correction (Duda & Martens DVF)
//       * pinna elevation notches (2 delayed negative taps)
//       * torso/shoulder echo (1 delayed positive tap)
//
// This reproduces the dominant binaural localisation cues -- ITD below ~1.5 kHz,
// ILD above it, spectral elevation notches, and near-field ILD boost -- at a
// tiny fraction of the memory a measured database needs.
//
// The synthesised set is stored as a spherical grid of HRIRs. At render time
// `HrtfDatabase::render()` performs barycentric-style bilinear interpolation
// between the four surrounding grid points in the MINIMUM-PHASE + PURE-DELAY
// domain: magnitudes are interpolated on the four neighbours' HRIRs while the
// broadband ITD is interpolated separately and reapplied via a fractional
// delay. Interpolating the raw HRIRs directly would comb-filter because the
// neighbours' impulses are misaligned in time; separating delay from spectrum
// is what makes dynamic (head-tracked / moving-source) interpolation
// artefact-free.
//
// If a measured HRIR set is available the same container accepts it through
// `loadMeasuredSet()`, so the interpolation machinery is shared.
// -----------------------------------------------------------------------------

#include "frostsoulx/rt/rt_types.h"
#include "frostsoulx/spatial/geometry.h"

#include <cstddef>
#include <vector>

namespace frostsoulx::spatial {

/// A single rendered binaural filter pair plus its broadband delays.
struct HrirPair {
    const float* left = nullptr;
    const float* right = nullptr;
    std::size_t taps = 0;
    float delayLeftSamples = 0.0f;
    float delayRightSamples = 0.0f;
};

struct HrtfModelParams {
    float headRadius = rt::kHeadRadius;  ///< metres
    float pinnaGain = 0.55f;             ///< elevation notch depth
    float torsoGain = 0.18f;             ///< shoulder reflection level
    bool enableNearField = true;
    bool enablePinna = true;
    bool enableTorso = true;
};

/// Spherical grid of HRIRs with dynamic bilinear interpolation.
class HrtfDatabase {
public:
    /// Build the parametric set. `irTaps` must be a power of two (64..512 is
    /// the useful range; 128 taps at 48 kHz = 2.7 ms covers pinna + torso).
    /// Grid resolution is (360/azStep) x ((90-(-90))/elStep + 1).
    bool buildParametric(double sampleRate, std::size_t irTaps,
                         float azStepDeg = 10.0f, float elStepDeg = 15.0f,
                         const HrtfModelParams& params = {});

    /// Adopt an externally measured set on the same regular grid layout.
    /// `data` is [elevationIndex][azimuthIndex][ear][tap], ears interleaved as
    /// left block then right block per direction.
    bool loadMeasuredSet(double sampleRate, std::size_t irTaps,
                         std::size_t numAz, std::size_t numEl,
                         float azStepDeg, float elStepDeg,
                         const float* data, const float* itdSamples);

    /// Interpolate the HRIR for an arbitrary direction/range into `outLeft` /
    /// `outRight` (each `irTaps()` samples). Real-time safe: no allocation.
    /// Returns the broadband ITD split across the two ears, which the caller
    /// applies with a fractional delay line.
    HrirPair render(const SphericalCoord& dir,
                    float* FSX_RESTRICT outLeft,
                    float* FSX_RESTRICT outRight) const noexcept;

    std::size_t irTaps() const noexcept { return irTaps_; }
    std::size_t numAzimuth() const noexcept { return numAz_; }
    std::size_t numElevation() const noexcept { return numEl_; }
    double sampleRate() const noexcept { return sampleRate_; }
    bool valid() const noexcept { return valid_; }

    /// Total HRIR storage in bytes (diagnostics).
    std::size_t memoryBytes() const noexcept {
        return (hrir_.size() + itd_.size()) * sizeof(float);
    }

private:
    const float* gridLeft(std::size_t az, std::size_t el) const noexcept {
        return hrir_.data() + ((el * numAz_ + az) * 2) * irTaps_;
    }
    const float* gridRight(std::size_t az, std::size_t el) const noexcept {
        return hrir_.data() + ((el * numAz_ + az) * 2 + 1) * irTaps_;
    }
    float gridItd(std::size_t az, std::size_t el) const noexcept {
        return itd_[el * numAz_ + az];
    }

    bool valid_ = false;
    double sampleRate_ = 0.0;
    std::size_t irTaps_ = 0;
    std::size_t numAz_ = 0;
    std::size_t numEl_ = 0;
    float azStep_ = 0.0f;
    float elStep_ = 0.0f;
    HrtfModelParams params_{};

    std::vector<float> hrir_;  // [el][az][ear][tap]
    std::vector<float> itd_;   // [el][az] broadband ITD in samples (+ = right lags)
};

// ---------------------------------------------------------------------------
// Analytic building blocks, exposed for unit testing.
// ---------------------------------------------------------------------------

/// Woodworth-Schlosberg spherical-head ITD in seconds for an incidence angle
/// `theta` (radians) between the source direction and the interaural axis.
/// Positive result = additional path length to the far ear.
float woodworthItdSeconds(float theta, float headRadius) noexcept;

/// Head-shadow single-pole/zero coefficient set for an incidence angle.
/// Implements the Brown & Duda spherical-head approximation.
struct HeadShadowCoeffs {
    float b0 = 1.0f, b1 = 0.0f, a1 = 0.0f;
};
HeadShadowCoeffs headShadowFilter(float cosTheta, float headRadius,
                                  double sampleRate) noexcept;

/// Duda & Martens near-field ILD/level correction for a source at `range`
/// metres and incidence `cosTheta`. Returns a linear gain.
float nearFieldGain(float cosTheta, float range, float headRadius) noexcept;

} // namespace frostsoulx::spatial
