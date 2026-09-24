#include "frostsoulx/spatial/ambisonics.h"

#include <algorithm>
#include <cmath>

namespace frostsoulx::spatial {

// ---------------------------------------------------------------------------
// Real spherical harmonics, ACN ordering, SN3D normalisation.
//
// Explicit closed forms up to degree 3 -- faster and more accurate than a
// generic associated-Legendre recursion at these orders, and branch-free.
// ---------------------------------------------------------------------------

void evaluateSphericalHarmonics(const Vec3& unitDir, int order, float* out) noexcept {
    if (out == nullptr) return;
    order = std::clamp(order, 0, kMaxAmbisonicOrder);

    const Vec3 d = unitDir.normalized();
    // Ambisonics axis convention: x forward, y left, z up.
    const float x = d.x, y = d.y, z = d.z;

    // Degree 0
    out[0] = 1.0f;
    if (order == 0) return;

    // Degree 1 (ACN 1..3 = Y, Z, X)
    out[1] = y;
    out[2] = z;
    out[3] = x;
    if (order == 1) return;

    const float xx = x * x, yy = y * y, zz = z * z;

    // Degree 2 (ACN 4..8), SN3D
    out[4] = 1.7320508f * x * y;                    // V
    out[5] = 1.7320508f * y * z;                    // T
    out[6] = 0.5f * (3.0f * zz - 1.0f);             // R
    out[7] = 1.7320508f * x * z;                    // S
    out[8] = 0.8660254f * (xx - yy);                // U
    if (order == 2) return;

    // Degree 3 (ACN 9..15), SN3D
    out[9] = 0.7905694f * y * (3.0f * xx - yy);
    out[10] = 3.8729833f * x * y * z;
    out[11] = 0.6123724f * y * (5.0f * zz - 1.0f);
    out[12] = 0.5f * z * (5.0f * zz - 3.0f);
    out[13] = 0.6123724f * x * (5.0f * zz - 1.0f);
    out[14] = 1.9364917f * z * (xx - yy);
    out[15] = 0.7905694f * x * (xx - 3.0f * yy);
}

void maxReGains(int order, float* out) noexcept {
    if (out == nullptr) return;
    order = std::clamp(order, 0, kMaxAmbisonicOrder);

    // max-rE weights: g_l = P_l(cos(theta_max)) where theta_max is the largest
    // root of the Legendre polynomial of degree order+1.
    static const float kThetaMax[kMaxAmbisonicOrder + 1] = {
        0.0f,         // order 0: trivial
        2.0f / 3.0f,  // cos for order 1
        0.774597f,    // order 2
        0.861136f,    // order 3
    };
    const float c = kThetaMax[order];

    for (int l = 0; l <= order; ++l) {
        float p;
        switch (l) {
            case 0: p = 1.0f; break;
            case 1: p = c; break;
            case 2: p = 0.5f * (3.0f * c * c - 1.0f); break;
            default: p = 0.5f * c * (5.0f * c * c - 3.0f); break;
        }
        out[l] = p;
    }
    if (order == 0) out[0] = 1.0f;
}

// ---------------------------------------------------------------------------
// Speaker layouts
// ---------------------------------------------------------------------------

namespace {
SphericalCoord sc(float az, float el) { return SphericalCoord{az, el, 1.0f}; }
} // namespace

SpeakerLayout SpeakerLayout::cube8() {
    SpeakerLayout l;
    const float e = 35.26f;  // cube vertex elevation
    for (int i = 0; i < 4; ++i) {
        const float az = 45.0f + 90.0f * static_cast<float>(i);
        l.positions.push_back(sc(az, e));
        l.positions.push_back(sc(az, -e));
    }
    return l;
}

SpeakerLayout SpeakerLayout::dodeca12() {
    // 12 near-uniform points (icosahedral vertices) -- good through 2nd order.
    SpeakerLayout l;
    const float e = 26.565f;
    for (int i = 0; i < 5; ++i) {
        const float az = 72.0f * static_cast<float>(i);
        l.positions.push_back(sc(az, e));
        l.positions.push_back(sc(az + 36.0f, -e));
    }
    l.positions.push_back(sc(0.0f, 90.0f));
    l.positions.push_back(sc(0.0f, -90.0f));
    return l;
}

SpeakerLayout SpeakerLayout::sphere26() {
    // 26-point Lebedev-style grid: 6 axes + 12 edges + 8 corners.
    SpeakerLayout l;
    l.positions.push_back(sc(0.0f, 0.0f));
    l.positions.push_back(sc(90.0f, 0.0f));
    l.positions.push_back(sc(180.0f, 0.0f));
    l.positions.push_back(sc(270.0f, 0.0f));
    l.positions.push_back(sc(0.0f, 90.0f));
    l.positions.push_back(sc(0.0f, -90.0f));
    for (int i = 0; i < 4; ++i) {
        const float az = 45.0f + 90.0f * static_cast<float>(i);
        l.positions.push_back(sc(az, 0.0f));
    }
    for (int i = 0; i < 4; ++i) {
        const float az = 90.0f * static_cast<float>(i);
        l.positions.push_back(sc(az, 45.0f));
        l.positions.push_back(sc(az, -45.0f));
    }
    for (int i = 0; i < 4; ++i) {
        const float az = 45.0f + 90.0f * static_cast<float>(i);
        l.positions.push_back(sc(az, 35.26f));
        l.positions.push_back(sc(az, -35.26f));
    }
    return l;
}

SpeakerLayout SpeakerLayout::immersive714() {
    SpeakerLayout l;
    // Ear-level bed: L, R, C, Ls, Rs, Lb, Rb (LFE is handled separately).
    l.positions.push_back(sc(30.0f, 0.0f));    // L
    l.positions.push_back(sc(-30.0f, 0.0f));   // R
    l.positions.push_back(sc(0.0f, 0.0f));     // C
    l.positions.push_back(sc(90.0f, 0.0f));    // Ls
    l.positions.push_back(sc(-90.0f, 0.0f));   // Rs
    l.positions.push_back(sc(150.0f, 0.0f));   // Lb
    l.positions.push_back(sc(-150.0f, 0.0f));  // Rb
    // Height layer.
    l.positions.push_back(sc(45.0f, 45.0f));    // Ltf
    l.positions.push_back(sc(-45.0f, 45.0f));   // Rtf
    l.positions.push_back(sc(135.0f, 45.0f));   // Ltr
    l.positions.push_back(sc(-135.0f, 45.0f));  // Rtr
    return l;
}

// ---------------------------------------------------------------------------
// AmbisonicDecoder
//
// Mode matching: with Y the (channels x speakers) SH matrix evaluated at the
// speaker directions, the decoder is the pseudo-inverse D = Y^T (Y Y^T)^-1.
// For near-uniform layouts Y Y^T is close to (S/C) I, so we solve the small
// C x C system directly with Gauss-Jordan and Tikhonov regularisation for
// robustness on irregular layouts.
// ---------------------------------------------------------------------------

namespace {

bool invertMatrix(std::vector<double>& m, std::size_t n) {
    std::vector<double> inv(n * n, 0.0);
    for (std::size_t i = 0; i < n; ++i) inv[i * n + i] = 1.0;

    for (std::size_t col = 0; col < n; ++col) {
        // Partial pivot.
        std::size_t pivot = col;
        double best = std::fabs(m[col * n + col]);
        for (std::size_t r = col + 1; r < n; ++r) {
            const double v = std::fabs(m[r * n + col]);
            if (v > best) {
                best = v;
                pivot = r;
            }
        }
        if (best < 1.0e-12) return false;

        if (pivot != col) {
            for (std::size_t k = 0; k < n; ++k) {
                std::swap(m[col * n + k], m[pivot * n + k]);
                std::swap(inv[col * n + k], inv[pivot * n + k]);
            }
        }

        const double d = m[col * n + col];
        const double invd = 1.0 / d;
        for (std::size_t k = 0; k < n; ++k) {
            m[col * n + k] *= invd;
            inv[col * n + k] *= invd;
        }

        for (std::size_t r = 0; r < n; ++r) {
            if (r == col) continue;
            const double f = m[r * n + col];
            if (f == 0.0) continue;
            for (std::size_t k = 0; k < n; ++k) {
                m[r * n + k] -= f * m[col * n + k];
                inv[r * n + k] -= f * inv[col * n + k];
            }
        }
    }
    m.swap(inv);
    return true;
}

} // namespace

bool AmbisonicDecoder::prepare(int order, const SpeakerLayout& layout, bool maxRe) {
    ready_ = false;
    order_ = std::clamp(order, 0, kMaxAmbisonicOrder);
    hoaChannels_ = ambisonicChannels(order_);
    numSpeakers_ = layout.size();

    if (numSpeakers_ < hoaChannels_) return false;  // under-determined

    const std::size_t C = hoaChannels_;
    const std::size_t S = numSpeakers_;

    // Y: C x S spherical harmonics at each speaker direction.
    std::vector<double> Y(C * S, 0.0);
    std::array<float, kMaxAmbisonicChannels> sh{};
    for (std::size_t s = 0; s < S; ++s) {
        evaluateSphericalHarmonics(layout.positions[s].toCartesian().normalized(), order_,
                                   sh.data());
        for (std::size_t c = 0; c < C; ++c) Y[c * S + s] = sh[c];
    }

    // G = Y Y^T  (C x C), Tikhonov-regularised.
    std::vector<double> G(C * C, 0.0);
    for (std::size_t i = 0; i < C; ++i) {
        for (std::size_t j = 0; j < C; ++j) {
            double acc = 0.0;
            for (std::size_t s = 0; s < S; ++s) acc += Y[i * S + s] * Y[j * S + s];
            G[i * C + j] = acc;
        }
    }
    const double lambda = 1.0e-5 * static_cast<double>(S);
    for (std::size_t i = 0; i < C; ++i) G[i * C + i] += lambda;

    if (!invertMatrix(G, C)) return false;

    // D = Y^T G^-1  -> (S x C), row-major [speaker][channel].
    matrix_.assign(S * C, 0.0f);
    for (std::size_t s = 0; s < S; ++s) {
        for (std::size_t c = 0; c < C; ++c) {
            double acc = 0.0;
            for (std::size_t k = 0; k < C; ++k) acc += Y[k * S + s] * G[k * C + c];
            matrix_[s * C + c] = static_cast<float>(acc);
        }
    }

    // max-rE degree weighting: concentrates the energy vector, which is the
    // perceptually correct decode above the ~700 Hz localisation transition.
    if (maxRe && order_ > 0) {
        std::array<float, kMaxAmbisonicOrder + 1> g{};
        maxReGains(order_, g.data());

        // Preserve total energy so enabling max-rE does not change loudness.
        double num = 0.0, den = 0.0;
        for (int l = 0; l <= order_; ++l) {
            const double count = 2.0 * l + 1.0;
            num += count * g[static_cast<std::size_t>(l)] * g[static_cast<std::size_t>(l)];
            den += count;
        }
        const float norm = (num > 0.0) ? static_cast<float>(std::sqrt(den / num)) : 1.0f;

        for (std::size_t s = 0; s < S; ++s) {
            for (int l = 0; l <= order_; ++l) {
                const std::size_t begin = static_cast<std::size_t>(l * l);
                const std::size_t end = static_cast<std::size_t>((l + 1) * (l + 1));
                const float w = g[static_cast<std::size_t>(l)] * norm;
                for (std::size_t c = begin; c < end && c < C; ++c) matrix_[s * C + c] *= w;
            }
        }
    }

    ready_ = true;
    return true;
}

void AmbisonicDecoder::decodeFrame(const float* FSX_RESTRICT hoa,
                                   float* FSX_RESTRICT out) const noexcept {
    if (!ready_) return;
    const std::size_t C = hoaChannels_;
    for (std::size_t s = 0; s < numSpeakers_; ++s) {
        const float* row = matrix_.data() + s * C;
        float acc = 0.0f;
        for (std::size_t c = 0; c < C; ++c) acc += row[c] * hoa[c];
        out[s] = acc;
    }
}

void AmbisonicDecoder::decodeBlock(const float* const* hoa, float* const* out,
                                   std::size_t frames) const noexcept {
    if (!ready_) return;
    const std::size_t C = hoaChannels_;
    for (std::size_t s = 0; s < numSpeakers_; ++s) {
        const float* row = matrix_.data() + s * C;
        float* dst = out[s];
        rt::vecClear(dst, frames);
        for (std::size_t c = 0; c < C; ++c) {
            const float g = row[c];
            if (g == 0.0f) continue;
            rt::vecAddScaled(dst, hoa[c], g, frames);
        }
    }
}

// ---------------------------------------------------------------------------
// AmbisonicRotator
//
// Degree 1 rotates with the 3x3 rotation matrix directly. Degrees 2 and 3 use
// the recursive Ivanic-Ruedenberg construction from the degree-1 block, which
// is numerically stable and avoids Wigner-D/Euler-angle singularities.
// ---------------------------------------------------------------------------

namespace {

// Ivanic & Ruedenberg (1996, with the 1998 erratum) helper functions.
// R is the degree-1 block indexed [-1,0,1] x [-1,0,1] as a 3x3.
inline float centeredGet(const float* M, int dim, int i, int j) {
    const int c = (dim - 1) / 2;
    return M[static_cast<std::size_t>((i + c) * dim + (j + c))];
}

float P(int i, int l, int a, int b, const float* R1, const float* Rlm1) {
    const int lm1 = 2 * (l - 1) + 1;
    const float ri1 = centeredGet(R1, 3, i, 1);
    const float rim1 = centeredGet(R1, 3, i, -1);
    const float ri0 = centeredGet(R1, 3, i, 0);

    if (b == l) {
        return ri1 * centeredGet(Rlm1, lm1, a, l - 1) -
               rim1 * centeredGet(Rlm1, lm1, a, -l + 1);
    }
    if (b == -l) {
        return ri1 * centeredGet(Rlm1, lm1, a, -l + 1) +
               rim1 * centeredGet(Rlm1, lm1, a, l - 1);
    }
    return ri0 * centeredGet(Rlm1, lm1, a, b);
}

float U(int l, int m, int n, const float* R1, const float* Rlm1) {
    return P(0, l, m, n, R1, Rlm1);
}

float V(int l, int m, int n, const float* R1, const float* Rlm1) {
    if (m == 0) {
        return P(1, l, 1, n, R1, Rlm1) + P(-1, l, -1, n, R1, Rlm1);
    }
    if (m > 0) {
        const float d = (m == 1) ? 1.0f : 0.0f;
        return P(1, l, m - 1, n, R1, Rlm1) * std::sqrt(1.0f + d) -
               P(-1, l, -m + 1, n, R1, Rlm1) * (1.0f - d);
    }
    const float d = (m == -1) ? 1.0f : 0.0f;
    return P(1, l, m + 1, n, R1, Rlm1) * (1.0f - d) +
           P(-1, l, -m - 1, n, R1, Rlm1) * std::sqrt(1.0f + d);
}

float W(int l, int m, int n, const float* R1, const float* Rlm1) {
    if (m == 0) return 0.0f;
    if (m > 0) {
        return P(1, l, m + 1, n, R1, Rlm1) + P(-1, l, -m - 1, n, R1, Rlm1);
    }
    return P(1, l, m - 1, n, R1, Rlm1) - P(-1, l, -m + 1, n, R1, Rlm1);
}

/// Build the degree-l rotation block from degree-1 and degree-(l-1).
void buildBlock(int l, const float* R1, const float* Rlm1, float* Rl) {
    const int dim = 2 * l + 1;
    for (int m = -l; m <= l; ++m) {
        for (int n = -l; n <= l; ++n) {
            const float denom = (std::abs(n) == l) ? static_cast<float>((2 * l) * (2 * l - 1))
                                                   : static_cast<float>((l + n) * (l - n));
            const float u = std::sqrt(static_cast<float>((l + m) * (l - m)) / denom);
            const float v = std::sqrt(static_cast<float>((1 + (m == 0 ? 1 : 0)) *
                                                         (l + std::abs(m) - 1) *
                                                         (l + std::abs(m))) /
                                      denom) *
                            (1.0f - 2.0f * (m == 0 ? 1.0f : 0.0f)) * 0.5f;
            const float w = std::sqrt(static_cast<float>((l - std::abs(m) - 1) *
                                                         (l - std::abs(m))) /
                                      denom) *
                            (1.0f - (m == 0 ? 1.0f : 0.0f)) * -0.5f;

            float acc = 0.0f;
            if (u != 0.0f) acc += u * U(l, m, n, R1, Rlm1);
            if (v != 0.0f) acc += v * V(l, m, n, R1, Rlm1);
            if (w != 0.0f) acc += w * W(l, m, n, R1, Rlm1);
            Rl[static_cast<std::size_t>((m + l) * dim + (n + l))] = acc;
        }
    }
}

} // namespace

void AmbisonicRotator::setOrder(int order) noexcept {
    order_ = std::clamp(order, 0, kMaxAmbisonicOrder);
    channels_ = ambisonicChannels(order_);
    identity_ = true;
}

void AmbisonicRotator::setOrientation(const HeadOrientation& o) noexcept {
    const float y = o.yawDeg * rt::kDegToRad;
    const float p = o.pitchDeg * rt::kDegToRad;
    const float r = o.rollDeg * rt::kDegToRad;

    identity_ = (std::fabs(y) < 1.0e-6f && std::fabs(p) < 1.0e-6f && std::fabs(r) < 1.0e-6f);
    if (identity_) return;

    const float cy = std::cos(y), sy = std::sin(y);
    // +Y is LEFT, so a right-handed rotation about +Y by a positive angle
    // points the nose DOWN. `HeadOrientation::pitchDeg` is documented as
    // positive = look UP, so the pitch term is negated here. This must stay in
    // lock-step with `ListenerFrame::rebuild()`.
    const float cp = std::cos(p), sp = -std::sin(p);
    const float cr = std::cos(r), sr = std::sin(r);

    // Head-to-world rotation R = Rz(yaw) * Ry(-pitch) * Rx(roll); its columns are
    // the listener's forward/left/up axes, exactly as `ListenerFrame` builds them.
    const Vec3 fwd{cy * cp, sy * cp, -sp};
    const Vec3 left{cy * sp * sr - sy * cr, sy * sp * sr + cy * cr, cp * sr};
    const Vec3 up{cy * sp * cr + sy * sr, sy * sp * cr - cy * sr, cp * cr};

    // Rotating the sound field into the listener's frame is the WORLD-TO-LOCAL
    // map R^T (= ListenerFrame::toLocal), whose rows are those same axes.
    // Negating the Euler angles is NOT equivalent to transposing unless the
    // multiplication order is also reversed, so build the transpose directly.
    const float m[9] = {
        fwd.x,  fwd.y,  fwd.z,
        left.x, left.y, left.z,
        up.x,   up.y,   up.z};

    // Degree-1 block in ACN order (Y, Z, X) <- SH axes map to (y, z, x).
    // Index the block as [-1,0,1] = [y, z, x].
    const int map[3] = {1, 2, 0};  // block index -> cartesian axis
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            d1_[static_cast<std::size_t>(i * 3 + j)] =
                m[static_cast<std::size_t>(map[i] * 3 + map[j])];
        }
    }

    if (order_ >= 2) buildBlock(2, d1_.data(), d1_.data(), d2_.data());
    if (order_ >= 3) buildBlock(3, d1_.data(), d2_.data(), d3_.data());
}

