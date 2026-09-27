#include "frostsoulx/spatial/rir_generator.h"
#include "frostsoulx/rt/rt_types.h"

#include <algorithm>
#include <cmath>
#include <random>

namespace frostsoulx::spatial {

RirGenerator::RirGenerator(const RirGeneratorConfig& cfg) : cfg_(cfg) {}

std::vector<GeometricReflectionPath> RirGenerator::calculateReflectionPaths(const SpaceProfile& space) const {
    std::vector<GeometricReflectionPath> paths;
    const float c = rt::kSpeedOfSound;
    const Vec3 dims = space.dimensions() * space.scale();
    const Vec3 src = space.sourcePosition();
    const Vec3 lis = space.listenerPosition();

    ListenerFrame listenerFrame;
    listenerFrame.setPosition(lis);
    listenerFrame.setOrientation(space.listenerOrientation());

    // -------------------------------------------------------------------------
    // 0. Direct Path
    // -------------------------------------------------------------------------
    const Vec3 directRay = src - lis;
    const float directDist = directRay.length();
    if (directDist > 1.0e-4f) {
        GeometricReflectionPath direct;
        direct.order = 0;
        direct.distanceMeters = directDist;
        direct.delaySeconds = directDist / c;
        direct.arrivalDirection = listenerFrame.toLocalSpherical(src);
        direct.broadbandGain = 1.0f / std::max(directDist, 0.5f);

        constexpr std::array<float, kNumAcousticBands> kAirAttenPerM = {
            0.000025f, 0.000075f, 0.00020f, 0.000475f, 0.00110f, 0.003375f
        };
        for (std::size_t b = 0; b < kNumAcousticBands; ++b) {
            direct.bandGains[b] = std::exp(-kAirAttenPerM[b] * directDist);
        }
        direct.isScattered = false;
        paths.push_back(direct);
    }

    // -------------------------------------------------------------------------
    // 1. Image Source Method (ISM) for Specular & Scattered Reflections
    // -------------------------------------------------------------------------
    const int maxOrder = std::clamp(cfg_.maxIsmOrder, 0, 5);

    // Multi-band boundary reflection factors:
    std::array<std::array<float, kNumAcousticBands>, 6> reflPerSurf{};
    std::array<float, 6> scatteringPerSurf{};
    for (std::size_t i = 0; i < 6; ++i) {
        const auto& b = space.boundary(static_cast<RoomSurface>(i));
        const float effOpen = std::clamp(std::max(b.openness, space.openness()), 0.0f, 1.0f);
        scatteringPerSurf[i] = b.material.scattering;
        for (std::size_t band = 0; band < kNumAcousticBands; ++band) {
            const float alpha = b.material.absorption[band];
            reflPerSurf[i][band] = std::sqrt(std::max(0.0f, (1.0f - alpha) * (1.0f - effOpen)));
        }
    }

    // Adaptive ISM reflection orders:
    // Narrow dimensions (e.g. Tunnel width/height, Car cabin) produce rapid transversal
    // bounces before longitudinal sound travels far. We scale maximum order adaptively per axis
    // to capture true waveguide flutter without calculating millions of redundant paths.
    const int maxOrderX = std::clamp(static_cast<int>(80.0f / std::max(dims.x, 2.0f)), 1, 3);
    const int maxOrderY = std::clamp(static_cast<int>(50.0f / std::max(dims.y, 1.2f)), 1, 5);
    const int maxOrderZ = std::clamp(static_cast<int>(40.0f / std::max(dims.z, 1.0f)), 1, 4);
    const int maxOrderTotal = std::max({maxOrderX, maxOrderY, maxOrderZ, cfg_.maxIsmOrder});

    for (int mx = -maxOrderX; mx <= maxOrderX; ++mx) {
        for (int my = -maxOrderY; my <= maxOrderY; ++my) {
            for (int mz = -maxOrderZ; mz <= maxOrderZ; ++mz) {
                const int order = std::abs(mx) + std::abs(my) + std::abs(mz);
                if (order == 0 || order > maxOrderTotal) continue;

                // Image source coordinates in room space
                const float sx_rel = src.x + dims.x * 0.5f;
                const float px_rel = (mx % 2 == 0) ? (static_cast<float>(mx) * dims.x + sx_rel)
                                                   : (static_cast<float>(mx + 1) * dims.x - sx_rel);
                const float px = px_rel - dims.x * 0.5f;

                const float sy_rel = src.y + dims.y * 0.5f;
                const float py_rel = (my % 2 == 0) ? (static_cast<float>(my) * dims.y + sy_rel)
                                                   : (static_cast<float>(my + 1) * dims.y - sy_rel);
                const float py = py_rel - dims.y * 0.5f;

                const float sz_rel = src.z;
                const float pz = (mz % 2 == 0) ? (static_cast<float>(mz) * dims.z + sz_rel)
                                               : (static_cast<float>(mz + 1) * dims.z - sz_rel);

                const Vec3 imgPos{px, py, pz};
                const Vec3 ray = imgPos - lis;
                const float dist = ray.length();
                if (dist < 1.0e-3f) continue;

                GeometricReflectionPath path;
                path.order = order;
                path.distanceMeters = dist;
                path.delaySeconds = dist / c;
                path.arrivalDirection = listenerFrame.toLocalSpherical(imgPos);

                const int nx_pos = std::max(0, mx);
                const int nx_neg = std::max(0, -mx);
                const int ny_pos = std::max(0, my);
                const int ny_neg = std::max(0, -my);
                const int nz_pos = std::max(0, mz);
                const int nz_neg = std::max(0, -mz);

                float midGain = 0.0f;
                constexpr std::array<float, kNumAcousticBands> kAirAttenPerM = {
                    0.000025f, 0.000075f, 0.00020f, 0.000475f, 0.00110f, 0.003375f
                };

                for (std::size_t band = 0; band < kNumAcousticBands; ++band) {
                    const float rFront = reflPerSurf[static_cast<std::size_t>(RoomSurface::Front)][band];
                    const float rBack  = reflPerSurf[static_cast<std::size_t>(RoomSurface::Back)][band];
                    const float rLeft  = reflPerSurf[static_cast<std::size_t>(RoomSurface::Left)][band];
                    const float rRight = reflPerSurf[static_cast<std::size_t>(RoomSurface::Right)][band];
                    const float rCeil  = reflPerSurf[static_cast<std::size_t>(RoomSurface::Ceiling)][band];
                    const float rFloor = reflPerSurf[static_cast<std::size_t>(RoomSurface::Floor)][band];

                    const float refl = std::pow(rFront, static_cast<float>(nx_pos)) *
                                       std::pow(rBack, static_cast<float>(nx_neg)) *
                                       std::pow(rLeft, static_cast<float>(ny_pos)) *
                                       std::pow(rRight, static_cast<float>(ny_neg)) *
                                       std::pow(rCeil, static_cast<float>(nz_pos)) *
                                       std::pow(rFloor, static_cast<float>(nz_neg));

                    const float air = std::exp(-kAirAttenPerM[band] * dist);
                    path.bandGains[band] = refl * air;
                    if (band == 2 || band == 3) midGain += refl * air * 0.5f;
                }

                if (midGain < 1.0e-4f) continue;
                path.broadbandGain = (1.0f / std::max(dist, 0.5f)) * midGain;
                path.isScattered = false;
                paths.push_back(path);

                // For irregular spaces with high wall scattering (e.g. Cave / Stadium),
                // generate distributed scattered path
                float avgScat = 0.0f;
                if (nx_pos > 0) avgScat += scatteringPerSurf[static_cast<std::size_t>(RoomSurface::Front)];
                if (nx_neg > 0) avgScat += scatteringPerSurf[static_cast<std::size_t>(RoomSurface::Back)];
                if (ny_pos > 0) avgScat += scatteringPerSurf[static_cast<std::size_t>(RoomSurface::Left)];
                if (ny_neg > 0) avgScat += scatteringPerSurf[static_cast<std::size_t>(RoomSurface::Right)];
                if (nz_pos > 0) avgScat += scatteringPerSurf[static_cast<std::size_t>(RoomSurface::Ceiling)];
                if (nz_neg > 0) avgScat += scatteringPerSurf[static_cast<std::size_t>(RoomSurface::Floor)];
                avgScat /= static_cast<float>(order);

                if (avgScat > 0.35f) {
                    GeometricReflectionPath scatPath = path;
                    scatPath.isScattered = true;
                    scatPath.broadbandGain *= avgScat * 0.4f;
                    scatPath.arrivalDirection.azimuthDeg += 12.0f * (mx % 2 == 0 ? 1.0f : -1.0f);
                    scatPath.delaySeconds += 0.003f * static_cast<float>(order);
                    paths.push_back(scatPath);
                }
            }
        }
    }

    return paths;
}

StereoBrir RirGenerator::generateBrir(const SpaceProfile& space) const {
    HrtfDatabase hrtf;
    hrtf.buildParametric(cfg_.sampleRate, 128, 10.0f, 15.0f);
    return generateBrir(space, hrtf);
}

StereoBrir RirGenerator::generateBrir(const SpaceProfile& space, const HrtfDatabase& hrtf) const {
    StereoBrir brir;
    brir.sampleRate = cfg_.sampleRate;
    brir.taps = cfg_.maxTaps;
    brir.left.assign(cfg_.maxTaps, 0.0f);
    brir.right.assign(cfg_.maxTaps, 0.0f);

    if (cfg_.sampleRate < 8000.0 || cfg_.maxTaps < 128 || !hrtf.valid()) {
        return brir;
    }

    const float fs = static_cast<float>(cfg_.sampleRate);
    const float c = rt::kSpeedOfSound;

    // 1. Calculate discrete geometric reflection paths (direct + early reflections)
    const auto paths = calculateReflectionPaths(space);
    const std::size_t hrirTaps = hrtf.irTaps();
    std::vector<float> hrirL(hrirTaps, 0.0f);
    std::vector<float> hrirR(hrirTaps, 0.0f);

    for (const auto& path : paths) {
        const float delaySamples = path.delaySeconds * fs;
        if (delaySamples >= static_cast<float>(cfg_.maxTaps - hrirTaps)) {
            continue;
        }

        // Spatialise via anechoic HRTF matching angle of arrival
        hrtf.render(path.arrivalDirection, hrirL.data(), hrirR.data());

        // Apply physical material frequency damping to the HRIR taps:
        // High-frequency absorption (carpet, upholstery, air attenuation over distance)
        // softly rolls off reflection treble; hard ceramic tile / glass maintains pristine sparkle.
        const float lowGain = 0.5f * (path.bandGains[0] + path.bandGains[1]);
        const float highGain = 0.5f * (path.bandGains[4] + path.bandGains[5]);
        const float hfDamp = std::clamp(1.0f - (highGain / std::max(lowGain, 1.0e-3f)), 0.0f, 0.85f);
        if (hfDamp > 0.03f) {
            float yL = 0.0f, yR = 0.0f;
            for (std::size_t t = 0; t < hrirTaps; ++t) {
                yL += (1.0f - hfDamp) * (hrirL[t] - yL);
                yR += (1.0f - hfDamp) * (hrirR[t] - yR);
                hrirL[t] = yL;
                hrirR[t] = yR;
            }
        }

        const std::size_t baseIdx = static_cast<std::size_t>(delaySamples);
        const float frac = delaySamples - static_cast<float>(baseIdx);
        const float gain = path.broadbandGain;

        for (std::size_t t = 0; t < hrirTaps; ++t) {
            const std::size_t outIdx = baseIdx + t;
            if (outIdx + 1 < cfg_.maxTaps) {
                const float sl = hrirL[t] * gain;
                const float sr = hrirR[t] * gain;
                brir.left[outIdx] += sl * (1.0f - frac);
                brir.left[outIdx + 1] += sl * frac;
                brir.right[outIdx] += sr * (1.0f - frac);
                brir.right[outIdx + 1] += sr * frac;
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. Physically Modeled Statistical Diffuse Tail
    // -------------------------------------------------------------------------
    if (cfg_.enableDiffuseTail && space.openness() < 0.95f) {
        const float V = space.volume();
        // Transition time from specular to diffuse field: t_mix ~ sqrt(V) / c
        const float tMix = std::clamp(std::sqrt(V) / c, 0.012f, 0.120f);
        const std::size_t startSample = static_cast<std::size_t>(tMix * fs);

        // Sabine/Eyring T60 per band (Low: 125-250Hz, Mid: 500-1000Hz, High: 2000-4000Hz)
        const auto t60Bands = space.reverberationTimeT60();
        const float t60Low = std::clamp(0.5f * (t60Bands[0] + t60Bands[1]), 0.10f, 15.0f);
        const float t60Mid = std::clamp(0.5f * (t60Bands[2] + t60Bands[3]), 0.10f, 15.0f);
        const float t60High = std::clamp(0.5f * (t60Bands[4] + t60Bands[5]), 0.05f, 10.0f);

        const float decayRateLow = 6.907755f / t60Low;
        const float decayRateMid = 6.907755f / t60Mid;
        const float decayRateHigh = 6.907755f / t60High;

        // Diffuse energy density scaling based on volume and mean absorption
        const float diffuseScale = (cfg_.diffuseEnergyRatio * 0.12f) / std::sqrt(std::max(V, 10.0f));

        std::mt19937 rng(1337);
        std::normal_distribution<float> dist(0.0f, 1.0f);

        // Dynamic frequency-dependent damping based on physical T60 high-to-mid ratio
        const float hfRatio = std::clamp(t60High / t60Mid, 0.10f, 1.0f);
        const float dampCoeff = std::clamp(std::exp(-1.0f / (0.003f * hfRatio * fs)), 0.05f, 0.98f);

        float lpL = 0.0f;
        float lpR = 0.0f;

        for (std::size_t n = startSample; n < cfg_.maxTaps; ++n) {
            const float t = static_cast<float>(n) / fs;
            const float dt = t - tMix;

            const float envLow = std::exp(-decayRateLow * dt);
            const float envMid = std::exp(-decayRateMid * dt);
            const float envHigh = std::exp(-decayRateHigh * dt);

            // Multi-band envelope weighting captures bass ratio and high air decay
            const float env = 0.25f * envLow + 0.50f * envMid + 0.25f * envHigh;

            const float fadeIn = std::min(1.0f, dt / 0.010f);

            const float wL = dist(rng);
            const float wR = dist(rng);

            // 3D Binaural Decorrelation & Envelopment
            const float diffL = (wL * 0.80f + wR * 0.20f) * env * diffuseScale * fadeIn;
            const float diffR = (wR * 0.80f + wL * 0.20f) * env * diffuseScale * fadeIn;

            lpL += (1.0f - dampCoeff) * (diffL - lpL);
            lpR += (1.0f - dampCoeff) * (diffR - lpR);

            brir.left[n] += lpL;
            brir.right[n] += lpR;
        }
    }

    // -------------------------------------------------------------------------
    // 3. Acoustic Energy & Peak Normalization (Headroom Safe)
    // -------------------------------------------------------------------------
    // Linear convolution with an impulse response whose peak was normalized to 1.0
    // results in massive over-unity gain (+15 to +25 dB) across music because of the
    // constructive summation of hundreds of reflection paths and diffuse tail.
    // We normalize the total RMS energy (L2 norm) to a safe target gain (-7 dBFS / 0.45)
    // and bound individual peaks to 0.55 to prevent any clipping under full-scale input.
    double energyL = 0.0;
    double energyR = 0.0;
    float maxPeak = 0.0f;
    for (std::size_t i = 0; i < cfg_.maxTaps; ++i) {
        const float l = brir.left[i];
        const float r = brir.right[i];
        energyL += static_cast<double>(l) * l;
        energyR += static_cast<double>(r) * r;
        maxPeak = std::max(maxPeak, std::max(std::fabs(l), std::fabs(r)));
    }

    const double rmsEnergy = std::sqrt(std::max(energyL, energyR));
    if (rmsEnergy > 1.0e-5 && maxPeak > 1.0e-5f) {
        constexpr float kTargetRmsGain = 0.45f; // -6.9 dB target RMS gain
        constexpr float kMaxPeakCeiling = 0.55f; // peak ceiling for transient safety
        float norm = static_cast<float>(kTargetRmsGain / rmsEnergy);
        if (maxPeak * norm > kMaxPeakCeiling) {
            norm = kMaxPeakCeiling / maxPeak;
        }
        for (std::size_t i = 0; i < cfg_.maxTaps; ++i) {
            brir.left[i] *= norm;
            brir.right[i] *= norm;
        }
    }

    return brir;
}

} // namespace frostsoulx::spatial
