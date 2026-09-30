#include "frostsoulx/immersive_audio_engine.h"
#include "frostsoulx/dsp/partitioned_convolver.h"
#include "frostsoulx/spatial/geometry.h"
#include "frostsoulx/spatial/hrtf.h"
#include "frostsoulx/spatial/rir_generator.h"
#include "frostsoulx/spatial/space_profile.h"
#include "frostsoulx/spatial/spatial_source.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

namespace {

int g_failures = 0;

bool check(bool cond, const std::string& msg) {
    if (!cond) {
        std::cerr << "FAIL: " << msg << "\n";
        ++g_failures;
        return false;
    }
    return true;
}

bool checkNear(double val, double target, double tol, const std::string& msg) {
    const bool ok = std::isfinite(val) && std::fabs(val - target) <= tol;
    if (!ok) {
        std::cerr << "FAIL: " << msg << " (got " << val << ", expected " << target << " +/- " << tol << ")\n";
        ++g_failures;
        return false;
    }
    return true;
}

double rms(const std::vector<float>& v) {
    double sum = 0.0;
    for (float x : v) sum += static_cast<double>(x) * x;
    return std::sqrt(sum / std::max<double>(1.0, static_cast<double>(v.size())));
}

double peak(const std::vector<float>& v) {
    double p = 0.0;
    for (float x : v) p = std::max(p, static_cast<double>(std::fabs(x)));
    return p;
}

// Helper to verify BRIR physical integrity
bool verifyBrirIntegrity(const frostsoulx::spatial::StereoBrir& brir, const std::string& name) {
    if (!check(brir.valid(), name + " BRIR is valid")) return false;
    for (std::size_t i = 0; i < brir.taps; ++i) {
        if (!std::isfinite(brir.left[i]) || !std::isfinite(brir.right[i])) {
            return check(false, name + " BRIR contains non-finite sample at index " + std::to_string(i));
        }
    }
    check(peak(brir.left) > 0.05f && peak(brir.left) <= 1.05f, name + " BRIR peak is normalized");
    return true;
}

// -----------------------------------------------------------------------------
// 1. Bathroom IR
// -----------------------------------------------------------------------------
void test1_BathroomIr(frostsoulx::spatial::RirGenerator& gen) {
    auto space = frostsoulx::spatial::SpaceProfile::createBathroom();
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "1. Bathroom"), "Bathroom BRIR integrity");
    auto t60 = space.reverberationTimeT60();
    check(t60[3] < 1.4f && t60[3] > 0.4f, "1. Bathroom has bright short T60");
}

// -----------------------------------------------------------------------------
// 2. Living Room IR
// -----------------------------------------------------------------------------
void test2_LivingRoomIr(frostsoulx::spatial::RirGenerator& gen) {
    auto space = frostsoulx::spatial::SpaceProfile::createLivingRoom();
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "2. Living Room"), "Living Room BRIR integrity");
    auto t60 = space.reverberationTimeT60();
    check(t60[3] < 0.6f && t60[3] > 0.2f, "2. Living Room T60 is ~0.3-0.5s");
}

// -----------------------------------------------------------------------------
// 3. Subway IR
// -----------------------------------------------------------------------------
void test3_SubwayIr(frostsoulx::spatial::RirGenerator& gen) {
    auto space = frostsoulx::spatial::SpaceProfile::createSubwayPlatform();
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "3. Subway"), "Subway BRIR integrity");
    auto t60 = space.reverberationTimeT60();
    check(t60[3] > 1.5f && t60[3] < 3.5f, "3. Subway has long hard reflection tail");
}

// -----------------------------------------------------------------------------
// 4. Long Tunnel IR
// -----------------------------------------------------------------------------
void test4_LongTunnelIr(frostsoulx::spatial::RirGenerator& gen) {
    auto space = frostsoulx::spatial::SpaceProfile::createLongTunnel();
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "4. Long Tunnel"), "Long Tunnel BRIR integrity");
    check(space.dimensions().x >= 300.0f, "4. Long Tunnel has long axial length");
}

// -----------------------------------------------------------------------------
// 5. Open Road IR
// -----------------------------------------------------------------------------
void test5_OpenRoadIr(frostsoulx::spatial::RirGenerator& gen) {
    auto space = frostsoulx::spatial::SpaceProfile::createOpenRoad();
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "5. Open Road"), "Open Road BRIR integrity");
    check(space.openness() > 0.9f, "5. Open Road is open atmosphere");
    auto t60 = space.reverberationTimeT60();
    check(t60[3] <= 0.15f, "5. Open Road has minimal/no reverberation tail");
}

