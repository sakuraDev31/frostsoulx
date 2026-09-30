# Engine provenance

The repository started from `sakuraDev31/Audio-engine-plugin-for-frostsoulx-` branch `genspark_ai_developer` at commit `4621f7d`.

Native fixes ported from FrostSoulX `genspark_ai_developer`:

- `09d4f564b` — bypass nonlinear room processing at zero intensity.
- `34cf239b2` — keep limiter out of normal immersive peaks.

The Android Media3 adapter, JNI bridge, UI, and playback lifecycle remain in FrostSoulX and are intentionally not duplicated here.
