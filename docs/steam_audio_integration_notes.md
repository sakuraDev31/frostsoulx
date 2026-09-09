# Steam Audio integration notes

Sources consulted:

1. Official downloads: https://valvesoftware.github.io/steam-audio/downloads.html
2. Official C API build instructions: https://valvesoftware.github.io/steam-audio/doc/capi/build-instructions.html
3. Official getting started: https://valvesoftware.github.io/steam-audio/doc/capi/getting-started.html
4. Official programmer guide: https://valvesoftware.github.io/steam-audio/doc/capi/guide.html
5. Official audio buffers API: https://valvesoftware.github.io/steam-audio/doc/capi/audio-buffers.html
6. Official Apache-2.0 license: https://github.com/ValveSoftware/steam-audio/blob/master/LICENSE.md
7. Official repository: https://github.com/ValveSoftware/steam-audio

Verified facts used for implementation:

- The official downloads page identifies Steam Audio 4.8.1 as the latest release at the time of research.
- The official repository lists Android arm64 support and the C API package contains `include/phonon.h` plus `lib/android-armv8/libphonon.so`.
- The C API uses `IPLContext`, `IPLHRTF`, and `IPLBinauralEffect` objects. A binaural effect accepts mono or stereo input and emits stereo output.
- Steam Audio audio buffers are 32-bit floating point and deinterleaved. Interleaved Media3 PCM therefore needs explicit conversion into preallocated channel buffers before calling `iplBinauralEffectApply`, followed by conversion back to interleaved output.
- The default HRTF is created with `IPLHRTFSettings.type = IPL_HRTFTYPE_DEFAULT`; binaural processing uses `IPLBinauralEffectParams` with a direction, interpolation mode, spatial blend, and HRTF.
- The official build documentation requires Android SDK platform 25 or later, Android NDK, CMake, and platform-specific libraries. The archive was downloaded from the official v4.8.1 release URL and only the include files, Android ARM64 library, and Apache-2.0 license were vendored into the project.
- The Apache-2.0 license requires redistribution of the license and preservation of copyright/attribution notices; modified files must carry notices stating that they were changed.
- The official documentation says Steam Audio effect objects maintain internal state across frames, so one binaural effect instance must remain associated with one continuous processor stream and be reset/recreated on format changes.
