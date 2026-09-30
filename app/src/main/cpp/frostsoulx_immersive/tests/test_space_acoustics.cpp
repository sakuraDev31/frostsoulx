#include "frostsoulx/dsp/partitioned_convolver.h"
#include "frostsoulx/spatial/geometry.h"
#include "frostsoulx/spatial/hrtf.h"
#include "frostsoulx/spatial/rir_generator.h"
#include "frostsoulx/spatial/space_profile.h"
#include "frostsoulx/spatial/spatial_source.h"

#include <algorithm>
#include <cmath>
#include <iostream>
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

// -----------------------------------------------------------------------------
// 1. SpaceProfile and 12 Physical Presets Tests
// -----------------------------------------------------------------------------
void testSpacePresets() {
    using namespace frostsoulx::spatial;

    // Test all 12 factory presets
    const std::vector<SpaceProfile::Preset> allPresets = {
        SpaceProfile::Preset::Bathroom,
        SpaceProfile::Preset::LivingRoom,
        SpaceProfile::Preset::MediumHall,
        SpaceProfile::Preset::LargeHall,
        SpaceProfile::Preset::ConcertHall,
        SpaceProfile::Preset::SubwayPlatform,
        SpaceProfile::Preset::LongSubwayTunnel,
        SpaceProfile::Preset::LongTunnel,
        SpaceProfile::Preset::ClosedCar,
        SpaceProfile::Preset::OpenRoad,
        SpaceProfile::Preset::Cave,
        SpaceProfile::Preset::Stadium
    };

    check(allPresets.size() == 12, "12 physical space presets defined");

    for (auto preset : allPresets) {
        auto space = SpaceProfile::createPreset(preset);
        check(!space.name().empty(), "Preset has non-empty name");
        check(space.volume() > 0.0f, "Preset " + space.name() + " has positive volume");
        check(space.totalSurfaceArea() > 0.0f, "Preset " + space.name() + " has positive surface area");

        auto t60 = space.reverberationTimeT60();
        for (std::size_t b = 0; b < kNumAcousticBands; ++b) {
            check(std::isfinite(t60[b]) && t60[b] > 0.0f,
                  "Preset " + space.name() + " has valid finite T60 for band " + std::to_string(b));
        }
    }

    // Specific physics checks on key profiles:
    auto bath = SpaceProfile::createBathroom();
    check(bath.volume() < 20.0f, "Bathroom is small volume");
    auto bathT60 = bath.reverberationTimeT60();
    check(bathT60[3] < 1.4f && bathT60[3] > 0.4f, "Bathroom has short bright reverberation");

    auto car = SpaceProfile::createClosedCar();
    check(car.volume() < 10.0f, "Car cabin is very small volume");
    auto carT60 = car.reverberationTimeT60();
    check(carT60[3] < 0.25f, "Car cabin reverberation is extremely short");

    auto concert = SpaceProfile::createConcertHall();
    auto concertT60 = concert.reverberationTimeT60();
    check(concertT60[3] >= 1.5f && concertT60[3] <= 2.8f, "ConcertHall T60 at 1 kHz is in 1.5 - 2.8s range");

    auto stadium = SpaceProfile::createStadium();
    check(stadium.volume() > 100000.0f, "Stadium has massive geometric volume");
    check(stadium.openness() >= 0.5f, "Stadium is largely open to the sky");

    auto road = SpaceProfile::createOpenRoad();
    check(road.openness() >= 0.8f, "Open road has high openness");
    auto roadT60 = road.reverberationTimeT60();
    check(roadT60[3] <= 0.15f, "Open road has minimal reverberation");
}

// -----------------------------------------------------------------------------
// 2. 3D Spatial Source Model Tests
// -----------------------------------------------------------------------------
void testSpatialSourceModel() {
    using namespace frostsoulx::spatial;

    // Coordinate conversion test
    // User coords: X = right, Y = up, Z = front
    // Engine coords: +X = front, +Y = left, +Z = up
    {
        Coord3D userRight{5.0f, 0.0f, 0.0f};
        Vec3 eng = userRight.toEngineCoords();
        checkNear(eng.x, 0.0, 1.0e-5, "User Right -> Engine X (front) = 0");
        checkNear(eng.y, -5.0, 1.0e-5, "User Right -> Engine Y (left) = -5");
        checkNear(eng.z, 0.0, 1.0e-5, "User Right -> Engine Z (up) = 0");

        Coord3D roundtrip = Coord3D::fromEngineCoords(eng);
        checkNear(roundtrip.x, 5.0, 1.0e-5, "Roundtrip user X");
        checkNear(roundtrip.y, 0.0, 1.0e-5, "Roundtrip user Y");
        checkNear(roundtrip.z, 0.0, 1.0e-5, "Roundtrip user Z");
    }

    // Source3D acoustic metrics:
    {
        Source3D src;
        src.setListenerPosition({0.0f, 0.0f, 0.0f});
        // Place source 4 metres in front
        src.setPosition({0.0f, 0.0f, 4.0f});

        checkNear(src.distance(), 4.0, 1.0e-4, "Distance is 4 metres");
        checkNear(src.azimuthDeg(), 0.0, 1.0e-2, "Directly in front azimuth is 0 deg");
        checkNear(src.elevationDeg(), 0.0, 1.0e-2, "Directly in front elevation is 0 deg");
        checkNear(src.distanceAttenuation(), 0.25, 1.0e-3, "1/d distance attenuation for 4m is 0.25");
        checkNear(src.directPathDelaySeconds(), 4.0 / 343.0, 1.0e-4, "Direct path delay tau = d / c");
        checkNear(src.itdSeconds(), 0.0, 1.0e-5, "Frontal source has 0 ITD");

        // Place source to the left (X = -3m in user coords)
        src.setPosition({-3.0f, 0.0f, 0.0f});
        check(src.azimuthDeg() > 80.0f && src.azimuthDeg() < 100.0f, "Left source azimuth is ~ +90 deg");
        check(src.itdSeconds() > 0.0004f, "Woodworth ITD positive for left ear arrival");
        check(src.ildGainLeft() > src.ildGainRight(), "Left ear ILD gain is louder for left source");

        // Place source overhead (Y = +3m in user coords)
        src.setPosition({0.0f, 3.0f, 0.0f});
        check(src.elevationDeg() > 80.0f && src.elevationDeg() <= 90.0f, "Overhead source elevation is ~ +90 deg");
    }
}

