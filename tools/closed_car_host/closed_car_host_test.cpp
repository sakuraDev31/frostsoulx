#include "frostsoulx/ImmersiveAudioEngine.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <limits>
#include <vector>

using frostsoulx::ImmersiveAudioEngine;
using frostsoulx::ImmersiveProcessResult;
using frostsoulx::RoomSimulationPreset;

struct Metrics {
    double energy = 0.0;
    double difference = 0.0;
    double delayedEnergy = 0.0;
    float peak = 0.0f;
    int nonFinite = 0;
};

struct Controls {
    float fader;
    float reverbSeconds;
    float reflection;
    float dampening;
};

static float leftInput(int sample, int sampleRate) {
    const float time = static_cast<float>(sample) / static_cast<float>(sampleRate);
    return (sample == 0 ? 0.95f : 0.0f) +
        0.22f * std::sin(2.0f * 3.14159265f * 440.0f * time);
}

static float rightInput(int sample, int sampleRate) {
    const float time = static_cast<float>(sample) / static_cast<float>(sampleRate);
    return 0.11f * std::sin(2.0f * 3.14159265f * 880.0f * time + 0.7f);
}

static Metrics render(const Controls& controls, RoomSimulationPreset preset = RoomSimulationPreset::ClosedCar) {
    constexpr int sampleRate = 48000;
    constexpr int blockFrames = 384;
    constexpr int blockCount = 260;

    ImmersiveAudioEngine engine;
    if (!engine.prepare(sampleRate, blockFrames)) {
        std::fprintf(stderr, "FAIL: engine.prepare\n");
        std::exit(2);
    }
    engine.setRoomSimulationPreset(preset);
    engine.setEnabled(true);
    engine.setSpatialBlend(1.0f);
    engine.setRoomMix(1.0f);
    engine.setReflectionAmount(controls.reflection);
    engine.setReverbTimeSeconds(controls.reverbSeconds);
    engine.setDampening(controls.dampening);
    engine.setRoomSize(0.5f);
    engine.setStereoWidth(0.5f);
    engine.setCarFader(controls.fader);

    Metrics metrics;
    std::vector<float> buffer(static_cast<std::size_t>(blockFrames) * 2U);
    for (int block = 0; block < blockCount; ++block) {
        for (int frame = 0; frame < blockFrames; ++frame) {
            const int sample = block * blockFrames + frame;
            buffer[static_cast<std::size_t>(frame) * 2U] = leftInput(sample, sampleRate);
            buffer[static_cast<std::size_t>(frame) * 2U + 1U] = rightInput(sample, sampleRate);
        }
        if (!engine.process(buffer.data(), blockFrames)) {
            std::fprintf(stderr, "FAIL: process block=%d result=%d\n", block,
                         static_cast<int>(engine.lastProcessResult()));
            std::exit(3);
        }
        for (int frame = 0; frame < blockFrames; ++frame) {
            const int sample = block * blockFrames + frame;
            const float outputLeft = buffer[static_cast<std::size_t>(frame) * 2U];
            const float outputRight = buffer[static_cast<std::size_t>(frame) * 2U + 1U];
            if (!std::isfinite(outputLeft) || !std::isfinite(outputRight)) ++metrics.nonFinite;
            metrics.peak = std::max(metrics.peak, std::max(std::fabs(outputLeft), std::fabs(outputRight)));
            metrics.energy += static_cast<double>(outputLeft * outputLeft + outputRight * outputRight);
            metrics.difference += static_cast<double>(
                std::fabs(outputLeft - leftInput(sample, sampleRate)) +
                std::fabs(outputRight - rightInput(sample, sampleRate)));
            if (sample >= 2400) {
                metrics.delayedEnergy += static_cast<double>(outputLeft * outputLeft + outputRight * outputRight);
            }
        }
    }
    return metrics;
}

static void require(bool condition, const char* message) {
    if (!condition) {
        std::fprintf(stderr, "FAIL: %s\n", message);
        std::exit(10);
    }
}

static void printMetrics(const char* name, const Metrics& metrics) {
    std::printf("%-18s energy=%12.6f diff=%12.6f delayed=%12.6f peak=%.6f nonfinite=%d\n",
                name, metrics.energy, metrics.difference, metrics.delayedEnergy,
                metrics.peak, metrics.nonFinite);
}

