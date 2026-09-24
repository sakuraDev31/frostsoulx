# FrostSoulX Current Sound-System Audit

**Audit scope.** This audit covers the `genspark_ai_developer` branch at commit `ee2ae9ae89ce3e437adb498a129d118b205fcefd`. No application source was modified during the audit.

## Executive conclusion

FrostSoulX does not have one single sound processor. It currently has three distinct layers:

1. **Media3’s primary playback path**, built by `MusicService.buildLocalPlayer()`.
2. **An optional first-party Stereo Surround processor**, inserted into the Media3 `DefaultAudioSink` processor chain only when the persisted Surround toggle is enabled.
3. **Android framework `AudioEffect` instances**, including Equalizer, BassBoost, Virtualizer, and LoudnessEnhancer, attached to the player’s audio session independently of the Media3 PCM processor chain.

The current Stereo Surround implementation is therefore replaceable at a clearly defined boundary, but removing only its C++ files would not remove the complete sound system. A safe replacement must account for the Kotlin adapter, JNI library, CMake target, MusicService renderer factory and rebuild lifecycle, Surround runtime/settings screens, persisted preferences, and the separate Android AudioEffect layer.

## Runtime signal path

```text
Media item / decoder
    → Media3 ExoPlayer localPlayer
    → DefaultRenderersFactory
    → DefaultAudioSink
    → SilenceSkippingAudioProcessor
    → SonicAudioProcessor
    → optional StereoSurroundAudioProcessor
    → AudioTrack / platform output

Separately, for the player audio session:
    Android AudioEffect session
    → Equalizer / BassBoost / Virtualizer / LoudnessEnhancer
    → platform audio output
```

The secondary crossfade player also uses the same `createRenderersFactory()` and can therefore instantiate the same optional Surround processor. This is important: the replacement boundary must cover both the primary local player and the crossfade player, or the app could contain two inconsistent processing paths.

## Component inventory

| Layer | Current component | Location | Role | Replacement impact |
|---|---|---|---|---|
| Player construction | Media3 ExoPlayer 1.10.1 | `MusicService.kt` | Decoder, renderer, sink, queue and playback state | Preserve unchanged unless the replacement requires a different sink adapter |
| Sink chain | `SilenceSkippingAudioProcessor` | `MusicService.kt` | Existing silence-skipping stage | Preserve unless intentionally replaced; unrelated to custom Surround |
| Sink chain | `SonicAudioProcessor` | `MusicService.kt` | Playback-speed/pitch processing | Preserve; it is part of normal Media3 behavior |
| Optional processor | `StereoSurroundAudioProcessor` | `playback/StereoSurroundAudioProcessor.kt` | Media3 PCM adapter and strict OFF path | Remove or replace as one unit |
| Native DSP | `StereoSurroundProcessor` | `app/src/main/cpp/frostsoulx_surround/` | In-place float stereo pseudo-surround processing | Replacement candidate; no channel-count expansion |
| JNI bridge | `frostsoulx_surround_jni.cpp` | `app/src/main/cpp/` | PCM16/float conversion, scratch buffers, diagnostics, JNI lifecycle | Remove or replace with the new engine adapter |
| Runtime control | `StereoSurroundRuntime` | `StereoSurroundAudioProcessor.kt` | Shared enabled/intensity/tuning state and rebuild callback | Replace with new engine runtime contract |
| Lifecycle integration | `requestSurroundRebuild()` | `MusicService.kt` | Rebuilds player when toggle changes | Preserve the state-preserving rebuild pattern or replace with a safer sink reconfiguration |
| Native build | CMake targets `frostsoulx_stereo_surround` and `frostsoulx_surround_jni` | `app/src/main/cpp/CMakeLists.txt` | Compiles and packages the current native engine | Replace target and JNI wiring |
| Android effects | Equalizer, BassBoost, Virtualizer, LoudnessEnhancer | `MusicService.kt` | Session-level platform effects | Separate integration; do not accidentally remove while replacing Surround |
| Connected plugin | C++17 host-independent DSP library | `/home/ubuntu/Audio-engine-plugin-for-frostsoulx-` | Sound Field, HRTF, early reflections and room geometry | Candidate source, not yet Android-ready |

