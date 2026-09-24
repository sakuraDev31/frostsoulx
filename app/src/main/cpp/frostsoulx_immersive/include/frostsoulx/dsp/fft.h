#pragma once

// -----------------------------------------------------------------------------
// Real-input FFT for the Frostsoulx convolution engine.
//
// Design notes
// ------------
// * Iterative radix-2 decimation-in-time complex FFT with precomputed twiddle
//   factors and a precomputed bit-reversal permutation. No recursion, no
//   trig calls, and no allocation in `forward()` / `inverse()`.
// * `RealFft` transforms N real samples using an N/2-point complex FFT plus a
//   split step, which is ~2x faster and halves the working set -- important on
//   mobile where L1/L2 pressure dominates convolution cost.
// * Output layout is N/2+1 interleaved complex bins ([re,im] pairs), i.e.
//   N+2 floats. DC and Nyquist both carry a zero imaginary part so the buffer
//   can be fed directly to the SIMD complex MAC without special-casing.
// * The inverse transform applies the 1/N scale so forward->inverse is an
//   identity (to float precision).
//
// Split-step algebra (N-point real transform from an M=N/2 complex transform):
//   pack   z[i] = x[2i] + j*x[2i+1],           Z = FFT_M(z)
//   untangle for k in [0, M]:
//          Ze[k] = (Z[k] + conj(Z[M-k])) / 2       (even-sample spectrum)
//          Zo[k] = (Z[k] - conj(Z[M-k])) / (2j)    (odd-sample spectrum)
//          X[k]  = Ze[k] + W_N^k * Zo[k],  W_N = exp(-j*2*pi/N)
//   The inverse simply runs the same identities backwards.
//
// All buffers are owned by the object and sized at construction; the transform
// entry points are real-time safe.
// -----------------------------------------------------------------------------

#include "frostsoulx/rt/rt_types.h"

#include <cstddef>
#include <utility>
#include <vector>

namespace frostsoulx::dsp {

/// In-place iterative radix-2 complex FFT over interleaved [re,im] data.
class ComplexFft {
public:
    ComplexFft() = default;

    /// `size` must be a power of two >= 2. Allocates twiddles + reversal table.
    explicit ComplexFft(std::size_t size) { resize(size); }

    void resize(std::size_t size) {
        if (!rt::isPow2(size) || size < 2) {
            size_ = 0;
            reverse_.clear();
            twiddle_.clear();
            return;
        }
        size_ = size;

        // Bit-reversal permutation table.
        reverse_.assign(size_, 0);
        std::size_t bits = 0;
        while ((std::size_t{1} << bits) < size_) ++bits;
        for (std::size_t i = 0; i < size_; ++i) {
            std::size_t r = 0;
            for (std::size_t b = 0; b < bits; ++b) {
                if (i & (std::size_t{1} << b)) r |= std::size_t{1} << (bits - 1 - b);
            }
            reverse_[i] = r;
        }

        // Twiddles per stage, stored contiguously: for a stage with half-span h
        // we need h factors exp(-j*pi*k/h).
        twiddle_.clear();
        twiddle_.reserve(size_ * 2);
        for (std::size_t h = 1; h < size_; h <<= 1) {
            for (std::size_t k = 0; k < h; ++k) {
                const double ang = -static_cast<double>(rt::kPi) * static_cast<double>(k) /
                                   static_cast<double>(h);
                twiddle_.push_back(static_cast<float>(std::cos(ang)));
                twiddle_.push_back(static_cast<float>(std::sin(ang)));
            }
        }
    }

    std::size_t size() const noexcept { return size_; }
    bool valid() const noexcept { return size_ >= 2; }

    /// Forward transform, in place. `data` holds `size()` interleaved complex values.
    void forward(float* FSX_RESTRICT data) const noexcept { run(data, false); }

    /// Inverse transform, in place, including the 1/N normalisation.
    void inverse(float* FSX_RESTRICT data) const noexcept {
        run(data, true);
        rt::vecScale(data, 1.0f / static_cast<float>(size_), size_ * 2);
    }

private:
    void run(float* FSX_RESTRICT data, bool conjugate) const noexcept {
        if (size_ < 2) return;

        for (std::size_t i = 0; i < size_; ++i) {
            const std::size_t r = reverse_[i];
            if (r > i) {
                std::swap(data[i * 2], data[r * 2]);
                std::swap(data[i * 2 + 1], data[r * 2 + 1]);
            }
        }

        const float sign = conjugate ? -1.0f : 1.0f;
        std::size_t twOffset = 0;
        for (std::size_t h = 1; h < size_; h <<= 1) {
            const std::size_t span = h << 1;
            const float* FSX_RESTRICT tw = twiddle_.data() + twOffset;
            for (std::size_t base = 0; base < size_; base += span) {
                for (std::size_t k = 0; k < h; ++k) {
                    const float wr = tw[k * 2];
                    const float wi = tw[k * 2 + 1] * sign;

                    const std::size_t i0 = (base + k) * 2;
                    const std::size_t i1 = (base + k + h) * 2;

                    const float xr = data[i1];
                    const float xi = data[i1 + 1];
                    const float tr = xr * wr - xi * wi;
                    const float ti = xr * wi + xi * wr;

                    const float ur = data[i0];
                    const float ui = data[i0 + 1];

                    data[i0] = ur + tr;
                    data[i0 + 1] = ui + ti;
                    data[i1] = ur - tr;
                    data[i1 + 1] = ui - ti;
                }
            }
            twOffset += h * 2;
        }
    }

