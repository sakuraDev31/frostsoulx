#include "frostsoulx/dsp/partitioned_convolver.h"

#include <algorithm>
#include <cmath>

namespace frostsoulx::dsp {

// ---------------------------------------------------------------------------
// ConvolutionTier
// ---------------------------------------------------------------------------

void ConvolutionTier::prepare(std::size_t blockSize, std::size_t maxTaps,
                              std::size_t subBlock, bool immediate) {
    partitions_ = 0;
    if (!rt::isPow2(blockSize) || blockSize < 2) return;
    if (maxTaps == 0 || subBlock == 0 || subBlock > blockSize) return;
    if (blockSize % subBlock != 0) return;

    blockSize_ = blockSize;
    fftSize_ = blockSize * 2;
    subBlock_ = subBlock;
    immediate_ = immediate;

    fft_.resize(fftSize_);
    specFloats_ = fft_.spectrumFloats();

    maxPartitions_ = (maxTaps + blockSize_ - 1) / blockSize_;
    if (maxPartitions_ == 0) maxPartitions_ = 1;

    fdl_.assign(maxPartitions_ * specFloats_, 0.0f);
    for (int b = 0; b < 2; ++b) {
        irSpec_[b].assign(maxPartitions_ * specFloats_, 0.0f);
        irPartitions_[b] = 0;
    }

    window_.assign(fftSize_, 0.0f);
    accumA_.assign(specFloats_, 0.0f);
    accumB_.assign(specFloats_, 0.0f);
    timeA_.assign(fftSize_, 0.0f);
    timeB_.assign(fftSize_, 0.0f);
    pending_.assign(blockSize_, 0.0f);
    padScratch_.assign(fftSize_, 0.0f);

    fdlWrite_ = 0;
    activeBank_ = 0;
    fadeBlocks_ = 0;
    fadeCounter_ = 0;
    fillPos_ = 0;
    // Tier 0 emits the block it just consumed; deeper tiers start by emitting
    // silence until their first transform completes.
    emitPos_ = immediate_ ? blockSize_ : 0;
    configured_ = true;
}

void ConvolutionTier::loadIr(const float* ir, std::size_t taps) {
    if (!configured_) return;

    const int target = activeBank_ ^ 1;
    std::fill(irSpec_[target].begin(), irSpec_[target].end(), 0.0f);

    if (ir == nullptr || taps == 0) {
        irPartitions_[target] = 0;
        return;
    }

    std::size_t parts = (taps + blockSize_ - 1) / blockSize_;
    parts = std::min(parts, maxPartitions_);
    irPartitions_[target] = parts;

    // Each IR partition is zero-padded to the full FFT size (overlap-save).
    for (std::size_t p = 0; p < parts; ++p) {
        std::fill(padScratch_.begin(), padScratch_.end(), 0.0f);
        const std::size_t begin = p * blockSize_;
        const std::size_t count = std::min(blockSize_, taps - begin);
        std::copy(ir + begin, ir + begin + count, padScratch_.begin());
        fft_.forward(padScratch_.data(), irSpec_[target].data() + p * specFloats_);
    }
}

void ConvolutionTier::beginCrossfade(std::size_t blocks) noexcept {
    if (!configured_) return;
    const int target = activeBank_ ^ 1;
    if (blocks == 0 || irPartitions_[activeBank_] == 0) {
        // Nothing to fade from -- switch instantly.
        activeBank_ = target;
        partitions_ = irPartitions_[activeBank_];
        fadeBlocks_ = 0;
        fadeCounter_ = 0;
        return;
    }
    fadeBlocks_ = blocks;
    fadeCounter_ = blocks;
    partitions_ = std::max(irPartitions_[activeBank_], irPartitions_[target]);
}

void ConvolutionTier::reset() noexcept {
    if (!configured_) return;
    std::fill(fdl_.begin(), fdl_.end(), 0.0f);
    std::fill(window_.begin(), window_.end(), 0.0f);
    std::fill(pending_.begin(), pending_.end(), 0.0f);
    fdlWrite_ = 0;
    fillPos_ = 0;
    emitPos_ = immediate_ ? blockSize_ : 0;
    // Finish any in-flight crossfade immediately; state is being cleared anyway.
    if (fadeCounter_ > 0) {
        activeBank_ ^= 1;
        partitions_ = irPartitions_[activeBank_];
        fadeCounter_ = 0;
        fadeBlocks_ = 0;
    }
}

void ConvolutionTier::transformAndAccumulate() noexcept {
    const bool fading = fadeCounter_ > 0;
    const int bankA = activeBank_;
    const int bankB = activeBank_ ^ 1;
    const std::size_t pa = irPartitions_[bankA];
    const std::size_t pb = fading ? irPartitions_[bankB] : 0;

    // window_ holds the newest 2*B samples: [previous B | current B].
    float* spec = fdl_.data() + fdlWrite_ * specFloats_;
    fft_.forward(window_.data(), spec);

    if (pa == 0 && pb == 0) {
        std::fill(pending_.begin(), pending_.end(), 0.0f);
        fdlWrite_ = (fdlWrite_ + 1) % maxPartitions_;
        return;
    }

    std::fill(accumA_.begin(), accumA_.end(), 0.0f);
    if (fading) std::fill(accumB_.begin(), accumB_.end(), 0.0f);

    const std::size_t bins = specFloats_ / 2;
    const std::size_t pmax = std::max(pa, pb);

    for (std::size_t p = 0; p < pmax; ++p) {
        // Walk the frequency delay line backwards from the newest spectrum.
        const std::size_t idx = (fdlWrite_ + maxPartitions_ - p) % maxPartitions_;
        const float* x = fdl_.data() + idx * specFloats_;
        if (p < pa) {
            rt::cplxMulAccumulate(accumA_.data(), x,
                                  irSpec_[bankA].data() + p * specFloats_, bins);
        }
        if (p < pb) {
            rt::cplxMulAccumulate(accumB_.data(), x,
                                  irSpec_[bankB].data() + p * specFloats_, bins);
        }
    }

    fdlWrite_ = (fdlWrite_ + 1) % maxPartitions_;

    fft_.inverse(accumA_.data(), timeA_.data());

    // Overlap-save: the valid linear-convolution output is the SECOND half.
    const float* validA = timeA_.data() + blockSize_;
    if (!fading) {
        rt::vecCopy(pending_.data(), validA, blockSize_);
        return;
    }

    fft_.inverse(accumB_.data(), timeB_.data());
    const float* validB = timeB_.data() + blockSize_;

    // Equal-power crossfade, interpolated across the block so the transition is
    // continuous at block boundaries as well as within them.
    const float t0 = 1.0f - static_cast<float>(fadeCounter_) / static_cast<float>(fadeBlocks_);
    const float t1 = 1.0f - static_cast<float>(fadeCounter_ - 1) / static_cast<float>(fadeBlocks_);
    const float inv = 1.0f / static_cast<float>(blockSize_);
    for (std::size_t i = 0; i < blockSize_; ++i) {
        const float t = t0 + (t1 - t0) * (static_cast<float>(i) * inv);
        pending_[i] = validA[i] * std::cos(t * rt::kHalfPi) +
                      validB[i] * std::sin(t * rt::kHalfPi);
    }

    if (--fadeCounter_ == 0) {
        activeBank_ = bankB;
        partitions_ = irPartitions_[activeBank_];
        fadeBlocks_ = 0;
    }
}

void ConvolutionTier::processSubBlock(const float* FSX_RESTRICT in,
                                      float* FSX_RESTRICT out) noexcept {
    if (!configured_) return;

    // Always maintain input history, even while this tier carries no IR, so
    // that a later IR load starts with a correct sliding window.
    std::copy(in, in + subBlock_,
              window_.begin() + static_cast<std::ptrdiff_t>(blockSize_ + fillPos_));
    fillPos_ += subBlock_;

    if (fillPos_ >= blockSize_) {
        if (partitions_ > 0) {
            transformAndAccumulate();
        } else {
            // No IR: keep the FDL coherent without paying for the MAC.
            fft_.forward(window_.data(), fdl_.data() + fdlWrite_ * specFloats_);
            fdlWrite_ = (fdlWrite_ + 1) % maxPartitions_;
            std::fill(pending_.begin(), pending_.end(), 0.0f);
        }
        // Slide: the current half becomes the previous half.
        std::copy(window_.begin() + static_cast<std::ptrdiff_t>(blockSize_),
                  window_.end(), window_.begin());
        fillPos_ = 0;
        emitPos_ = 0;
    }

    if (emitPos_ + subBlock_ <= blockSize_) {
        rt::vecCopy(out, pending_.data() + emitPos_, subBlock_);
        emitPos_ += subBlock_;
    } else {
        rt::vecClear(out, subBlock_);
    }
}

// ---------------------------------------------------------------------------
// NonUniformConvolver
// ---------------------------------------------------------------------------

bool NonUniformConvolver::prepare(const Config& cfg) {
    ready_ = false;
    tiers_.clear();
    layout_.clear();

    if (!rt::isPow2(cfg.headBlock) || cfg.headBlock < 16) return false;
    if (cfg.maxTaps == 0 || cfg.maxTiers == 0) return false;
    if (cfg.growth < 2 || !rt::isPow2(cfg.growth)) return false;

    cfg_ = cfg;

    // ---------------------------------------------------------------------
    // Tier scheduling.
    //
    // A tier with block size B_i fed in sub-blocks of B_0 completes its
    // transform once every B_i/B_0 callbacks, and the overlap-save result it
    // emits on that callback is the output for the OLDEST sub-block of the
    // window. Its algorithmic delay is therefore exactly
    //
    //     D_i = B_i - B_0          (D_0 = 0)
    //
    // Because a delayed convolution with IR segment h[O .. O+L) is equivalent
    // to convolving with taps at offset D + O, we pin the tap offset to the
    // delay: O_i = D_i = B_i - B_0. The tiers then tile the impulse response
    // with no extra alignment delay lines and no redundant work.
    //
    //   growth=4, B_0=128:  [0,384) B=128 | [384,1920) B=512
    //                       [1920,8064) B=2048 | [8064,..) B=8192
    // ---------------------------------------------------------------------
    const std::size_t head = cfg.headBlock;
    std::size_t block = head;
    std::size_t offset = 0;
    for (std::size_t t = 0; t < cfg.maxTiers && offset < cfg.maxTaps; ++t) {
        const std::size_t nextBlock = block * cfg.growth;
        const std::size_t nextOffset = nextBlock - head;
        const bool last = (t + 1 == cfg.maxTiers) || (nextOffset >= cfg.maxTaps);
        const std::size_t end = last ? cfg.maxTaps : nextOffset;
        if (end <= offset) break;

        TierLayout l;
        l.block = block;
        l.offset = offset;
        l.length = end - offset;
        layout_.push_back(l);

        offset = end;
        block = nextBlock;
        if (last) break;
    }
    if (layout_.empty()) return false;

    tiers_.resize(layout_.size());
    for (std::size_t t = 0; t < layout_.size(); ++t) {
        tiers_[t].prepare(layout_[t].block, layout_[t].length, head, /*immediate=*/t == 0);
        if (!tiers_[t].configured()) return false;
        if (tiers_[t].latencySamples() != layout_[t].offset) return false;  // invariant
    }

    mix_.assign(head, 0.0f);
    activeTaps_ = 0;
    ready_ = true;
    return true;
}

void NonUniformConvolver::loadIr(const float* ir, std::size_t taps) {
    if (!ready_) return;
    taps = std::min(taps, cfg_.maxTaps);
    activeTaps_ = (ir != nullptr) ? taps : 0;

    for (std::size_t t = 0; t < tiers_.size(); ++t) {
        const TierLayout& l = layout_[t];
        if (ir == nullptr || l.offset >= taps) {
            tiers_[t].loadIr(nullptr, 0);
        } else {
            tiers_[t].loadIr(ir + l.offset, std::min(l.length, taps - l.offset));
        }
    }
    // Crossfade length is expressed in each tier's own blocks, so tail tiers
    // transition more gradually than the head -- which is what we want.
    for (auto& tier : tiers_) tier.beginCrossfade(cfg_.crossfadeBlocks);
}

void NonUniformConvolver::processBlock(const float* FSX_RESTRICT in,
                                       float* FSX_RESTRICT out) noexcept {
    const std::size_t n = cfg_.headBlock;
    if (!ready_) {
        rt::vecClear(out, n);
        return;
    }

    rt::vecClear(out, n);
    for (auto& tier : tiers_) {
        tier.processSubBlock(in, mix_.data());
        rt::vecAdd(out, mix_.data(), n);
    }
}

void NonUniformConvolver::reset() noexcept {
    for (auto& tier : tiers_) tier.reset();
}

// ---------------------------------------------------------------------------
// MimoConvolver
// ---------------------------------------------------------------------------

bool MimoConvolver::prepare(std::size_t numInputs, std::size_t numOutputs,
                            const NonUniformConvolver::Config& cfg) {
    ready_ = false;
    if (numInputs == 0 || numOutputs == 0) return false;

    numInputs_ = numInputs;
    numOutputs_ = numOutputs;
    cfg_ = cfg;

    paths_.clear();
    paths_.resize(numInputs * numOutputs);
    for (auto& p : paths_) {
        if (!p.prepare(cfg)) return false;
    }
    scratch_.assign(cfg.headBlock, 0.0f);
    ready_ = true;
    return true;
}

void MimoConvolver::loadIr(std::size_t in, std::size_t out, const float* ir, std::size_t taps) {
    if (!ready_ || in >= numInputs_ || out >= numOutputs_) return;
    path(in, out).loadIr(ir, taps);
}

void MimoConvolver::processBlock(const float* const* in, float* const* out) noexcept {
    if (!ready_) return;
    const std::size_t n = cfg_.headBlock;

    for (std::size_t o = 0; o < numOutputs_; ++o) rt::vecClear(out[o], n);

    for (std::size_t i = 0; i < numInputs_; ++i) {
        for (std::size_t o = 0; o < numOutputs_; ++o) {
            NonUniformConvolver& p = path(i, o);
            if (p.activeTaps() == 0) continue;
            p.processBlock(in[i], scratch_.data());
            rt::vecAdd(out[o], scratch_.data(), n);
        }
    }
}

void MimoConvolver::reset() noexcept {
    for (auto& p : paths_) p.reset();
}

} // namespace frostsoulx::dsp
