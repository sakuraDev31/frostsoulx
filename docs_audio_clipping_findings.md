# Audio clipping investigation

## Findings

Android's official LoudnessEnhancer documentation states that target gain is the maximum gain applied to the signal and that signals amplified outside the platform sample range are compressed. Therefore, LoudnessEnhancer is not a substitute for pre-gain headroom when EQ, bass boost, or other effects raise peaks.

A/B testing showed that clipping disappeared when the legacy native processing path was detached from Media3. That path and its player controls have now been removed. Playback currently uses the stable Media3 audio path together with the separately managed Android audio-effects path in `MusicService`.

The remaining clipping risk is cumulative gain: positive EQ bands, bass enhancement, virtualizer gain, or loudness enhancement can raise peaks. Any future replacement engine must be introduced independently, with explicit headroom and output-range tests before it is connected to playback.

## Current safeguards

1. Keep the Media3 playback path free of the removed native processor.
2. Apply automatic EQ headroom when EQ is enabled, and prevent positive output gain from exceeding the safety ceiling.
3. Verify the audio-session rebind path reapplies effect enabled states and gain parameters after player or session changes.
4. Treat any future audio engine as an isolated component until clipping and bypass behavior are verified with deterministic PCM tests.

## Sources

- Android LoudnessEnhancer API: https://developer.android.com/reference/android/media/audiofx/LoudnessEnhancer
- AOSP LoudnessEnhancer source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/audiofx/LoudnessEnhancer.java
- AndroidX Media3 AudioProcessor API: https://developer.android.com/reference/androidx/media3/common/audio/AudioProcessor
