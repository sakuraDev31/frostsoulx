#include "frostsoulx/spatial/hrtf.h"

#include <algorithm>
#include <cmath>

namespace frostsoulx::spatial {

namespace {

/// Fractional-delay windowed-sinc kernel written additively into `dst`.
/// A 3rd-order Lagrange kernel is too soft for HRIR synthesis; a 8-tap
/// Blackman-windowed sinc keeps the passband flat to ~0.45 fs.
void addFractionalImpulse(float* dst, std::size_t taps, float delaySamples, float gain) noexcept {
    if (!std::isfinite(delaySamples) || !std::isfinite(gain)) return;
    constexpr int kHalf = 4;  // 8-tap kernel
    const float clamped = std::max(delaySamples, 0.0f);
    const int base = static_cast<int>(std::floor(clamped));
    const float frac = clamped - static_cast<float>(base);

    for (int k = -kHalf + 1; k <= kHalf; ++k) {
        const int idx = base + k;
        if (idx < 0 || idx >= static_cast<int>(taps)) continue;
        const float t = static_cast<float>(k) - frac;
        float s;
        if (std::fabs(t) < 1.0e-6f) {
            s = 1.0f;
        } else {
            const float pt = rt::kPi * t;
            s = std::sin(pt) / pt;
        }
        // Blackman window over the kernel support.
        const float w = 0.42f -
                        0.5f * std::cos(rt::kPi * (t + kHalf) / kHalf) +
                        0.08f * std::cos(rt::kTwoPi * (t + kHalf) / kHalf);
        dst[static_cast<std::size_t>(idx)] += gain * s * w;
    }
}

/// Apply a one-pole/one-zero IIR in place: y[n] = b0 x[n] + b1 x[n-1] - a1 y[n-1].
void applyBiquad1(float* data, std::size_t n, const HeadShadowCoeffs& c) noexcept {
    float x1 = 0.0f, y1 = 0.0f;
    for (std::size_t i = 0; i < n; ++i) {
        const float x = data[i];
        const float y = c.b0 * x + c.b1 * x1 - c.a1 * y1;
        x1 = x;
        y1 = rt::flushDenormal(y);
        data[i] = y1;
    }
}

} // namespace

// ---------------------------------------------------------------------------
// Analytic components
// ---------------------------------------------------------------------------

float woodworthItdSeconds(float theta, float headRadius) noexcept {
    // Woodworth & Schlosberg spherical-head ITD. `theta` is the source azimuth
    // measured from the median plane (0 = front, +pi/2 = fully lateral):
    //
    //     ITD(theta) = (a / c) * (theta + sin(theta))
    //
    // At a = 87.5 mm this peaks at ~656 us laterally, which matches measured
    // human ITD ranges. This returns the TOTAL interaural difference; the
    // per-ear split is done by `earPathExcessSeconds`.
    const float a = std::clamp(headRadius, 0.05f, 0.12f);
    const float t = std::clamp(theta, -rt::kHalfPi, rt::kHalfPi);
    return (a / rt::kSpeedOfSound) * (t + std::sin(t));
}

namespace {

/// Per-ear path excess relative to the head centre, in seconds.
///
/// `cosPhi` is the cosine of the angle between the source direction and the
/// ear's outward normal (+1 = source directly at that ear).
///
///   * Illuminated ear (cosPhi > 0): the wave arrives early by a*cosPhi.
///   * Shadowed ear (cosPhi < 0): the wave creeps around the sphere from the
///     tangent point, arriving late by a*(phi - pi/2).
///
/// Summing the two ears reproduces Woodworth's (a/c)(theta + sin theta)
/// exactly, so the ITD is physically consistent rather than double counted.
float earPathExcessSeconds(float cosPhi, float headRadius) noexcept {
    const float a = std::clamp(headRadius, 0.05f, 0.12f);
    const float c = std::clamp(cosPhi, -1.0f, 1.0f);
    const float phi = std::acos(c);
    if (c >= 0.0f) {
        return -(a / rt::kSpeedOfSound) * c;  // early
    }
    return (a / rt::kSpeedOfSound) * (phi - rt::kHalfPi);  // late (creeping)
}

} // namespace

HeadShadowCoeffs headShadowFilter(float cosTheta, float headRadius,
                                  double sampleRate) noexcept {
    // Brown & Duda structural model. The spherical head behaves as a
    // single-pole/single-zero filter whose zero moves with incidence angle:
    //
    //   H(s) = (1 + alpha*s / (2*w0)) / (1 + s / (2*w0)),  w0 = c / a
    //
    // alpha in [0, 2]: 0 at the fully shadowed ear (max attenuation),
    // 2 at the fully illuminated ear (slight boost).
    HeadShadowCoeffs c;
    if (sampleRate <= 0.0) return c;

    const float a = std::clamp(headRadius, 0.05f, 0.12f);
    const float w0 = rt::kSpeedOfSound / a;

    // Angle from the ear axis: cosTheta = +1 -> ipsilateral, -1 -> contralateral.
    // kAlphaMin sets the depth of the contralateral shadow. Brown & Duda use
    // 0.1, which yields a broadband ILD near 22 dB at 90 deg -- steeper than
    // most measured sets. 0.25 lands the lateral ILD around 16 dB, matching
    // typical KEMAR/CIPIC broadband values without softening the HF cue.
    constexpr float kAlphaMin = 0.25f;
    const float alpha = (1.0f + kAlphaMin * 0.5f) +
                        (1.0f - kAlphaMin * 0.5f) * std::clamp(cosTheta, -1.0f, 1.0f);

    // Bilinear transform with the standard 2*fs prewarp factor.
    const float fs2 = 2.0f * static_cast<float>(sampleRate);
    const float denom = fs2 + w0;
    c.b0 = (w0 + alpha * fs2) / denom;
    c.b1 = (w0 - alpha * fs2) / denom;
    c.a1 = -(fs2 - w0) / denom;
    return c;
}

float nearFieldGain(float cosTheta, float range, float headRadius) noexcept {
    // Duda & Martens: inside roughly 1 m the ILD grows sharply because the
    // wavefront curvature no longer matches the far-field plane-wave model.
    // rho = range / headRadius; the correction vanishes as rho -> inf.
    const float a = std::clamp(headRadius, 0.05f, 0.12f);
    const float rho = std::max(range / a, 1.25f);
    if (rho > 16.0f) return 1.0f;

    const float c = std::clamp(cosTheta, -1.0f, 1.0f);
    // Proximity boost on the near ear, extra attenuation on the far ear.
    const float proximity = 1.0f / (1.0f - 0.85f / rho);
    const float shading = 1.0f + 0.5f * (1.0f - c) * (1.0f / rho);
    return std::clamp(proximity / shading, 0.25f, 4.0f);
}

// ---------------------------------------------------------------------------
// HrtfDatabase
// ---------------------------------------------------------------------------

bool HrtfDatabase::buildParametric(double sampleRate, std::size_t irTaps,
                                   float azStepDeg, float elStepDeg,
                                   const HrtfModelParams& params) {
    valid_ = false;
    if (sampleRate < 8000.0 || !rt::isPow2(irTaps) || irTaps < 32 || irTaps > 1024) return false;
    if (azStepDeg <= 0.0f || azStepDeg > 45.0f) return false;
    if (elStepDeg <= 0.0f || elStepDeg > 45.0f) return false;

    sampleRate_ = sampleRate;
    irTaps_ = irTaps;
    azStep_ = azStepDeg;
    elStep_ = elStepDeg;
    params_ = params;

    numAz_ = static_cast<std::size_t>(std::lround(360.0f / azStepDeg));
    numEl_ = static_cast<std::size_t>(std::lround(180.0f / elStepDeg)) + 1;
    if (numAz_ < 4 || numEl_ < 3) return false;

    hrir_.assign(numAz_ * numEl_ * 2 * irTaps_, 0.0f);
    itd_.assign(numAz_ * numEl_, 0.0f);

    const float fs = static_cast<float>(sampleRate);
    // Leading pad so per-ear fractional delays never run off the front of the
    // buffer. The largest negative excursion is a/c seconds (source directly
    // at one ear); add the 4-tap interpolation half-kernel on top. This pad is
    // a constant latency shared by every direction, so it neither colours the
    // response nor displaces the image.
    const float maxAdvance = (std::clamp(params.headRadius, 0.05f, 0.12f) /
                              rt::kSpeedOfSound) * fs;
    const float basePad = std::ceil(maxAdvance) + 5.0f;

    for (std::size_t ei = 0; ei < numEl_; ++ei) {
        const float elDeg = -90.0f + static_cast<float>(ei) * elStep_;
        const float el = elDeg * rt::kDegToRad;

        for (std::size_t ai = 0; ai < numAz_; ++ai) {
            const float azDeg = static_cast<float>(ai) * azStep_;
            const float az = azDeg * rt::kDegToRad;

            // Unit direction in listener-local space.
            const float ce = std::cos(el);
            const Vec3 dir{ce * std::cos(az), ce * std::sin(az), std::sin(el)};

            // Interaural axis is +Y (left). Ear normals point outward.
            const float cosLeft = dir.y;    // +1 when source is at the left ear
            const float cosRight = -dir.y;  // +1 when source is at the right ear

            // --- ITD (Woodworth, split per ear) ------------------------------
            // Each ear gets its own path excess relative to the head centre;
            // their difference equals the Woodworth ITD, so the pair is
            // physically consistent and centred (no net latency drift).
            const float dL = earPathExcessSeconds(cosLeft, params.headRadius) * fs;
            const float dR = earPathExcessSeconds(cosRight, params.headRadius) * fs;

            // Elevation compresses the ITD (sources overhead are equidistant).
            const float elFactor = std::cos(el);
            const float delayL = basePad + dL * elFactor;
            const float delayR = basePad + dR * elFactor;

            float* L = hrir_.data() + ((ei * numAz_ + ai) * 2) * irTaps_;
            float* R = hrir_.data() + ((ei * numAz_ + ai) * 2 + 1) * irTaps_;

            // --- Direct wavefront -------------------------------------------
            float gL = 1.0f;
            float gR = 1.0f;
            if (params.enableNearField) {
                gL = nearFieldGain(cosLeft, 1.0f, params.headRadius);
                gR = nearFieldGain(cosRight, 1.0f, params.headRadius);
            }
            addFractionalImpulse(L, irTaps_, delayL, gL);
            addFractionalImpulse(R, irTaps_, delayR, gR);

            // --- Pinna elevation notches ------------------------------------
            // Two negative reflections whose delays shorten as the source rises;
            // this synthesises the 6-10 kHz spectral notch that carries most of
            // the elevation cue, and the front/back disambiguation.
            if (params.enablePinna) {
                const float front = std::clamp(dir.x, -1.0f, 1.0f);
                for (int p = 0; p < 2; ++p) {
                    // Base delays ~0.1 ms and ~0.19 ms, modulated by elevation.
                    const float baseMs = (p == 0) ? 0.105f : 0.190f;
                    const float elevMod = 0.55f + 0.45f * std::cos(el * 0.85f + 0.35f);
                    const float frontMod = 0.85f + 0.15f * front;
                    const float d = baseMs * 0.001f * fs * elevMod * frontMod;
                    const float g = -params.pinnaGain * ((p == 0) ? 1.0f : 0.62f);
                    addFractionalImpulse(L, irTaps_, delayL + d, g * gL);
                    addFractionalImpulse(R, irTaps_, delayR + d, g * gR);
                }
            }

            // --- Torso / shoulder reflection --------------------------------
            // A single positive echo around 0.6-1.0 ms; strongest for sources
            // below the horizon, absent for sources overhead.
            if (params.enableTorso) {
                const float below = std::clamp(0.5f - 0.5f * std::sin(el), 0.0f, 1.0f);
                if (below > 0.01f) {
                    const float d = (0.62f + 0.38f * below) * 0.001f * fs;
                    const float g = params.torsoGain * below;
                    addFractionalImpulse(L, irTaps_, delayL + d, g * gL);
                    addFractionalImpulse(R, irTaps_, delayR + d, g * gR);
                }
            }

            // --- Head shadow -------------------------------------------------
            applyBiquad1(L, irTaps_, headShadowFilter(cosLeft, params.headRadius, sampleRate));
            applyBiquad1(R, irTaps_, headShadowFilter(cosRight, params.headRadius, sampleRate));

            itd_[ei * numAz_ + ai] = delayR - delayL;
        }
    }

    valid_ = true;
    return true;
}

bool HrtfDatabase::loadMeasuredSet(double sampleRate, std::size_t irTaps,
                                   std::size_t numAz, std::size_t numEl,
                                   float azStepDeg, float elStepDeg,
                                   const float* data, const float* itdSamples) {
    valid_ = false;
    if (sampleRate < 8000.0 || irTaps < 8 || numAz < 4 || numEl < 3) return false;
    if (data == nullptr) return false;

    sampleRate_ = sampleRate;
    irTaps_ = irTaps;
    numAz_ = numAz;
    numEl_ = numEl;
    azStep_ = azStepDeg;
    elStep_ = elStepDeg;

    hrir_.assign(data, data + numAz * numEl * 2 * irTaps);
    itd_.assign(numAz * numEl, 0.0f);
    if (itdSamples != nullptr) {
        std::copy(itdSamples, itdSamples + numAz * numEl, itd_.begin());
    }
    valid_ = true;
    return true;
}

HrirPair HrtfDatabase::render(const SphericalCoord& dir,
                              float* FSX_RESTRICT outLeft,
                              float* FSX_RESTRICT outRight) const noexcept {
    HrirPair out;
    if (!valid_ || outLeft == nullptr || outRight == nullptr) return out;

    const float az = wrapAzimuth(dir.azimuthDeg);
    const float el = clampElevation(dir.elevationDeg);

    // Grid coordinates.
    const float azPos = az / azStep_;
    const float elPos = std::max(0.0f, (el + 90.0f) / elStep_);

    std::size_t az0 = static_cast<std::size_t>(azPos) % numAz_;
    std::size_t az1 = (az0 + 1) % numAz_;
    float azFrac = azPos - std::floor(azPos);

    std::size_t el0 = static_cast<std::size_t>(std::floor(elPos));
    if (el0 >= numEl_ - 1) el0 = numEl_ - 2;
    const std::size_t el1 = el0 + 1;
    float elFrac = std::clamp(elPos - static_cast<float>(el0), 0.0f, 1.0f);

    azFrac = std::clamp(azFrac, 0.0f, 1.0f);

    // Bilinear weights over the four surrounding grid directions.
    const float w00 = (1.0f - azFrac) * (1.0f - elFrac);
    const float w10 = azFrac * (1.0f - elFrac);
    const float w01 = (1.0f - azFrac) * elFrac;
    const float w11 = azFrac * elFrac;

    rt::vecClear(outLeft, irTaps_);
    rt::vecClear(outRight, irTaps_);

    const struct { std::size_t a, e; float w; } corners[4] = {
        {az0, el0, w00}, {az1, el0, w10}, {az0, el1, w01}, {az1, el1, w11}};

    for (const auto& c : corners) {
        if (c.w <= 0.0f) continue;
        rt::vecAddScaled(outLeft, gridLeft(c.a, c.e), c.w, irTaps_);
        rt::vecAddScaled(outRight, gridRight(c.a, c.e), c.w, irTaps_);
    }

    // Interpolate the broadband ITD separately from the spectra. The grid
    // HRIRs already carry their own delay, so this value is reported for
    // diagnostics and for renderers that want to re-time the pair explicitly.
    float itd = 0.0f;
    for (const auto& c : corners) {
        if (c.w <= 0.0f) continue;
        itd += gridItd(c.a, c.e) * c.w;
    }

    // Near-field range correction is applied per render (the grid is built at
    // 1 m) so a moving source gets continuous proximity shading.
    if (params_.enableNearField && dir.distanceMeters > 0.0f && dir.distanceMeters < 2.0f) {
        const Vec3 u = SphericalCoord{az, el, 1.0f}.toCartesian();
        const float gl = nearFieldGain(u.y, dir.distanceMeters, params_.headRadius) /
                         std::max(nearFieldGain(u.y, 1.0f, params_.headRadius), 1.0e-6f);
        const float gr = nearFieldGain(-u.y, dir.distanceMeters, params_.headRadius) /
                         std::max(nearFieldGain(-u.y, 1.0f, params_.headRadius), 1.0e-6f);
        rt::vecScale(outLeft, std::clamp(gl, 0.25f, 4.0f), irTaps_);
        rt::vecScale(outRight, std::clamp(gr, 0.25f, 4.0f), irTaps_);
    }

    out.left = outLeft;
    out.right = outRight;
    out.taps = irTaps_;
    out.delayLeftSamples = itd < 0.0f ? -itd : 0.0f;
    out.delayRightSamples = itd > 0.0f ? itd : 0.0f;
    return out;
}

} // namespace frostsoulx::spatial
