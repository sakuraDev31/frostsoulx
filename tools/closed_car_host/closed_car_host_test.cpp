#include "frostsoulx/ImmersiveAudioEngine.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <vector>

using frostsoulx::ImmersiveAudioEngine;
using frostsoulx::RoomSimulationPreset;

struct Metrics {
    double energy = 0.0;
    double difference = 0.0;
    double delayedEnergy = 0.0;
};

static Metrics render(float fader, float reverbSeconds, float reflection, float dampening) {
    constexpr int sampleRate = 48000;
    constexpr int blockFrames = 384;
    constexpr int blockCount = 260;

    ImmersiveAudioEngine engine;
    if (!engine.prepare(sampleRate, blockFrames)) {
        std::fprintf(stderr, "engine.prepare failed\n");
        std::exit(2);
    }
    engine.setRoomSimulationPreset(RoomSimulationPreset::ClosedCar);
    engine.setEnabled(true);
    engine.setSpatialBlend(1.0f);
    engine.setRoomMix(1.0f);
    engine.setReflectionAmount(reflection);
    engine.setReverbTimeSeconds(reverbSeconds);
    engine.setDampening(dampening);
    engine.setRoomSize(0.5f);
    engine.setStereoWidth(0.5f);
    engine.setCarFader(fader);

    Metrics metrics;
    std::vector<float> buffer(static_cast<std::size_t>(blockFrames) * 2U);
    for (int block = 0; block < blockCount; ++block) {
        for (int frame = 0; frame < blockFrames; ++frame) {
            const int sample = block * blockFrames + frame;
            const float time = static_cast<float>(sample) / static_cast<float>(sampleRate);
            // Asymmetric material: a left impulse plus independent L/R tones.
            buffer[static_cast<std::size_t>(frame) * 2U] =
                (sample == 0 ? 0.95f : 0.0f) +
                0.22f * std::sin(2.0f * 3.14159265f * 440.0f * time);
            buffer[static_cast<std::size_t>(frame) * 2U + 1U] =
                0.11f * std::sin(2.0f * 3.14159265f * 880.0f * time + 0.7f);
        }

        if (!engine.process(buffer.data(), blockFrames)) {
            std::fprintf(stderr, "engine.process failed at block %d, result=%d\n",
                         block, static_cast<int>(engine.lastProcessResult()));
            std::exit(3);
        }

        for (int frame = 0; frame < blockFrames; ++frame) {
            const int sample = block * blockFrames + frame;
            const float time = static_cast<float>(sample) / static_cast<float>(sampleRate);
            const float dryLeft = (sample == 0 ? 0.95f : 0.0f) +
                0.22f * std::sin(2.0f * 3.14159265f * 440.0f * time);
            const float dryRight =
                0.11f * std::sin(2.0f * 3.14159265f * 880.0f * time + 0.7f);
            const float outputLeft = buffer[static_cast<std::size_t>(frame) * 2U];
            const float outputRight = buffer[static_cast<std::size_t>(frame) * 2U + 1U];
            metrics.energy += static_cast<double>(outputLeft * outputLeft + outputRight * outputRight);
            metrics.difference += static_cast<double>(
                std::fabs(outputLeft - dryLeft) + std::fabs(outputRight - dryRight));
            if (sample >= 2400) {
                metrics.delayedEnergy += static_cast<double>(outputLeft * outputLeft + outputRight * outputRight);
            }
        }
    }
    return metrics;
}

static void printMetrics(const char* name, const Metrics& metrics) {
    std::printf("%s energy=%.9f diff=%.9f delayed=%.9f\n",
                name, metrics.energy, metrics.difference, metrics.delayedEnergy);
}

int main() {
    const Metrics front = render(-1.0f, 1.2f, 1.0f, 0.2f);
    const Metrics center = render(0.0f, 1.2f, 1.0f, 0.2f);
    const Metrics rear = render(1.0f, 1.2f, 1.0f, 0.2f);
    const Metrics shortReverb = render(0.0f, 0.2f, 1.0f, 0.2f);
    const Metrics longReverb = render(0.0f, 8.0f, 1.0f, 0.2f);
    const Metrics lowReflection = render(0.0f, 1.2f, 0.0f, 0.2f);
    const Metrics highReflection = render(0.0f, 1.2f, 1.0f, 0.2f);
    const Metrics lowDampening = render(0.0f, 1.2f, 1.0f, 0.0f);
    const Metrics highDampening = render(0.0f, 1.2f, 1.0f, 1.0f);

    printMetrics("front", front);
    printMetrics("center", center);
    printMetrics("rear", rear);
    printMetrics("short_reverb", shortReverb);
    printMetrics("long_reverb", longReverb);
    printMetrics("low_reflection", lowReflection);
    printMetrics("high_reflection", highReflection);
    printMetrics("low_dampening", lowDampening);
    printMetrics("high_dampening", highDampening);

    const double faderFrontCenter = std::fabs(front.delayedEnergy - center.delayedEnergy);
    const double faderCenterRear = std::fabs(center.delayedEnergy - rear.delayedEnergy);
    const double reverbDelta = std::fabs(longReverb.delayedEnergy - shortReverb.delayedEnergy);
    const double reflectionDelta = std::fabs(highReflection.delayedEnergy - lowReflection.delayedEnergy);
    const double dampeningDelta = std::fabs(highDampening.delayedEnergy - lowDampening.delayedEnergy);
    std::printf("deltas fader_front_center=%.9f fader_center_rear=%.9f reverb=%.9f reflection=%.9f dampening=%.9f\n",
                faderFrontCenter, faderCenterRear, reverbDelta, reflectionDelta, dampeningDelta);

    if (faderFrontCenter <= 1.0e-8 || faderCenterRear <= 1.0e-8 ||
        reverbDelta <= 1.0e-8 || reflectionDelta <= 1.0e-8 || dampeningDelta <= 1.0e-8) {
        std::fprintf(stderr, "one or more controls produced no measurable output delta\n");
        return 4;
    }
    return 0;
}
