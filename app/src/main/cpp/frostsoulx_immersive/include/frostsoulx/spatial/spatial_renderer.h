#pragma once

// -----------------------------------------------------------------------------
// Binaural spatial renderer: the stage that actually wires the DSP modules
// together into a working signal path.
//
//   stereo PCM
//     -> two virtual source objects (width-dependent azimuth)
//     -> HOA encode                      (AmbisonicEncoder)
//     -> sound-field rotation            (AmbisonicRotator, head tracking)
//     -> HOA -> binaural convolution     (MimoConvolver + HrtfDatabase)
//     -> stereo out
//
// Why the HOA bus and not one HRTF pair per source
// ------------------------------------------------
// Decoding the HOA bus to a virtual loudspeaker array and binauralising each
// speaker would cost `numSpeakers * 2` convolutions. Because the decode matrix
// and the speaker HRIRs are both STATIC, they can be collapsed once at
// prepare() time into a single set of `hoaChannels * 2` filters:
//
//     F[c][ear] = sum_s  D[s][c] * hrir[s][ear]
//
// Head rotation still works because it is applied to the HOA bus BEFORE these
// filters, so the collapse costs nothing in flexibility. For 1st order this is
// 8 convolutions instead of 24 for a 12-speaker array, and the cost no longer
// depends on the virtual array size at all.
//
// VBAP is used for discrete point-source placement (`renderObject`) and for
// the discrete-speaker output mode, where HOA order would limit sharpness.
//
// Latency: exactly one render block (`blockSize()`), reported by
// `latencySamples()`. The convolver itself is zero-latency; the delay comes
// only from the block adapter that lets `process()` accept arbitrary frame
// counts from the host.
//
// Real-time contract: `process()` performs no allocation, no locking and no
// system calls. All storage is reserved by `prepare()`.
// -----------------------------------------------------------------------------

#include "frostsoulx/dsp/partitioned_convolver.h"
#include "frostsoulx/rt/rt_types.h"
#include "frostsoulx/spatial/ambisonics.h"
#include "frostsoulx/spatial/geometry.h"
#include "frostsoulx/spatial/hrtf.h"

#include <cstddef>
#include <vector>

namespace frostsoulx::spatial {

/// Virtual speaker array used to build the HOA -> binaural filters.
enum class VirtualArray {
    Cube8,      ///< cheapest, adequate for 1st order
    Dodeca12,   ///< default; good through 2nd order
    Sphere26,   ///< highest accuracy, 3rd order
};

struct SpatialRendererConfig {
    int ambisonicOrder = 2;
    VirtualArray array = VirtualArray::Dodeca12;
    std::size_t hrirTaps = 128;     ///< power of two, 64..512
    std::size_t renderBlock = 128;  ///< power of two; also the added latency
    float azimuthStepDeg = 10.0f;
    float elevationStepDeg = 15.0f;
    bool maxRe = true;
};

class SpatialRenderer {
public:
    /// Allocate and build the HRTF set, decoder and collapsed filters.
    /// Not real-time safe. Returns false on invalid configuration.
    bool prepare(double sampleRate, int maxFrames, const SpatialRendererConfig& cfg = {});

    /// Clear all filter/FIFO state without reallocating. Real-time safe.
    void reset() noexcept;

    /// Stereo width of the two virtual sources, [0, 1].
    /// 0 collapses both to the front (mono image), 1 places them at +/-90 deg.
    /// Smoothed internally; safe to call from the control thread.
    void setStereoWidth(float width) noexcept;

    /// Dry/spatialised balance, [0, 1]. 0 is fully dry (bit-transparent apart
    /// from the renderer's fixed latency), 1 is fully spatialised.
    /// The dry path is delay-matched to the wet path internally, so blending
    /// never comb-filters. Smoothed; safe from the control thread.
    void setSpatialBlend(float blend) noexcept;

    /// Head orientation for sound-field rotation. Control thread only
    /// (rebuilds the rotation matrices); `process()` reads the result.
    void setHeadOrientation(const HeadOrientation& o) noexcept;

    /// Process interleaved stereo in place. `frames` may be any value in
    /// [0, maxFrames]. Real-time safe.
    void process(float* FSX_RESTRICT interleavedStereo, int frames) noexcept;

    /// Direct object panning gains over the virtual array (VBAP). Exposed for
    /// the discrete-speaker path and for tests. Real-time safe.
    /// `gains` receives `numVirtualSpeakers()` values.
    void objectGains(const SphericalCoord& dir, float spread,
                     float* FSX_RESTRICT gains) const noexcept;

    /// Encode a mono object straight into the HOA bus scratch (RT safe).
    /// Returns the number of HOA channels written.
    std::size_t encodeObject(const SphericalCoord& dir, float* FSX_RESTRICT gains) const noexcept;

    bool ready() const noexcept { return ready_; }
    std::size_t blockSize() const noexcept { return block_; }
    /// Algorithmic latency added by this stage, in samples.
    std::size_t latencySamples() const noexcept { return ready_ ? block_ : 0; }
    std::size_t hoaChannels() const noexcept { return hoaChannels_; }
    std::size_t numVirtualSpeakers() const noexcept { return layout_.size(); }
    int order() const noexcept { return order_; }
    double sampleRate() const noexcept { return sampleRate_; }
    const HrtfDatabase& hrtf() const noexcept { return hrtf_; }
    const VbapPanner& vbap() const noexcept { return vbap_; }
    /// Broadband gain applied after the binaural filters to normalise the
    /// decode + HRTF chain back to unity for a centred source.
    float normalizationGain() const noexcept { return outputGain_; }

private:
    void renderBlock() noexcept;
    void updateSourceGains() noexcept;

    bool ready_ = false;
    double sampleRate_ = 0.0;
    int order_ = 2;
    std::size_t hoaChannels_ = 9;
    std::size_t block_ = 128;
    float outputGain_ = 1.0f;

    HrtfDatabase hrtf_;
    SpeakerLayout layout_;
    AmbisonicDecoder decoder_;
    AmbisonicEncoder encoder_;
    AmbisonicRotator rotator_;
    VbapPanner vbap_;

    // hoaChannels_ inputs -> 2 binaural outputs.
    dsp::MimoConvolver binaural_;

    // Smoothed stereo width -> virtual source azimuths.
    float widthTarget_ = 0.5f;
    float widthCurrent_ = 0.5f;
    float widthCoeff_ = 1.0f;

    // Dry/wet spatial blend, ramped across each render block.
    float blendTarget_ = 1.0f;
    float blendCurrent_ = 1.0f;


    // Encoding gains for the left/right virtual sources.
    std::vector<float> gainsL_;
    std::vector<float> gainsR_;

    // HOA bus, one buffer per channel, `block_` samples each.
    std::vector<float> hoaStorage_;
    std::vector<float*> hoaPtrs_;
    std::vector<const float*> hoaConstPtrs_;

    // Binaural output buffers.
    std::vector<float> outStorage_;
    std::vector<float*> outPtrs_;

    // Block adapter FIFOs (interleaved stereo).
    std::vector<float> inFifo_;
    std::vector<float> outFifo_;
    std::size_t inFill_ = 0;
    std::size_t outAvail_ = 0;
    std::size_t outRead_ = 0;

    mutable std::vector<float> scratchGains_;
};

} // namespace frostsoulx::spatial
