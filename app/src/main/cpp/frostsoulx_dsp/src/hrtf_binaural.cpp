#include "frostsoulx/dsp/hrtf_binaural.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>

namespace frostsoulx::dsp {
namespace {
constexpr float kPi = 3.14159265358979323846f;
constexpr float kTwoPi = 2.0f * kPi;

float sanitize(float value, float fallback) noexcept {
    return std::isfinite(value) ? value : fallback;
}

float angularDifference(float a, float b) noexcept {
    float difference = std::fmod(a - b + 180.0f, 360.0f);
    if (difference < 0.0f) {
        difference += 360.0f;
    }
    return difference - 180.0f;
}

}  // namespace

void HrtfBinauralProcessor::prepare(Format format) noexcept {
    format_ = format;
    if (!std::isfinite(format_.sampleRate) || format_.sampleRate < 8000.0) {
        format_.sampleRate = 48000.0;
    }
    reset();
}

void HrtfBinauralProcessor::reset() noexcept {
    inputHistory_.fill(0.0f);
    historyIndex_ = 0;
    crossfadePosition_ = crossfadeSamples_;
    resetPartitionedState();
}

void HrtfBinauralProcessor::resetPartitionedState() noexcept {
    for (auto& spectrum : inputSpectra_) {
        for (auto& value : spectrum) {
            value = {};
        }
    }
    partitionInput_.fill(0.0f);
    partitionOutputLeft_.fill(0.0f);
    partitionOutputRight_.fill(0.0f);
    targetPartitionOutputLeft_.fill(0.0f);
    targetPartitionOutputRight_.fill(0.0f);
    overlapLeft_.fill(0.0f);
    overlapRight_.fill(0.0f);
    targetOverlapLeft_.fill(0.0f);
    targetOverlapRight_.fill(0.0f);
    partitionInputFrames_ = 0;
    partitionOutputFrames_ = 0;
    inputPartitionIndex_ = 0;
}

void HrtfBinauralProcessor::fft(std::array<Complex, kFftSize>& values, bool inverse) const noexcept {
    for (std::size_t i = 1, j = 0; i < kFftSize; ++i) {
        std::size_t bit = kFftSize >> 1U;
        for (; (j & bit) != 0; bit >>= 1U) {
            j ^= bit;
        }
        j ^= bit;
        if (i < j) {
            std::swap(values[i], values[j]);
        }
    }

    for (std::size_t length = 2; length <= kFftSize; length <<= 1U) {
        const float angle = (inverse ? 2.0f : -2.0f) * kPi / static_cast<float>(length);
        const Complex root{std::cos(angle), std::sin(angle)};
        for (std::size_t start = 0; start < kFftSize; start += length) {
            Complex factor{1.0f, 0.0f};
            const std::size_t half = length >> 1U;
            for (std::size_t i = 0; i < half; ++i) {
                const Complex even = values[start + i];
                const Complex odd{
                    values[start + i + half].real * factor.real - values[start + i + half].imaginary * factor.imaginary,
                    values[start + i + half].real * factor.imaginary + values[start + i + half].imaginary * factor.real,
                };
                values[start + i] = {even.real + odd.real, even.imaginary + odd.imaginary};
                values[start + i + half] = {even.real - odd.real, even.imaginary - odd.imaginary};
                factor = {
                    factor.real * root.real - factor.imaginary * root.imaginary,
                    factor.real * root.imaginary + factor.imaginary * root.real,
                };
            }
        }
    }

    if (inverse) {
        const float scale = 1.0f / static_cast<float>(kFftSize);
        for (auto& value : values) {
            value.real *= scale;
            value.imaginary *= scale;
        }
    }
}

void HrtfBinauralProcessor::preparePartitionedFilters() noexcept {
    usePartitioned_ = taps_ > kPartitionSize * 4U;
    if (!usePartitioned_) {
        return;
    }

    const std::size_t partitions = (taps_ + kPartitionSize - 1U) / kPartitionSize;
    for (std::size_t partition = 0; partition < partitions; ++partition) {
        fftScratch_.fill({});
        const std::size_t offset = partition * kPartitionSize;
        const std::size_t remaining = std::min(kPartitionSize, taps_ - offset);
        for (std::size_t i = 0; i < remaining; ++i) {
            fftScratch_[i].real = leftHrir_[offset + i];
        }
        fft(fftScratch_, false);
        currentLeftPartitions_[partition] = fftScratch_;

        fftScratch_.fill({});
        for (std::size_t i = 0; i < remaining; ++i) {
            fftScratch_[i].real = rightHrir_[offset + i];
        }
        fft(fftScratch_, false);
        currentRightPartitions_[partition] = fftScratch_;

        if (hasTarget_) {
            fftScratch_.fill({});
            for (std::size_t i = 0; i < remaining; ++i) {
                fftScratch_[i].real = targetLeftHrir_[offset + i];
            }
            fft(fftScratch_, false);
            targetLeftPartitions_[partition] = fftScratch_;

            fftScratch_.fill({});
            for (std::size_t i = 0; i < remaining; ++i) {
                fftScratch_[i].real = targetRightHrir_[offset + i];
            }
            fft(fftScratch_, false);
            targetRightPartitions_[partition] = fftScratch_;
        }
    }
}

void HrtfBinauralProcessor::processPartitionedBlock() noexcept {
    const std::size_t partitions = (taps_ + kPartitionSize - 1U) / kPartitionSize;
    const auto render = [&](const auto& leftFilters, const auto& rightFilters, auto& outputLeft, auto& outputRight, auto& overlapLeft, auto& overlapRight) {
        fftScratch_.fill({});
        for (std::size_t i = 0; i < kPartitionSize; ++i) {
            fftScratch_[i].real = partitionInput_[i];
        }
        fft(fftScratch_, false);
        inputSpectra_[inputPartitionIndex_] = fftScratch_;

        std::array<Complex, kFftSize> accumulatedLeft{};
        std::array<Complex, kFftSize> accumulatedRight{};
        for (std::size_t partition = 0; partition < partitions; ++partition) {
            const std::size_t historyIndex = (inputPartitionIndex_ + kMaxPartitions - partition) % kMaxPartitions;
            for (std::size_t bin = 0; bin < kFftSize; ++bin) {
                const Complex input = inputSpectra_[historyIndex][bin];
                const Complex left = leftFilters[partition][bin];
                const Complex right = rightFilters[partition][bin];
                accumulatedLeft[bin].real += input.real * left.real - input.imaginary * left.imaginary;
                accumulatedLeft[bin].imaginary += input.real * left.imaginary + input.imaginary * left.real;
                accumulatedRight[bin].real += input.real * right.real - input.imaginary * right.imaginary;
                accumulatedRight[bin].imaginary += input.real * right.imaginary + input.imaginary * right.real;
            }
        }

        fft(accumulatedLeft, true);
        fft(accumulatedRight, true);
        for (std::size_t i = 0; i < kPartitionSize; ++i) {
            outputLeft[i] = accumulatedLeft[i].real + overlapLeft[i];
            outputRight[i] = accumulatedRight[i].real + overlapRight[i];
            overlapLeft[i] = accumulatedLeft[i + kPartitionSize].real;
            overlapRight[i] = accumulatedRight[i + kPartitionSize].real;
        }
    };

    render(currentLeftPartitions_, currentRightPartitions_, partitionOutputLeft_, partitionOutputRight_, overlapLeft_, overlapRight_);
    if (hasTarget_) {
        render(targetLeftPartitions_, targetRightPartitions_, targetPartitionOutputLeft_, targetPartitionOutputRight_, targetOverlapLeft_, targetOverlapRight_);
    }
}

void HrtfBinauralProcessor::clearResponses() noexcept {
    leftHrir_.fill(0.0f);
    rightHrir_.fill(0.0f);
    targetLeftHrir_.fill(0.0f);
    targetRightHrir_.fill(0.0f);
    taps_ = 0;
    hasTarget_ = false;
}

bool HrtfBinauralProcessor::copyResponse(
    std::array<float, kMaxTaps>& left,
    std::array<float, kMaxTaps>& right,
    ImpulseResponse response) noexcept {
    if (response.left == nullptr || response.right == nullptr || response.taps == 0) {
        return false;
    }

    const std::size_t copiedTaps = std::min(response.taps, kMaxTaps);
    for (std::size_t i = 0; i < copiedTaps; ++i) {
        left[i] = std::isfinite(response.left[i]) ? response.left[i] : 0.0f;
        right[i] = std::isfinite(response.right[i]) ? response.right[i] : 0.0f;
    }
    for (std::size_t i = copiedTaps; i < kMaxTaps; ++i) {
        left[i] = 0.0f;
        right[i] = 0.0f;
    }
    return true;
}

bool HrtfBinauralProcessor::setImpulseResponse(ImpulseResponse response) noexcept {
    if (!copyResponse(leftHrir_, rightHrir_, response)) {
        return false;
    }
    targetLeftHrir_ = leftHrir_;
    targetRightHrir_ = rightHrir_;
    taps_ = std::min(response.taps, kMaxTaps);
    directionCount_ = 0;
    crossfadePosition_ = crossfadeSamples_;
    hasTarget_ = false;
    reset();
    preparePartitionedFilters();
    return true;
}

bool HrtfBinauralProcessor::setDirectionalResponses(
    const DirectionalImpulseResponse* responses,
    std::size_t count) noexcept {
    if (responses == nullptr || count == 0) {
        return false;
    }

    const std::size_t copiedDirections = std::min(count, kMaxDirections);
    std::size_t copied = 0;
    std::size_t commonTaps = 0;
    for (std::size_t i = 0; i < copiedDirections; ++i) {
        if (responses[i].response.left == nullptr || responses[i].response.right == nullptr || responses[i].response.taps == 0) {
            continue;
        }
        StoredDirection& destination = directions_[copied];
        destination.azimuthDegrees = wrapAzimuth(sanitize(responses[i].azimuthDegrees, 0.0f));
        destination.elevationDegrees = clampElevation(sanitize(responses[i].elevationDegrees, 0.0f));
        if (!copyResponse(destination.left, destination.right, responses[i].response)) {
            continue;
        }
        commonTaps = commonTaps == 0 ? std::min(responses[i].response.taps, kMaxTaps)
                                     : std::min(commonTaps, std::min(responses[i].response.taps, kMaxTaps));
        ++copied;
    }

    if (copied == 0) {
        return false;
    }

    directionCount_ = copied;
    taps_ = commonTaps;
    for (std::size_t i = taps_; i < kMaxTaps; ++i) {
        leftHrir_[i] = 0.0f;
        rightHrir_[i] = 0.0f;
        targetLeftHrir_[i] = 0.0f;
        targetRightHrir_[i] = 0.0f;
    }
    const bool directionSet = setDirection(azimuthDegrees_, elevationDegrees_);
    preparePartitionedFilters();
    return directionSet;
}

bool HrtfBinauralProcessor::loadFhrtfAsset(const char* path) noexcept {
    if (path == nullptr) {
        return false;
    }

    std::FILE* file = std::fopen(path, "rb");
    if (file == nullptr) {
        return false;
    }

    char magic[8]{};
    std::uint32_t version = 0;
    float sampleRate = 0.0f;
    std::uint32_t directionCount = 0;
    std::uint32_t taps = 0;
    const bool headerOk = std::fread(magic, sizeof(magic), 1, file) == 1
        && std::fread(&version, sizeof(version), 1, file) == 1
        && std::fread(&sampleRate, sizeof(sampleRate), 1, file) == 1
        && std::fread(&directionCount, sizeof(directionCount), 1, file) == 1
        && std::fread(&taps, sizeof(taps), 1, file) == 1;
    const bool validHeader = headerOk
        && std::memcmp(magic, "FHRIR01", 7) == 0
        && version == 1U
        && std::isfinite(sampleRate)
        && sampleRate >= 8000.0f
        && sampleRate <= 384000.0f
        && directionCount > 0U
        && directionCount <= kMaxDirections
        && taps > 0U
        && taps <= kMaxTaps;
    if (!validHeader) {
        std::fclose(file);
        return false;
    }

    std::array<DirectionalImpulseResponse, kMaxDirections> metadata{};
    std::array<std::array<float, kMaxTaps>, kMaxDirections> left{};
    std::array<std::array<float, kMaxTaps>, kMaxDirections> right{};
    for (std::size_t direction = 0; direction < directionCount; ++direction) {
        float azimuth = 0.0f;
        float elevation = 0.0f;
        if (std::fread(&azimuth, sizeof(azimuth), 1, file) != 1
            || std::fread(&elevation, sizeof(elevation), 1, file) != 1
            || std::fread(left[direction].data(), sizeof(float), taps, file) != taps
            || std::fread(right[direction].data(), sizeof(float), taps, file) != taps) {
            std::fclose(file);
            return false;
        }
        metadata[direction] = {
            azimuth,
            elevation,
            {left[direction].data(), right[direction].data(), taps},
        };
    }
    std::fclose(file);

    const double previousRate = format_.sampleRate;
    format_.sampleRate = static_cast<double>(sampleRate);
    if (!setDirectionalResponses(metadata.data(), directionCount)) {
        format_.sampleRate = previousRate;
        return false;
    }
    return true;
}

bool HrtfBinauralProcessor::setDirection(float azimuthDegrees, float elevationDegrees) noexcept {
    if (directionCount_ == 0) {
        return false;
    }
    const float azimuth = wrapAzimuth(sanitize(azimuthDegrees, 0.0f));
    const float elevation = clampElevation(sanitize(elevationDegrees, 0.0f));
    if (!interpolateDirection(azimuth, elevation)) {
        return false;
    }
    azimuthDegrees_ = azimuth;
    elevationDegrees_ = elevation;
    preparePartitionedFilters();
    return true;
}

void HrtfBinauralProcessor::setCrossfadeSamples(std::size_t samples) noexcept {
    crossfadeSamples_ = std::min(samples, static_cast<std::size_t>(8192));
    if (!hasTarget_) {
        crossfadePosition_ = crossfadeSamples_;
    }
}

void HrtfBinauralProcessor::setRoomGeometry(RoomGeometry geometry) noexcept {
    geometry.roomDimensions.x = std::clamp(sanitize(geometry.roomDimensions.x, 10.0f), 0.1f, 1000.0f);
    geometry.roomDimensions.y = std::clamp(sanitize(geometry.roomDimensions.y, 10.0f), 0.1f, 1000.0f);
    geometry.roomDimensions.z = std::clamp(sanitize(geometry.roomDimensions.z, 3.0f), 0.1f, 1000.0f);
    geometry.referenceDistance = std::clamp(sanitize(geometry.referenceDistance, 1.0f), 0.01f, 1000.0f);
    geometry.maxDistance = std::clamp(sanitize(geometry.maxDistance, 100.0f), geometry.referenceDistance, 10000.0f);
    geometry.directDistanceExponent = std::clamp(sanitize(geometry.directDistanceExponent, 1.0f), 0.0f, 2.0f);
    geometry.reflectionDistanceExponent = std::clamp(sanitize(geometry.reflectionDistanceExponent, 0.5f), 0.0f, 2.0f);
    geometry.reflectionBalance = std::clamp(sanitize(geometry.reflectionBalance, 1.0f), 0.0f, 4.0f);
    roomGeometry_ = geometry;
    sourceDistance_ = roomDistance(roomGeometry_);
}

void HrtfBinauralProcessor::setDistanceAttenuationEnabled(bool enabled) noexcept {
    distanceAttenuationEnabled_ = enabled;
}

float HrtfBinauralProcessor::sourceDistance() const noexcept {
    return sourceDistance_;
}

void HrtfBinauralProcessor::setEnabled(bool enabled) noexcept {
    enabled_ = enabled;
}

bool HrtfBinauralProcessor::enabled() const noexcept {
    return enabled_;
}

std::size_t HrtfBinauralProcessor::tapCount() const noexcept {
    return taps_;
}

std::size_t HrtfBinauralProcessor::directionCount() const noexcept {
    return directionCount_;
}

float HrtfBinauralProcessor::azimuthDegrees() const noexcept {
    return azimuthDegrees_;
}

float HrtfBinauralProcessor::elevationDegrees() const noexcept {
    return elevationDegrees_;
}

float HrtfBinauralProcessor::wrapAzimuth(float degrees) const noexcept {
    float wrapped = std::fmod(degrees + 180.0f, 360.0f);
    if (wrapped < 0.0f) {
        wrapped += 360.0f;
    }
    return wrapped - 180.0f;
}

float HrtfBinauralProcessor::clampElevation(float degrees) const noexcept {
    return std::clamp(degrees, -90.0f, 90.0f);
}

bool HrtfBinauralProcessor::interpolateDirection(float azimuth, float elevation) noexcept {
    std::array<std::size_t, 4> nearest{};
    std::array<float, 4> distances{};
    std::size_t nearestCount = 0;

    for (std::size_t i = 0; i < directionCount_; ++i) {
        const float azimuthDistance = angularDifference(azimuth, directions_[i].azimuthDegrees);
        const float elevationDistance = elevation - directions_[i].elevationDegrees;
        const float distanceSquared = azimuthDistance * azimuthDistance + elevationDistance * elevationDistance;

        std::size_t insertAt = nearestCount;
        while (insertAt > 0 && distances[insertAt - 1U] > distanceSquared) {
            if (insertAt < 4) {
                nearest[insertAt] = nearest[insertAt - 1U];
                distances[insertAt] = distances[insertAt - 1U];
            }
            --insertAt;
        }
        if (insertAt < 4) {
            nearest[insertAt] = i;
            distances[insertAt] = distanceSquared;
            if (nearestCount < 4) {
                ++nearestCount;
            }
        }
    }

    if (nearestCount == 0) {
        return false;
    }

    targetLeftHrir_.fill(0.0f);
    targetRightHrir_.fill(0.0f);
    float weightSum = 0.0f;
    for (std::size_t n = 0; n < nearestCount; ++n) {
        if (distances[n] < 1.0e-6f) {
            targetLeftHrir_ = directions_[nearest[n]].left;
            targetRightHrir_ = directions_[nearest[n]].right;
            weightSum = 1.0f;
            break;
        }
        const float weight = 1.0f / distances[n];
        weightSum += weight;
        for (std::size_t tap = 0; tap < taps_; ++tap) {
            targetLeftHrir_[tap] += directions_[nearest[n]].left[tap] * weight;
            targetRightHrir_[tap] += directions_[nearest[n]].right[tap] * weight;
        }
    }

    if (weightSum > 1.0e-6f && weightSum != 1.0f) {
        for (std::size_t tap = 0; tap < taps_; ++tap) {
            targetLeftHrir_[tap] /= weightSum;
            targetRightHrir_[tap] /= weightSum;
        }
    }

    if (crossfadeSamples_ == 0 || taps_ == 0) {
        leftHrir_ = targetLeftHrir_;
        rightHrir_ = targetRightHrir_;
        crossfadePosition_ = crossfadeSamples_;
        hasTarget_ = false;
    } else {
        crossfadePosition_ = 0;
        hasTarget_ = true;
    }
    return true;
}

float HrtfBinauralProcessor::convolve(const std::array<float, kMaxTaps>& response) const noexcept {
    float output = 0.0f;
    std::size_t history = historyIndex_;
    for (std::size_t tap = 0; tap < taps_; ++tap) {
        output += inputHistory_[history] * response[tap];
        history = history == 0 ? kMaxTaps - 1U : history - 1U;
    }
    return output;
}

void HrtfBinauralProcessor::process(
    const float* monoInput,
    float* interleavedStereoOutput,
    std::size_t frames) noexcept {
    if (monoInput == nullptr || interleavedStereoOutput == nullptr || frames == 0) {
        return;
    }

    for (std::size_t frame = 0; frame < frames; ++frame) {
        const float input = std::isfinite(monoInput[frame]) ? monoInput[frame] : 0.0f;
        inputHistory_[historyIndex_] = input;

        float left = input;
        float right = input;
        if (enabled_ && taps_ > 0) {
            if (usePartitioned_) {
                if (partitionOutputFrames_ > 0) {
                    const std::size_t outputIndex = kPartitionSize - partitionOutputFrames_;
                    left = partitionOutputLeft_[outputIndex];
                    right = partitionOutputRight_[outputIndex];
                    if (hasTarget_ && crossfadeSamples_ > 0) {
                        const float progress = std::clamp(
                            static_cast<float>(crossfadePosition_ + 1U) / static_cast<float>(crossfadeSamples_),
                            0.0f,
                            1.0f);
                        const float fadeIn = std::sin(progress * (kPi * 0.5f));
                        const float fadeOut = std::cos(progress * (kPi * 0.5f));
                        left = left * fadeOut + targetPartitionOutputLeft_[outputIndex] * fadeIn;
                        right = right * fadeOut + targetPartitionOutputRight_[outputIndex] * fadeIn;
                        ++crossfadePosition_;
                        if (crossfadePosition_ >= crossfadeSamples_) {
                            leftHrir_ = targetLeftHrir_;
                            rightHrir_ = targetRightHrir_;
                            currentLeftPartitions_ = targetLeftPartitions_;
                            currentRightPartitions_ = targetRightPartitions_;
                            hasTarget_ = false;
                        }
                    }
                    --partitionOutputFrames_;
                }

                partitionInput_[partitionInputFrames_++] = input;
                if (partitionInputFrames_ == kPartitionSize) {
                    processPartitionedBlock();
                    partitionInputFrames_ = 0;
                    partitionOutputFrames_ = kPartitionSize;
                    inputPartitionIndex_ = (inputPartitionIndex_ + 1U) % kMaxPartitions;
                }
            } else {
                const float currentLeft = convolve(leftHrir_);
                const float currentRight = convolve(rightHrir_);
                if (hasTarget_ && crossfadeSamples_ > 0) {
                    const float targetLeft = convolve(targetLeftHrir_);
                    const float targetRight = convolve(targetRightHrir_);
                    const float progress = std::clamp(
                        static_cast<float>(crossfadePosition_ + 1U) / static_cast<float>(crossfadeSamples_),
                        0.0f,
                        1.0f);
                    const float fadeIn = std::sin(progress * (kPi * 0.5f));
                    const float fadeOut = std::cos(progress * (kPi * 0.5f));
                    left = currentLeft * fadeOut + targetLeft * fadeIn;
                    right = currentRight * fadeOut + targetRight * fadeIn;
                    ++crossfadePosition_;
                    if (crossfadePosition_ >= crossfadeSamples_) {
                        leftHrir_ = targetLeftHrir_;
                        rightHrir_ = targetRightHrir_;
                        hasTarget_ = false;
                    }
                } else {
                    left = currentLeft;
                    right = currentRight;
                }
            }
        }

        if (enabled_ && distanceAttenuationEnabled_) {
            const float directGain = roomDistanceGain(
                sourceDistance_,
                roomGeometry_.referenceDistance,
                roomGeometry_.directDistanceExponent,
                roomGeometry_.maxDistance);
            left *= directGain;
            right *= directGain;
        }
        interleavedStereoOutput[frame * 2U] = left;
        interleavedStereoOutput[frame * 2U + 1U] = right;
        historyIndex_ = (historyIndex_ + 1U) % kMaxTaps;
    }
}

}  // namespace frostsoulx::dsp
