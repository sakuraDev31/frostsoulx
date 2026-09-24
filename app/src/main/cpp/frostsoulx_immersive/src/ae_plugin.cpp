#include "ae_plugin.h"

#include "frostsoulx/immersive_audio_engine.h"
#include "frostsoulx/spatial/space_profile.h"
#include "frostsoulx/spatial/spatial_source.h"

#include <algorithm>
#include <cmath>
#include <cstring>

struct AeEngine {
    frostsoulx::ImmersiveAudioEngine engine;
    int maxFrames = 0;
    float headYaw = 0.0f;
    float headPitch = 0.0f;
    float headRoll = 0.0f;
    float sourceX = 0.0f;
    float sourceY = 0.0f;
    float sourceZ = 2.0f;
    float listenerX = 0.0f;
    float listenerY = 0.0f;
    float listenerZ = 0.0f;
    int trajectoryType = 0;
    float trajectorySpeed = 1.0f;
    float trajectoryTime = 0.0f;
    float roomDimX = 10.0f;
    float roomDimY = 4.0f;
    float roomDimZ = 12.0f;
};

namespace {

constexpr int kMaxPluginFrames = 8192;

int roomPresetFromValue(float value) noexcept {
    if (!std::isfinite(value)) return 0;
    const int choice = static_cast<int>(std::lround(value));
    return std::clamp(choice, 0, 5);
}

frostsoulx::RoomSimulationPreset roomPreset(int choice) noexcept {
    switch (choice) {
        case 1: return frostsoulx::RoomSimulationPreset::SmallRoom;
        case 2: return frostsoulx::RoomSimulationPreset::Studio;
        case 3: return frostsoulx::RoomSimulationPreset::ConcertHall;
        case 4: return frostsoulx::RoomSimulationPreset::Cathedral;
        case 5: return frostsoulx::RoomSimulationPreset::Subway;
        case 0:
        default: return frostsoulx::RoomSimulationPreset::Off;
    }
}

frostsoulx::spatial::SpaceProfile::Preset spacePresetFromValue(float value) noexcept {
    using Preset = frostsoulx::spatial::SpaceProfile::Preset;
    const int choice = std::clamp(static_cast<int>(std::lround(value)), 0, 11);
    switch (choice) {
        case 0: return Preset::Bathroom;
        case 1: return Preset::LivingRoom;
        case 2: return Preset::MediumHall;
        case 3: return Preset::LargeHall;
        case 4: return Preset::ConcertHall;
        case 5: return Preset::SubwayPlatform;
        case 6: return Preset::LongSubwayTunnel;
        case 7: return Preset::LongTunnel;
        case 8: return Preset::ClosedCar;
        case 9: return Preset::OpenRoad;
        case 10: return Preset::Cave;
        case 11: return Preset::Stadium;
        default: return Preset::LivingRoom;
    }
}

frostsoulx::SpatialBackend backendFromValue(float value) noexcept {
    const int b = static_cast<int>(std::lround(value));
    switch (b) {
        case 1: return frostsoulx::SpatialBackend::SteamAudio;
        case 2: return frostsoulx::SpatialBackend::Native;
        case 3: return frostsoulx::SpatialBackend::FullConvolution;
        case 0:
        default: return frostsoulx::SpatialBackend::None;
    }
}

frostsoulx::spatial::AcousticMaterial materialFromIndex(int index) noexcept {
    using namespace frostsoulx::spatial;
    switch (index) {
        case 0: return AcousticMaterial::Concrete();
        case 1: return AcousticMaterial::Brick();
        case 2: return AcousticMaterial::WoodPanel();
        case 3: return AcousticMaterial::Glass();
        case 4: return AcousticMaterial::Plasterboard();
        case 5: return AcousticMaterial::Carpet();
        case 6: return AcousticMaterial::Marble();
        case 7: return AcousticMaterial::AcousticDrapes();
        case 8: return AcousticMaterial::OpenAir();
        case 9: return AcousticMaterial::CeramicTile();
        case 10: return AcousticMaterial::RoughStone();
        case 11: return AcousticMaterial::CarInterior();
        case 12: return AcousticMaterial::Asphalt();
        case 13: return AcousticMaterial::AudienceSeating();
        default: return AcousticMaterial::Concrete();
    }
}

void applyTrajectory(AeEngine* e, int type) {
    using namespace frostsoulx::spatial;
    switch (type) {
        case 1: e->engine.setSourceTrajectory(TrajectoryFactory::createLeftToRight()); break;
        case 2: e->engine.setSourceTrajectory(TrajectoryFactory::createFrontToBack()); break;
        case 3: e->engine.setSourceTrajectory(TrajectoryFactory::createBackToFront()); break;
        case 4: e->engine.setSourceTrajectory(TrajectoryFactory::createHorizontalArc()); break;
        case 5: e->engine.setSourceTrajectory(TrajectoryFactory::createVerticalArcOverhead()); break;
        case 6: e->engine.setSourceTrajectory(TrajectoryFactory::createOrbital3D()); break;
        case 7: e->engine.setSourceTrajectory(TrajectoryFactory::createCircular()); break;
        case 8: e->engine.setSourceTrajectory(TrajectoryFactory::createCompoundDemo()); break;
        default: break;
    }
}

} // namespace

