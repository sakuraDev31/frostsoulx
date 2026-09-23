#pragma once

// -----------------------------------------------------------------------------
// Frostsoulx real-time foundation: numeric helpers, denormal control, and
// portable SIMD wrappers (ARM64 NEON / x86-64 SSE / scalar).
//
// Everything in this header is header-only, allocation-free and callable from
// the audio thread.
// -----------------------------------------------------------------------------

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>

#if defined(__aarch64__) || defined(__ARM_NEON) || defined(__ARM_NEON__)
#define FSX_SIMD_NEON 1
#include <arm_neon.h>
#elif defined(__SSE2__) || defined(_M_X64) || defined(__x86_64__)
#define FSX_SIMD_SSE 1
#include <emmintrin.h>
#include <xmmintrin.h>
#endif

#if defined(FSX_SIMD_NEON) || defined(FSX_SIMD_SSE)
#define FSX_HAS_SIMD 1
#else
#define FSX_HAS_SIMD 0
#endif

#define FSX_RESTRICT __restrict

namespace frostsoulx::rt {

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

inline constexpr float kPi = 3.14159265358979323846f;
inline constexpr float kTwoPi = 6.28318530717958647692f;
inline constexpr float kHalfPi = 1.57079632679489661923f;
inline constexpr float kInvPi = 0.31830988618379067154f;
inline constexpr float kDegToRad = kPi / 180.0f;
inline constexpr float kRadToDeg = 180.0f / kPi;

// Speed of sound at ~20 degrees C, metres/second.
inline constexpr float kSpeedOfSound = 343.0f;
// Nominal head radius used by the spherical-head ITD model (Woodworth).
inline constexpr float kHeadRadius = 0.0875f;

// Denormals below this magnitude are flushed by `flushDenormal`.
inline constexpr float kDenormalGuard = 1.0e-20f;

// ---------------------------------------------------------------------------
// Scalar helpers
// ---------------------------------------------------------------------------

/// Clamp with NaN/Inf rejection. Non-finite input collapses to `fallback`.
inline float sanitize(float v, float lo, float hi, float fallback = 0.0f) noexcept {
    if (!std::isfinite(v)) return fallback;
    return std::clamp(v, lo, hi);
}

inline float clampUnit(float v) noexcept { return sanitize(v, 0.0f, 1.0f, 0.0f); }

/// Flush denormal / subnormal values to zero. Denormal arithmetic on ARM is
/// not trapped but on x86 it can cost 100x; either way recursive filter state
/// must not decay into the subnormal range.
inline float flushDenormal(float v) noexcept {
    return (std::fabs(v) < kDenormalGuard) ? 0.0f : v;
}

/// Equal-power (constant-intensity) crossfade pair for a mix in [0,1].
inline void equalPowerGains(float mix, float& dry, float& wet) noexcept {
    const float m = clampUnit(mix);
    dry = std::cos(m * kHalfPi);
    wet = std::sin(m * kHalfPi);
}

/// One-pole smoothing coefficient for a given time constant.
/// `y += coeff * (target - y)` reaches 1-1/e of the step after `tauSeconds`.
inline float onePoleCoeff(float tauSeconds, double sampleRate) noexcept {
    if (tauSeconds <= 0.0f || sampleRate <= 0.0) return 1.0f;
    const double c = 1.0 - std::exp(-1.0 / (static_cast<double>(tauSeconds) * sampleRate));
    return static_cast<float>(std::clamp(c, 0.0, 1.0));
}

/// Decay coefficient reaching -60 dB after `t60Seconds`.
inline float t60Coeff(float t60Seconds, float delaySeconds) noexcept {
    if (t60Seconds <= 0.0f || delaySeconds <= 0.0f) return 0.0f;
    return std::exp(-6.9077552789821f * delaySeconds / t60Seconds);
}

inline float dbToGain(float db) noexcept { return std::pow(10.0f, db * 0.05f); }
inline float gainToDb(float g) noexcept {
    return 20.0f * std::log10(std::max(g, 1.0e-9f));
}

/// Next power of two >= v (v >= 1).
inline std::size_t nextPow2(std::size_t v) noexcept {
    std::size_t p = 1;
    while (p < v) p <<= 1;
    return p;
}

inline bool isPow2(std::size_t v) noexcept { return v != 0 && (v & (v - 1)) == 0; }

// ---------------------------------------------------------------------------
// Denormal control (RAII) -- set FTZ/DAZ for the duration of the callback.
// ---------------------------------------------------------------------------

class ScopedDenormalDisable {
public:
    ScopedDenormalDisable() noexcept {
#if defined(FSX_SIMD_SSE)
        saved_ = _mm_getcsr();
        // FTZ (bit 15) | DAZ (bit 6)
        _mm_setcsr(saved_ | 0x8040u);
#elif defined(__aarch64__)
        std::uint64_t fpcr = 0;
        __asm__ __volatile__("mrs %0, fpcr" : "=r"(fpcr));
        saved_ = fpcr;
        // FZ (bit 24) flush-to-zero for AArch64 floating point.
        __asm__ __volatile__("msr fpcr, %0" : : "r"(fpcr | (1ull << 24)));
#endif
    }