void AmbisonicRotator::rotateFrame(float* FSX_RESTRICT hoa) const noexcept {
    if (identity_ || order_ == 0) return;

    float* s = scratch_.data();
    for (std::size_t i = 0; i < channels_; ++i) s[i] = hoa[i];

    // Degree 0 passes through unchanged (omnidirectional).
    // Degree 1: ACN 1..3
    for (int i = 0; i < 3; ++i) {
        float acc = 0.0f;
        for (int j = 0; j < 3; ++j) acc += d1_[static_cast<std::size_t>(i * 3 + j)] * s[1 + j];
        hoa[1 + i] = acc;
    }
    if (order_ < 2) return;

    for (int i = 0; i < 5; ++i) {
        float acc = 0.0f;
        for (int j = 0; j < 5; ++j) acc += d2_[static_cast<std::size_t>(i * 5 + j)] * s[4 + j];
        hoa[4 + i] = acc;
    }
    if (order_ < 3) return;

    for (int i = 0; i < 7; ++i) {
        float acc = 0.0f;
        for (int j = 0; j < 7; ++j) acc += d3_[static_cast<std::size_t>(i * 7 + j)] * s[9 + j];
        hoa[9 + i] = acc;
    }
}

// ---------------------------------------------------------------------------
// VbapPanner
// ---------------------------------------------------------------------------

