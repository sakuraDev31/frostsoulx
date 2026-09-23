#pragma once

// -----------------------------------------------------------------------------
// Higher-Order Ambisonics (HOA) encoding / decoding and VBAP panning.
//
// Conventions
// -----------
// * ACN channel ordering, SN3D normalisation (the AmbiX convention).
//   Channel index for degree l, order m:  ACN = l*(l+1) + m.
// * Orders 0..3 are supported: 1, 4, 9 or 16 channels.
// * The axis convention matches `geometry.h` (+X forward, +Y left, +Z up).
//
// Why HOA in a headphone engine
// -----------------------------
// Rendering every object through its own HRTF pair costs one convolution pair
// per object and scales linearly. Encoding objects into a shared HOA bus and
// decoding that bus once through a fixed virtual-loudspeaker array makes the
// binaural cost CONSTANT regardless of object count, and it is exactly the
// representation head-tracking wants: a listener rotation is a single matrix
// multiply on the HOA bus (sound-field rotation) instead of re-interpolating
// every source's HRTF.
//
// The engine uses both paths -- see `hybrid_renderer.h`. Sources that need
// maximum spatial fidelity (few, prominent, near) go direct-HRTF; the diffuse
// bed and reflections go through HOA. That is the "hybrid" in the renderer.
//
// Decoders
// --------
// * `AmbisonicDecoder` builds a mode-matching (pseudo-inverse) decoder for an
//   arbitrary virtual speaker layout, with optional max-rE weighting which
//   maximises the energy-vector concentration and is the standard choice for
//   perceptually stable imaging above ~700 Hz.
// * `VbapPanner` does 3D vector-base amplitude panning over a triangulated
//   speaker mesh -- used for the discrete-speaker output mode and for placing
//   image-source reflections without HOA order limits.
// -----------------------------------------------------------------------------

#include "frostsoulx/rt/rt_types.h"
#include "frostsoulx/spatial/geometry.h"

#include <array>
#include <cstddef>
#include <vector>

namespace frostsoulx::spatial {

inline constexpr int kMaxAmbisonicOrder = 3;
inline constexpr std::size_t kMaxAmbisonicChannels = 16;  // (3+1)^2

/// Number of ACN channels for an order.
constexpr std::size_t ambisonicChannels(int order) noexcept {
    return static_cast<std::size_t>((order + 1) * (order + 1));
}

/// Evaluate real spherical harmonics (SN3D, ACN order) for a unit direction.
/// `out` must hold at least `ambisonicChannels(order)` floats.
void evaluateSphericalHarmonics(const Vec3& unitDir, int order, float* out) noexcept;

/// max-rE per-degree gains for the given order (Daniel/Zotter).
/// `out` receives one gain per degree l in [0, order].
void maxReGains(int order, float* out) noexcept;

/// Encodes point sources into an HOA bus.
class AmbisonicEncoder {
public:
    void setOrder(int order) noexcept {
        order_ = std::clamp(order, 0, kMaxAmbisonicOrder);
        channels_ = ambisonicChannels(order_);
    }

    int order() const noexcept { return order_; }
    std::size_t channels() const noexcept { return channels_; }

    /// Compute the encoding gains for a direction. Real-time safe.
    void gainsFor(const Vec3& unitDir, float* gains) const noexcept {
        evaluateSphericalHarmonics(unitDir, order_, gains);
    }

private:
    int order_ = 1;
    std::size_t channels_ = 4;
};

/// Virtual/real loudspeaker layout on the unit sphere.
struct SpeakerLayout {
    std::vector<SphericalCoord> positions;

    void clear() { positions.clear(); }
    std::size_t size() const noexcept { return positions.size(); }

    /// Near-uniform spherical layouts suitable for binaural virtualisation.
    /// Higher counts reduce decoder error but cost one HRTF convolution pair
    /// each, so the engine defaults to the 12-point layout for order 2-3.
    static SpeakerLayout cube8();
    static SpeakerLayout dodeca12();
    static SpeakerLayout sphere26();
    /// Standard 7.1.4 immersive layout (for discrete-speaker output).
    static SpeakerLayout immersive714();
};

/// Mode-matching HOA decoder for an arbitrary layout.
class AmbisonicDecoder {
public:
    /// Build the decode matrix. Not real-time safe.
    /// `maxRe` applies max-rE degree weighting.
    bool prepare(int order, const SpeakerLayout& layout, bool maxRe = true);

    /// Decode one sample frame: `hoa` has `hoaChannels()` values, `out`
    /// receives `numSpeakers()` values. Real-time safe.
    void decodeFrame(const float* FSX_RESTRICT hoa, float* FSX_RESTRICT out) const noexcept;

    /// Decode a block of `frames` samples. `hoa` and `out` are arrays of
    /// channel pointers. Real-time safe.
    void decodeBlock(const float* const* hoa, float* const* out,
                     std::size_t frames) const noexcept;

    std::size_t numSpeakers() const noexcept { return numSpeakers_; }
    std::size_t hoaChannels() const noexcept { return hoaChannels_; }
    int order() const noexcept { return order_; }
    bool ready() const noexcept { return ready_; }

