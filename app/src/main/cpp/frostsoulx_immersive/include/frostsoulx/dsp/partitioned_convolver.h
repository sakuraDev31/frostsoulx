#pragma once

// -----------------------------------------------------------------------------
// Non-uniform partitioned convolution (NUPC).
//
// Why non-uniform
// ---------------
// Uniform partitioned convolution with block B costs O(P * B log B) per block
// where P = L/B. Small B gives low latency but many partitions; large B is
// cheap but adds latency. A non-uniform scheme uses small partitions for the
// head of the impulse response and geometrically larger partitions for the
// tail, which is where most of the length (and none of the latency
// sensitivity) lives. For a 2 s BRIR at 48 kHz this is typically 3-6x cheaper
// than uniform partitioning at the same latency.
//
// Scheduling / alignment
// ----------------------
// Tier i has block size B_i and covers IR taps [O_i, O_i + L_i).
//
//   * Tier 0 (B_0 = B) is evaluated in the same callback as the input arrives,
//     so it contributes with ZERO algorithmic delay.
//   * Tier i > 0 accumulates B_i/B sub-blocks before it can transform, and the
//     block it then emits corresponds to the oldest sub-block of its window.
//     Its algorithmic delay is therefore exactly D_i = B_i - B.
//
// A delayed convolution with IR taps [O, O+L) equals a convolution with taps
// at offset D + O, so we pin O_i = D_i = B_i - B. The tiers then tile the IR
// with no alignment delay lines and no duplicated work:
//
//     growth = 4, B = 128:
//       tier 0: taps [0,    384)   block  128   delay 0
//       tier 1: taps [384,  1920)  block  512   delay 384
//       tier 2: taps [1920, 8064)  block 2048   delay 1920
//       tier 3: taps [8064, L)     block 8192   delay 8064
//
// Each tier is an overlap-save frequency-delay-line (FDL): FFT size 2*B_i,
// input is the sliding window of the last 2*B_i samples, and the valid output
// is the second half of the inverse transform.
//
// Seamless IR replacement
// -----------------------
// Every tier holds two IR spectrum sets. On swap the engine keeps convolving
// with both (the input FDL is shared, so only the complex MAC is duplicated)
// and equal-power crossfades between them over a fixed number of blocks. There
// is no click, no gap, and no reallocation on the audio thread.
//
// Real-time contract: `process()` performs no allocation, no locking and no
// system calls. All storage is reserved by `prepare()`.
// -----------------------------------------------------------------------------

#include "frostsoulx/dsp/fft.h"
#include "frostsoulx/rt/rt_types.h"

#include <cstddef>
#include <vector>

namespace frostsoulx::dsp {

/// One overlap-save frequency-delay-line tier with dual-IR crossfade support.
class ConvolutionTier {
public:
    /// Configure the tier. `blockSize` must be a power of two.
    /// `maxTaps` bounds the IR segment length this tier will ever hold.
    /// `subBlock` is the granularity at which `pushSubBlock`/`readSubBlock`
    /// are called (tier 0 uses subBlock == blockSize).
    void prepare(std::size_t blockSize, std::size_t maxTaps, std::size_t subBlock, bool immediate);

    /// Replace the IR segment. Real-time safe as long as `taps <= maxTaps`
    /// given to `prepare`; performs FFTs, so call from the control thread
    /// (the engine double-buffers and hands over via an atomic flag).
    void loadIr(const float* ir, std::size_t taps);

    /// Begin crossfading from the active IR set to the freshly loaded one.
    void beginCrossfade(std::size_t blocks) noexcept;

    /// Feed `subBlock` input samples and read `subBlock` output samples.
    /// For `immediate` tiers the returned samples reflect the input just
    /// pushed; otherwise they are delayed by exactly `blockSize` samples.
    void processSubBlock(const float* FSX_RESTRICT in, float* FSX_RESTRICT out) noexcept;

    void reset() noexcept;

    std::size_t blockSize() const noexcept { return blockSize_; }
    std::size_t numPartitions() const noexcept { return partitions_; }
    /// Algorithmic delay contributed by this tier: B_i - subBlock (0 for tier 0).
    std::size_t latencySamples() const noexcept {
        return immediate_ ? 0 : (blockSize_ - subBlock_);
    }
    bool configured() const noexcept { return configured_; }
    bool active() const noexcept { return partitions_ > 0; }

private:
    void transformAndAccumulate() noexcept;

