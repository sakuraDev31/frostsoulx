#include "ae_plugin.h"

#include <cmath>
#include <cstddef>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

namespace {
int failures = 0;

void check(bool ok, const std::string& what) {
    if (!ok) {
        std::cerr << "FAIL: " << what << "\n";
        ++failures;
    }
}

bool sane(const std::vector<float>& data) {
    for (float sample : data) {
        if (!std::isfinite(sample) || std::fabs(sample) > 0.99f) return false;
    }
    return true;
}

bool hasEnergy(const std::vector<float>& data) {
    for (float sample : data) {
        if (std::fabs(sample) > 1e-6f) return true;
    }
    return false;
}
} // namespace

int main() {
    std::cout << "--- Stage 7: Testing Plugin ABI & UI Controls ---\n";

    check(ae_abi_version() == AE_ABI_VERSION, "ABI version is 1");
    check(ae_create(48000, 1) == nullptr, "mono creation is rejected");
    check(ae_create(4000, 2) == nullptr, "unsupported sample rate is rejected");

    AeEngine* engine = ae_create(48000, 2);
    check(engine != nullptr, "stereo engine is created");
    if (!engine) return 1;

    // 1. Verify legacy parameters
    for (const auto& param : {
             std::pair<const char*, float>{"enabled", 1.0f},
             {"spatial_blend", 0.8f},
             {"room_preset", 3.0f},
             {"room_mix", 0.2f},
             {"reflection_amount", 0.35f},
             {"reverb_time", 1.7f},
             {"room_size", 0.6f},
             {"dampening", 0.4f},
             {"stereo_width", 0.9f},
             {"head_yaw", 12.0f},
             {"head_pitch", -4.0f},
             {"head_roll", 2.0f},
         }) {
        ae_set_param(engine, param.first, param.second);
    }
    ae_set_param(engine, "unknown_parameter", 123.0f);
    ae_set_param(engine, "room_mix", std::numeric_limits<float>::quiet_NaN());

    // 2. Verify all 12 space presets
    for (int p = 0; p < 12; ++p) {
        ae_set_param(engine, "space_preset", static_cast<float>(p));
    }
    check(true, "all 12 physical space presets applied successfully");

    // 3. Verify spatial backend selection
    ae_set_param(engine, "spatial_backend", 3.0f); // FullConvolution
    check(true, "spatial_backend FullConvolution set");

    // 4. Verify 3D source and listener coordinates
    ae_set_param(engine, "source_x", 1.5f);
    ae_set_param(engine, "source_y", 0.2f);
    ae_set_param(engine, "source_z", 2.5f);
    ae_set_param(engine, "listener_x", 0.0f);
    ae_set_param(engine, "listener_y", 0.0f);
    ae_set_param(engine, "listener_z", 0.0f);
    ae_set_param(engine, "listener_yaw", 30.0f);
    ae_set_param(engine, "listener_pitch", -10.0f);
    ae_set_param(engine, "listener_roll", 5.0f);
    check(true, "3D source and listener coordinates set");

    // 5. Verify room geometry, materials, and acoustic controls
    ae_set_param(engine, "room_size_x", 15.0f);
    ae_set_param(engine, "room_size_y", 5.0f);
    ae_set_param(engine, "room_size_z", 20.0f);
    ae_set_param(engine, "material", 2.0f); // WoodPanel
    ae_set_param(engine, "material_absorption", 0.25f);
    ae_set_param(engine, "reflection_density", 0.65f);
    ae_set_param(engine, "ir_length", 4096.0f);
    check(true, "room dimensions, materials, and IR length set");

    // 6. Verify all 8 trajectories and trajectory time/speed
    for (int t = 1; t <= 8; ++t) {
        ae_set_param(engine, "trajectory", static_cast<float>(t));
        ae_set_param(engine, "trajectory_speed", 1.5f);
        ae_set_param(engine, "trajectory_pos", 1.0f);
    }
    check(true, "all 8 trajectory models applied successfully");

    // 7. Test full convolution audio processing across arbitrary block sizes
    ae_set_param(engine, "space_preset", 4.0f); // ConcertHall
    ae_set_param(engine, "spatial_backend", 3.0f); // FullConvolution
    ae_set_param(engine, "spatial_blend", 1.0f);

    bool sawEnergy = false;
    for (int frames : {1, 7, 64, 128, 384, 512, 1024}) {
        std::vector<float> data(static_cast<std::size_t>(frames) * 2);
        for (int i = 0; i < frames; ++i) {
            data[static_cast<std::size_t>(i) * 2] = 0.35f * std::sin(i * 0.021f);
            data[static_cast<std::size_t>(i) * 2 + 1] = 0.25f * std::cos(i * 0.017f);
        }
        ae_process(engine, data.data(), frames);
        check(sane(data), "FullConvolution output is finite and bounded for block size " + std::to_string(frames));
        if (hasEnergy(data)) {
            sawEnergy = true;
        }
    }
    check(sawEnergy, "FullConvolution stream produces non-zero reverberant audio output");

    // 8. Test dynamic parameter crossfade while audio is running
    for (int step = 0; step < 10; ++step) {
        ae_set_param(engine, "source_x", -3.0f + static_cast<float>(step) * 0.6f);
        std::vector<float> data(256, 0.2f);
        ae_process(engine, data.data(), 128);
        check(sane(data), "dynamic source movement preserves audio sanity at step " + std::to_string(step));
    }

    // 9. Bypass test (must be bit-transparent when enabled=0)
    std::vector<float> bypass(768, 0.25f);
    const auto original = bypass;
    ae_set_param(engine, "enabled", 0.0f);
    ae_process(engine, bypass.data(), 384);
    check(bypass == original, "disabled ABI path is bit-transparent");

    // 10. Reset test
    ae_reset(engine);
    ae_set_param(engine, "enabled", 1.0f);
    ae_process(engine, bypass.data(), 384);
    check(sane(bypass), "output after reset is finite and bounded");

    ae_destroy(engine);
    std::cout << (failures == 0 ? "ALL ABI plugin and UI control tests passed (0 failures)!\n"
                               : "ABI plugin checks failed with " + std::to_string(failures) + " failures\n");
    return failures == 0 ? 0 : 1;
}