    /// Row-major [speaker][hoaChannel] decode matrix (diagnostics/tests).
    const std::vector<float>& matrix() const noexcept { return matrix_; }

private:
    bool ready_ = false;
    int order_ = 1;
    std::size_t numSpeakers_ = 0;
    std::size_t hoaChannels_ = 0;
    std::vector<float> matrix_;
};

/// Sound-field rotation for head tracking.
///
/// Rotating the HOA bus is O(channels^2) per sample frame at worst, but since
/// the rotation matrix is block-diagonal per degree it is only
/// 1 + 9 + 25 = 35 multiply-adds for 3rd order -- far cheaper than
/// re-interpolating every source HRTF, and it applies to the reverberant field
/// as well as the direct sound.
class AmbisonicRotator {
public:
    void setOrder(int order) noexcept;
    /// Recompute the rotation matrices for a head orientation.
    /// Control thread (uses trig); the engine double-buffers the result.
    void setOrientation(const HeadOrientation& o) noexcept;

    /// Rotate one HOA frame in place. Real-time safe.
    void rotateFrame(float* FSX_RESTRICT hoa) const noexcept;

    bool isIdentity() const noexcept { return identity_; }
    int order() const noexcept { return order_; }

private:
    int order_ = 1;
    std::size_t channels_ = 4;
    bool identity_ = true;
    // Block-diagonal: degree l occupies a (2l+1)^2 block.
    std::array<float, 1> d0_{1.0f};
    std::array<float, 9> d1_{};
    std::array<float, 25> d2_{};
    std::array<float, 49> d3_{};
    mutable std::array<float, kMaxAmbisonicChannels> scratch_{};
};

/// Vector-Base Amplitude Panning over a speaker layout.
///
/// Non-full-sphere layouts
/// -----------------------
/// Real rigs are almost never closed spheres. Two degenerate cases have to be
/// handled explicitly or the panner produces silence:
///
///  * PLANAR layouts (horizontal rings, stereo/quad/5.1 beds) have no valid
///    triangulation at all -- every triple is coplanar with the origin, so the
///    3x3 base matrix is singular. These fall back to classic 2D pair-wise
///    VBAP in the plane the speakers span, with out-of-plane directions
///    projected onto it.
///  * PARTIAL 3D layouts (domes, 7.1.4 -- no floor speakers) triangulate fine
///    but leave a hole in the hull. A direction pointing into the hole has no
///    triplet with all-positive gains, so it must be projected onto the
///    nearest hull boundary instead of being clamped to zero.
class VbapPanner {
public:
    /// Triangulate the layout (convex hull on the unit sphere), or fall back
    /// to a planar 2D ring if the layout is degenerate. Not RT safe.
    bool prepare(const SpeakerLayout& layout);

    /// Compute panning gains for a direction. `gains` receives
    /// `numSpeakers()` values, at most three of which are non-zero
    /// (two for planar layouts). Directions outside the layout's coverage are
    /// projected onto the nearest boundary, never silenced.
    /// Real-time safe.
    void gainsFor(const Vec3& unitDir, float* FSX_RESTRICT gains) const noexcept;

    /// Spread the source across more speakers (0 = point, 1 = fully diffuse).
    /// Implemented as the MDAP multi-direction average, which keeps the
    /// perceived direction while widening the source.
    void gainsForSpread(const Vec3& unitDir, float spread,
                        float* FSX_RESTRICT gains) const noexcept;

    std::size_t numSpeakers() const noexcept { return speakers_.size(); }
    std::size_t numTriplets() const noexcept { return triplets_.size(); }
    /// Adjacent speaker pairs used by the planar fallback.
    std::size_t numPairs() const noexcept { return pairs_.size(); }
    /// True when the layout was degenerate and 2D pair panning is in use.
    bool isPlanar() const noexcept { return planar_; }
    bool ready() const noexcept { return ready_; }

    /// Largest supported layout size for the RT-safe scratch in
    /// `gainsForSpread`.
    static constexpr std::size_t kMaxSpeakers = 128;

private:
    struct Triplet {
        std::size_t a = 0, b = 0, c = 0;
        // Inverse of the 3x3 base matrix, row-major.
        std::array<float, 9> inv{};
    };

    struct Pair {
        std::size_t a = 0, b = 0;
        // Inverse of the 2x2 base matrix (in-plane coords), row-major.
        std::array<float, 4> inv{};
    };

    bool preparePlanar(const SpeakerLayout& layout);
    void planarGains(const Vec3& unitDir, float* FSX_RESTRICT gains) const noexcept;

    bool ready_ = false;
    bool planar_ = false;
    std::vector<Vec3> speakers_;
    std::vector<Triplet> triplets_;
    std::vector<Pair> pairs_;
    // Orthonormal basis of the plane spanned by a degenerate layout.
    Vec3 planeU_{1.0f, 0.0f, 0.0f};
    Vec3 planeV_{0.0f, 1.0f, 0.0f};
};

} // namespace frostsoulx::spatial
