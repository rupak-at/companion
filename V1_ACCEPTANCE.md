# V1 Acceptance and Device QA

## Automated verification

Run:

```bash
./gradlew test lint assembleDebug
```

The suite verifies compilation on API 28+, Android lint, APK packaging, time boundaries,
sunrise/sunset overrides, WMO weather mapping, condition priority, temperature overrides,
fallback behavior, supported-state coverage, and non-repeating message selection.

## Implemented acceptance criteria

- [x] Five-step permission onboarding
- [x] Overlay enable and disable controls
- [x] Foreground service with persistent notification and Hide action
- [x] Draggable companion with smooth edge snapping
- [x] Normalized saved position, live reset, screen clamping, and rotation correction
- [x] Idle, tap, double-tap, drag, and state-transition reactions
- [x] Long-press quick actions
- [x] Timed one-to-two-line contextual message bubble
- [x] Morning, day, evening, and night classification
- [x] Sunrise and sunset boundary support
- [x] Clear, cloud, rain, storm, fog, snow, hot, and cold mapping
- [x] Approximate-location-only weather access
- [x] Manual city fallback (Kathmandu) and time-only fallback
- [x] Open-Meteo integration with no application API key
- [x] One-hour refresh cadence and three-hour cached-weather fallback
- [x] Graceful weather, permission, and location failure paths
- [x] WorkManager refresh and active-overlay update broadcast
- [x] Screen-off animation pause and screen-on context reevaluation
- [x] Settings for messages, automatic messages, weather, size, and reduced motion
- [x] All-state preview and hidden developer override screen
- [x] Companion state survives normal app reopening

## Required physical-device release QA

These checks require at least one Android 9 device and one current Android device. They
cannot be certified by JVM tests or static lint alone.

- [ ] Grant, deny, and later revoke overlay permission
- [ ] Grant and deny approximate location permission
- [ ] Drag near status bar, navigation area, display cutout, and rounded corners
- [ ] Rotate in both directions and verify the companion remains visible
- [ ] Verify single tap, double tap, drag, and long press do not conflict
- [ ] Leave messages visible over bright and dark wallpapers
- [ ] Lock and unlock the device; verify animation pause and resume
- [ ] Disable networking; verify cached then time-only fallback after cache expiry
- [ ] Kill the process and reopen the app; verify safe restoration
- [ ] Exercise foreground-service behavior on a restrictive OEM Android build
- [ ] Observe a full-day weather/time transition cycle
- [ ] Measure idle battery use over 24 hours
- [ ] Confirm animation readability at 68dp, 84dp, and 104dp
- [ ] Complete a crash-free daily-use soak test

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