bool VbapPanner::preparePlanar(const SpeakerLayout& layout) {
    // Degenerate (coplanar-with-origin) layout: horizontal rings, stereo,
    // quad, 5.1 beds. Fall back to classic 2D pair-wise VBAP.
    (void)layout;
    const std::size_t n = speakers_.size();
    if (n < 2) return false;

    // Plane normal from the speaker scatter: the eigenvector of the smallest
    // spread. For a coplanar set the cross product of any two independent
    // speaker vectors gives it directly; average over all pairs for stability.
    Vec3 nrm{0.0f, 0.0f, 0.0f};
    for (std::size_t i = 0; i < n; ++i) {
        for (std::size_t j = i + 1; j < n; ++j) {
            Vec3 c = cross(speakers_[i], speakers_[j]);
            if (c.lengthSquared() < 1.0e-12f) continue;
            c = c.normalized();
            // Keep a consistent hemisphere so the contributions do not cancel.
            if (dot(c, nrm) < 0.0f) c = c * -1.0f;
            nrm = nrm + c;
        }
    }
    if (nrm.lengthSquared() < 1.0e-12f) return false;
    nrm = nrm.normalized();

    // Orthonormal in-plane basis.
    Vec3 ref{1.0f, 0.0f, 0.0f};
    if (std::fabs(dot(ref, nrm)) > 0.9f) ref = Vec3{0.0f, 0.0f, 1.0f};
    planeU_ = cross(nrm, ref).normalized();
    planeV_ = cross(nrm, planeU_).normalized();

    // Sort speakers by in-plane angle, then pair adjacent ones around the ring.
    std::vector<std::pair<float, std::size_t>> order;
    order.reserve(n);
    for (std::size_t i = 0; i < n; ++i) {
        const float u = dot(speakers_[i], planeU_);
        const float v = dot(speakers_[i], planeV_);
        if (std::fabs(u) < 1.0e-6f && std::fabs(v) < 1.0e-6f) continue;
        order.emplace_back(std::atan2(v, u), i);
    }
    if (order.size() < 2) return false;
    std::sort(order.begin(), order.end(),
              [](const auto& a, const auto& b) { return a.first < b.first; });

    pairs_.clear();
    const std::size_t m = order.size();
    for (std::size_t k = 0; k < m; ++k) {
        const std::size_t ia = order[k].second;
        const std::size_t ib = order[(k + 1) % m].second;
        if (m == 2 && k == 1) break;  // a 2-speaker layout has a single arc

        const float ax = dot(speakers_[ia], planeU_), ay = dot(speakers_[ia], planeV_);
        const float bx = dot(speakers_[ib], planeU_), by = dot(speakers_[ib], planeV_);

        // Invert the 2x2 base [a b] (columns are the speaker vectors).
        const float det = ax * by - bx * ay;
        if (std::fabs(det) < 1.0e-6f) continue;  // collinear pair (antipodal)
        const float invDet = 1.0f / det;

        Pair p;
        p.a = ia;
        p.b = ib;
        p.inv[0] = by * invDet;
        p.inv[1] = -bx * invDet;
        p.inv[2] = -ay * invDet;
        p.inv[3] = ax * invDet;
        pairs_.push_back(p);
    }

    if (pairs_.empty()) return false;
    planar_ = true;
    ready_ = true;
    return true;
}

