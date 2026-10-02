#include "frostsoulx/spatial/spatial_renderer.h"

#include <algorithm>
#include <cmath>

namespace frostsoulx::spatial {

namespace {

SpeakerLayout makeLayout(VirtualArray a) {
    switch (a) {
        case VirtualArray::Cube8: return SpeakerLayout::cube8();
        case VirtualArray::Sphere26: return SpeakerLayout::sphere26();
        case VirtualArray::Dodeca12:
        default: return SpeakerLayout::dodeca12();
    }
}

} // namespace

bool SpatialRenderer::prepare(double sampleRate, int maxFrames,
                              const SpatialRendererConfig& cfg) {
    ready_ = false;

    if (sampleRate < 8000.0 || maxFrames <= 0) return false;
    if (!rt::isPow2(cfg.renderBlock) || cfg.renderBlock < 32) return false;
    if (!rt::isPow2(cfg.hrirTaps) || cfg.hrirTaps < 32 || cfg.hrirTaps > 512) return false;

    sampleRate_ = sampleRate;
    order_ = std::clamp(cfg.ambisonicOrder, 0, kMaxAmbisonicOrder);
    hoaChannels_ = ambisonicChannels(order_);
    block_ = cfg.renderBlock;

    // --- HRTF set -----------------------------------------------------------
    if (!hrtf_.buildParametric(sampleRate, cfg.hrirTaps, cfg.azimuthStepDeg,
                               cfg.elevationStepDeg)) {
        return false;
    }

    // --- Virtual array, decoder, panner -------------------------------------
    layout_ = makeLayout(cfg.array);
    if (layout_.size() < hoaChannels_) {
        // Requested order needs more virtual speakers than the array provides.
        layout_ = SpeakerLayout::sphere26();
    }
    if (!decoder_.prepare(order_, layout_, cfg.maxRe)) return false;
    if (!vbap_.prepare(layout_)) return false;

    encoder_.setOrder(order_);
    rotator_.setOrder(order_);
    rotator_.setOrientation(HeadOrientation{});

    // --- Collapse decode x HRIR into hoaChannels_ x 2 filters ---------------
    //
    //   F[c][ear][n] = sum_s  D[s][c] * hrir_s[ear][n]
    //
    // Doing this once at prepare() time means the per-sample cost is
    // independent of the virtual array size.
    const std::size_t taps = hrtf_.irTaps();
    const std::size_t S = layout_.size();
    const std::size_t C = hoaChannels_;

    std::vector<float> filters(C * 2 * taps, 0.0f);
    std::vector<float> hrirL(taps, 0.0f);
    std::vector<float> hrirR(taps, 0.0f);
    const std::vector<float>& D = decoder_.matrix();  // [speaker][channel]

    for (std::size_t s = 0; s < S; ++s) {
        hrtf_.render(layout_.positions[s], hrirL.data(), hrirR.data());
        for (std::size_t c = 0; c < C; ++c) {
            const float g = D[s * C + c];
            if (g == 0.0f) continue;
            rt::vecAddScaled(filters.data() + (c * 2) * taps, hrirL.data(), g, taps);
            rt::vecAddScaled(filters.data() + (c * 2 + 1) * taps, hrirR.data(), g, taps);
        }
    }

    // --- Binaural convolver -------------------------------------------------
    dsp::NonUniformConvolver::Config ccfg;
    ccfg.headBlock = block_;
    ccfg.maxTaps = taps;
    ccfg.maxTiers = 1;  // HRIRs are short; a single uniform tier is optimal.
    ccfg.growth = 4;
    ccfg.crossfadeBlocks = 0;
    if (!binaural_.prepare(C, 2, ccfg)) return false;

    for (std::size_t c = 0; c < C; ++c) {
        binaural_.loadIr(c, 0, filters.data() + (c * 2) * taps, taps);
        binaural_.loadIr(c, 1, filters.data() + (c * 2 + 1) * taps, taps);
    }

    // --- Normalisation ------------------------------------------------------
    // Encode a centred unit source, decode it and measure the resulting
    // binaural peak so the whole chain is unity-gain for a front source. This
    // keeps the renderer from changing programme level, which matters because
    // the engine's limiter downstream must stay out of the way of normal
    // material.
    {
        std::vector<float> enc(kMaxAmbisonicChannels, 0.0f);
        evaluateSphericalHarmonics(Vec3{1.0f, 0.0f, 0.0f}, order_, enc.data());
        double peak = 0.0;
        for (int ear = 0; ear < 2; ++ear) {
            std::vector<double> acc(taps, 0.0);
            for (std::size_t c = 0; c < C; ++c) {
                const float* f = filters.data() + (c * 2 + static_cast<std::size_t>(ear)) * taps;
                for (std::size_t n = 0; n < taps; ++n) acc[n] += enc[c] * f[n];
            }
            double sum = 0.0;
            for (std::size_t n = 0; n < taps; ++n) sum += std::fabs(acc[n]);
            peak = std::max(peak, sum);
        }
        outputGain_ = (peak > 1.0e-6) ? static_cast<float>(1.0 / peak) : 1.0f;
        outputGain_ = std::clamp(outputGain_, 1.0e-3f, 32.0f);
    }

    // --- Buffers ------------------------------------------------------------
    gainsL_.assign(kMaxAmbisonicChannels, 0.0f);
    gainsR_.assign(kMaxAmbisonicChannels, 0.0f);

    hoaStorage_.assign(C * block_, 0.0f);
    hoaPtrs_.resize(C);
    hoaConstPtrs_.resize(C);
    for (std::size_t c = 0; c < C; ++c) {
        hoaPtrs_[c] = hoaStorage_.data() + c * block_;
        hoaConstPtrs_[c] = hoaPtrs_[c];
    }

    outStorage_.assign(2 * block_, 0.0f);
    outPtrs_ = {outStorage_.data(), outStorage_.data() + block_};

    inFifo_.assign(block_ * 2, 0.0f);
    outFifo_.assign(block_ * 2, 0.0f);
    inFill_ = 0;
    // Prime the output FIFO with one block of silence: that is the renderer's
    // algorithmic latency, and it lets process() always satisfy the host.
    outAvail_ = block_;
    outRead_ = 0;

    scratchGains_.assign(std::max<std::size_t>(layout_.size(), 1), 0.0f);

    // ~15 ms width smoothing: fast enough to feel immediate, slow enough that
    // a slider drag cannot zipper the encoding gains.
    widthCoeff_ = rt::onePoleCoeff(0.015f, sampleRate);
    widthCurrent_ = widthTarget_;
    blendCurrent_ = blendTarget_;
    updateSourceGains();

    ready_ = true;
    return true;
}

void SpatialRenderer::reset() noexcept {
    if (!ready_) return;
    binaural_.reset();
    rt::vecClear(hoaStorage_.data(), hoaStorage_.size());
    rt::vecClear(outStorage_.data(), outStorage_.size());
    rt::vecClear(inFifo_.data(), inFifo_.size());
    rt::vecClear(outFifo_.data(), outFifo_.size());
    inFill_ = 0;
    outAvail_ = block_;
    outRead_ = 0;
    widthCurrent_ = widthTarget_;
    blendCurrent_ = blendTarget_;
    updateSourceGains();
}

void SpatialRenderer::setStereoWidth(float width) noexcept {
    widthTarget_ = rt::clampUnit(width);
}

void SpatialRenderer::setSpatialBlend(float blend) noexcept {
    blendTarget_ = rt::clampUnit(blend);
}

void SpatialRenderer::setHeadOrientation(const HeadOrientation& o) noexcept {
    rotator_.setOrientation(o);
}

void SpatialRenderer::updateSourceGains() noexcept {
    // Two virtual sources symmetric about the median plane. Width 0 puts both
    // dead ahead (mono), width 1 puts them fully lateral at +/-90 deg.
    const float az = 15.0f + 75.0f * widthCurrent_;
    const Vec3 l = SphericalCoord{az, 0.0f, 1.0f}.toCartesian();
    const Vec3 r = SphericalCoord{-az, 0.0f, 1.0f}.toCartesian();
    encoder_.gainsFor(l.normalized(), gainsL_.data());
    encoder_.gainsFor(r.normalized(), gainsR_.data());
}

std::size_t SpatialRenderer::encodeObject(const SphericalCoord& dir,
                                          float* FSX_RESTRICT gains) const noexcept {
    if (gains == nullptr) return 0;
    encoder_.gainsFor(dir.toCartesian().normalized(), gains);
    return hoaChannels_;
}

void SpatialRenderer::objectGains(const SphericalCoord& dir, float spread,
                                  float* FSX_RESTRICT gains) const noexcept {
    if (gains == nullptr || !ready_) return;
    vbap_.gainsForSpread(dir.toCartesian().normalized(), spread, gains);
}

void SpatialRenderer::renderBlock() noexcept {
    const std::size_t C = hoaChannels_;
    const std::size_t n = block_;

    // --- Encode the two virtual sources into the HOA bus --------------------
    for (std::size_t c = 0; c < C; ++c) rt::vecClear(hoaPtrs_[c], n);

    // Smooth the width across the block so slider moves never zipper.
    float w = widthCurrent_;
    const bool widthMoving = std::fabs(widthTarget_ - w) > 1.0e-6f;

    for (std::size_t i = 0; i < n; ++i) {
        const float l = inFifo_[i * 2];
        const float r = inFifo_[i * 2 + 1];
        for (std::size_t c = 0; c < C; ++c) {
            hoaPtrs_[c][i] = gainsL_[c] * l + gainsR_[c] * r;
        }
    }

    if (widthMoving) {
        w += (widthTarget_ - w) * std::min(1.0f, widthCoeff_ * static_cast<float>(n));
        widthCurrent_ = rt::clampUnit(w);
        updateSourceGains();
    }

    // --- Sound-field rotation (head tracking) -------------------------------
    if (!rotator_.isIdentity()) {
        float frame[kMaxAmbisonicChannels];
        for (std::size_t i = 0; i < n; ++i) {
            for (std::size_t c = 0; c < C; ++c) frame[c] = hoaPtrs_[c][i];
            rotator_.rotateFrame(frame);
            for (std::size_t c = 0; c < C; ++c) hoaPtrs_[c][i] = frame[c];
        }
    }

    // --- HOA -> binaural convolution ----------------------------------------
    binaural_.processBlock(hoaConstPtrs_.data(), outPtrs_.data());

    // --- Interleave, normalise and blend against the aligned dry path ------
    //
    // The convolver is zero-latency, so the wet block it just produced
    // corresponds sample-for-sample to the input currently in `inFifo_`. Both
    // are emitted together on the next call, which is where the renderer's
    // single block of latency comes from. Blending against `inFifo_` therefore
    // keeps dry and wet phase-aligned: blend=0 is exactly the input delayed by
    // `latencySamples()`, with no comb filtering at intermediate settings.
    const float g = outputGain_;
    const float b0 = blendCurrent_;
    const float b1 = rt::clampUnit(blendTarget_);
    const float step = (b1 - b0) / static_cast<float>(n);

    for (std::size_t i = 0; i < n; ++i) {
        float l = outPtrs_[0][i] * g;
        float r = outPtrs_[1][i] * g;
        if (!std::isfinite(l)) l = 0.0f;
        if (!std::isfinite(r)) r = 0.0f;

        const float b = b0 + step * static_cast<float>(i);
        const float dry = 1.0f - b;
        outFifo_[i * 2] = dry * inFifo_[i * 2] + b * l;
        outFifo_[i * 2 + 1] = dry * inFifo_[i * 2 + 1] + b * r;
    }
    blendCurrent_ = b1;

    outAvail_ = n;
    outRead_ = 0;
}

void SpatialRenderer::process(float* FSX_RESTRICT interleavedStereo, int frames) noexcept {
    if (!ready_ || interleavedStereo == nullptr || frames <= 0) return;

    const std::size_t n = block_;
    std::size_t done = 0;
    const std::size_t total = static_cast<std::size_t>(frames);

    while (done < total) {
        // Fill the input FIFO.
        const std::size_t want = std::min(n - inFill_, total - done);
        for (std::size_t i = 0; i < want; ++i) {
            inFifo_[(inFill_ + i) * 2] = interleavedStereo[(done + i) * 2];
            inFifo_[(inFill_ + i) * 2 + 1] = interleavedStereo[(done + i) * 2 + 1];
        }
        inFill_ += want;

        // Drain the previously rendered block into the caller's buffer.
        const std::size_t avail = std::min(outAvail_ - outRead_, want);
        for (std::size_t i = 0; i < avail; ++i) {
            interleavedStereo[(done + i) * 2] = outFifo_[(outRead_ + i) * 2];
            interleavedStereo[(done + i) * 2 + 1] = outFifo_[(outRead_ + i) * 2 + 1];
        }
        outRead_ += avail;
        // Any shortfall is the one-block priming latency.
        for (std::size_t i = avail; i < want; ++i) {
            interleavedStereo[(done + i) * 2] = 0.0f;
            interleavedStereo[(done + i) * 2 + 1] = 0.0f;
        }

        done += want;

        if (inFill_ == n) {
            renderBlock();
            inFill_ = 0;
        }
    }
}

} // namespace frostsoulx::spatial