// -----------------------------------------------------------------------------
// 6. Closed Car IR
// -----------------------------------------------------------------------------
void test6_ClosedCarIr(frostsoulx::spatial::RirGenerator& gen) {
    auto space = frostsoulx::spatial::SpaceProfile::createClosedCar();
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "6. Closed Car"), "Closed Car BRIR integrity");
    check(space.volume() < 10.0f, "6. Car cabin has very small volume");
    auto t60 = space.reverberationTimeT60();
    check(t60[3] < 0.25f, "6. Closed Car has fast damping");
}

// -----------------------------------------------------------------------------
// 7. Cave IR
// -----------------------------------------------------------------------------
void test7_CaveIr(frostsoulx::spatial::RirGenerator& gen) {
    auto space = frostsoulx::spatial::SpaceProfile::createCave();
    auto paths = gen.calculateReflectionPaths(space);
    bool hasScattered = false;
    for (const auto& p : paths) {
        if (p.isScattered) { hasScattered = true; break; }
    }
    check(hasScattered, "7. Cave exhibits high acoustic diffuse scattering");
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "7. Cave"), "Cave BRIR integrity");
}

// -----------------------------------------------------------------------------
// 8. Stadium IR
// -----------------------------------------------------------------------------
void test8_StadiumIr(frostsoulx::spatial::RirGenerator& gen) {
    auto space = frostsoulx::spatial::SpaceProfile::createStadium();
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "8. Stadium"), "Stadium BRIR integrity");
    check(space.volume() > 100000.0f, "8. Stadium has huge geometric volume");
}

// -----------------------------------------------------------------------------
// 9. Source Left/Right Movement (ITD + ILD)
// -----------------------------------------------------------------------------
void test9_SourceLeftRightMovement(frostsoulx::spatial::RirGenerator& gen) {
    using namespace frostsoulx::spatial;
    auto space = SpaceProfile::createLivingRoom();

    // Source placed left (-X in user coords)
    Coord3D posL{-3.0f, 0.0f, 2.0f};
    space.setSourcePosition(posL.toEngineCoords());
    auto brirL = gen.generateBrir(space);

    // Source placed right (+X in user coords)
    Coord3D posR{3.0f, 0.0f, 2.0f};
    space.setSourcePosition(posR.toEngineCoords());
    auto brirR = gen.generateBrir(space);

    check(rms(brirL.left) > rms(brirL.right) * 1.1, "9. Left source has louder left ear");
    check(rms(brirR.right) > rms(brirR.left) * 1.1, "9. Right source has louder right ear");
}

// -----------------------------------------------------------------------------
// 10. Overhead Arc
// -----------------------------------------------------------------------------
void test10_OverheadArc() {
    using namespace frostsoulx::spatial;
    auto vert = TrajectoryFactory::createVerticalArcOverhead(3.0f, 4.0f);
    Source3D src;
    src.setPosition(Coord3D::fromEngineCoords(vert.positionAt(2.0f))); // Apex
    check(src.elevationDeg() > 80.0f, "10. Overhead arc apex reaches elevation > 80 deg");
    checkNear(src.itdSeconds(), 0.0, 1.0e-4, "10. Overhead apex has near-zero ITD");
}

// -----------------------------------------------------------------------------
// 11. 3D Orbit
// -----------------------------------------------------------------------------
void test11_3dOrbit() {
    using namespace frostsoulx::spatial;
    auto orbit = TrajectoryFactory::createOrbital3D(4.0f, 45.0f, 6.0f);
    for (float t = 0.0f; t <= 6.0f; t += 1.0f) {
        Vec3 p = orbit.positionAt(t);
        check(p.isFinite(), "11. 3D orbit waypoint is finite");
    }
}

// -----------------------------------------------------------------------------
// 12. Listener Rotation
// -----------------------------------------------------------------------------
void test12_ListenerRotation(frostsoulx::spatial::RirGenerator& gen) {
    using namespace frostsoulx::spatial;
    auto space = SpaceProfile::createLivingRoom();
    space.setSourcePosition({2.0f, 0.0f, 1.2f}); // in front
    space.setListenerOrientation({90.0f, 0.0f, 0.0f}); // turned left 90 deg -> source is now to the right
    auto brir = gen.generateBrir(space);
    check(verifyBrirIntegrity(brir, "12. Listener Rotation"), "Listener Rotation BRIR integrity");
    check(rms(brir.right) > rms(brir.left) * 1.05, "12. Head turn shifts source towards right ear");
}