void VbapPanner::planarGains(const Vec3& unitDir, float* FSX_RESTRICT gains) const noexcept {
    // Project the direction onto the speaker plane. An out-of-plane source
    // (e.g. overhead on a horizontal ring) collapses to its in-plane bearing
    // rather than falling silent.
    const Vec3 p = unitDir.normalized();
    float u = dot(p, planeU_);
    float v = dot(p, planeV_);
    const float len = std::sqrt(u * u + v * v);
    if (len < 1.0e-6f) {
        // Directly along the plane normal: no bearing information. Spread
        // evenly so energy is preserved instead of dropping to silence.
        const float g = 1.0f / std::sqrt(static_cast<float>(speakers_.size()));
        for (std::size_t i = 0; i < speakers_.size(); ++i) gains[i] = g;
        return;
    }
    u /= len;
    v /= len;

    std::size_t bestIdx = 0;
    float bestScore = -1.0e30f;
    float bestG[2] = {0.0f, 0.0f};

    for (std::size_t k = 0; k < pairs_.size(); ++k) {
        const Pair& pr = pairs_[k];
        const float g0 = pr.inv[0] * u + pr.inv[1] * v;
        const float g1 = pr.inv[2] * u + pr.inv[3] * v;
        const float score = std::min(g0, g1);
        if (score > bestScore) {
            bestScore = score;
            bestIdx = k;
            bestG[0] = g0;
            bestG[1] = g1;
        }
        if (score >= -1.0e-6f) break;  // inside this arc
    }

    const Pair& pr = pairs_[bestIdx];
    float g0 = std::max(bestG[0], 0.0f);
    float g1 = std::max(bestG[1], 0.0f);
    const float norm = std::sqrt(g0 * g0 + g1 * g1);
    if (norm < 1.0e-9f) {
        // PARTIAL ring (stereo pair, front-only bar, any arc that does not
        // close the circle): the direction lies in the uncovered gap, so both
        // pair gains clamp to zero. Project onto the nearest boundary speaker
        // instead of emitting silence -- the same rule the 3D path applies to
        // holes in a non-full-sphere hull.
        std::size_t nearest = 0;
        float bestDot = -2.0f;
        for (std::size_t i = 0; i < speakers_.size(); ++i) {
            const float su = dot(speakers_[i], planeU_);
            const float sv = dot(speakers_[i], planeV_);
            const float sl = std::sqrt(su * su + sv * sv);
            if (sl < 1.0e-6f) continue;
            const float d = (u * su + v * sv) / sl;
            if (d > bestDot) {
                bestDot = d;
                nearest = i;
            }
        }
        if (bestDot > -2.0f) gains[nearest] += 1.0f;
        return;
    }
    const float inv = 1.0f / norm;
    gains[pr.a] += g0 * inv;
    gains[pr.b] += g1 * inv;
}

