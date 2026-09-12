# Haze build failure notes

The Haze blur implementation was pushed in commit `2708dd2ae` and arm64 run `34366507441` failed during Gradle configuration, before Kotlin compilation.

Confirmed error from the GitHub Actions log:

`dev.chrisbanes.haze:haze-android:2.0.0-beta03` and `dev.chrisbanes.haze:haze-utils-android:2.0.0-beta03` require compileSdk 37.2 or later, while the app currently uses `compileSdk = 37`.

The current project must not be raised to a newer compile SDK merely for this UI change. The targeted fix is to use a Haze release compatible with the existing compile SDK, preferably `2.0.0-beta02` if its API remains compatible, then rerun arm64 CI.

Authoritative Haze usage/API source:
- https://chrisbanes.github.io/haze/latest/usage/
- https://github.com/chrisbanes/haze

The current Haze API used by the app is source-backed Compose blur: `hazeSource`, `HazeInput.Sources`, `hazeBlur`, `HazeBlurStyle`, `blurRadius`, and `noiseFactor`. The previous Dimezis crash came from a self-referential ancestor `BlurTarget`; Haze avoids that Android view hierarchy relationship.
