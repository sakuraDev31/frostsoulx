#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstddef>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

#include "frostsoulx/StereoSurroundProcessor.h"

namespace {
constexpr int kFrames = 8192;

std::vector<float> makeSignal() {
    std::vector<float> signal(static_cast<std::size_t>(kFrames) * 2U);
    for (int frame = 0; frame < kFrames; ++frame) {
        const float t = static_cast<float>(frame) / 48000.0f;
        const float left = 0.38f * std::sin(2.0f * 3.14159265f * 271.0f * t) +
                           0.12f * std::sin(2.0f * 3.14159265f * 1900.0f * t);
        const float right = 0.14f * std::sin(2.0f * 3.14159265f * 271.0f * t + 0.47f) +
                            0.08f * std::sin(2.0f * 3.14159265f * 3200.0f * t);
        signal[static_cast<std::size_t>(frame) * 2U] = left;
        signal[static_cast<std::size_t>(frame) * 2U + 1U] = right;
    }
    return signal;
}

struct Metrics {
    float maxDifference = 0.0f;
    float changedPercent = 0.0f;
    float peak = 0.0f;
    long long nanCount = 0;
    long long infCount = 0;
};

Metrics run(frostsoulx::StereoSurroundProcessor::Parameters parameters, float intensity) {
    const auto input = makeSignal();
    auto output = input;
    frostsoulx::StereoSurroundProcessor processor;
    processor.prepare(48000.0, 2, kFrames);
    processor.setParameters(parameters);
    processor.setIntensity(intensity);
    processor.setEnabled(true);
    processor.process(output.data(), kFrames);

    Metrics metrics;
    long long changed = 0;
    for (std::size_t index = 0; index < output.size(); ++index) {
        const float value = output[index];
        if (std::isnan(value)) ++metrics.nanCount;
        if (std::isinf(value)) ++metrics.infCount;
        metrics.peak = std::max(metrics.peak, std::fabs(value));
        metrics.maxDifference = std::max(metrics.maxDifference, std::fabs(value - input[index]));
        if (std::fabs(value - input[index]) > 1.0e-7f) ++changed;
    }
    metrics.changedPercent = 100.0f * static_cast<float>(changed) / static_cast<float>(output.size());
    return metrics;
}

void require(bool condition, const std::string& message) {
    if (!condition) {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(1);
    }
}
}

int main() {
    const auto defaults = frostsoulx::StereoSurroundProcessor::Parameters{};
    auto invalid = defaults;
    invalid.lowFrequencyCutoffHz = std::numeric_limits<float>::quiet_NaN();
    invalid.sideExtractionGain = std::numeric_limits<float>::infinity();
    invalid.decorrelationAInputCoefficient = -std::numeric_limits<float>::infinity();
    const auto invalidMetrics = run(invalid, 1.0f);
    require(invalidMetrics.nanCount == 0 && invalidMetrics.infCount == 0, "invalid parameters produce finite output");
    require(invalidMetrics.peak < 1.0f, "invalid parameters remain within safe peak");
    const auto zero = run(defaults, 0.0f);
    require(zero.maxDifference == 0.0f, "intensity 0 is transparent");
    require(zero.changedPercent == 0.0f, "intensity 0 changes no samples");

    const float intensities[] = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};
    float previousDifference = 0.0f;
    for (float intensity : intensities) {
        const auto metrics = run(defaults, intensity);
        require(metrics.nanCount == 0 && metrics.infCount == 0, "no NaN/Inf at intensity " + std::to_string(intensity));
        require(metrics.peak < 1.0f, "safe peak at intensity " + std::to_string(intensity));
        if (intensity > 0.0f) {
            require(metrics.maxDifference > 0.0f, "positive measurable difference at intensity " + std::to_string(intensity));
            require(metrics.maxDifference + 1.0e-6f >= previousDifference, "difference is generally non-decreasing with intensity");
            previousDifference = metrics.maxDifference;
        }
        std::cout << "intensity=" << intensity << " maxDifference=" << metrics.maxDifference
                  << " changedPercent=" << metrics.changedPercent << " peak=" << metrics.peak << '\n';
    }

    const auto compareParameter = [&](const char* name, auto mutate) {
        auto changed = defaults;
        mutate(changed);
        const auto baseline = run(defaults, 0.85f);
        const auto variant = run(changed, 0.85f);
        require(variant.maxDifference != baseline.maxDifference || variant.changedPercent != baseline.changedPercent,
                std::string("parameter reaches processor: ") + name);
    };
    compareParameter("low cutoff", [](auto& p) { p.lowFrequencyCutoffHz = 1000.0f; });
    compareParameter("side extraction gain", [](auto& p) { p.sideExtractionGain = 0.85f; });
    compareParameter("delay A", [](auto& p) { p.delayASamples = 121; });
    compareParameter("delay B", [](auto& p) { p.delayBSamples = 143; });
    compareParameter("decorrelation A", [](auto& p) { p.decorrelationAInputCoefficient = 0.75f; });
    compareParameter("decorrelation B", [](auto& p) { p.decorrelationBInputCoefficient = 0.75f; });
    compareParameter("side high base", [](auto& p) { p.sideHighMixBase = 0.8f; });
    compareParameter("side high span", [](auto& p) { p.sideHighMixIntensitySpan = 0.9f; });
    compareParameter("ambience A weight", [](auto& p) { p.ambienceDecorrelatedAWeight = 0.9f; });
    compareParameter("rear ambience weight", [](auto& p) { p.rearAmbienceWeight = 0.9f; });
    compareParameter("maximum rear contribution", [](auto& p) { p.maxRearContribution = 0.4f; });

    std::cout << "PASS: V1 Stereo Surround tuning and diagnostics signal checks\n";
    return 0;
}