bool VbapPanner::prepare(const SpeakerLayout& layout) {
    ready_ = false;
    planar_ = false;
    speakers_.clear();
    triplets_.clear();
    pairs_.clear();

    if (layout.size() < 2) return false;
    if (layout.size() > kMaxSpeakers) return false;
    speakers_.reserve(layout.size());
    for (const auto& p : layout.positions) {
        speakers_.push_back(p.toCartesian().normalized());
    }

    const std::size_t n = speakers_.size();

    // Build candidate triangles: every triple whose circumscribed cap contains
    // no other speaker is a face of the convex hull (Delaunay on the sphere).
    for (std::size_t a = 0; a < n; ++a) {
        for (std::size_t b = a + 1; b < n; ++b) {
            for (std::size_t c = b + 1; c < n; ++c) {
                const Vec3& A = speakers_[a];
                const Vec3& B = speakers_[b];
                const Vec3& C = speakers_[c];

                const Vec3 nrm = cross(B - A, C - A);
                const float nl = nrm.length();
                if (nl < 1.0e-5f) continue;  // degenerate / collinear

                const Vec3 un = nrm * (1.0f / nl);
                const float plane = dot(un, A);
                if (std::fabs(plane) < 1.0e-4f) continue;  // passes through origin

                // Orient outward.
                const Vec3 outward = (plane > 0.0f) ? un : un * -1.0f;
                const float d = std::fabs(plane);

                // Hull face test: no other speaker beyond this plane.
                bool isFace = true;
                for (std::size_t k = 0; k < n && isFace; ++k) {
                    if (k == a || k == b || k == c) continue;
                    if (dot(outward, speakers_[k]) > d + 1.0e-4f) isFace = false;
                }
                if (!isFace) continue;

                // Invert the 3x3 base [A B C] (columns are the speaker vectors).
                const float m00 = A.x, m01 = B.x, m02 = C.x;
                const float m10 = A.y, m11 = B.y, m12 = C.y;
                const float m20 = A.z, m21 = B.z, m22 = C.z;

                const float det = m00 * (m11 * m22 - m12 * m21) -
                                  m01 * (m10 * m22 - m12 * m20) +
                                  m02 * (m10 * m21 - m11 * m20);
                if (std::fabs(det) < 1.0e-6f) continue;
                const float invDet = 1.0f / det;

                Triplet t;
                t.a = a;
                t.b = b;
                t.c = c;
                t.inv[0] = (m11 * m22 - m12 * m21) * invDet;
                t.inv[1] = (m02 * m21 - m01 * m22) * invDet;
                t.inv[2] = (m01 * m12 - m02 * m11) * invDet;
                t.inv[3] = (m12 * m20 - m10 * m22) * invDet;
                t.inv[4] = (m00 * m22 - m02 * m20) * invDet;
                t.inv[5] = (m02 * m10 - m00 * m12) * invDet;
                t.inv[6] = (m10 * m21 - m11 * m20) * invDet;
                t.inv[7] = (m01 * m20 - m00 * m21) * invDet;
                t.inv[8] = (m00 * m11 - m01 * m10) * invDet;
                triplets_.push_back(t);
            }
        }
    }

    // No valid triangle => the layout is degenerate (all speakers coplanar
    // with the origin, e.g. a horizontal ring). Use 2D pair panning instead of
    // failing, which is what stereo / quad / 5.1 beds need.
    if (triplets_.empty()) return preparePlanar(layout);

    ready_ = true;
    return true;
}

