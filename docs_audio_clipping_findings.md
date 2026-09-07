# Audio clipping investigation

## Findings

Android's official LoudnessEnhancer documentation states that target gain is the maximum gain applied to the signal and that signals amplified outside the platform sample range are compressed. Therefore, LoudnessEnhancer is not a substitute for pre-gain headroom when EQ, bass boost, or other effects raise peaks.

The current app has two independent audio paths. The native Media3 `NativeSpatialDspAudioProcessor` bypasses native processing when disabled and returns a slice of the original PCM buffer. The Android audio-effects path in `MusicService` independently creates Equalizer, BassBoost, Virtualizer, and LoudnessEnhancer instances for the player's audio session. Turning off the FrostSoulX native DSP control does not automatically disable these Android effects.

The likely clipping mechanism is cumulative gain: positive EQ bands and/or bass/virtualizer enhancement can raise peaks, while LoudnessEnhancer may still be enabled according to EQ settings. The native DSP path also needs to keep gain below the final limiter ceiling when HRTF and width/reverb are combined.

## Planned implementation safeguards

1. Make the navigation pill solid black in pure-black mode and solid white in light mode, with fixed slim height and stretched width.
2. Ensure the DSP master toggle updates both the persisted UI state and the service-owned processor state immediately.
3. Keep native DSP bypass bit-transparent when off.
4. Apply automatic EQ headroom when EQ is enabled, and avoid allowing positive output gain to bypass the safety ceiling.
5. Add a final native output safety stage after HRTF mixing, and validate the output range in a deterministic native test where possible.
6. Verify the audio session rebind path reapplies effect enabled states and gain parameters after player/session changes.

## Sources

- Android LoudnessEnhancer API: https://developer.android.com/reference/android/media/audiofx/LoudnessEnhancer
- AOSP LoudnessEnhancer source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/audiofx/LoudnessEnhancer.java
- AndroidX Media3 AudioProcessor API: https://developer.android.com/reference/androidx/media3/common/audio/AudioProcessor