// -----------------------------------------------------------------------------
// 3. Moving Source Trajectory System Tests
// -----------------------------------------------------------------------------
void testTrajectorySystem() {
    using namespace frostsoulx::spatial;

    // A. Left -> Right
    auto lr = TrajectoryFactory::createLeftToRight(10.0f, 2.0f, 0.0f, 4.0f);
    check(lr.waypoints.size() >= 30, "LeftToRight trajectory generated waypoints");
    Vec3 pStart = lr.positionAt(0.0f);
    Vec3 pEnd = lr.positionAt(4.0f);
    Coord3D uStart = Coord3D::fromEngineCoords(pStart);
    Coord3D uEnd = Coord3D::fromEngineCoords(pEnd);
    checkNear(uStart.x, -5.0, 1.0e-2, "LeftToRight starts at X = -5m");
    checkNear(uEnd.x, 5.0, 1.0e-2, "LeftToRight ends at X = +5m");

    // B. Front -> Back
    auto fb = TrajectoryFactory::createFrontToBack(10.0f, 0.0f, 0.0f, 4.0f);
    Coord3D fbStart = Coord3D::fromEngineCoords(fb.positionAt(0.0f));
    Coord3D fbEnd = Coord3D::fromEngineCoords(fb.positionAt(4.0f));
    checkNear(fbStart.z, 5.0, 1.0e-2, "FrontToBack starts in front (Z = +5m)");
    checkNear(fbEnd.z, -5.0, 1.0e-2, "FrontToBack ends in back (Z = -5m)");

    // E. Vertical Arc Overhead (Front -> Overhead -> Back)
    auto vert = TrajectoryFactory::createVerticalArcOverhead(3.0f, 4.0f);
    Coord3D vFront = Coord3D::fromEngineCoords(vert.positionAt(0.0f));
    Coord3D vApex = Coord3D::fromEngineCoords(vert.positionAt(2.0f));
    Coord3D vBack = Coord3D::fromEngineCoords(vert.positionAt(4.0f));
    checkNear(vFront.z, 3.0, 1.0e-2, "Vertical arc begins in front");
    checkNear(vApex.y, 3.0, 1.0e-2, "Vertical arc reaches overhead apex at Y = +3m");
    checkNear(vBack.z, -3.0, 1.0e-2, "Vertical arc lands in back");

    // G. Circular trajectory around listener
    auto circ = TrajectoryFactory::createCircular(4.0f, 0.0f, 4.0f);
    for (float t = 0.0f; t <= 4.0f; t += 0.5f) {
        Vec3 pos = circ.positionAt(t);
        Coord3D uPos = Coord3D::fromEngineCoords(pos);
        const float r = std::sqrt(uPos.x * uPos.x + uPos.z * uPos.z);
        checkNear(r, 4.0, 3.0e-2, "Circular motion maintains constant radius");
    }

    // Compound Demo: LEFT -> FRONT -> OVERHEAD -> BACK -> RIGHT
    auto demo = TrajectoryFactory::createCompoundDemo(3.0f, 8.0f);
    check(demo.waypoints.size() >= 48, "Compound demo trajectory generated");
}