void VbapPanner::gainsFor(const Vec3& unitDir, float* FSX_RESTRICT gains) const noexcept {
    const std::size_t n = speakers_.size();
    rt::vecClear(gains, n);
    if (!ready_) return;

    if (planar_) {
        planarGains(unitDir, gains);
        return;
    }

    const Vec3 p = unitDir.normalized();

    // Pass 1: find the face that actually contains the direction (all
    // barycentric gains non-negative). Track the least-negative candidate so a
    // direction landing in a numerical crack between faces still resolves.
    std::size_t bestIdx = 0;
    float bestScore = -1.0e30f;
    float bestG[3] = {0.0f, 0.0f, 0.0f};
    bool inside = false;

    for (std::size_t t = 0; t < triplets_.size(); ++t) {
        const Triplet& tri = triplets_[t];
        const float g0 = tri.inv[0] * p.x + tri.inv[1] * p.y + tri.inv[2] * p.z;
        const float g1 = tri.inv[3] * p.x + tri.inv[4] * p.y + tri.inv[5] * p.z;
        const float g2 = tri.inv[6] * p.x + tri.inv[7] * p.y + tri.inv[8] * p.z;

        const float score = std::min(std::min(g0, g1), g2);
        if (score > bestScore) {
            bestScore = score;
            bestIdx = t;
            bestG[0] = g0;
            bestG[1] = g1;
            bestG[2] = g2;
        }
        if (score >= -1.0e-6f) {  // inside this face; done
            inside = true;
            break;
        }
    }

    // Pass 2: the direction lies in a HOLE of a partial layout (below a dome,
    // under a 7.1.4 rig). Clamping the negative gains of the least-bad face and
    // renormalising can still yield all-zero gains, which would silence the
    // source. Instead pick the face whose clamped gain vector points closest to
    // the requested direction -- that projects the source onto the nearest hull
    // edge/vertex, the standard behaviour for non-full-sphere layouts.
    if (!inside) {
        float bestDot = -1.0e30f;
        bool found = false;
        for (std::size_t t = 0; t < triplets_.size(); ++t) {
            const Triplet& tri = triplets_[t];
            const float c0 = std::max(tri.inv[0] * p.x + tri.inv[1] * p.y + tri.inv[2] * p.z, 0.0f);
            const float c1 = std::max(tri.inv[3] * p.x + tri.inv[4] * p.y + tri.inv[5] * p.z, 0.0f);
            const float c2 = std::max(tri.inv[6] * p.x + tri.inv[7] * p.y + tri.inv[8] * p.z, 0.0f);
            const float mag = std::sqrt(c0 * c0 + c1 * c1 + c2 * c2);
            if (mag < 1.0e-9f) continue;

            const Vec3 v = speakers_[tri.a] * c0 + speakers_[tri.b] * c1 + speakers_[tri.c] * c2;
            const float vl = v.length();
            if (vl < 1.0e-9f) continue;
            const float score = dot(v, p) / vl;
            if (score > bestDot) {
                bestDot = score;
                bestIdx = t;
                bestG[0] = c0;
                bestG[1] = c1;
                bestG[2] = c2;
                found = true;
            }
        }
        // Absolutely no face can represent this direction (extremely sparse
        // layout): fall back to the single nearest speaker so the source is
        // still audible.
        if (!found) {
            std::size_t nearest = 0;
            float nd = -1.0e30f;
            for (std::size_t i = 0; i < n; ++i) {
                const float s = dot(speakers_[i], p);
                if (s > nd) {
                    nd = s;
                    nearest = i;
                }
            }
            gains[nearest] = 1.0f;
            return;
        }
    }

    const Triplet& tri = triplets_[bestIdx];
    float g0 = std::max(bestG[0], 0.0f);
    float g1 = std::max(bestG[1], 0.0f);
    float g2 = std::max(bestG[2], 0.0f);

    // Constant-power normalisation.
    const float norm = std::sqrt(g0 * g0 + g1 * g1 + g2 * g2);
    if (norm < 1.0e-9f) return;
    const float inv = 1.0f / norm;
    g0 *= inv;
    g1 *= inv;
    g2 *= inv;

    gains[tri.a] += g0;
    gains[tri.b] += g1;
    gains[tri.c] += g2;
}

