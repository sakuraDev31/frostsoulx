# FrostSoulX clipping investigation — corrected finding

## User clarification

The reported clipping also occurred while the app equalizer and other visible sound-shaping controls were off. Therefore, an EQ boost is not a sufficient explanation for the defect.

## Trace result

`MusicService` was creating and retaining an Android audio-effect session while playback was ready or buffering, independently of the native DSP master switch. The effect objects were set to `enabled = false`, but the session and vendor effect chain could still remain attached to the player audio session. The neutral native-DSP bypass therefore did not guarantee a fully clean Android output path.

The native spatial processor itself has a bypass path that forwards the input buffer without native processing when disabled. Consequently, the revised fix avoids changing the neutral PCM path and instead removes the app-owned Android effect session whenever the independent equalizer master switch is off.

## Fix in commit `7f2e4e191`

When EQ is disabled, `MusicService` now closes and releases the entire Android audio-effect session. Session creation and reconciliation are also gated by `desiredEqSettings.enabled`. This prevents hidden or vendor-specific Android effect implementations from remaining attached during a supposedly neutral playback path.

When EQ is enabled, the existing effect settings and headroom logic remain available. Native DSP/HRTF behavior is unchanged.

## Validation

`git diff --check` passed. The local Gradle attempt could not configure the Android build because this sandbox does not have an Android SDK configured (`ANDROID_HOME`/`local.properties` missing); this is an environment limitation rather than a Kotlin compiler error.

The pushed arm64 workflow is queued at:

https://github.com/sakuraDev31/frostsoulx/actions/runs/34093220692

SHA: `7f2e4e191b036013c75aad223eb4c9b4fb43ce3d`

## Device test matrix

| Test | EQ master | Native DSP | Expected result |
|---|---:|---:|---|
| Clean bypass | Off | Off | No app-owned Android effect session; no clipping introduced by FrostSoulX |
| EQ path | On | Off | EQ/headroom path active and bounded |
| Native spatial | Off | On | Native DSP/HRTF path active; Android EQ session remains released |
| Combined | On | On | Both paths active with existing headroom and native limiter |

A final device test is still required because OEM audio services may apply post-processing outside the FrostSoulX process.

## References

[1]: https://developer.android.com/reference/android/media/audiofx/AudioEffect Android AudioEffect API reference

[2]: https://developer.android.com/reference/android/media/audiofx/Equalizer Android Equalizer API reference

[3]: https://developer.android.com/reference/androidx/media3/common/audio/AudioProcessor AndroidX Media3 AudioProcessor API reference