    std::size_t size_ = 0;
    std::vector<std::size_t> reverse_;
    std::vector<float> twiddle_;
};

/// Real-input FFT of size N built on an N/2-point complex FFT.
///
/// `forward`: N real samples -> (N/2+1) interleaved complex bins (N+2 floats).
/// `inverse`: (N/2+1) interleaved complex bins -> N real samples.
class RealFft {
public:
    RealFft() = default;
    explicit RealFft(std::size_t n) { resize(n); }

    /// `n` must be a power of two >= 4.
    void resize(std::size_t n) {
        if (!rt::isPow2(n) || n < 4) {
            n_ = 0;
            half_ = 0;
            return;
        }
        n_ = n;
        half_ = n / 2;
        inner_.resize(half_);
        scratch_.assign(half_ * 2, 0.0f);

        // W_N^k = exp(-j*2*pi*k/N) for k in [0, M] where M = N/2.
        split_.assign((half_ + 1) * 2, 0.0f);
        for (std::size_t k = 0; k <= half_; ++k) {
            const double ang = -static_cast<double>(rt::kTwoPi) * static_cast<double>(k) /
                               static_cast<double>(n_);
            split_[k * 2] = static_cast<float>(std::cos(ang));
            split_[k * 2 + 1] = static_cast<float>(std::sin(ang));
        }
    }

    std::size_t size() const noexcept { return n_; }
    /// Number of complex bins produced: N/2 + 1.
    std::size_t numBins() const noexcept { return n_ ? half_ + 1 : 0; }
    /// Floats required for a spectrum buffer: 2*(N/2+1) = N+2.
    std::size_t spectrumFloats() const noexcept { return n_ ? n_ + 2 : 0; }
    bool valid() const noexcept { return n_ >= 4 && inner_.valid(); }

    /// `time` has N real samples; `spec` receives N+2 floats.
    void forward(const float* FSX_RESTRICT time, float* FSX_RESTRICT spec) noexcept {
        if (!valid()) return;

        // Pack even samples as real, odd samples as imaginary.
        for (std::size_t i = 0; i < half_; ++i) {
            scratch_[i * 2] = time[i * 2];
            scratch_[i * 2 + 1] = time[i * 2 + 1];
        }
        inner_.forward(scratch_.data());

        const std::size_t M = half_;
        for (std::size_t k = 0; k <= M; ++k) {
            const std::size_t a = k % M;          // Z[k]
            const std::size_t b = (M - k) % M;    // Z[M-k]

            const float zr = scratch_[a * 2];
            const float zi = scratch_[a * 2 + 1];
            const float cr = scratch_[b * 2];
            const float ci = -scratch_[b * 2 + 1];  // conj(Z[M-k])

            // Ze = (Z + conj(Zc)) / 2
            const float er = 0.5f * (zr + cr);
            const float ei = 0.5f * (zi + ci);
            // Zo = (Z - conj(Zc)) / (2j)  ->  dividing by j rotates by -90 deg.
            const float dr = 0.5f * (zr - cr);
            const float di = 0.5f * (zi - ci);
            const float or_ = di;
            const float oi = -dr;

            const float wr = split_[k * 2];
            const float wi = split_[k * 2 + 1];
            const float tr = or_ * wr - oi * wi;
            const float ti = or_ * wi + oi * wr;

            spec[k * 2] = er + tr;
            spec[k * 2 + 1] = ei + ti;
        }

        // DC and Nyquist are exactly real for real input; kill rounding dust so
        // downstream complex MACs stay on the real axis.
        spec[1] = 0.0f;
        spec[M * 2 + 1] = 0.0f;
    }

    /// `spec` holds N+2 floats; `time` receives N real samples.
    void inverse(const float* FSX_RESTRICT spec, float* FSX_RESTRICT time) noexcept {
        if (!valid()) return;

        const std::size_t M = half_;
        for (std::size_t k = 0; k < M; ++k) {
            const std::size_t m = M - k;

            const float xr = spec[k * 2];
            const float xi = spec[k * 2 + 1];
            const float yr = spec[m * 2];
            const float yi = -spec[m * 2 + 1];  // conj(X[M-k])

            // Ze = (X + conj(X[M-k])) / 2
            const float er = 0.5f * (xr + yr);
            const float ei = 0.5f * (xi + yi);
            // W^k * Zo = (X - conj(X[M-k])) / 2  ->  undo by conj(W^k).
            const float pr = 0.5f * (xr - yr);
            const float pi = 0.5f * (xi - yi);

            const float wr = split_[k * 2];
            const float wi = -split_[k * 2 + 1];  // conj(W_N^k)
            const float or_ = pr * wr - pi * wi;
            const float oi = pr * wi + pi * wr;

            // Z[k] = Ze[k] + j * Zo[k]
            scratch_[k * 2] = er - oi;
            scratch_[k * 2 + 1] = ei + or_;
        }

        inner_.inverse(scratch_.data());

        for (std::size_t i = 0; i < half_; ++i) {
            time[i * 2] = scratch_[i * 2];
            time[i * 2 + 1] = scratch_[i * 2 + 1];
        }
    }

private:
    std::size_t n_ = 0;
    std::size_t half_ = 0;
    ComplexFft inner_;
    std::vector<float> scratch_;
    std::vector<float> split_;
};

} // namespace frostsoulx::dsp