    RealFft fft_;
    std::size_t blockSize_ = 0;
    std::size_t fftSize_ = 0;
    std::size_t specFloats_ = 0;
    std::size_t maxPartitions_ = 0;
    std::size_t partitions_ = 0;
    std::size_t subBlock_ = 0;
    bool immediate_ = true;
    bool configured_ = false;

    // Frequency delay line of input spectra (ring of `maxPartitions_`).
    std::vector<float> fdl_;
    std::size_t fdlWrite_ = 0;

    // Two IR spectrum banks: [bank][partition][specFloats]
    std::vector<float> irSpec_[2];
    std::size_t irPartitions_[2] = {0, 0};
    int activeBank_ = 0;

    // Crossfade state.
    std::size_t fadeBlocks_ = 0;
    std::size_t fadeCounter_ = 0;

    std::vector<float> window_;   // sliding input window, 2*B
    std::vector<float> accumA_;   // spectrum accumulator (active bank)
    std::vector<float> accumB_;   // spectrum accumulator (incoming bank)
    std::vector<float> timeA_;    // ifft scratch
    std::vector<float> timeB_;
    std::vector<float> pending_;    // emission buffer, B samples
    std::vector<float> padScratch_; // zero-padded IR partition staging
    std::size_t fillPos_ = 0;       // sub-block accumulation position
    std::size_t emitPos_ = 0;
};

/// Single-channel non-uniform partitioned convolver.
class NonUniformConvolver {
public:
    struct Config {
        std::size_t headBlock = 128;   ///< tier 0 block size (power of two)
        std::size_t maxTaps = 96000;   ///< longest IR supported
        std::size_t maxTiers = 4;      ///< tier count cap
        std::size_t growth = 4;        ///< block-size multiplier per tier
        std::size_t crossfadeBlocks = 4;
    };

    /// Allocates all working storage. Not real-time safe.
    bool prepare(const Config& cfg);

    /// Load an impulse response (control thread). `taps` is clamped to maxTaps.
    /// Triggers a crossfade from the previously active IR.
    void loadIr(const float* ir, std::size_t taps);

    /// Process exactly `headBlock` samples. Real-time safe.
    void processBlock(const float* FSX_RESTRICT in, float* FSX_RESTRICT out) noexcept;

    void reset() noexcept;

    std::size_t blockSize() const noexcept { return cfg_.headBlock; }
    std::size_t latencySamples() const noexcept { return 0; }
    std::size_t activeTaps() const noexcept { return activeTaps_; }
    std::size_t tierCount() const noexcept { return tiers_.size(); }
    bool ready() const noexcept { return ready_; }

private:
    Config cfg_{};
    bool ready_ = false;
    std::size_t activeTaps_ = 0;

    struct TierLayout {
        std::size_t block = 0;
        std::size_t offset = 0;
        std::size_t length = 0;
    };

    std::vector<ConvolutionTier> tiers_;
    std::vector<TierLayout> layout_;
    std::vector<float> mix_;  // per-tier output scratch (headBlock)
};

/// Multi-channel (MIMO) convolver: `inputs` x `outputs` IR matrix.
/// Used for BRIR rendering (2 in x 2 out), virtual-speaker binauralisation and
/// Ambisonics decoding, where every input feeds every output through its own
/// impulse response.
class MimoConvolver {
public:
    bool prepare(std::size_t numInputs, std::size_t numOutputs,
                 const NonUniformConvolver::Config& cfg);

    /// Load the IR for path (in -> out).
    void loadIr(std::size_t in, std::size_t out, const float* ir, std::size_t taps);

    /// `in` and `out` are arrays of channel pointers, each with `blockSize()`
    /// samples. Output buffers are overwritten.
    void processBlock(const float* const* in, float* const* out) noexcept;

    void reset() noexcept;

    std::size_t blockSize() const noexcept { return cfg_.headBlock; }
    std::size_t numInputs() const noexcept { return numInputs_; }
    std::size_t numOutputs() const noexcept { return numOutputs_; }
    bool ready() const noexcept { return ready_; }

private:
    NonUniformConvolver& path(std::size_t in, std::size_t out) noexcept {
        return paths_[in * numOutputs_ + out];
    }

    NonUniformConvolver::Config cfg_{};
    std::size_t numInputs_ = 0;
    std::size_t numOutputs_ = 0;
    bool ready_ = false;
    std::vector<NonUniformConvolver> paths_;
    std::vector<float> scratch_;
};

} // namespace frostsoulx::dsp
