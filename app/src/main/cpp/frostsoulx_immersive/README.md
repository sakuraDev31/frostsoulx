# FrostSoulX Audio Engine

This repository is the **canonical native audio-engine source** for FrostSoulX. It is based on the Audio Engine Plugin repository and includes the latest native fixes validated in FrostSoulX.

## Current engine

The engine is a C++17, real-time-safe Steam Audio processor with a small public API:

```cpp
#include "frostsoulx/immersive_audio_engine.h"

frostsoulx::ImmersiveAudioEngine engine;
engine.prepare(48000, 384);       // control/lifecycle thread
engine.setSpatialBlend(1.0f);     // control thread
engine.setEnabled(true);          // control thread
engine.process(interleavedStereo, frames); // audio thread
```

`process()` performs no allocation, locking, file I/O, or logging. The app adapter remains responsible for the strict renderer-level OFF bypass, PCM conversion, and Media3 lifecycle.

## Integrated FrostSoulX fixes

This canonical engine includes:

- **Room Off / zero-intensity transparency:** room processing is bypassed when disabled or when effective room mix is zero; the old nonlinear soft clip was removed.
- **Reflection-energy normalization:** reflection taps are normalized to prevent over-unity correlated accumulation.
- **Smoothed limiter attack:** limiter gain reduction ramps instead of snapping sample-to-sample.
- **Limiter headroom correction:** normal HRTF/room peaks remain outside the dynamics stage; the limiter is reserved for exceptional peaks.
- **384-frame Steam Audio quantum:** keeps the processing block near 8 ms at 48 kHz.
- **Validated space-design controls:** room size, dampening, and stereo width remain normalized to `[0, 1]`.

## Processing path

```text
Media3 PCM in FrostSoulX
  → app/JNI adapter
  → this engine
  → Steam Audio HRTF
  → optional room simulation
  → safety limiter for exceptional peaks
  → app/JNI adapter
  → Media3 AudioSink
```

The OFF path must remain independent of this engine. If Steam Audio cannot load or preparation fails, the adapter must fail closed to the original Media3 audio path.

## Repository layout

| Path | Purpose |
| --- | --- |
| `include/frostsoulx/immersive_audio_engine.h` | Stable host-facing API. |
| `src/immersive_audio_engine.cpp` | Steam Audio engine implementation. |
| `tests/test_immersive_audio_engine.cpp` | Host smoke test. |
| `third_party/steamaudio_sdk/` | Vendored Steam Audio headers and platform libraries. |
| `CMakeLists.txt` | Host and Android build contract. |

## Host build

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
ctest --test-dir build --output-on-failure
```

The host build validates the fallback contract when Steam Audio is unavailable. Android builds enable the vendored backend for `arm64-v8a` and `x86_64` through the Android toolchain.

## FrostSoulX integration

FrostSoulX should consume this repository’s engine source or a versioned package and keep only the Android/JNI/Media3 adapter locally. The adapter must preserve the media item, queue, position, play/pause state, playback parameters, shuffle/repeat state, and volume across renderer changes.

Every engine change requires asymmetric stereo test material and measurements at bypass, zero intensity, and full intensity. Do not add allocations, locks, logging, or file I/O to the callback path.