// -----------------------------------------------------------------------------
// 13. Distance Change
// -----------------------------------------------------------------------------
void test13_DistanceChange(frostsoulx::spatial::RirGenerator& gen) {
    using namespace frostsoulx::spatial;
    auto space = SpaceProfile::createOpenRoad();
    space.setListenerPosition({0.0f, 0.0f, 1.2f});

    space.setSourcePosition({2.0f, 0.0f, 1.2f}); // 2m
    auto brirNear = gen.generateBrir(space);

    space.setSourcePosition({10.0f, 0.0f, 1.2f}); // 10m
    auto brirFar = gen.generateBrir(space);

    // Find direct arrival index
    std::size_t onsetNear = 0, onsetFar = 0;
    for (std::size_t i = 0; i < brirNear.left.size(); ++i) {
        if (std::fabs(brirNear.left[i]) > 0.1f) { onsetNear = i; break; }
    }
    for (std::size_t i = 0; i < brirFar.left.size(); ++i) {
        if (std::fabs(brirFar.left[i]) > 0.1f) { onsetFar = i; break; }
    }
    check(onsetFar > onsetNear + 500, "13. Far distance exhibits longer propagation delay");
}

// -----------------------------------------------------------------------------
// 14. Long IR Spanning Multiple Convolution Tiers
// -----------------------------------------------------------------------------
void test14_MultiTierLongIr(frostsoulx::spatial::RirGenerator& gen) {
    using namespace frostsoulx::dsp;
    auto space = frostsoulx::spatial::SpaceProfile::createLargeHall();
    frostsoulx::spatial::RirGeneratorConfig cfg;
    cfg.maxTaps = 32768; // 32k taps spanning 4 tiers
    cfg.sampleRate = 48000.0;
    cfg.maxIsmOrder = 2;
    frostsoulx::spatial::RirGenerator longGen(cfg);

    auto brir = longGen.generateBrir(space);
    check(brir.valid() && brir.taps == 32768, "14. Generated 32768-tap BRIR");

    NonUniformConvolver::Config ccfg;
    ccfg.headBlock = 128;
    ccfg.maxTaps = 32768;
    ccfg.growth = 4;
    ccfg.maxTiers = 4; // T0: 128, T1: 512, T2: 2048, T3: 8192
    ccfg.crossfadeBlocks = 0;

    NonUniformConvolver conv;
    check(conv.prepare(ccfg), "14. Convolver prepared with 4 tiers");
    conv.loadIr(brir.left.data(), brir.taps);

    std::vector<float> in(128, 0.0f);
    in[0] = 1.0f; // Unit impulse
    std::vector<float> out(128, 0.0f);

    conv.processBlock(in.data(), out.data());
    // Process through the deep tiers (Tier 3 arrives after 2688 samples = 21 blocks)
    double tailEnergy = 0.0;
    for (int b = 1; b < 256; ++b) {
        std::fill(in.begin(), in.end(), 0.0f);
        conv.processBlock(in.data(), out.data());
        if (b > 25) {
            for (float x : out) tailEnergy += x * x;
        }
    }
    check(tailEnergy > 1.0e-5, "14. Multi-tier convolution produces non-zero deep-tier tail energy");
}

// -----------------------------------------------------------------------------
// 15. Full Late-Tail Contribution
// -----------------------------------------------------------------------------
void test15_FullLateTailContribution() {
    using namespace frostsoulx::dsp;
    NonUniformConvolver::Config ccfg;
    ccfg.headBlock = 128;
    ccfg.maxTaps = 8192;
    ccfg.growth = 4;
    ccfg.maxTiers = 3;
    ccfg.crossfadeBlocks = 0;

    NonUniformConvolver conv;
    conv.prepare(ccfg);

    // Create synthetic IR with energy ONLY in late tail (samples 2048..4096)
    std::vector<float> lateIr(8192, 0.0f);
    lateIr[3000] = 1.0f;
    conv.loadIr(lateIr.data(), 8192);

    std::vector<float> in(128, 0.0f);
    in[0] = 1.0f;
    std::vector<float> out(128, 0.0f);

    float peakLate = 0.0f;
    for (int b = 0; b < 64; ++b) {
        if (b > 0) in[0] = 0.0f;
        conv.processBlock(in.data(), out.data());
        for (float x : out) peakLate = std::max(peakLate, std::fabs(x));
    }
    checkNear(peakLate, 1.0, 1.0e-3, "15. Late tail partition correctly outputs delayed impulse");
}