## Current Stereo Surround lifecycle

At service startup, persisted `StereoSurroundIntensityKey` and `StereoSurroundEnabledKey` are loaded before `localPlayer` is built. `buildLocalPlayer()` calls `createRenderersFactory()`. That factory creates `SilenceSkippingAudioProcessor` and `SonicAudioProcessor`, then conditionally creates and attaches `StereoSurroundAudioProcessor` when the runtime is enabled.

When the user toggles Surround, `StereoSurroundRuntime.setEnabled()` invokes the registered transition handler. `MusicService.requestSurroundRebuild()` serializes the operation through `surroundRebuildMutex`, captures the current queue, media index, position, play/pause state, repeat mode, shuffle state, playback parameters, and volume, creates a replacement local player, restores those values, replaces the cast/player wrapper and media session player, then releases the old player.

This fixes the original lifecycle problem where changing the toggle only changed a runtime boolean while the already-created Media3 sink still had no processor. It also means toggling Surround is a full player/sink rebuild and can interrupt playback or crossfade state even though the queue and position are restored.

## Current bypass semantics

The Kotlin adapter has a strong OFF path. When `enabled` is false, `queueInput()` copies input bytes into a dedicated output buffer and returns without calling JNI. The native processor is not called. The Media3 input buffer is not returned directly; the copied buffer avoids ownership/recycling issues.

When enabled with intensity zero, the native path can still run for diagnostics but the C++ processor returns before changing samples. This is not the same as the strict OFF path: JNI and diagnostics work still occur.

The native processor accepts only stereo PCM16 or PCM float. Unsupported channel counts and encodings return `AudioFormat.NOT_SET`, so Media3 should deactivate this processor for unsupported formats. The JNI bridge caps processing at 8192 frames per call, uses fixed arrays for PCM16 conversion and input snapshots, and clamps PCM16 output to the valid range.

## Main technical risks

### 1. Two independent processing systems

The Android AudioEffect layer is separate from the Media3 processor chain. A user can have Equalizer, BassBoost, Virtualizer, or LoudnessEnhancer active while Stereo Surround is also active. A replacement plan must define whether these remain, are composed in a documented order, or are replaced by one unified engine. Removing only Stereo Surround will not guarantee an acoustically unprocessed path.

### 2. Crossfade duplication

`createSecondaryCrossfadePlayer()` uses the same renderer factory as the primary player. If a replacement processor is inserted through that factory, it will also be used by the crossfade player. That is probably desirable, but the new engine must be safe when two processor instances exist simultaneously and when the secondary instance is released during transitions.

### 3. Full player rebuild on toggle

The current ON/OFF transition recreates the local player and the cast wrapper. Queue and position are restored, but there is an unavoidable interruption risk, and crossfade state is explicitly cancelled before rebuilding. A new engine should preferably support a stable processor instance with atomic parameter changes when possible; if sink reconstruction is required, the current state-transfer contract must be preserved and tested.

### 4. Native lifetime assumptions

`StereoSurroundRuntime` holds a process-global reference to the current processor. `MusicService` calls `detachProcessor()` before rebuilding and `detach()` during service teardown. Any replacement runtime must avoid retaining a released processor and must define ownership clearly when primary and crossfade players coexist.

### 5. JNI processing contract

The current JNI bridge assumes direct `ByteBuffer` access, two-channel interleaved PCM, PCM16 or float encoding, and a maximum block size of 8192 frames. A replacement engine needs an explicit Media3 adapter that either preserves this contract or performs safe format conversion outside the DSP callback. It must not silently treat unsupported formats as stereo.

### 6. Connected plugin licensing

The connected plugin repository’s README states that license terms have not yet been selected. It also states that the runtime HRTF path requires a licensed HRTF/ SOFA dataset decision. The plugin cannot be integrated into a distributable app until its code license and any HRTF asset licenses are resolved and documented.

### 7. Plugin is not Android-integrated yet

The plugin is a host-independent C++17 static library with CMake host tests. It does not currently provide Android JNI bindings, an Android CMake target, a Media3 `AudioProcessor`, AudioTrack integration, ABI packaging, or an app lifecycle adapter. It is a DSP source candidate, not a drop-in replacement.