// -----------------------------------------------------------------------------
// 4. Geometric Reflection Model & BRIR Generator Tests
// -----------------------------------------------------------------------------
void testGeometricReflectionModel() {
    using namespace frostsoulx::spatial;

    HrtfDatabase hrtf;
    check(hrtf.buildParametric(48000.0, 128, 10.0f, 15.0f), "HRTF database built");

    RirGeneratorConfig cfg;
    cfg.sampleRate = 48000.0;
    cfg.maxTaps = 16384;
    cfg.maxIsmOrder = 2;
    cfg.enableDiffuseTail = true;
    RirGenerator rirGen(cfg);

    // Test A: Geometric reflection paths
    auto hall = SpaceProfile::createMediumHall();
    auto paths = rirGen.calculateReflectionPaths(hall);
    check(!paths.empty(), "Geometric reflection paths calculated");

    // Check direct path (order 0)
    check(paths[0].order == 0, "First path is direct path (order 0)");
    checkNear(paths[0].distanceMeters, (hall.sourcePosition() - hall.listenerPosition()).length(),
              1.0e-3, "Direct path distance matches source-listener separation");
    check(paths.size() > 10, "Multiple specular reflection paths generated");

    // Test B: BRIR Generation & Symmetry for centered source
    {
        auto room = SpaceProfile::createLivingRoom();
        room.setSourcePosition({2.0f, 0.0f, 1.2f});
        room.setListenerPosition({0.0f, 0.0f, 1.2f});
        room.setListenerOrientation({0.0f, 0.0f, 0.0f});

        StereoBrir brir = rirGen.generateBrir(room, hrtf);
        check(brir.valid(), "Living room BRIR is valid");
        check(brir.taps == cfg.maxTaps, "BRIR tap length matches maxTaps");
        check(peak(brir.left) > 0.5 && peak(brir.left) <= 1.01, "Peak direct sound normalized to 1.0");

        const double rmsL = rms(brir.left);
        const double rmsR = rms(brir.right);
        checkNear(rmsL, rmsR, rmsL * 0.05, "Centered source yields symmetric BRIR energy");
    }

    // Test C: Natural Reverberant Decay
    {
        auto concert = SpaceProfile::createConcertHall();
        StereoBrir brir = rirGen.generateBrir(concert, hrtf);

        // Find direct sound onset
        std::size_t directOnset = 0;
        for (std::size_t i = 0; i < brir.left.size(); ++i) {
            if (std::fabs(brir.left[i]) > 0.05f) { directOnset = i; break; }
        }

        std::vector<float> head(brir.left.begin() + directOnset, brir.left.begin() + directOnset + 1024);
        std::vector<float> tail(brir.left.end() - 1024, brir.left.end());
        check(rms(head) > rms(tail) * 2.0, "Physical BRIR exhibits natural reverberant decay");
    }
}

// -----------------------------------------------------------------------------
// 5. Multi-Tier Convolver with Generated Full BRIR
// -----------------------------------------------------------------------------
void testFullBrirConvolution() {
    using namespace frostsoulx;
    using namespace frostsoulx::spatial;

    // Generate physical 8192-tap studio BRIR
    RirGeneratorConfig rcfg;
    rcfg.sampleRate = 48000.0;
    rcfg.maxTaps = 8192;
    rcfg.maxIsmOrder = 2;
    rcfg.enableDiffuseTail = true;

    RirGenerator rirGen(rcfg);
    auto room = SpaceProfile::createLivingRoom();
    StereoBrir brir = rirGen.generateBrir(room);
    check(brir.valid(), "Generated 8192-tap living room BRIR");

    // Configure 3-tier NonUniformConvolver to consume the full BRIR
    dsp::NonUniformConvolver::Config ccfg;
    ccfg.headBlock = 128;
    ccfg.maxTaps = 8192;
    ccfg.maxTiers = 3;
    ccfg.growth = 4;
    ccfg.crossfadeBlocks = 0;

    dsp::NonUniformConvolver convL, convR;
    check(convL.prepare(ccfg) && convR.prepare(ccfg), "Prepared 3-tier NonUniformConvolvers");

    convL.loadIr(brir.left.data(), brir.taps);
    convR.loadIr(brir.right.data(), brir.taps);

    // Convolve impulse through the full-convolution engine
    std::vector<float> in(128, 0.0f);
    in[0] = 1.0f; // Unit impulse
    std::vector<float> outL(128), outR(128);

    convL.processBlock(in.data(), outL.data());
    convR.processBlock(in.data(), outR.data());

    // Convolver output reproduces the initial taps of the BRIR
    double diff = 0.0;
    for (size_t i = 0; i < 128; ++i) {
        diff = std::max(diff, static_cast<double>(std::fabs(outL[i] - brir.left[i])));
    }
    check(diff < 1.0e-4, "Full-convolution engine reproduces physical BRIR direct arrival");

    // Process additional blocks to verify full tail convolution without dropouts
    for (int b = 1; b < 64; ++b) {
        std::fill(in.begin(), in.end(), 0.0f);
        convL.processBlock(in.data(), outL.data());
        convR.processBlock(in.data(), outR.data());
        for (size_t i = 0; i < 128; ++i) {
            check(std::isfinite(outL[i]) && std::isfinite(outR[i]), "All convolved samples are finite");
        }
    }
}

} // namespace

int main() {
    std::cout << "Running Acoustic Space Simulation System Tests...\n";
    testSpacePresets();
    testSpatialSourceModel();
    testTrajectorySystem();
    testGeometricReflectionModel();
    testFullBrirConvolution();

    if (g_failures != 0) {
        std::cerr << g_failures << " check(s) failed in Space Simulation tests!\n";
        return 1;
    }
    std::cout << "All Acoustic Space Simulation System tests passed successfully!\n";
    return 0;
}
