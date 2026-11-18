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

    // Retarget from the current convex filter, not the stale active filter.
    if (fadeCounter_ > 0) {
        const float t = 1.0f - static_cast<float>(fadeCounter_) / static_cast<float>(fadeBlocks_);
        auto& a = irSpec_[activeBank_];
        const auto& b = irSpec_[activeBank_ ^ 1];
        for (std::size_t i = 0; i < a.size(); ++i) a[i] += t * (b[i] - a[i]);
        irPartitions_[activeBank_] = std::max(irPartitions_[0], irPartitions_[1]);
        fadeCounter_ = 0;
    }
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

    // Unity-sum crossfade (IRs are correlated), interpolated across the block so the transition is
    // continuous at block boundaries as well as within them.
    const float t0 = 1.0f - static_cast<float>(fadeCounter_) / static_cast<float>(fadeBlocks_);
    const float t1 = 1.0f - static_cast<float>(fadeCounter_ - 1) / static_cast<float>(fadeBlocks_);
    const float inv = 1.0f / static_cast<float>(blockSize_);
    for (std::size_t i = 0; i < blockSize_; ++i) {
        const float t = t0 + (t1 - t0) * (static_cast<float>(i) * inv);
        pending_[i] = validA[i] + t * (validB[i] - validA[i]);
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

bool MimoConvolver::prepare(std::size_t inputs, std::size_t outputs,
                            const NonUniformConvolver::Config& cfg) {
    ready_ = false;
    if (!inputs || !outputs || inputs > 32 || outputs > 32 ||
        !rt::isPow2(cfg.headBlock) || cfg.headBlock < 16 || !cfg.maxTaps ||
        !cfg.maxTiers || cfg.growth < 2 || !rt::isPow2(cfg.growth)) return false;
    cfg_ = cfg;
    numInputs_ = inputs;
    numOutputs_ = outputs;
    tiers_.clear();
    std::size_t block = cfg.headBlock, offset = 0;
    for (std::size_t t = 0; t < cfg.maxTiers && offset < cfg.maxTaps; ++t) {
        const std::size_t next = block * cfg.growth - cfg.headBlock;
        const std::size_t end = t + 1 == cfg.maxTiers ? cfg.maxTaps : std::min(next, cfg.maxTaps);
        Tier tier;
        tier.block = block; tier.offset = offset; tier.length = end - offset;
        tier.parts = (tier.length + block - 1) / block;
        tier.fft.resize(2 * block); tier.controlFft.resize(2 * block);
        tier.bins = tier.fft.spectrumFloats();
        tier.window.assign(inputs * 2 * block, 0.0f);
        tier.fdl.assign(inputs * tier.parts * tier.bins, 0.0f);
        tier.accum.assign(tier.bins, 0.0f);
        tier.time.assign(2 * block, 0.0f); tier.pad.assign(2 * block, 0.0f);
        for (auto& bank : tier.spectra) bank.assign(inputs * outputs * tier.parts * tier.bins, 0.0f);
        for (auto& parts : tier.irParts) parts.assign(inputs * outputs, 0);
        for (auto& pending : tier.pending) pending.assign(outputs * block, 0.0f);
        tier.emit = block;
        tiers_.push_back(std::move(tier));
        offset = end; block *= cfg.growth;
    }
    staging_.assign(inputs * outputs * cfg.maxTaps, 0.0f);
    mixA_.assign(outputs * cfg.headBlock, 0.0f);
    mixB_.assign(outputs * cfg.headBlock, 0.0f);
    for (auto& state : state_) state.store(0);
    mailbox_.store(-1); active_ = incoming_ = -1;
    fade_ = 0;
    fadeLength_ = std::max<std::size_t>(1, cfg.crossfadeBlocks * cfg.headBlock);
    ready_ = true;
    return true;
}

bool MimoConvolver::loadMatrix(const float* const* ir, std::size_t taps) {
    if (!ready_ || !ir) return false;
    int bank = -1;
    for (int b = 0; b < 4; ++b) {
        int free = 0;
        if (state_[static_cast<std::size_t>(b)].compare_exchange_strong(free, 1)) { bank = b; break; }
    }
    if (bank < 0) return false;
    taps = std::min(taps, cfg_.maxTaps);
    for (auto& tier : tiers_) {
        auto& spectra = tier.spectra[static_cast<std::size_t>(bank)];
        std::fill(spectra.begin(), spectra.end(), 0.0f);
        auto& used = tier.irParts[static_cast<std::size_t>(bank)];
        std::fill(used.begin(), used.end(), 0);
        for (std::size_t path = 0; path < numInputs_ * numOutputs_; ++path) {
            if (!ir[path]) continue;
            for (std::size_t p = 0; p < tier.parts; ++p) {
                const std::size_t start = tier.offset + p * tier.block;
                if (start >= taps || p * tier.block >= tier.length) break;
                const std::size_t count = std::min({tier.block, taps - start, tier.length - p * tier.block});
                std::fill(tier.pad.begin(), tier.pad.end(), 0.0f);
                bool nonzero = false;
                for (std::size_t n = 0; n < count; ++n) {
                    const float x = ir[path][start + n];
                    tier.pad[n] = std::isfinite(x) ? x : 0.0f;
                    nonzero = nonzero || tier.pad[n] != 0.0f;
                }
                if (!nonzero) continue;
                used[path] = p + 1;
                tier.controlFft.forward(tier.pad.data(), spectra.data() + (path * tier.parts + p) * tier.bins);
            }
        }
    }
    state_[static_cast<std::size_t>(bank)].store(2, std::memory_order_release);
    const int obsolete = mailbox_.exchange(bank, std::memory_order_acq_rel);
    if (obsolete >= 0) state_[static_cast<std::size_t>(obsolete)].store(0, std::memory_order_release);
    return true;
}

void MimoConvolver::loadIr(std::size_t in, std::size_t out, const float* ir, std::size_t taps) {
    if (!ready_ || in >= numInputs_ || out >= numOutputs_) return;
    float* dst = staging_.data() + (in * numOutputs_ + out) * cfg_.maxTaps;
    std::fill(dst, dst + cfg_.maxTaps, 0.0f);
    if (ir) std::copy_n(ir, std::min(taps, cfg_.maxTaps), dst);
    const float* ptrs[1024];
    for (std::size_t p = 0; p < numInputs_ * numOutputs_; ++p) ptrs[p] = staging_.data() + p * cfg_.maxTaps;
    (void)loadMatrix(ptrs, cfg_.maxTaps);
}

void MimoConvolver::renderTier(Tier& tier, int bank, int slot) noexcept {
    auto& pending = tier.pending[static_cast<std::size_t>(slot)];
    const auto& spectra = tier.spectra[static_cast<std::size_t>(bank)];
    const auto& used = tier.irParts[static_cast<std::size_t>(bank)];
    for (std::size_t o = 0; o < numOutputs_; ++o) {
        bool active = false;
        for (std::size_t i = 0; i < numInputs_; ++i) active = active || used[i * numOutputs_ + o] != 0;
        if (!active) { rt::vecClear(pending.data() + o * tier.block, tier.block); continue; }
        rt::vecClear(tier.accum.data(), tier.bins);
        for (std::size_t i = 0; i < numInputs_; ++i) {
            for (std::size_t p = 0; p < used[i * numOutputs_ + o]; ++p) {
                const std::size_t idx = (tier.write + tier.parts - 1 - p) % tier.parts;
                const float* x = tier.fdl.data() + (i * tier.parts + idx) * tier.bins;
                const float* h = spectra.data() + ((i * numOutputs_ + o) * tier.parts + p) * tier.bins;
                rt::cplxMulAccumulate(tier.accum.data(), x, h, tier.bins / 2);
            }
        }
        tier.fft.inverse(tier.accum.data(), tier.time.data());
        rt::vecCopy(pending.data() + o * tier.block, tier.time.data() + tier.block, tier.block);
    }
}

void MimoConvolver::adoptPending() noexcept {
    if (incoming_ >= 0) return; // Coalesce rapid control updates without retarget clicks.
    const int bank = mailbox_.exchange(-1, std::memory_order_acq_rel);
    if (bank < 0) return;
    state_[static_cast<std::size_t>(bank)].store(3, std::memory_order_release);
    if (active_ < 0) { active_ = bank; return; }
    incoming_ = bank;
    fade_ = 0;
    // Reconstruct the new tail for the current emission position from shared
    // input history. Never start a new IR with an empty/delayed tail.
    for (auto& tier : tiers_) renderTier(tier, incoming_, 1);
}

void MimoConvolver::processBlock(const float* const* in, float* const* out) noexcept {
    if (!ready_) return;
    adoptPending();
    const std::size_t n = cfg_.headBlock;
    rt::vecClear(mixA_.data(), mixA_.size());
    rt::vecClear(mixB_.data(), mixB_.size());
    for (auto& tier : tiers_) {
        for (std::size_t i = 0; i < numInputs_; ++i) {
            float* window = tier.window.data() + i * 2 * tier.block;
            for (std::size_t k = 0; k < n; ++k) window[tier.block + tier.fill + k] = std::isfinite(in[i][k]) ? in[i][k] : 0.0f;
        }
        tier.fill += n;
        if (tier.fill == tier.block) {
            for (std::size_t i = 0; i < numInputs_; ++i) {
                float* window = tier.window.data() + i * 2 * tier.block;
                tier.fft.forward(window, tier.fdl.data() + (i * tier.parts + tier.write) * tier.bins);
                rt::vecCopy(window, window + tier.block, tier.block);
            }
            tier.write = (tier.write + 1) % tier.parts;
            if (active_ >= 0) renderTier(tier, active_, 0);
            if (incoming_ >= 0) renderTier(tier, incoming_, 1);
            tier.fill = 0; tier.emit = 0;
        }
        if (tier.emit + n <= tier.block) {
            for (std::size_t o = 0; o < numOutputs_; ++o) {
                rt::vecAdd(mixA_.data() + o * n, tier.pending[0].data() + o * tier.block + tier.emit, n);
                if (incoming_ >= 0) rt::vecAdd(mixB_.data() + o * n, tier.pending[1].data() + o * tier.block + tier.emit, n);
            }
            tier.emit += n;
        }
    }
    for (std::size_t k = 0; k < n; ++k) {
        const float t = incoming_ < 0 ? 0.0f : (cfg_.crossfadeBlocks == 0 ? 1.0f :
            std::min(1.0f, static_cast<float>(fade_ + k) / static_cast<float>(fadeLength_)));
        for (std::size_t o = 0; o < numOutputs_; ++o) {
            const float a = mixA_[o * n + k];
            out[o][k] = a + t * (mixB_[o * n + k] - a);
        }
    }
    if (incoming_ >= 0 && (fade_ += n) >= fadeLength_) {
        state_[static_cast<std::size_t>(active_)].store(0, std::memory_order_release);
        active_ = incoming_; incoming_ = -1;
        for (auto& tier : tiers_) tier.pending[0].swap(tier.pending[1]);
    }
}

void MimoConvolver::reset() noexcept {
    // Lifecycle operation: serialize reset/prepare with both threads.
    if (incoming_ >= 0) {
        state_[static_cast<std::size_t>(active_)].store(0);
        active_ = incoming_; incoming_ = -1;
    }
    const int latest = mailbox_.exchange(-1);
    if (latest >= 0) {
        if (active_ >= 0) state_[static_cast<std::size_t>(active_)].store(0);
        active_ = latest; state_[static_cast<std::size_t>(active_)].store(3);
    }
    for (auto& tier : tiers_) {
        rt::vecClear(tier.window.data(), tier.window.size());
        rt::vecClear(tier.fdl.data(), tier.fdl.size());
        for (auto& p : tier.pending) rt::vecClear(p.data(), p.size());
        tier.write = tier.fill = 0; tier.emit = tier.block;
    }
    fade_ = 0;
}

} // namespace frostsoulx::dsp