static void testBypass() {
    ImmersiveAudioEngine engine;
    require(engine.prepare(48000, 384), "bypass prepare");
    engine.setEnabled(false);
    std::vector<float> buffer(768, 0.25f);
    const std::vector<float> original = buffer;
    require(!engine.process(buffer.data(), 384), "OFF should report bypass");
    require(engine.lastProcessResult() == ImmersiveProcessResult::Disabled, "OFF result should be Disabled");
    require(buffer == original, "OFF path must remain byte-for-byte unchanged");
    engine.setEnabled(true);
    require(!engine.process(nullptr, 384), "null input should be rejected");
    require(engine.lastProcessResult() == ImmersiveProcessResult::InvalidInput, "invalid input result");
    std::printf("bypass_and_validation PASS\n");
}

static void testAllPresets() {
    const RoomSimulationPreset presets[] = {
        RoomSimulationPreset::Off, RoomSimulationPreset::SmallRoom,
        RoomSimulationPreset::Studio, RoomSimulationPreset::ConcertHall,
        RoomSimulationPreset::Cathedral, RoomSimulationPreset::Subway,
        RoomSimulationPreset::ClosedCar,
    };
    for (const RoomSimulationPreset preset : presets) {
        const Metrics metrics = render(Controls{0.0f, 1.2f, 1.0f, 0.5f}, preset);
        require(metrics.nonFinite == 0, "preset produced NaN/Inf");
        require(metrics.peak <= 1.00001f, "preset exceeded clipping-safe peak");
    }
    std::printf("all_room_presets PASS\n");
}

int main() {
    testBypass();
    testAllPresets();

    const Metrics front = render(Controls{-1.0f, 1.2f, 1.0f, 0.2f});
    const Metrics center = render(Controls{0.0f, 1.2f, 1.0f, 0.2f});
    const Metrics rear = render(Controls{1.0f, 1.2f, 1.0f, 0.2f});
    const Metrics shortReverb = render(Controls{0.0f, 0.2f, 1.0f, 0.2f});
    const Metrics longReverb = render(Controls{0.0f, 8.0f, 1.0f, 0.2f});
    const Metrics lowReflection = render(Controls{0.0f, 1.2f, 0.0f, 0.2f});
    const Metrics highReflection = render(Controls{0.0f, 1.2f, 1.0f, 0.2f});
    const Metrics lowDampening = render(Controls{0.0f, 1.2f, 1.0f, 0.0f});
    const Metrics highDampening = render(Controls{0.0f, 1.2f, 1.0f, 1.0f});

    printMetrics("front", front); printMetrics("center", center); printMetrics("rear", rear);
    printMetrics("short_reverb", shortReverb); printMetrics("long_reverb", longReverb);
    printMetrics("low_reflection", lowReflection); printMetrics("high_reflection", highReflection);
    printMetrics("low_dampening", lowDampening); printMetrics("high_dampening", highDampening);

    for (const Metrics& metrics : {front, center, rear, shortReverb, longReverb,
                                   lowReflection, highReflection, lowDampening, highDampening}) {
        require(metrics.nonFinite == 0, "closed-car control produced NaN/Inf");
        require(metrics.peak <= 1.00001f, "closed-car control exceeded clipping-safe peak");
    }

    const double faderFrontCenter = std::fabs(front.delayedEnergy - center.delayedEnergy);
    const double faderCenterRear = std::fabs(center.delayedEnergy - rear.delayedEnergy);
    const double reverbDelta = std::fabs(longReverb.delayedEnergy - shortReverb.delayedEnergy);
    const double reflectionDelta = std::fabs(highReflection.delayedEnergy - lowReflection.delayedEnergy);
    const double dampeningDelta = std::fabs(highDampening.delayedEnergy - lowDampening.delayedEnergy);
    std::printf("deltas fader_front_center=%.9f fader_center_rear=%.9f reverb=%.9f reflection=%.9f dampening=%.9f\n",
                faderFrontCenter, faderCenterRear, reverbDelta, reflectionDelta, dampeningDelta);
    require(faderFrontCenter > 1.0e-8, "front/center fader produced no delta");
    require(faderCenterRear > 1.0e-8, "center/rear fader produced no delta");
    require(reverbDelta > 1.0e-8, "reverb time produced no delta");
    require(reflectionDelta > 1.0e-8, "reflection produced no delta");
    require(dampeningDelta > 1.0e-8, "dampening produced no delta");
    std::printf("closed_car_controls PASS\n");
    return 0;
}