void VbapPanner::gainsForSpread(const Vec3& unitDir, float spread,
                                float* FSX_RESTRICT gains) const noexcept {
    const std::size_t n = speakers_.size();
    rt::vecClear(gains, n);
    if (!ready_) return;

    const float s = rt::clampUnit(spread);
    if (s <= 1.0e-4f) {
        gainsFor(unitDir, gains);
        return;
    }

    // MDAP: average the panning gains over a ring of directions around the
    // nominal one. The ring radius grows with `spread`, widening the source
    // while preserving its centroid direction.
    const Vec3 p = unitDir.normalized();
    Vec3 up{0.0f, 0.0f, 1.0f};
    if (std::fabs(dot(up, p)) > 0.95f) up = Vec3{1.0f, 0.0f, 0.0f};
    const Vec3 t1 = cross(up, p).normalized();
    const Vec3 t2 = cross(p, t1).normalized();

    const float radius = s * 60.0f * rt::kDegToRad;
    constexpr int kRing = 6;

    // Centre direction carries a reducing share as spread increases.
    // `prepare()` rejects layouts above kMaxSpeakers, so this stack scratch is
    // always large enough and the routine stays allocation-free.
    float tmp[kMaxSpeakers];
    if (n > kMaxSpeakers) return;

    gainsFor(p, gains);
    rt::vecScale(gains, 1.0f - 0.5f * s, n);

    const float ringGain = (0.5f * s) / static_cast<float>(kRing);
    for (int i = 0; i < kRing; ++i) {
        const float phi = rt::kTwoPi * static_cast<float>(i) / static_cast<float>(kRing);
        const Vec3 off = (t1 * std::cos(phi) + t2 * std::sin(phi)) * std::sin(radius);
        const Vec3 dir = (p * std::cos(radius) + off).normalized();
        gainsFor(dir, tmp);
        rt::vecAddScaled(gains, tmp, ringGain, n);
    }

    // Renormalise to constant power.
    float e = 0.0f;
    for (std::size_t i = 0; i < n; ++i) e += gains[i] * gains[i];
    if (e > 1.0e-12f) rt::vecScale(gains, 1.0f / std::sqrt(e), n);
}

} // namespace frostsoulx::spatial
