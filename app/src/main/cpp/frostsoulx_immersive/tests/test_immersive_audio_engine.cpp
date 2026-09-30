// Host-side validation of the Frostsoulx immersive audio engine.
//
// The focus is the ACTUAL runtime signal path:
//
//   PCM -> ImmersiveAudioEngine::process()
//       -> NativeSpatialRenderer (HOA encode -> HOA rotation -> HRTF/HRIR
//          partitioned convolution; VBAP for object/discrete panning)
//       -> room processing
//       -> safety limiter
//       -> output
//
// Signals are deterministic (impulse, DC, sine, single-channel, LCG noise) so
// every check is reproducible. No test framework: a tiny check() helper keeps
// failures readable and the binary dependency-free.

#include "frostsoulx/immersive_audio_engine.h"

#include "frostsoulx/dsp/partitioned_convolver.h"
#include "frostsoulx/spatial/ambisonics.h"
#include "frostsoulx/spatial/geometry.h"
#include "frostsoulx/spatial/hrtf.h"
#include "frostsoulx/spatial/spatial_renderer.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

namespace {

int g_failures = 0;

bool check(bool condition, const std::string& what) {
    if (!condition) {
        std::cerr << "FAIL: " << what << "\n";
        ++g_failures;
    }
    return condition;
}

bool checkNear(double actual, double expected, double tol, const std::string& what) {
    const bool ok = std::isfinite(actual) && std::fabs(actual - expected) <= tol;
    if (!ok) {
        std::cerr << "FAIL: " << what << " (got " << actual << ", expected "
                  << expected << " +/- " << tol << ")\n";
        ++g_failures;
    }
    return ok;
}

constexpr int kSR = 48000;
constexpr int kN = frostsoulx::ImmersiveAudioEngine::kPreferredQuantumFrames;

/// Deterministic white noise in [-amp, amp].
struct Lcg {
    std::uint32_t s = 1u;
    float next(float amp) noexcept {
        s = s * 1664525u + 1013904223u;
        return amp * ((static_cast<float>(s >> 8) / 8388608.0f) - 1.0f);
    }
};

/// Scan an interleaved stereo buffer for NaN/Inf and out-of-range samples.
bool sane(const std::vector<float>& buf, float ceiling, const std::string& what) {
    for (std::size_t i = 0; i < buf.size(); ++i) {
        if (!std::isfinite(buf[i])) {
            std::cerr << "FAIL: " << what << " produced a non-finite sample at " << i << "\n";
            ++g_failures;
            return false;
        }
        if (std::fabs(buf[i]) > ceiling) {
            std::cerr << "FAIL: " << what << " exceeded " << ceiling << " (|" << buf[i]
                      << "| at " << i << ")\n";
            ++g_failures;
            return false;
        }
    }
    return true;
}

double energy(const std::vector<float>& b, int channel) {
    double e = 0.0;
    for (std::size_t i = static_cast<std::size_t>(channel); i < b.size(); i += 2) e += b[i] * b[i];
    return e;
}

double peakOf(const std::vector<float>& b) {
    double p = 0.0;
    for (float v : b) p = std::max(p, static_cast<double>(std::fabs(v)));
    return p;
}

/// Engine configured for a transparent measurement: no room, fully wet unless
/// overridden by the caller.
void configureDry(frostsoulx::ImmersiveAudioEngine& e) {
    e.setRoomSimulationPreset(frostsoulx::RoomSimulationPreset::Off);
    e.setRoomMix(0.0f);
    e.setSpatialBlend(1.0f);
    e.setEnabled(true);
}

// ---------------------------------------------------------------------------
// A. prepare + parameter state
// ---------------------------------------------------------------------------
void testPrepareAndParameters() {
    frostsoulx::ImmersiveAudioEngine engine;

    check(!engine.isPrepared(), "engine reports unprepared before prepare()");
    check(engine.lastProcessResult() == frostsoulx::ImmersiveProcessResult::NotPrepared,
          "unprepared engine reports NotPrepared");
    check(engine.backend() == frostsoulx::SpatialBackend::None,
          "unprepared engine reports no backend");

    // Unprepared process() must be rejected, not crash.
    std::vector<float> tmp(static_cast<std::size_t>(kN) * 2, 0.25f);
    check(!engine.process(tmp.data(), kN), "unprepared engine rejects audio");
    check(engine.lastProcessResult() == frostsoulx::ImmersiveProcessResult::NotPrepared,
          "unprepared process() reports NotPrepared");

    engine.setRoomSize(0.85f);
    engine.setDampening(0.35f);
    engine.setStereoWidth(0.9f);
    const auto c = engine.spaceDesignControls();
    checkNear(c.roomSize, 0.85, 1.0e-6, "roomSize slider persists");
    checkNear(c.dampening, 0.35, 1.0e-6, "dampening slider persists");
    checkNear(c.width, 0.9, 1.0e-6, "width slider persists");

    engine.setRoomSize(std::numeric_limits<float>::infinity());
    engine.setDampening(-2.0f);
    engine.setStereoWidth(3.0f);
    engine.setSpatialBlend(std::numeric_limits<float>::quiet_NaN());
    const auto cl = engine.spaceDesignControls();
    check(cl.roomSize == 0.0f && cl.dampening == 0.0f && cl.width == 1.0f,
          "space design controls clamp non-finite / out-of-range values");

    check(!engine.prepare(4000, kN), "prepare() rejects an unusable sample rate");
    check(!engine.prepare(kSR, 0), "prepare() rejects a non-positive frame count");

    check(engine.prepare(kSR, kN), "prepare(48000, 384) succeeds");
    check(engine.isPrepared(), "engine reports prepared");
    check(engine.maxFrames() == kN, "maxFrames() reflects prepare()");
    check(engine.lastProcessResult() == frostsoulx::ImmersiveProcessResult::Disabled,
          "freshly prepared engine reports Disabled");
}

// ---------------------------------------------------------------------------
// B. Native backend selection + reachability of the DSP modules
// ---------------------------------------------------------------------------
void testBackendSelection() {
    frostsoulx::ImmersiveAudioEngine engine;
    check(engine.prepare(kSR, kN), "prepare() for backend selection");

#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
    check(engine.backend() == frostsoulx::SpatialBackend::SteamAudio
              || engine.backend() == frostsoulx::SpatialBackend::Native,
          "a spatial backend is selected");
#else
    // Without Steam Audio the engine must fall back to the built-in renderer
    // rather than failing closed.
    check(engine.backend() == frostsoulx::SpatialBackend::Native,
          "native backend is selected when Steam Audio is unavailable");
    check(engine.latencySamples() > 0,
          "native backend reports its algorithmic latency");
#endif

    // The renderer the engine drives must actually own the HOA bus, the
    // HRTF set, the VBAP panner and the binaural convolver -- i.e. the DSP
    // modules are reachable from the runtime path, not merely compiled in.
    frostsoulx::spatial::SpatialRenderer r;
    check(r.prepare(static_cast<double>(kSR), kN), "SpatialRenderer::prepare()");
    check(r.ready(), "renderer reports ready");
    check(r.order() == 2 && r.hoaChannels() == 9, "2nd order HOA bus (9 channels)");
    check(r.numVirtualSpeakers() == 12, "dodeca12 virtual array in use");
    check(r.hrtf().valid() && r.hrtf().irTaps() == 128, "HRTF/HRIR set built (128 taps)");
    check(r.vbap().ready() && r.vbap().numTriplets() > 0, "VBAP panner triangulated");
    check(r.latencySamples() == r.blockSize(), "latency equals one render block");
    check(r.normalizationGain() > 0.0f && std::isfinite(r.normalizationGain()),
          "renderer reports a finite normalisation gain");
}

// ---------------------------------------------------------------------------
// C/D/E. Silence, non-zero stereo, and native spatial processing
// ---------------------------------------------------------------------------
void testNativeProcessing() {
    frostsoulx::ImmersiveAudioEngine engine;
    check(engine.prepare(kSR, kN), "prepare() for native processing");
    configureDry(engine);

    const auto expected =
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
        engine.backend() == frostsoulx::SpatialBackend::SteamAudio
            ? frostsoulx::ImmersiveProcessResult::SteamAudioProcessed
            : frostsoulx::ImmersiveProcessResult::NativeSpatialProcessed;
#else
        frostsoulx::ImmersiveProcessResult::NativeSpatialProcessed;
#endif

    // C. Silence in -> silence out, reported as success.
    {
        std::vector<float> s(static_cast<std::size_t>(kN) * 2, 0.0f);
        for (int b = 0; b < 4; ++b) {
            check(engine.process(s.data(), kN), "silent block is processed");
            check(engine.lastProcessResult() == expected,
                  "silent block reports spatial-processing success");
        }
        check(peakOf(s) == 0.0, "silence in produces silence out");
    }

    engine.reset();

    // D/E. Impulse: must produce a finite, bounded, non-silent binaural
    // response once the renderer's one-block latency has elapsed.
    {
        double totalEnergy = 0.0;
        double peak = 0.0;
        for (int b = 0; b < 6; ++b) {
            std::vector<float> buf(static_cast<std::size_t>(kN) * 2, 0.0f);
            if (b == 0) {
                buf[0] = 1.0f;
                buf[1] = 1.0f;
            }
            check(engine.process(buf.data(), kN), "impulse block is processed");
            check(engine.lastProcessResult() == expected,
                  "impulse block reports spatial-processing success");
            sane(buf, 0.99f, "impulse response");
            totalEnergy += energy(buf, 0) + energy(buf, 1);
            peak = std::max(peak, peakOf(buf));
        }
        check(totalEnergy > 1.0e-6, "impulse produces a non-silent binaural response");
        check(peak <= 1.0, "impulse response stays bounded");
    }

    engine.reset();

    // Stereo sine, several blocks: broadband gain must stay controlled. The
    // native path targets the same ~-6 dB headroom as the Steam Audio path so
    // the limiter stays out of the way of normal programme material.
    {
        double ein = 0.0;
        double eout = 0.0;
        int counted = 0;
        for (int b = 0; b < 24; ++b) {
            std::vector<float> buf(static_cast<std::size_t>(kN) * 2);
            for (int i = 0; i < kN; ++i) {
                const float t = static_cast<float>(b * kN + i) * 0.02f;
                buf[static_cast<std::size_t>(i) * 2] = 0.4f * std::sin(t);
                buf[static_cast<std::size_t>(i) * 2 + 1] = 0.4f * std::sin(t + 0.6f);
            }
            const double blockIn = energy(buf, 0) + energy(buf, 1);
            check(engine.process(buf.data(), kN), "sine block is processed");
            sane(buf, 0.99f, "stereo sine");
            if (b >= 4) {  // skip the latency / limiter settling region
                ein += blockIn;
                eout += energy(buf, 0) + energy(buf, 1);
                ++counted;
            }
        }
        check(counted > 0, "steady-state sine region measured");
        const double gain = std::sqrt(eout / std::max(ein, 1.0e-12));
        check(gain > 0.05 && gain < 1.5,
              "native spatial gain is controlled (no runaway, no collapse)");
    }

    // Variable block sizes must be accepted and stay sane.
    engine.reset();
    for (int frames : {1, 7, 64, 127, 128, 129, 383, kN}) {
        std::vector<float> buf(static_cast<std::size_t>(frames) * 2);
        Lcg rng;
        for (auto& v : buf) v = rng.next(0.5f);
        check(engine.process(buf.data(), frames),
              "block of " + std::to_string(frames) + " frames is accepted");
        sane(buf, 0.99f, "variable block size " + std::to_string(frames));
    }

    // Out-of-contract input must be rejected cleanly.
    {
        std::vector<float> buf(static_cast<std::size_t>(kN) * 2, 0.0f);
        check(!engine.process(nullptr, kN), "null buffer is rejected");
        check(engine.lastProcessResult() == frostsoulx::ImmersiveProcessResult::InvalidInput,
              "null buffer reports InvalidInput");
        check(!engine.process(buf.data(), 0), "zero frames is rejected");
        check(!engine.process(buf.data(), kN + 1), "oversized block is rejected");
        check(engine.lastProcessResult() == frostsoulx::ImmersiveProcessResult::InvalidInput,
              "oversized block reports InvalidInput");
    }
}

// ---------------------------------------------------------------------------
// F. Bypass / disabled engine
// ---------------------------------------------------------------------------
void testBypass() {
    frostsoulx::ImmersiveAudioEngine engine;
    check(engine.prepare(kSR, kN), "prepare() for bypass");

    std::vector<float> buf(static_cast<std::size_t>(kN) * 2, 0.0f);
    buf[0] = 1.0f;
    buf[1] = 0.25f;
    const auto original = buf;

    engine.setEnabled(false);
    check(!engine.process(buf.data(), kN), "disabled engine does not claim to process");
    check(buf == original, "disabled path leaves the buffer untouched");
    check(engine.lastProcessResult() == frostsoulx::ImmersiveProcessResult::Disabled,
          "disabled path reports Disabled");

    // Re-enabling must resume processing without a re-prepare.
    engine.setEnabled(true);
    engine.setSpatialBlend(1.0f);
    check(engine.process(buf.data(), kN), "re-enabled engine resumes processing");
}

// ---------------------------------------------------------------------------
// G/H. Room OFF transparency and room ON processing
// ---------------------------------------------------------------------------
void testRoomStages() {
    // G. Room Off + spatial blend 0 must be bit-transparent apart from the
    //    renderer's fixed latency. This is what proves the room and limiter
    //    stages are not colouring the signal when they should be inert.
    {
        frostsoulx::ImmersiveAudioEngine engine;
        check(engine.prepare(kSR, kN), "prepare() for room-off transparency");
        engine.setRoomSimulationPreset(frostsoulx::RoomSimulationPreset::Off);
        engine.setSpatialBlend(0.0f);
        engine.setEnabled(true);
        const int lat = engine.latencySamples();

        // Let the internal blend ramp settle before measuring.
        for (int b = 0; b < 2; ++b) {
            std::vector<float> z(static_cast<std::size_t>(kN) * 2, 0.0f);
            engine.process(z.data(), kN);
        }

        std::vector<float> in(static_cast<std::size_t>(kN) * 2);
        for (int i = 0; i < kN; ++i) {
            in[static_cast<std::size_t>(i) * 2] = 0.3f * std::sin(static_cast<float>(i) * 0.05f);
            in[static_cast<std::size_t>(i) * 2 + 1] = 0.2f * std::cos(static_cast<float>(i) * 0.07f);
        }
        std::vector<float> a = in;
        engine.process(a.data(), kN);
        std::vector<float> b(static_cast<std::size_t>(kN) * 2, 0.0f);
        engine.process(b.data(), kN);

        double maxErr = 0.0;
        for (int i = 0; i + lat < kN; ++i) {
            const std::size_t o = static_cast<std::size_t>(i + lat) * 2;
            const std::size_t s = static_cast<std::size_t>(i) * 2;
            maxErr = std::max(maxErr, static_cast<double>(std::fabs(a[o] - in[s])));
            maxErr = std::max(maxErr, static_cast<double>(std::fabs(a[o + 1] - in[s + 1])));
        }
        for (int i = 0; i < lat; ++i) {
            const std::size_t o = static_cast<std::size_t>(i) * 2;
            const std::size_t s = static_cast<std::size_t>(kN - lat + i) * 2;
            maxErr = std::max(maxErr, static_cast<double>(std::fabs(b[o] - in[s])));
            maxErr = std::max(maxErr, static_cast<double>(std::fabs(b[o + 1] - in[s + 1])));
        }
        check(maxErr < 1.0e-6,
              "room OFF + blend 0 is transparent apart from the reported latency");
    }

    // H. Room ON must add energy (reflections + reverb tail) and stay sane.
    {
        frostsoulx::ImmersiveAudioEngine dry;
        frostsoulx::ImmersiveAudioEngine wet;
        check(dry.prepare(kSR, kN) && wet.prepare(kSR, kN), "prepare() for room comparison");
        configureDry(dry);
        wet.setSpatialBlend(1.0f);
        wet.setRoomSimulationPreset(frostsoulx::RoomSimulationPreset::ConcertHall);
        wet.setRoomMix(0.35f);
        wet.setReflectionAmount(0.4f);
        wet.setReverbTimeSeconds(2.5f);
        wet.setEnabled(true);

        // The room stage is a dry/wet CROSSFADE, not an additive send, so the
        // meaningful signature of "room ON" is the decaying tail that persists
        // after the excitation stops -- not a rise in total energy.
        double dryTail = 0.0;
        double wetTail = 0.0;
        double tail = 0.0;
        for (int b = 0; b < 16; ++b) {
            std::vector<float> d(static_cast<std::size_t>(kN) * 2, 0.0f);
            std::vector<float> w(static_cast<std::size_t>(kN) * 2, 0.0f);
            if (b < 4) {  // 4 blocks of excitation, then silence to expose the tail
                for (int i = 0; i < kN; ++i) {
                    const float t = static_cast<float>(b * kN + i) * 0.02f;
                    const float s = 0.4f * std::sin(t);
                    d[static_cast<std::size_t>(i) * 2] = s;
                    d[static_cast<std::size_t>(i) * 2 + 1] = s;
                    w[static_cast<std::size_t>(i) * 2] = s;
                    w[static_cast<std::size_t>(i) * 2 + 1] = s;
                }
            }
            check(dry.process(d.data(), kN), "dry reference block is processed");
            check(wet.process(w.data(), kN), "room-on block is processed");
            sane(w, 0.99f, "room ON output");
            if (b >= 6) {  // well past the end of the excitation
                dryTail += energy(d, 0) + energy(d, 1);
                wetTail += energy(w, 0) + energy(w, 1);
                tail += energy(w, 0) + energy(w, 1);
            }
        }
        check(wetTail > dryTail * 10.0,
              "room ON adds reverberant energy after the input stops");
        check(tail > 1.0e-9, "room ON produces a decaying tail after the excitation stops");

        // Every preset must remain finite and bounded.
        for (auto preset : {frostsoulx::RoomSimulationPreset::SmallRoom,
                            frostsoulx::RoomSimulationPreset::Studio,
                            frostsoulx::RoomSimulationPreset::ConcertHall,
                            frostsoulx::RoomSimulationPreset::Cathedral,
                            frostsoulx::RoomSimulationPreset::Subway}) {
            frostsoulx::ImmersiveAudioEngine e;
            check(e.prepare(kSR, kN), "prepare() for preset sweep");
            e.setRoomSimulationPreset(preset);
            e.setRoomMix(1.0f);
            e.setReflectionAmount(1.0f);
            e.setReverbTimeSeconds(8.0f);
            e.setSpatialBlend(1.0f);
            e.setEnabled(true);
            Lcg rng;
            for (int b = 0; b < 12; ++b) {
                std::vector<float> buf(static_cast<std::size_t>(kN) * 2);
                for (auto& v : buf) v = rng.next(0.9f);
                check(e.process(buf.data(), kN), "preset block is processed");
                sane(buf, 0.99f, "room preset sweep");
            }
        }
    }
}

// ---------------------------------------------------------------------------
// I. Limiter is the final safety stage
// ---------------------------------------------------------------------------
void testLimiter() {
    frostsoulx::ImmersiveAudioEngine engine;
    check(engine.prepare(kSR, kN), "prepare() for limiter");
    // Worst case: maximum room wetness plus full-scale correlated input.
    engine.setRoomSimulationPreset(frostsoulx::RoomSimulationPreset::Cathedral);
    engine.setRoomMix(1.0f);
    engine.setReflectionAmount(1.0f);
    engine.setReverbTimeSeconds(8.0f);
    engine.setSpatialBlend(1.0f);
    engine.setEnabled(true);

    double worst = 0.0;
    for (int b = 0; b < 40; ++b) {
        std::vector<float> buf(static_cast<std::size_t>(kN) * 2);
        for (int i = 0; i < kN; ++i) {
            // Full-scale DC-ish square: the hardest case for a peak limiter.
            const float s = ((b * kN + i) / 64) % 2 == 0 ? 1.0f : -1.0f;
            buf[static_cast<std::size_t>(i) * 2] = s;
            buf[static_cast<std::size_t>(i) * 2 + 1] = s;
        }
        check(engine.process(buf.data(), kN), "limiter stress block is processed");
        sane(buf, 0.99f, "limiter stress");
        worst = std::max(worst, peakOf(buf));
    }
    check(worst <= 0.981, "limiter holds the output below the 0.98 ceiling");

    // Non-finite input must be sanitised, never propagated.
    {
        frostsoulx::ImmersiveAudioEngine e;
        check(e.prepare(kSR, kN), "prepare() for input sanitisation");
        configureDry(e);
        std::vector<float> buf(static_cast<std::size_t>(kN) * 2, 0.5f);
        buf[10] = std::numeric_limits<float>::quiet_NaN();
        buf[11] = std::numeric_limits<float>::infinity();
        buf[12] = -std::numeric_limits<float>::infinity();
        buf[13] = 1.0e9f;
        for (int b = 0; b < 4; ++b) {
            check(e.process(buf.data(), kN), "block with non-finite input is processed");
            sane(buf, 0.99f, "non-finite input sanitisation");
        }
    }
}

// ---------------------------------------------------------------------------
// J. Spatial blend
// ---------------------------------------------------------------------------
void testSpatialBlend() {
    // Blend must move the output monotonically from dry towards wet without
    // producing NaN, silence or a level jump.
    double previousDelta = -1.0;
    for (float blend : {0.0f, 0.25f, 0.5f, 0.75f, 1.0f}) {
        frostsoulx::ImmersiveAudioEngine engine;
        check(engine.prepare(kSR, kN), "prepare() for spatial blend");
        engine.setRoomSimulationPreset(frostsoulx::RoomSimulationPreset::Off);
        engine.setSpatialBlend(blend);
        engine.setEnabled(true);

        // Settle the blend ramp.
        for (int b = 0; b < 3; ++b) {
            std::vector<float> z(static_cast<std::size_t>(kN) * 2, 0.0f);
            engine.process(z.data(), kN);
        }

        const int lat = engine.latencySamples();
        std::vector<float> in(static_cast<std::size_t>(kN) * 2);
        for (int i = 0; i < kN; ++i) {
            const float t = static_cast<float>(i) * 0.03f;
            in[static_cast<std::size_t>(i) * 2] = 0.4f * std::sin(t);
            in[static_cast<std::size_t>(i) * 2 + 1] = 0.4f * std::sin(t + 1.1f);
        }
        std::vector<float> out = in;
        check(engine.process(out.data(), kN),
              "blend block is processed");
        sane(out, 0.99f, "spatial blend output");

        // Distance from the latency-aligned dry signal.
        double delta = 0.0;
        for (int i = 0; i + lat < kN; ++i) {
            const std::size_t o = static_cast<std::size_t>(i + lat) * 2;
            const std::size_t s = static_cast<std::size_t>(i) * 2;
            delta += (out[o] - in[s]) * (out[o] - in[s]);
            delta += (out[o + 1] - in[s + 1]) * (out[o + 1] - in[s + 1]);
        }
        if (blend == 0.0f) {
            check(delta < 1.0e-9, "blend 0 is fully dry");
        } else {
            check(delta > previousDelta,
                  "blend increases the spatialised contribution monotonically");
        }
        previousDelta = delta;
    }
}

// ---------------------------------------------------------------------------
// K. HOA rotation (coordinate convention regression)
// ---------------------------------------------------------------------------
void testHoaRotation() {
    using namespace frostsoulx::spatial;

    constexpr int kOrder = 3;
    const std::size_t C = ambisonicChannels(kOrder);

    AmbisonicEncoder enc;
    enc.setOrder(kOrder);
    AmbisonicRotator rot;
    rot.setOrder(kOrder);

    auto encodeAt = [&](float az, float el) {
        std::vector<float> g(kMaxAmbisonicChannels, 0.0f);
        enc.gainsFor(SphericalCoord{az, el, 1.0f}.toCartesian().normalized(), g.data());
        return g;
    };
    auto l1 = [&](const std::vector<float>& a, const std::vector<float>& b) {
        double d = 0.0;
        for (std::size_t c = 0; c < C; ++c) d += std::fabs(a[c] - b[c]);
        return d;
    };
    auto rotated = [&](const std::vector<float>& src, const HeadOrientation& o) {
        rot.setOrientation(o);
        std::vector<float> f = src;
        rot.rotateFrame(f.data());
        return f;
    };

    rot.setOrientation(HeadOrientation{});
    check(rot.isIdentity(), "zero head orientation is the identity rotation");

    // The rotator maps WORLD -> LISTENER-LOCAL, so turning the head must move
    // the sound field the OPPOSITE way. This is the convention the renderer
    // relies on; a sign flip here is inaudible in a static test but inverts
    // head tracking.
    //
    //   yaw   +90 (turn left) -> a front source appears at az = -90 (right)
    //   pitch +45 (look up)   -> a front source appears at el = -45 (below)
    //   roll  +45 (tilt right)-> an overhead source appears at az = +90 (left)
    const auto front = encodeAt(0.0f, 0.0f);
    const auto up = encodeAt(0.0f, 90.0f);

    {
        HeadOrientation o;
        o.yawDeg = 90.0f;
        const auto r = rotated(front, o);
        check(l1(r, encodeAt(-90.0f, 0.0f)) < 1.0e-4,
              "yaw +90 (turn left) moves a front source to the listener's right");
        check(l1(r, encodeAt(90.0f, 0.0f)) > 1.0,
              "yaw rotation direction is not inverted");
    }
    {
        HeadOrientation o;
        o.pitchDeg = 45.0f;
        const auto r = rotated(front, o);
        check(l1(r, encodeAt(0.0f, -45.0f)) < 1.0e-4,
              "pitch +45 (look up) moves a front source below the listener");
        check(l1(r, encodeAt(0.0f, 45.0f)) > 1.0,
              "pitch rotation direction is not inverted");
    }
    {
        HeadOrientation o;
        o.rollDeg = 45.0f;
        const auto r = rotated(up, o);
        check(l1(r, encodeAt(90.0f, 45.0f)) < 1.0e-4,
              "roll +45 (tilt right) moves an overhead source to the left");
        check(l1(r, encodeAt(-90.0f, 45.0f)) > 1.0,
              "roll rotation direction is not inverted");
    }

    // The rotator's convention must match ListenerFrame, which is what maps
    // world-space sources into the head frame everywhere else in the engine.
    for (const HeadOrientation o : {HeadOrientation{90.0f, 0.0f, 0.0f},
                                    HeadOrientation{0.0f, 45.0f, 0.0f},
                                    HeadOrientation{0.0f, 0.0f, 45.0f},
                                    HeadOrientation{37.0f, -21.0f, 13.0f}}) {
        ListenerFrame frame;
        frame.setOrientation(o);
        for (const auto& world : {SphericalCoord{0.0f, 0.0f, 1.0f},
                                  SphericalCoord{120.0f, 30.0f, 1.0f},
                                  SphericalCoord{-75.0f, -50.0f, 1.0f}}) {
            const Vec3 w = world.toCartesian().normalized();
            const Vec3 local = frame.toLocal(w).normalized();
            const auto viaRotator = rotated(
                [&] {
                    std::vector<float> g(kMaxAmbisonicChannels, 0.0f);
                    enc.gainsFor(w, g.data());
                    return g;
                }(),
                o);
            std::vector<float> viaFrame(kMaxAmbisonicChannels, 0.0f);
            enc.gainsFor(local, viaFrame.data());
            check(l1(viaRotator, viaFrame) < 1.0e-3,
                  "AmbisonicRotator agrees with ListenerFrame::toLocal()");
        }
    }

    // Rotation is orthogonal: energy must be preserved exactly.
    {
        HeadOrientation o{37.0f, -21.0f, 13.0f};
        double before = 0.0;
        for (std::size_t c = 0; c < C; ++c) before += front[c] * front[c];
        const auto r = rotated(front, o);
        double after = 0.0;
        for (std::size_t c = 0; c < C; ++c) {
            check(std::isfinite(r[c]), "rotated HOA channel is finite");
            after += r[c] * r[c];
        }
        checkNear(after / before, 1.0, 1.0e-4, "HOA rotation preserves energy");
    }

    // And it must be reachable from the engine without disturbing the audio.
    {
        frostsoulx::ImmersiveAudioEngine engine;
        check(engine.prepare(kSR, kN), "prepare() for head-tracked processing");
        configureDry(engine);
        engine.setHeadOrientation(35.0f, -12.0f, 8.0f);
        Lcg rng;
        for (int b = 0; b < 8; ++b) {
            std::vector<float> buf(static_cast<std::size_t>(kN) * 2);
            for (auto& v : buf) v = rng.next(0.5f);
            check(engine.process(buf.data(), kN), "head-tracked block is processed");
            sane(buf, 0.99f, "head-tracked output");
        }
        engine.setHeadOrientation(std::numeric_limits<float>::quiet_NaN(),
                                  std::numeric_limits<float>::infinity(), 0.0f);
        std::vector<float> buf(static_cast<std::size_t>(kN) * 2, 0.25f);
        check(engine.process(buf.data(), kN), "non-finite head orientation is tolerated");
        sane(buf, 0.99f, "output after a non-finite head orientation");
    }
}

// ---------------------------------------------------------------------------
// L. Non-full-sphere VBAP
// ---------------------------------------------------------------------------
void testNonFullSphereVbap() {
    using namespace frostsoulx::spatial;

    struct Case {
        const char* name;
        SpeakerLayout layout;
        bool expectPlanar;
    };

    std::vector<Case> cases;
    cases.push_back({"full sphere (dodeca12)", SpeakerLayout::dodeca12(), false});
    cases.push_back({"7.1.4 (no floor speakers)", SpeakerLayout::immersive714(), false});

    {  // Planar 5.0 horizontal ring: no valid triangulation at all.
        SpeakerLayout l;
        for (float az : {0.0f, 30.0f, -30.0f, 110.0f, -110.0f}) {
            l.positions.push_back({az, 0.0f, 1.0f});
        }
        cases.push_back({"planar 5.0 ring", l, true});
    }
    {  // Stereo pair: planar AND a partial arc -- the rear is uncovered.
        SpeakerLayout l;
        l.positions.push_back({30.0f, 0.0f, 1.0f});
        l.positions.push_back({-30.0f, 0.0f, 1.0f});
        cases.push_back({"stereo pair (partial arc)", l, true});
    }
    {  // Upper dome with no floor coverage: triangulates, but the hull has a
       // hole underneath the listener.
        SpeakerLayout l;
        for (int az = 0; az < 360; az += 45) {
            l.positions.push_back({static_cast<float>(az), 0.0f, 1.0f});
        }
        for (int az = 0; az < 360; az += 90) {
            l.positions.push_back({static_cast<float>(az), 45.0f, 1.0f});
        }
        l.positions.push_back({0.0f, 90.0f, 1.0f});
        cases.push_back({"dome, no floor", l, false});
    }

    for (const auto& c : cases) {
        const std::string name = c.name;
        VbapPanner v;
        if (!check(v.prepare(c.layout), name + ": prepare() succeeds")) continue;
        check(v.isPlanar() == c.expectPlanar,
              name + ": degenerate/planar layout detected correctly");
        if (c.expectPlanar) {
            check(v.numTriplets() == 0 && v.numPairs() > 0,
                  name + ": uses 2D pair panning, no invalid triangle assumption");
        } else {
            check(v.numTriplets() > 0, name + ": triangulated");
        }

        std::vector<float> gains(v.numSpeakers(), 0.0f);
        // Sweep the whole sphere, including directions the layout does not
        // cover. None may produce NaN, a negative gain, silence, or a gain
        // vector that is not unity-normalised.
        for (int az = -180; az < 180; az += 5) {
            for (int el = -90; el <= 90; el += 5) {
                const Vec3 d = SphericalCoord{static_cast<float>(az), static_cast<float>(el), 1.0f}
                                   .toCartesian()
                                   .normalized();
                for (float spread : {-1.0f, 0.0f, 0.5f, 1.0f}) {
                    if (spread < 0.0f) {
                        v.gainsFor(d, gains.data());
                    } else {
                        v.gainsForSpread(d, spread, gains.data());
                    }
                    double sumSq = 0.0;
                    for (float g : gains) {
                        if (!std::isfinite(g) || g < -1.0e-6f) {
                            check(false, name + ": produced an invalid gain");
                            return;
                        }
                        sumSq += static_cast<double>(g) * g;
                    }
                    const double norm = std::sqrt(sumSq);
                    if (norm < 1.0e-4) {
                        check(false, name + ": direction fell silent (uncovered direction "
                                         "was not projected onto the layout boundary)");
                        return;
                    }
                    if (std::fabs(norm - 1.0) > 1.0e-3) {
                        check(false, name + ": gains are not unity-normalised");
                        return;
                    }
                }
            }
        }
        check(true, name + ": all directions are finite, non-silent and normalised");
    }

    // Degenerate inputs must be rejected, not crash.
    {
        VbapPanner v;
        SpeakerLayout empty;
        check(!v.prepare(empty), "VBAP rejects an empty layout");
        SpeakerLayout single;
        single.positions.push_back({0.0f, 0.0f, 1.0f});
        check(!v.prepare(single), "VBAP rejects a single-speaker layout");
    }

    // Reachability: the renderer's panner must answer object queries.
    {
        SpatialRenderer r;
        check(r.prepare(static_cast<double>(kSR), kN), "renderer prepare() for object panning");
        std::vector<float> gains(r.numVirtualSpeakers(), 0.0f);
        r.objectGains(SphericalCoord{45.0f, 20.0f, 1.0f}, 0.3f, gains.data());
        double sumSq = 0.0;
        for (float g : gains) {
            check(std::isfinite(g) && g >= -1.0e-6f, "object gain is valid");
            sumSq += static_cast<double>(g) * g;
        }
        checkNear(std::sqrt(sumSq), 1.0, 1.0e-3, "object gains are unity-normalised");

        std::vector<float> hoa(kMaxAmbisonicChannels, 0.0f);
        check(r.encodeObject(SphericalCoord{45.0f, 20.0f, 1.0f}, hoa.data()) == r.hoaChannels(),
              "encodeObject() writes the full HOA bus");
    }
}

// ---------------------------------------------------------------------------
// M. Partitioned convolution
// ---------------------------------------------------------------------------
void testConvolution() {
    using namespace frostsoulx;

    dsp::NonUniformConvolver::Config cfg;
    cfg.headBlock = 128;
    cfg.maxTaps = 128;
    cfg.maxTiers = 1;
    cfg.growth = 4;
    cfg.crossfadeBlocks = 0;

    dsp::MimoConvolver m;
    check(m.prepare(1, 1, cfg), "MimoConvolver::prepare()");

    // Random IR, random input: the partitioned result must match a direct
    // time-domain convolution.
    Lcg rng;
    std::vector<float> ir(128);
    for (auto& v : ir) v = rng.next(1.0f);
    m.loadIr(0, 0, ir.data(), ir.size());

    constexpr int kBlocks = 8;
    std::vector<float> x(static_cast<std::size_t>(128 * kBlocks));
    for (auto& v : x) v = rng.next(0.5f);
    std::vector<float> y(x.size(), 0.0f);

    for (int b = 0; b < kBlocks; ++b) {
        const float* in = x.data() + static_cast<std::size_t>(b) * 128;
        float* out = y.data() + static_cast<std::size_t>(b) * 128;
        const float* ip[1] = {in};
        float* op[1] = {out};
        m.processBlock(ip, op);
    }

    double maxErr = 0.0;
    for (std::size_t n = 0; n < x.size(); ++n) {
        double ref = 0.0;
        for (std::size_t k = 0; k < ir.size() && k <= n; ++k) ref += ir[k] * x[n - k];
        maxErr = std::max(maxErr, std::fabs(ref - y[n]));
        check(std::isfinite(y[n]), "convolver output is finite");
    }
    check(maxErr < 1.0e-4, "partitioned convolution matches direct convolution");

    // An impulse in must reproduce the IR exactly (zero latency).
    {
        dsp::MimoConvolver d;
        check(d.prepare(1, 1, cfg), "impulse-test convolver prepare()");
        d.loadIr(0, 0, ir.data(), ir.size());
        std::vector<float> imp(128, 0.0f);
        imp[0] = 1.0f;
        std::vector<float> out(128, 0.0f);
        const float* ip[1] = {imp.data()};
        float* op[1] = {out.data()};
        d.processBlock(ip, op);
        double e = 0.0;
        for (std::size_t n = 0; n < 128; ++n) {
            e = std::max(e, static_cast<double>(std::fabs(out[n] - ir[n])));
        }
        check(e < 1.0e-4, "convolver is zero-latency (impulse reproduces the IR)");
    }

    // reset() must clear the tail without reallocating.
    {
        m.reset();
        std::vector<float> zero(128, 0.0f);
        std::vector<float> out(128, 1.0f);
        const float* ip[1] = {zero.data()};
        float* op[1] = {out.data()};
        m.processBlock(ip, op);
        for (float v : out) {
            check(std::fabs(v) < 1.0e-6f, "convolver reset() clears the tail");
        }
    }
}

// ---------------------------------------------------------------------------
// N. HRTF / HRIR
// ---------------------------------------------------------------------------
void testHrtf() {
    using namespace frostsoulx::spatial;

    HrtfDatabase h;
    check(h.buildParametric(static_cast<double>(kSR), 128, 10.0f, 15.0f),
          "HRTF set builds");
    check(h.valid(), "HRTF set reports valid");
    check(h.irTaps() == 128, "HRIR tap count");
    check(h.numAzimuth() == 36 && h.numElevation() == 13, "HRTF grid resolution");
    check(h.memoryBytes() > 0, "HRTF set occupies storage");

    std::vector<float> L(h.irTaps(), 0.0f);
    std::vector<float> R(h.irTaps(), 0.0f);

    auto rms = [](const std::vector<float>& v) {
        double e = 0.0;
        for (float s : v) e += static_cast<double>(s) * s;
        return std::sqrt(e / static_cast<double>(v.size()));
    };

    // Sweep the sphere: every HRIR must be finite and non-degenerate.
    for (int az = -180; az < 180; az += 5) {
        for (int el = -90; el <= 90; el += 5) {
            const auto pair = h.render(
                SphericalCoord{static_cast<float>(az), static_cast<float>(el), 1.0f},
                L.data(), R.data());
            check(pair.taps == h.irTaps(), "render() reports the tap count");
            for (std::size_t i = 0; i < h.irTaps(); ++i) {
                if (!std::isfinite(L[i]) || !std::isfinite(R[i])) {
                    check(false, "HRIR contains a non-finite tap");
                    return;
                }
            }
            if (!(std::isfinite(pair.delayLeftSamples) && std::isfinite(pair.delayRightSamples)
                  && pair.delayLeftSamples >= 0.0f && pair.delayRightSamples >= 0.0f)) {
                check(false, "HRIR ITD is not a valid non-negative delay");
                return;
            }
            if (rms(L) < 1.0e-6 && rms(R) < 1.0e-6) {
                check(false, "HRIR is entirely silent");
                return;
            }
        }
    }
    check(true, "all HRIRs across the sphere are finite and non-degenerate");

    // Symmetry: a centred source must be identical at both ears.
    {
        h.render(SphericalCoord{0.0f, 0.0f, 1.0f}, L.data(), R.data());
        checkNear(rms(L), rms(R), 1.0e-5, "front source is left/right symmetric");
    }

    // ILD sign and ITD sign: +azimuth is LEFT in this coordinate system, so a
    // source at az=+90 must be louder and earlier at the LEFT ear. A flip here
    // would invert the stereo image for the whole engine.
    {
        const auto left = h.render(SphericalCoord{90.0f, 0.0f, 1.0f}, L.data(), R.data());
        const double rl = rms(L);
        const double rr = rms(R);
        check(rl > rr * 2.0, "source at az=+90 is louder at the LEFT ear (ILD sign)");
        check(left.delayRightSamples > left.delayLeftSamples,
              "source at az=+90 arrives EARLIER at the left ear (ITD sign)");

        const auto right = h.render(SphericalCoord{-90.0f, 0.0f, 1.0f}, L.data(), R.data());
        check(rms(R) > rms(L) * 2.0, "source at az=-90 is louder at the RIGHT ear");
        check(right.delayLeftSamples > right.delayRightSamples,
              "source at az=-90 arrives earlier at the right ear");
        checkNear(right.delayLeftSamples, left.delayRightSamples, 1.0e-3,
                  "ITD magnitude is left/right symmetric");
        // Woodworth ITD for a ~0.0875 m head at 48 kHz is ~0.7 ms (~34 samples).
        check(left.delayRightSamples > 15.0f && left.delayRightSamples < 55.0f,
              "lateral ITD is physically plausible");
    }

    // Elevation must actually change the spectrum (pinna notches), otherwise
    // the elevation cue is missing.
    {
        std::vector<float> up(h.irTaps(), 0.0f);
        std::vector<float> upR(h.irTaps(), 0.0f);
        h.render(SphericalCoord{0.0f, 0.0f, 1.0f}, L.data(), R.data());
        h.render(SphericalCoord{0.0f, 60.0f, 1.0f}, up.data(), upR.data());
        double diff = 0.0;
        for (std::size_t i = 0; i < h.irTaps(); ++i) diff += std::fabs(up[i] - L[i]);
        check(diff > 1.0e-3, "elevation changes the HRIR (spectral elevation cue present)");
    }

    // Interpolation between grid points must not blow up or collapse.
    {
        h.render(SphericalCoord{7.0f, 0.0f, 1.0f}, L.data(), R.data());
        const double a = rms(L);
        h.render(SphericalCoord{13.0f, 0.0f, 1.0f}, L.data(), R.data());
        const double b = rms(L);
        check(a > 1.0e-5 && b > 1.0e-5 && a < 10.0 && b < 10.0,
              "off-grid interpolation stays bounded and non-silent");
    }

    // Analytic building blocks.
    {
        check(woodworthItdSeconds(0.0f, frostsoulx::rt::kHeadRadius) >= 0.0f,
              "Woodworth ITD is non-negative");
        const auto c = headShadowFilter(1.0f, frostsoulx::rt::kHeadRadius,
                                        static_cast<double>(kSR));
        check(std::isfinite(c.b0) && std::isfinite(c.b1) && std::isfinite(c.a1)
                  && std::fabs(c.a1) < 1.0f,
              "head-shadow filter is finite and stable");
        const float nf = nearFieldGain(1.0f, 0.3f, frostsoulx::rt::kHeadRadius);
        check(std::isfinite(nf) && nf > 0.0f, "near-field gain is a valid positive gain");
    }
}

// ---------------------------------------------------------------------------
// End-to-end: the chain must be a real runtime path, not just linked classes
// ---------------------------------------------------------------------------
void testEndToEndChain() {
    frostsoulx::ImmersiveAudioEngine engine;
    check(engine.prepare(kSR, kN), "prepare() for the end-to-end chain");

    const auto expected =
#if defined(FROSTSOULX_STEAM_AUDIO_AVAILABLE)
        engine.backend() == frostsoulx::SpatialBackend::SteamAudio
            ? frostsoulx::ImmersiveProcessResult::SteamAudioProcessed
            : frostsoulx::ImmersiveProcessResult::NativeSpatialProcessed;
#else
        frostsoulx::ImmersiveProcessResult::NativeSpatialProcessed;
#endif

    engine.setRoomSimulationPreset(frostsoulx::RoomSimulationPreset::Studio);
    engine.setRoomMix(0.2f);
    engine.setReflectionAmount(0.3f);
    engine.setReverbTimeSeconds(1.4f);
    engine.setRoomSize(0.6f);
    engine.setDampening(0.45f);
    engine.setStereoWidth(0.8f);
    engine.setSpatialBlend(0.9f);
    // Head facing forward for the symmetry checks below: any non-zero yaw
    // deliberately breaks left/right symmetry, which is the point of head
    // tracking but would make the inversion check meaningless.
    engine.setHeadOrientation(0.0f, 0.0f, 0.0f);
    engine.setEnabled(true);

    // A left-only and a right-only source must stay on their own side after
    // the full chain: spatialisation, room and limiter must not invert or
    // collapse the stereo image.
    auto sided = [&](bool leftSide) {
        engine.reset();
        double eL = 0.0;
        double eR = 0.0;
        for (int b = 0; b < 14; ++b) {
            std::vector<float> buf(static_cast<std::size_t>(kN) * 2, 0.0f);
            for (int i = 0; i < kN; ++i) {
                const float s = 0.45f * std::sin(static_cast<float>(b * kN + i) * 0.02f);
                buf[static_cast<std::size_t>(i) * 2 + (leftSide ? 0 : 1)] = s;
            }
            check(engine.process(buf.data(), kN), "single-sided block is processed");
            check(engine.lastProcessResult() == expected,
                  "single-sided block reports spatial-processing success");
            sane(buf, 0.99f, leftSide ? "left-only chain output" : "right-only chain output");
            if (b >= 4) {
                eL += energy(buf, 0);
                eR += energy(buf, 1);
            }
        }
        return std::pair<double, double>{eL, eR};
    };

    const auto l = sided(true);
    check(l.first > l.second, "left-only input stays dominant in the LEFT output");
    check(l.second > 0.0, "left-only input still produces natural crosstalk (HRTF)");

    const auto r = sided(false);
    check(r.second > r.first, "right-only input stays dominant in the RIGHT output");
    check(r.first > 0.0, "right-only input still produces natural crosstalk (HRTF)");

    // With the room engaged the two channels are intentionally de-correlated
    // (`decorrelationSkew` / `reflectionCrossFeed`), so only the SPATIAL stage
    // is required to be exactly mirror-symmetric. Measure it with the room
    // bypassed -- that is what actually detects a channel inversion.
    {
        frostsoulx::ImmersiveAudioEngine s;
        check(s.prepare(kSR, kN), "prepare() for spatial symmetry");
        configureDry(s);
        s.setStereoWidth(0.8f);

        auto sidedDry = [&](bool leftSide) {
            s.reset();
            double eL = 0.0;
            double eR = 0.0;
            for (int b = 0; b < 14; ++b) {
                std::vector<float> buf(static_cast<std::size_t>(kN) * 2, 0.0f);
                for (int i = 0; i < kN; ++i) {
                    const float v = 0.45f * std::sin(static_cast<float>(b * kN + i) * 0.02f);
                    buf[static_cast<std::size_t>(i) * 2 + (leftSide ? 0 : 1)] = v;
                }
                check(s.process(buf.data(), kN), "dry single-sided block is processed");
                if (b >= 4) {
                    eL += energy(buf, 0);
                    eR += energy(buf, 1);
                }
            }
            return std::pair<double, double>{eL, eR};
        };

        const auto dl = sidedDry(true);
        const auto dr = sidedDry(false);
        check(dl.first > dl.second, "dry chain: left-only stays on the left");
        check(dr.second > dr.first, "dry chain: right-only stays on the right");
        checkNear(dl.first, dr.second, dl.first * 1.0e-3,
                  "spatial stage is mirror-symmetric (ipsilateral)");
        checkNear(dl.second, dr.first, std::max(dl.second, 1.0e-9) * 1.0e-3,
                  "spatial stage is mirror-symmetric (contralateral, no inversion)");
    }

    // A long deterministic run must never drift into NaN, clipping or silence.
    engine.reset();
    Lcg rng;
    double totalEnergy = 0.0;
    double worstPeak = 0.0;
    for (int b = 0; b < 64; ++b) {
        std::vector<float> buf(static_cast<std::size_t>(kN) * 2);
        for (auto& v : buf) v = rng.next(0.6f);
        if (!check(engine.process(buf.data(), kN), "sustained-run block is processed")) return;
        if (!sane(buf, 0.99f, "sustained run")) return;
        totalEnergy += energy(buf, 0) + energy(buf, 1);
        worstPeak = std::max(worstPeak, peakOf(buf));
    }
    check(totalEnergy > 1.0, "sustained run keeps producing audio");
    check(worstPeak <= 0.981, "sustained run stays under the limiter ceiling");

    // reset() must return the engine to a clean, still-prepared state.
    engine.reset();
    check(engine.isPrepared(), "reset() keeps the engine prepared");
    check(engine.lastProcessResult() == frostsoulx::ImmersiveProcessResult::Disabled,
          "reset() restores the idle result");
    std::vector<float> after(static_cast<std::size_t>(kN) * 2, 0.0f);
    check(engine.process(after.data(), kN), "engine processes again after reset()");
    check(peakOf(after) == 0.0, "reset() cleared every delay line and filter tail");
}

} // namespace

int main() {
    static_assert(frostsoulx::ImmersiveAudioEngine::kPreferredQuantumFrames == 384);
    static_assert(static_cast<int>(frostsoulx::RoomSimulationPreset::Subway) == 5);

    testPrepareAndParameters();     // A
    testBackendSelection();         // B
    testNativeProcessing();         // C, D, E
    testBypass();                   // F
    testRoomStages();               // G, H
    testLimiter();                  // I
    testSpatialBlend();             // J
    testHoaRotation();              // K
    testNonFullSphereVbap();        // L
    testConvolution();              // M
    testHrtf();                     // N
    testEndToEndChain();            // full runtime chain

    if (g_failures != 0) {
        std::cerr << g_failures << " check(s) failed\n";
        return 1;
    }
    std::cout << "All immersive audio engine checks passed "
                 "(native path: PCM -> SpatialRenderer -> HRTF/HOA/VBAP -> room -> limiter)\n";
    return 0;
}