extern "C" {

AE_EXPORT int ae_abi_version(void) {
    return AE_ABI_VERSION;
}

AE_EXPORT AeEngine* ae_create(int sample_rate, int channels) {
    if (channels != 2 || sample_rate < 8000 || sample_rate > 384000) return nullptr;

    auto* e = new AeEngine();
    e->maxFrames = kMaxPluginFrames;
    if (!e->engine.prepare(sample_rate, e->maxFrames)) {
        delete e;
        return nullptr;
    }

    // The plugin starts as a transparent native spatial engine. The host can
    // then apply manifest parameters before the first audio callback.
    e->engine.setRoomSimulationPreset(frostsoulx::RoomSimulationPreset::Off);
    e->engine.setRoomMix(0.0f);
    e->engine.setSpatialBlend(1.0f);
    e->engine.setEnabled(true);
    return e;
}

AE_EXPORT void ae_destroy(AeEngine* e) {
    delete e;
}

AE_EXPORT void ae_set_param(AeEngine* e, const char* id, float value) {
    if (!e || !id || !std::isfinite(value)) return;

    if (std::strcmp(id, "enabled") == 0) {
        e->engine.setEnabled(value >= 0.5f);
    } else if (std::strcmp(id, "spatial_blend") == 0) {
        e->engine.setSpatialBlend(value);
    } else if (std::strcmp(id, "spatial_backend") == 0 || std::strcmp(id, "backend") == 0) {
        e->engine.setSpatialBackendPreference(backendFromValue(value));
    } else if (std::strcmp(id, "space_preset") == 0 || std::strcmp(id, "space") == 0) {
        e->engine.setSpacePreset(spacePresetFromValue(value));
    } else if (std::strcmp(id, "room_preset") == 0) {
        e->engine.setRoomSimulationPreset(roomPreset(roomPresetFromValue(value)));
    } else if (std::strcmp(id, "room_mix") == 0) {
        e->engine.setRoomMix(value);
    } else if (std::strcmp(id, "reflection_amount") == 0) {
        e->engine.setReflectionAmount(value);
    } else if (std::strcmp(id, "reverb_time") == 0) {
        e->engine.setReverbTimeSeconds(value);
    } else if (std::strcmp(id, "room_size") == 0) {
        e->engine.setRoomSize(value);
    } else if (std::strcmp(id, "room_size_x") == 0 || std::strcmp(id, "room_dim_x") == 0) {
        e->roomDimX = std::max(1.0f, value);
        e->engine.setRoomDimensions(e->roomDimX, e->roomDimY, e->roomDimZ);
    } else if (std::strcmp(id, "room_size_y") == 0 || std::strcmp(id, "room_dim_y") == 0) {
        e->roomDimY = std::max(1.0f, value);
        e->engine.setRoomDimensions(e->roomDimX, e->roomDimY, e->roomDimZ);
    } else if (std::strcmp(id, "room_size_z") == 0 || std::strcmp(id, "room_dim_z") == 0) {
        e->roomDimZ = std::max(1.0f, value);
        e->engine.setRoomDimensions(e->roomDimX, e->roomDimY, e->roomDimZ);
    } else if (std::strcmp(id, "dampening") == 0) {
        e->engine.setDampening(value);
    } else if (std::strcmp(id, "stereo_width") == 0) {
        e->engine.setStereoWidth(value);
    } else if (std::strcmp(id, "source_x") == 0) {
        e->sourceX = value;
        e->engine.setSourcePosition(e->sourceX, e->sourceY, e->sourceZ);
    } else if (std::strcmp(id, "source_y") == 0) {
        e->sourceY = value;
        e->engine.setSourcePosition(e->sourceX, e->sourceY, e->sourceZ);
    } else if (std::strcmp(id, "source_z") == 0) {
        e->sourceZ = value;
        e->engine.setSourcePosition(e->sourceX, e->sourceY, e->sourceZ);
    } else if (std::strcmp(id, "listener_x") == 0) {
        e->listenerX = value;
        e->engine.setListenerPosition(e->listenerX, e->listenerY, e->listenerZ);
    } else if (std::strcmp(id, "listener_y") == 0) {
        e->listenerY = value;
        e->engine.setListenerPosition(e->listenerX, e->listenerY, e->listenerZ);
    } else if (std::strcmp(id, "listener_z") == 0) {
        e->listenerZ = value;
        e->engine.setListenerPosition(e->listenerX, e->listenerY, e->listenerZ);
    } else if (std::strcmp(id, "head_yaw") == 0 || std::strcmp(id, "listener_yaw") == 0) {
        e->headYaw = value;
        e->engine.setListenerOrientation(e->headYaw, e->headPitch, e->headRoll);
    } else if (std::strcmp(id, "head_pitch") == 0 || std::strcmp(id, "listener_pitch") == 0) {
        e->headPitch = value;
        e->engine.setListenerOrientation(e->headYaw, e->headPitch, e->headRoll);
    } else if (std::strcmp(id, "head_roll") == 0 || std::strcmp(id, "listener_roll") == 0) {
        e->headRoll = value;
        e->engine.setListenerOrientation(e->headYaw, e->headPitch, e->headRoll);
    } else if (std::strcmp(id, "trajectory") == 0 || std::strcmp(id, "trajectory_type") == 0) {
        e->trajectoryType = std::clamp(static_cast<int>(std::lround(value)), 0, 8);
        applyTrajectory(e, e->trajectoryType);
    } else if (std::strcmp(id, "trajectory_speed") == 0) {
        e->trajectorySpeed = std::max(0.001f, value);
    } else if (std::strcmp(id, "trajectory_pos") == 0 || std::strcmp(id, "trajectory_time") == 0) {
        e->trajectoryTime = value;
        e->engine.setTrajectoryPosition(e->trajectoryTime * e->trajectorySpeed);
    } else if (std::strcmp(id, "material") == 0 || std::strcmp(id, "material_type") == 0) {
        const int m = std::clamp(static_cast<int>(std::lround(value)), 0, 13);
        const auto mat = materialFromIndex(m);
        for (int s = 0; s < 6; ++s) {
            e->engine.setBoundaryMaterial(static_cast<frostsoulx::spatial::RoomSurface>(s), mat);
        }
    } else if (std::strcmp(id, "material_absorption") == 0 || std::strcmp(id, "absorption") == 0) {
        const float alpha = std::clamp(value, 0.01f, 0.99f);
        frostsoulx::spatial::AcousticMaterial mat;
        mat.name = "CustomAbsorption";
        mat.absorption.fill(alpha);
        mat.scattering = 0.15f;
        for (int s = 0; s < 6; ++s) {
            e->engine.setBoundaryMaterial(static_cast<frostsoulx::spatial::RoomSurface>(s), mat);
        }
    } else if (std::strcmp(id, "reflection_density") == 0 || std::strcmp(id, "density") == 0) {
        e->engine.setReflectionDensity(value);
    } else if (std::strcmp(id, "ir_length") == 0) {
        const int taps = std::clamp(static_cast<int>(std::lround(value)), 512, 32768);
        e->engine.setIrLength(static_cast<std::size_t>(taps));
    }
    // Unknown parameter IDs are intentionally ignored per ABI contract.
}

AE_EXPORT void ae_process(AeEngine* e, float* data, int frames) {
    if (!e || !data || frames <= 0 || frames > e->maxFrames) return;
    (void)e->engine.process(data, frames);
}

AE_EXPORT void ae_reset(AeEngine* e) {
    if (e) e->engine.reset();
}

} // extern "C"