### 8. Build verification limitation in this sandbox

The app’s local Gradle build cannot configure because the sandbox does not provide `ANDROID_HOME` or a valid `local.properties` SDK path. The connected plugin host test could not be run because `cmake` is not installed in this environment. These are environment limitations, not evidence that either codebase compiles or fails on CI.

## Connected plugin assessment

The plugin is materially broader than the current app processor. It contains a stereo Sound Field processor, an HRTF binaural processor, a wet-only early-reflection processor, room geometry, directional HRIR interpolation, crossfades between filters, and a partitioned FFT path for long HRIRs. Its README explicitly distinguishes Sound Field, HRTF binaural, and platform Spatializer paths, recommending one spatial path at a time.

The plugin’s strongest replacement candidates are:

- **Sound Field** for a stereo enhancement mode that preserves two-channel output.
- **HRTF Binaural** for a headphone-specific mode, provided the app supplies a licensed HRIR/HRTF asset and a robust Android adapter.
- **Early Reflections** only as an optional wet stage after the primary spatial path, not as a replacement for the direct path.

The plugin should not be integrated as three simultaneous processors by default. The current app should select one spatial path—Sound Field, HRTF, or platform Spatializer—then apply any intentionally retained EQ/output stage in a documented order.

## Replacement boundary

A safe replacement should be staged in this order:

1. Freeze the current working branch and preserve the strict OFF path as a rollback reference.
2. Add the candidate engine as a separate native target without deleting the current engine.
3. Build a Media3 `AudioProcessor` adapter around one selected plugin processor, initially Sound Field only.
4. Run the adapter in a feature-flagged path with current player rebuild/state-transfer behavior unchanged.
5. Compare transparent OFF output, PCM format handling, clipping/headroom, process-call counts, and track transitions.
6. Add HRTF only as a separate headphone path after asset licensing and output-latency testing.
7. Remove the old Stereo Surround Kotlin/JNI/C++ system only after the replacement path passes CI and runtime A/B tests.
8. Keep the Android AudioEffect layer separate until a deliberate decision is made about whether the new engine owns EQ, bass, virtualizer, loudness, and headroom.

## Recommended immediate decision

Do **not** delete the current sound system yet. The correct next step is a parallel, feature-flagged adapter spike using the plugin’s Sound Field processor. It should first prove that the plugin can be compiled into the app’s Android CMake/ABI targets and attached to Media3 without changing the current OFF path. Once that is stable, the current Stereo Surround implementation can be removed in a controlled commit.

## References

1. [FrostSoulX `MusicService.kt`](https://github.com/sakuraDev31/frostsoulx/blob/genspark_ai_developer/app/src/main/kotlin/dev/vxs/frostsoulx/playback/MusicService.kt) — player construction, renderer factory, crossfade player, rebuild lifecycle, and Android AudioEffect management.
2. [FrostSoulX `StereoSurroundAudioProcessor.kt`](https://github.com/sakuraDev31/frostsoulx/blob/genspark_ai_developer/app/src/main/kotlin/dev/vxs/frostsoulx/playback/StereoSurroundAudioProcessor.kt) — Media3 adapter, strict OFF path, JNI contract, runtime state, and diagnostics.
3. [FrostSoulX native CMake](https://github.com/sakuraDev31/frostsoulx/blob/genspark_ai_developer/app/src/main/cpp/CMakeLists.txt) — current native targets and linkage.
4. [FrostSoulX native JNI bridge](https://github.com/sakuraDev31/frostsoulx/blob/genspark_ai_developer/app/src/main/cpp/frostsoulx_surround_jni.cpp) — PCM conversion, scratch buffers, diagnostics, and JNI entry points.
5. [FrostSoulX Stereo Surround processor](https://github.com/sakuraDev31/frostsoulx/blob/genspark_ai_developer/app/src/main/cpp/frostsoulx_surround/src/StereoSurroundProcessor.cpp) — current native DSP algorithm and bypass behavior.
6. [Connected FrostSoulX Audio Engine plugin](https://github.com/sakuraDev31/Audio-engine-plugin-for-frostsoulx-) — Sound Field, HRTF, early-reflection components, build contract, and licensing note.