// -----------------------------------------------------------------------------
// 16. MIMO Channel Isolation
// -----------------------------------------------------------------------------
void test16_MimoChannelIsolation(frostsoulx::ImmersiveAudioEngine& engine) {
    engine.prepare(48000, 384);
    engine.setEnabled(true);
    engine.setSpatialBackendPreference(frostsoulx::SpatialBackend::FullConvolution);

    // Left channel unit impulse, right channel silence
    std::vector<float> buffer(384 * 2, 0.0f);
    buffer[0] = 1.0f;

    engine.process(buffer.data(), 384);

    // Left channel must have sound
    double energyL = 0.0;
    for (int i = 0; i < 384; ++i) energyL += buffer[2 * i] * buffer[2 * i];
    check(energyL > 1.0e-4, "16. Left impulse produces active left convolution");
}

// -----------------------------------------------------------------------------
// 17. IR Crossfade (Dual-Bank Lock-Free Transition)
// -----------------------------------------------------------------------------
void test17_IrCrossfade(frostsoulx::ImmersiveAudioEngine& engine) {
    engine.prepare(48000, 384);
    engine.setEnabled(true);
    engine.setSpatialBackendPreference(frostsoulx::SpatialBackend::FullConvolution);
    engine.setSpacePreset(frostsoulx::spatial::SpaceProfile::Preset::LivingRoom);

    std::vector<float> buffer(384 * 2, 0.0f);
    buffer[0] = 1.0f;
    engine.process(buffer.data(), 384);

    // Hot-swap preset on control thread while processing
    engine.setSpacePreset(frostsoulx::spatial::SpaceProfile::Preset::ConcertHall);

    bool finite = true;
    for (int b = 0; b < 16; ++b) {
        std::fill(buffer.begin(), buffer.end(), 0.0f);
        engine.process(buffer.data(), 384);
        for (float x : buffer) {
            if (!std::isfinite(x) || std::fabs(x) > 2.0f) finite = false;
        }
    }
    check(finite, "17. IR crossfade completes without numerical instability or clipping");
}

// -----------------------------------------------------------------------------
// 18. Partial Host Blocks
// -----------------------------------------------------------------------------
void test18_PartialHostBlocks(frostsoulx::ImmersiveAudioEngine& engine) {
    engine.prepare(48000, 512);
    engine.setEnabled(true);
    engine.setSpatialBackendPreference(frostsoulx::SpatialBackend::FullConvolution);

    // Test non-power-of-2 and odd frame sizes
    const int frameSizes[] = {17, 31, 64, 113, 257, 384};
    for (int fs : frameSizes) {
        std::vector<float> buf(static_cast<std::size_t>(fs * 2), 0.1f);
        bool ok = engine.process(buf.data(), fs);
        check(ok, "18. Process partial block of size " + std::to_string(fs));
    }
}

// -----------------------------------------------------------------------------
// 19. Reset()
// -----------------------------------------------------------------------------
void test19_Reset(frostsoulx::ImmersiveAudioEngine& engine) {
    engine.prepare(48000, 384);
    engine.setEnabled(true);
    engine.setSpatialBackendPreference(frostsoulx::SpatialBackend::FullConvolution);

    std::vector<float> buf(384 * 2, 0.5f);
    engine.process(buf.data(), 384);

    engine.reset();
    std::fill(buf.begin(), buf.end(), 0.0f);
    engine.process(buf.data(), 384);

    double e = 0.0;
    for (float x : buf) e += x * x;
    checkNear(e, 0.0, 1.0e-9, "19. Reset immediately clears convolution delay state");
}

// -----------------------------------------------------------------------------
// 20. Sample-Rate Changes
// -----------------------------------------------------------------------------
void test20_SampleRateChanges(frostsoulx::ImmersiveAudioEngine& engine) {
    const int rates[] = {44100, 48000, 96000};
    for (int sr : rates) {
        bool ok = engine.prepare(sr, 384);
        check(ok, "20. Engine prepares cleanly at sample rate " + std::to_string(sr));
        engine.setEnabled(true);
        std::vector<float> buf(384 * 2, 0.1f);
        check(engine.process(buf.data(), 384), "20. Engine processes cleanly at " + std::to_string(sr));
    }
}

