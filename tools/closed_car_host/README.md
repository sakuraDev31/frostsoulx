# FrostSoulX Closed-Car Linux Host Test

This target compiles the same `ImmersiveAudioEngine.cpp` used by the Android app against the official Steam Audio Linux x64 SDK. It validates the Closed car room preset with asymmetric stereo material and reports measurable output deltas for the front/rear fader, reverb time, reflection amount, and dampening.

## Build and run

```bash
cmake -S tools/closed_car_host -B /tmp/frostsoulx-closed-car-build \
  -DCMAKE_BUILD_TYPE=Release \
  -DSTEAMAUDIO_SDK_ROOT=/path/to/steamaudio
cmake --build /tmp/frostsoulx-closed-car-build --parallel
ctest --test-dir /tmp/frostsoulx-closed-car-build --output-on-failure
/tmp/frostsoulx-closed-car-build/frostsoulx_closed_car_test
```

`STEAMAUDIO_SDK_ROOT` must contain `include/phonon.h` and `lib/linux-x64/libphonon.so`. The official Steam Audio C API archive can be obtained from the [Steam Audio downloads page](https://valvesoftware.github.io/steam-audio/downloads.html).