    ~ScopedDenormalDisable() noexcept {
#if defined(FSX_SIMD_SSE)
        _mm_setcsr(saved_);
#elif defined(__aarch64__)
        __asm__ __volatile__("msr fpcr, %0" : : "r"(saved_));
#endif
    }

    ScopedDenormalDisable(const ScopedDenormalDisable&) = delete;
    ScopedDenormalDisable& operator=(const ScopedDenormalDisable&) = delete;

private:
#if defined(FSX_SIMD_SSE)
    unsigned int saved_ = 0;
#elif defined(__aarch64__)
    std::uint64_t saved_ = 0;
#endif
};

// ---------------------------------------------------------------------------
// Vectorised buffer primitives.
//
// These are the hot inner loops of the convolution and mixing stages. Each has
// a scalar fallback so host builds and non-SIMD targets stay bit-correct
// (within float associativity) and testable.
// ---------------------------------------------------------------------------

inline void vecClear(float* FSX_RESTRICT dst, std::size_t n) noexcept {
    for (std::size_t i = 0; i < n; ++i) dst[i] = 0.0f;
}

inline void vecCopy(float* FSX_RESTRICT dst, const float* FSX_RESTRICT src, std::size_t n) noexcept {
    for (std::size_t i = 0; i < n; ++i) dst[i] = src[i];
}

/// dst[i] += src[i] * gain
inline void vecAddScaled(float* FSX_RESTRICT dst, const float* FSX_RESTRICT src,
                         float gain, std::size_t n) noexcept {
    std::size_t i = 0;
#if defined(FSX_SIMD_NEON)
    const float32x4_t g = vdupq_n_f32(gain);
    for (; i + 4 <= n; i += 4) {
        float32x4_t d = vld1q_f32(dst + i);
        const float32x4_t s = vld1q_f32(src + i);
        d = vmlaq_f32(d, s, g);
        vst1q_f32(dst + i, d);
    }
#elif defined(FSX_SIMD_SSE)
    const __m128 g = _mm_set1_ps(gain);
    for (; i + 4 <= n; i += 4) {
        __m128 d = _mm_loadu_ps(dst + i);
        const __m128 s = _mm_loadu_ps(src + i);
        d = _mm_add_ps(d, _mm_mul_ps(s, g));
        _mm_storeu_ps(dst + i, d);
    }
#endif
    for (; i < n; ++i) dst[i] += src[i] * gain;
}

/// dst[i] *= gain
inline void vecScale(float* FSX_RESTRICT dst, float gain, std::size_t n) noexcept {
    std::size_t i = 0;
#if defined(FSX_SIMD_NEON)
    const float32x4_t g = vdupq_n_f32(gain);
    for (; i + 4 <= n; i += 4) {
        vst1q_f32(dst + i, vmulq_f32(vld1q_f32(dst + i), g));
    }
#elif defined(FSX_SIMD_SSE)
    const __m128 g = _mm_set1_ps(gain);
    for (; i + 4 <= n; i += 4) {
        _mm_storeu_ps(dst + i, _mm_mul_ps(_mm_loadu_ps(dst + i), g));
    }
#endif
    for (; i < n; ++i) dst[i] *= gain;
}

/// dst[i] += src[i]
inline void vecAdd(float* FSX_RESTRICT dst, const float* FSX_RESTRICT src, std::size_t n) noexcept {
    std::size_t i = 0;
#if defined(FSX_SIMD_NEON)
    for (; i + 4 <= n; i += 4) {
        vst1q_f32(dst + i, vaddq_f32(vld1q_f32(dst + i), vld1q_f32(src + i)));
    }
#elif defined(FSX_SIMD_SSE)
    for (; i + 4 <= n; i += 4) {
        _mm_storeu_ps(dst + i, _mm_add_ps(_mm_loadu_ps(dst + i), _mm_loadu_ps(src + i)));
    }
#endif
    for (; i < n; ++i) dst[i] += src[i];
}

/// Peak absolute magnitude over the buffer.
inline float vecPeak(const float* FSX_RESTRICT src, std::size_t n) noexcept {
    float peak = 0.0f;
    std::size_t i = 0;
#if defined(FSX_SIMD_NEON)
    float32x4_t p = vdupq_n_f32(0.0f);
    for (; i + 4 <= n; i += 4) p = vmaxq_f32(p, vabsq_f32(vld1q_f32(src + i)));
    float tmp[4];
    vst1q_f32(tmp, p);
    peak = std::max(std::max(tmp[0], tmp[1]), std::max(tmp[2], tmp[3]));
#endif
    for (; i < n; ++i) peak = std::max(peak, std::fabs(src[i]));
    return peak;
}

/// Sum of squares (energy) over the buffer.
inline double vecEnergy(const float* FSX_RESTRICT src, std::size_t n) noexcept {
    double acc = 0.0;
    for (std::size_t i = 0; i < n; ++i) {
        acc += static_cast<double>(src[i]) * static_cast<double>(src[i]);
    }
    return acc;
}

/// True when every sample is finite.
inline bool vecIsFinite(const float* FSX_RESTRICT src, std::size_t n) noexcept {
    for (std::size_t i = 0; i < n; ++i) {
        if (!std::isfinite(src[i])) return false;
    }
    return true;
}

/// Complex multiply-accumulate on interleaved [re,im] spectra:
///   acc += a * b   (complex product), over `bins` complex values.
/// This is the innermost operation of frequency-domain partitioned convolution.
inline void cplxMulAccumulate(float* FSX_RESTRICT acc, const float* FSX_RESTRICT a,
                              const float* FSX_RESTRICT b, std::size_t bins) noexcept {
    std::size_t i = 0;
#if defined(FSX_SIMD_NEON)
    // Deinterleave 4 complex pairs at a time: vld2q gives {re[4], im[4]}.
    for (; i + 4 <= bins; i += 4) {
        const float32x4x2_t va = vld2q_f32(a + i * 2);
        const float32x4x2_t vb = vld2q_f32(b + i * 2);
        float32x4x2_t vc = vld2q_f32(acc + i * 2);
        // re += ar*br - ai*bi
        vc.val[0] = vmlaq_f32(vc.val[0], va.val[0], vb.val[0]);
        vc.val[0] = vmlsq_f32(vc.val[0], va.val[1], vb.val[1]);
        // im += ar*bi + ai*br
        vc.val[1] = vmlaq_f32(vc.val[1], va.val[0], vb.val[1]);
        vc.val[1] = vmlaq_f32(vc.val[1], va.val[1], vb.val[0]);
        vst2q_f32(acc + i * 2, vc);
    }
#elif defined(FSX_SIMD_SSE)
    for (; i + 2 <= bins; i += 2) {
        const __m128 va = _mm_loadu_ps(a + i * 2);  // ar0 ai0 ar1 ai1
        const __m128 vb = _mm_loadu_ps(b + i * 2);
        __m128 vc = _mm_loadu_ps(acc + i * 2);
        const __m128 ar = _mm_shuffle_ps(va, va, _MM_SHUFFLE(2, 2, 0, 0));
        const __m128 ai = _mm_shuffle_ps(va, va, _MM_SHUFFLE(3, 3, 1, 1));
        const __m128 bswap = _mm_shuffle_ps(vb, vb, _MM_SHUFFLE(2, 3, 0, 1));
        // ar*b + ai*swap(b) * (-1, +1)
        __m128 prod = _mm_mul_ps(ar, vb);
        const __m128 cross = _mm_mul_ps(ai, bswap);
        const __m128 sign = _mm_set_ps(0.0f, -0.0f, 0.0f, -0.0f);
        prod = _mm_add_ps(prod, _mm_xor_ps(cross, sign));
        vc = _mm_add_ps(vc, prod);
        _mm_storeu_ps(acc + i * 2, vc);
    }
#endif
    for (; i < bins; ++i) {
        const float ar = a[i * 2], ai = a[i * 2 + 1];
        const float br = b[i * 2], bi = b[i * 2 + 1];
        acc[i * 2] += ar * br - ai * bi;
        acc[i * 2 + 1] += ar * bi + ai * br;
    }
}

} // namespace frostsoulx::rt