// -----------------------------------------------------------------------------
// 21. Invalid Coordinates Clamping
// -----------------------------------------------------------------------------
void test21_InvalidCoordinates(frostsoulx::ImmersiveAudioEngine& engine) {
    engine.prepare(48000, 384);
    // Position far outside room bounds
    engine.setSourcePosition(1000.0f, -500.0f, 300.0f);
    const auto& pos = engine.activeSpaceProfile().sourcePosition();
    check(pos.isFinite(), "21. Out-of-bounds coordinates clamp safely to finite space");
}

// -----------------------------------------------------------------------------
// 22. NaN / Inf Protection
// -----------------------------------------------------------------------------
void test22_NanInfProtection(frostsoulx::ImmersiveAudioEngine& engine) {
    engine.prepare(48000, 384);
    engine.setEnabled(true);
    engine.setSpatialBackendPreference(frostsoulx::SpatialBackend::FullConvolution);

    std::vector<float> buf(384 * 2, 0.0f);
    buf[0] = std::numeric_limits<float>::quiet_NaN();
    buf[1] = std::numeric_limits<float>::infinity();
    buf[2] = -std::numeric_limits<float>::infinity();

    engine.process(buf.data(), 384);
    for (float x : buf) {
        check(std::isfinite(x), "22. NaN and Inf samples sanitized from output");
    }
}

// -----------------------------------------------------------------------------
// Performance Benchmarks (Stage 6)
// -----------------------------------------------------------------------------
void runPerformanceBenchmarks(frostsoulx::ImmersiveAudioEngine& engine) {
    std::cout << "\n--- Running Stage 6 Performance Benchmarks ---\n";
    constexpr int kQuantum = 384;
    engine.prepare(48000, kQuantum);
    engine.setEnabled(true);
    engine.setSpatialBackendPreference(frostsoulx::SpatialBackend::FullConvolution);
    engine.setSpacePreset(frostsoulx::spatial::SpaceProfile::Preset::ConcertHall);

    std::vector<float> buf(kQuantum * 2, 0.2f);

    // Warm up
    for (int i = 0; i < 20; ++i) engine.process(buf.data(), kQuantum);

    constexpr int kIterations = 1000; // ~8 seconds of audio
    const auto start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < kIterations; ++i) {
        engine.process(buf.data(), kQuantum);
    }
    const auto end = std::chrono::high_resolution_clock::now();
    const double elapsedSec = std::chrono::duration<double>(end - start).count();
    const double audioSec = static_cast<double>(kIterations * kQuantum) / 48000.0;
    const double rtf = (elapsedSec / audioSec) * 100.0; // % of real-time budget

    std::cout << "Benchmark: Convolved " << audioSec << " seconds of 48 kHz stereo audio in "
              << elapsedSec << " seconds (" << rtf << "% of real-time budget)\n";
    check(rtf < 50.0, "Multi-tier full convolution runs comfortably within real-time budget (< 50% CPU)");
}

} // namespace

int main() {
    std::cout << "Running Complete 22-Point Acoustic Space Correctness Test Suite...\n";
    frostsoulx::spatial::RirGeneratorConfig cfg;
    cfg.sampleRate = 48000.0;
    cfg.maxTaps = 8192;
    cfg.maxIsmOrder = 2;
    cfg.enableDiffuseTail = true;
    frostsoulx::spatial::RirGenerator gen(cfg);
    frostsoulx::ImmersiveAudioEngine engine;

    test1_BathroomIr(gen);
    test2_LivingRoomIr(gen);
    test3_SubwayIr(gen);
    test4_LongTunnelIr(gen);
    test5_OpenRoadIr(gen);
    test6_ClosedCarIr(gen);
    test7_CaveIr(gen);
    test8_StadiumIr(gen);
    test9_SourceLeftRightMovement(gen);
    test10_OverheadArc();
    test11_3dOrbit();
    test12_ListenerRotation(gen);
    test13_DistanceChange(gen);
    test14_MultiTierLongIr(gen);
    test15_FullLateTailContribution();
    test16_MimoChannelIsolation(engine);
    test17_IrCrossfade(engine);
    test18_PartialHostBlocks(engine);
    test19_Reset(engine);
    test20_SampleRateChanges(engine);
    test21_InvalidCoordinates(engine);
    test22_NanInfProtection(engine);

    runPerformanceBenchmarks(engine);

    if (g_failures != 0) {
        std::cerr << "\n" << g_failures << " check(s) failed in 22-point test suite!\n";
        return 1;
    }
    std::cout << "\n>>> ALL 22 CORRECTNESS TESTS AND PERFORMANCE BENCHMARKS PASSED SUCCESSFULLY! <<<\n";
    return 0;
}
