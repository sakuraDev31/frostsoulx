#include "frostsoulx/spatial/space_profile.h"

#include <algorithm>
#include <cmath>

namespace frostsoulx::spatial {

// -----------------------------------------------------------------------------
// Acoustic Materials Library
// -----------------------------------------------------------------------------

AcousticMaterial AcousticMaterial::Concrete() {
    return {"Concrete", {0.01f, 0.01f, 0.02f, 0.02f, 0.02f, 0.03f}, 0.05f};
}

AcousticMaterial AcousticMaterial::Brick() {
    return {"Brick", {0.03f, 0.03f, 0.03f, 0.04f, 0.05f, 0.07f}, 0.25f};
}

AcousticMaterial AcousticMaterial::WoodPanel() {
    return {"WoodPanel", {0.28f, 0.22f, 0.17f, 0.09f, 0.10f, 0.11f}, 0.15f};
}

AcousticMaterial AcousticMaterial::Glass() {
    return {"Glass", {0.18f, 0.06f, 0.04f, 0.03f, 0.02f, 0.02f}, 0.05f};
}

AcousticMaterial AcousticMaterial::Plasterboard() {
    return {"Plasterboard", {0.15f, 0.11f, 0.05f, 0.04f, 0.07f, 0.09f}, 0.10f};
}

AcousticMaterial AcousticMaterial::Carpet() {
    return {"Carpet", {0.02f, 0.06f, 0.14f, 0.37f, 0.60f, 0.65f}, 0.35f};
}

AcousticMaterial AcousticMaterial::Marble() {
    return {"Marble", {0.01f, 0.01f, 0.01f, 0.01f, 0.02f, 0.02f}, 0.05f};
}

AcousticMaterial AcousticMaterial::AcousticDrapes() {
    return {"AcousticDrapes", {0.10f, 0.32f, 0.55f, 0.70f, 0.74f, 0.70f}, 0.40f};
}

AcousticMaterial AcousticMaterial::OpenAir() {
    // 100% absorption = sound escapes into the open atmosphere
    return {"OpenAir", {0.99f, 0.99f, 0.99f, 0.99f, 0.99f, 0.99f}, 0.0f};
}

AcousticMaterial AcousticMaterial::CeramicTile() {
    // Highly reflective hard glazed tile
    return {"CeramicTile", {0.01f, 0.01f, 0.015f, 0.02f, 0.025f, 0.035f}, 0.05f};
}

AcousticMaterial AcousticMaterial::RoughStone() {
    // Irregular rough rock surface with high scattering
    return {"RoughStone", {0.06f, 0.08f, 0.10f, 0.12f, 0.15f, 0.18f}, 0.75f};
}

AcousticMaterial AcousticMaterial::CarInterior() {
    // Padded upholstery and dashboard materials
    return {"CarInterior", {0.15f, 0.25f, 0.40f, 0.50f, 0.60f, 0.65f}, 0.20f};
}

AcousticMaterial AcousticMaterial::Asphalt() {
    // Road asphalt / smooth concrete outdoor surface
    return {"Asphalt", {0.03f, 0.04f, 0.05f, 0.07f, 0.08f, 0.10f}, 0.15f};
}

AcousticMaterial AcousticMaterial::AudienceSeating() {
    // Upholstered seats with audience present
    return {"AudienceSeating", {0.30f, 0.42f, 0.58f, 0.68f, 0.70f, 0.68f}, 0.45f};
}

// -----------------------------------------------------------------------------
// SourceTrajectory
// -----------------------------------------------------------------------------

Vec3 SourceTrajectory::positionAt(float timeSeconds) const noexcept {
    if (waypoints.empty()) return {0.0f, 0.0f, 0.0f};
    if (waypoints.size() == 1 || timeSeconds <= waypoints.front().timeSeconds) {
        return waypoints.front().position;
    }
    if (timeSeconds >= waypoints.back().timeSeconds) {
        return waypoints.back().position;
    }

    for (std::size_t i = 0; i + 1 < waypoints.size(); ++i) {
        if (timeSeconds >= waypoints[i].timeSeconds && timeSeconds <= waypoints[i + 1].timeSeconds) {
            const float dt = waypoints[i + 1].timeSeconds - waypoints[i].timeSeconds;
            const float t = (dt > 1.0e-5f) ? (timeSeconds - waypoints[i].timeSeconds) / dt : 0.0f;
            return waypoints[i].position + (waypoints[i + 1].position - waypoints[i].position) * t;
        }
    }
    return waypoints.back().position;
}

HeadOrientation SourceTrajectory::orientationAt(float timeSeconds) const noexcept {
    if (waypoints.empty()) return {};
    if (waypoints.size() == 1 || timeSeconds <= waypoints.front().timeSeconds) {
        return waypoints.front().orientation;
    }
    if (timeSeconds >= waypoints.back().timeSeconds) {
        return waypoints.back().orientation;
    }

    for (std::size_t i = 0; i + 1 < waypoints.size(); ++i) {
        if (timeSeconds >= waypoints[i].timeSeconds && timeSeconds <= waypoints[i + 1].timeSeconds) {
            const float dt = waypoints[i + 1].timeSeconds - waypoints[i].timeSeconds;
            const float t = (dt > 1.0e-5f) ? (timeSeconds - waypoints[i].timeSeconds) / dt : 0.0f;
            HeadOrientation o;
            o.yawDeg = waypoints[i].orientation.yawDeg +
                       (waypoints[i + 1].orientation.yawDeg - waypoints[i].orientation.yawDeg) * t;
            o.pitchDeg = waypoints[i].orientation.pitchDeg +
                         (waypoints[i + 1].orientation.pitchDeg - waypoints[i].orientation.pitchDeg) * t;
            o.rollDeg = waypoints[i].orientation.rollDeg +
                        (waypoints[i + 1].orientation.rollDeg - waypoints[i].orientation.rollDeg) * t;
            return o;
        }
    }
    return waypoints.back().orientation;
}

// -----------------------------------------------------------------------------
// SpaceProfile
// -----------------------------------------------------------------------------

SpaceProfile::SpaceProfile(std::string name, Vec3 dimensions)
    : name_(std::move(name)) {
    setDimensions(dimensions);
    for (std::size_t i = 0; i < 6; ++i) {
        boundaries_[i].surface = static_cast<RoomSurface>(i);
        boundaries_[i].material = AcousticMaterial::Plasterboard();
        boundaries_[i].openness = 0.0f;
    }
    boundaries_[static_cast<std::size_t>(RoomSurface::Floor)].material = AcousticMaterial::Carpet();
    boundaries_[static_cast<std::size_t>(RoomSurface::Ceiling)].material = AcousticMaterial::Plasterboard();
}

void SpaceProfile::setDimensions(const Vec3& dims) noexcept {
    dimensions_.x = std::max(dims.x, 1.0f);
    dimensions_.y = std::max(dims.y, 1.0f);
    dimensions_.z = std::max(dims.z, 1.0f);
    sourcePos_ = clampToRoom(sourcePos_);
    listenerPos_ = clampToRoom(listenerPos_);
}

Vec3 SpaceProfile::clampToRoom(const Vec3& p) const noexcept {
    const Vec3 eff = effectiveDimensions();
    constexpr float kWallMargin = 0.1f;
    return {
        std::clamp(p.x, -eff.x * 0.5f + kWallMargin, eff.x * 0.5f - kWallMargin),
        std::clamp(p.y, -eff.y * 0.5f + kWallMargin, eff.y * 0.5f - kWallMargin),
        std::clamp(p.z, 0.0f + kWallMargin, eff.z - kWallMargin)
    };
}

float SpaceProfile::volume() const noexcept {
    const Vec3 d = effectiveDimensions();
    return d.x * d.y * d.z;
}

float SpaceProfile::surfaceArea(RoomSurface surface) const noexcept {
    const Vec3 d = effectiveDimensions();
    switch (surface) {
        case RoomSurface::Floor:
        case RoomSurface::Ceiling:
            return d.x * d.y;
        case RoomSurface::Front:
        case RoomSurface::Back:
            return d.y * d.z;
        case RoomSurface::Left:
        case RoomSurface::Right:
            return d.x * d.z;
    }
    return 0.0f;
}

float SpaceProfile::totalSurfaceArea() const noexcept {
    const Vec3 d = effectiveDimensions();
    return 2.0f * (d.x * d.y + d.x * d.z + d.y * d.z);
}

std::array<float, kNumAcousticBands> SpaceProfile::averageAbsorption() const noexcept {
    std::array<float, kNumAcousticBands> avg{};
    const float totalArea = totalSurfaceArea();
    if (totalArea <= 0.0f) return avg;

    for (std::size_t s = 0; s < 6; ++s) {
        const auto surf = static_cast<RoomSurface>(s);
        const float area = surfaceArea(surf);
        const auto& b = boundaries_[s];
        const float effOpen = std::clamp(std::max(b.openness, openness_), 0.0f, 1.0f);

        for (std::size_t band = 0; band < kNumAcousticBands; ++band) {
            // Material absorption blended with openness (open = 1.0 absorption)
            const float alpha = b.material.absorption[band] * (1.0f - effOpen) + 1.0f * effOpen;
            avg[band] += area * alpha;
        }
    }

    for (std::size_t band = 0; band < kNumAcousticBands; ++band) {
        avg[band] = std::clamp(avg[band] / totalArea, 0.01f, 0.99f);
    }
    return avg;
}

std::array<float, kNumAcousticBands> SpaceProfile::reverberationTimeT60() const noexcept {
    std::array<float, kNumAcousticBands> t60{};
    const float V = volume();
    const float S = totalSurfaceArea();
    if (V <= 0.0f || S <= 0.0f) return t60;

    const auto alpha = averageAbsorption();

    // Standard atmospheric air attenuation 4*m (m^-1) across octave bands
    // (125, 250, 500, 1k, 2k, 4k Hz) at 20 deg C, 50% relative humidity:
    constexpr std::array<float, kNumAcousticBands> kAirAbsorption4m = {
        0.0001f, 0.0003f, 0.0008f, 0.0019f, 0.0044f, 0.0135f
    };

    for (std::size_t band = 0; band < kNumAcousticBands; ++band) {
        const float a = alpha[band];
        const float airTerm = kAirAbsorption4m[band] * V;
        if (a >= 0.98f) {
            t60[band] = 0.05f; // An-echoic
        } else if (a < 0.02f) {
            // Sabine formula with air absorption
            t60[band] = (0.161f * V) / std::max(S * a + airTerm, 1.0e-3f);
        } else {
            // Eyring-Norris formula with air absorption: T60 = 0.161 * V / (-S * ln(1 - a) + 4mV)
            const float denom = -S * std::log(1.0f - a) + airTerm;
            t60[band] = (0.161f * V) / std::max(denom, 1.0e-3f);
        }
        t60[band] = std::clamp(t60[band], 0.05f, 20.0f);
    }
    return t60;
}

// -----------------------------------------------------------------------------
// Factory Presets (All 12 Physical Environments)
// -----------------------------------------------------------------------------

SpaceProfile SpaceProfile::createBathroom() {
    SpaceProfile p("Bathroom", {2.4f, 2.0f, 2.5f});
    p.setSourcePosition({0.5f, 0.0f, 1.2f});
    p.setListenerPosition({0.0f, 0.0f, 1.2f});

    // Small bathroom with ceramic tiles, mirror, and doorway/ventilation absorption
    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::CeramicTile(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::Plasterboard(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::CeramicTile(), 0.0f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::WoodPanel(), 0.10f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::CeramicTile(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::Glass(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createLivingRoom() {
    SpaceProfile p("Living Room", {6.0f, 4.5f, 2.8f});
    p.setSourcePosition({1.5f, 0.0f, 1.2f});
    p.setListenerPosition({0.0f, 0.0f, 1.2f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::Carpet(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::Plasterboard(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::WoodPanel(), 0.0f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::AcousticDrapes(), 0.0f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::Plasterboard(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::Glass(), 0.1f});
    return p;
}

SpaceProfile SpaceProfile::createMediumHall() {
    SpaceProfile p("Medium Hall", {18.0f, 12.0f, 6.5f});
    p.setSourcePosition({5.0f, 0.0f, 1.4f});
    p.setListenerPosition({-2.0f, 0.0f, 1.4f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::WoodPanel(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::Plasterboard(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::WoodPanel(), 0.0f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::AcousticDrapes(), 0.0f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::Brick(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::Brick(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createLargeHall() {
    SpaceProfile p("Large Hall", {35.0f, 22.0f, 12.0f});
    p.setSourcePosition({10.0f, 0.0f, 1.6f});
    p.setListenerPosition({-5.0f, 0.0f, 1.4f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::WoodPanel(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::Plasterboard(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::WoodPanel(), 0.0f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::Brick(), 0.0f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::Brick(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::Brick(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createConcertHall() {
    SpaceProfile p("Concert Hall", {36.0f, 24.0f, 15.0f});
    p.setSourcePosition({8.0f, 0.0f, 1.8f});
    p.setListenerPosition({-3.0f, 0.0f, 1.4f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::AudienceSeating(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::Plasterboard(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::WoodPanel(), 0.0f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::AcousticDrapes(), 0.0f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::WoodPanel(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::WoodPanel(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createSubwayPlatform() {
    SpaceProfile p("Subway Platform", {80.0f, 10.0f, 4.5f});
    p.setSourcePosition({15.0f, 1.5f, 1.5f});
    p.setListenerPosition({-5.0f, -1.0f, 1.5f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::Asphalt(), 0.05f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::Concrete(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::OpenAir(), 0.5f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::OpenAir(), 0.5f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::CeramicTile(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::CeramicTile(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createLongSubwayTunnel() {
    SpaceProfile p("Long Subway Tunnel", {200.0f, 5.5f, 5.0f});
    p.setSourcePosition({30.0f, 0.0f, 1.5f});
    p.setListenerPosition({0.0f, 0.0f, 1.5f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::Concrete(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::Concrete(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::OpenAir(), 0.4f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::OpenAir(), 0.4f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::Concrete(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::Concrete(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createLongTunnel() {
    SpaceProfile p("Long Tunnel", {350.0f, 9.0f, 6.0f});
    p.setSourcePosition({50.0f, 1.0f, 1.5f});
    p.setListenerPosition({0.0f, 0.0f, 1.5f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::Asphalt(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::Concrete(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::OpenAir(), 0.35f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::OpenAir(), 0.35f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::Concrete(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::Concrete(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createClosedCar() {
    SpaceProfile p("Closed Car Cabin", {2.8f, 1.6f, 1.2f});
    p.setSourcePosition({0.4f, -0.3f, 0.8f});
    p.setListenerPosition({0.0f, 0.0f, 0.8f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::Carpet(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::CarInterior(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::Glass(), 0.0f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::CarInterior(), 0.0f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::Glass(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::Glass(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createOpenRoad() {
    SpaceProfile p("Open Road", {100.0f, 40.0f, 20.0f});
    p.setOpenness(0.98f);
    p.setSourcePosition({10.0f, 0.0f, 1.5f});
    p.setListenerPosition({0.0f, 0.0f, 1.5f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::Asphalt(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::OpenAir(), 1.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::OpenAir(), 0.98f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::OpenAir(), 0.98f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::OpenAir(), 0.98f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::OpenAir(), 0.98f});
    return p;
}

SpaceProfile SpaceProfile::createCave() {
    SpaceProfile p("Subterranean Cave", {28.0f, 20.0f, 14.0f});
    p.setSourcePosition({6.0f, 2.0f, 1.5f});
    p.setListenerPosition({-2.0f, 0.0f, 1.5f});

    const BoundarySurface stoneFloor{RoomSurface::Floor, AcousticMaterial::RoughStone(), 0.0f};
    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::RoughStone(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::RoughStone(), 0.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::RoughStone(), 0.0f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::RoughStone(), 0.0f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::RoughStone(), 0.0f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::RoughStone(), 0.0f});
    return p;
}

SpaceProfile SpaceProfile::createStadium() {
    SpaceProfile p("Open Stadium Arena", {140.0f, 90.0f, 35.0f});
    p.setOpenness(0.7f);
    p.setSourcePosition({30.0f, 10.0f, 2.0f});
    p.setListenerPosition({-20.0f, 0.0f, 5.0f});

    p.setBoundary(RoomSurface::Floor, {RoomSurface::Floor, AcousticMaterial::Asphalt(), 0.0f});
    p.setBoundary(RoomSurface::Ceiling, {RoomSurface::Ceiling, AcousticMaterial::OpenAir(), 1.0f});
    p.setBoundary(RoomSurface::Front, {RoomSurface::Front, AcousticMaterial::Concrete(), 0.5f});
    p.setBoundary(RoomSurface::Back, {RoomSurface::Back, AcousticMaterial::Concrete(), 0.5f});
    p.setBoundary(RoomSurface::Left, {RoomSurface::Left, AcousticMaterial::Concrete(), 0.5f});
    p.setBoundary(RoomSurface::Right, {RoomSurface::Right, AcousticMaterial::Concrete(), 0.5f});
    return p;
}

SpaceProfile SpaceProfile::createPreset(Preset preset) {
    switch (preset) {
        case Preset::Bathroom: return createBathroom();
        case Preset::LivingRoom: return createLivingRoom();
        case Preset::MediumHall: return createMediumHall();
        case Preset::LargeHall: return createLargeHall();
        case Preset::ConcertHall: return createConcertHall();
        case Preset::SubwayPlatform: return createSubwayPlatform();
        case Preset::LongSubwayTunnel: return createLongSubwayTunnel();
        case Preset::LongTunnel: return createLongTunnel();
        case Preset::ClosedCar: return createClosedCar();
        case Preset::OpenRoad: return createOpenRoad();
        case Preset::Cave: return createCave();
        case Preset::Stadium: return createStadium();
    }
    return createLivingRoom();
}

} // namespace frostsoulx::spatial
