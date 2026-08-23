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
- [x] Free positioning anywhere inside screen bounds with optional edge snapping
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
- [x] Static emoji appearance with curated emoji selection
- [x] Configurable 35–100% inactive opacity and automatic idle fade
- [x] Dedicated public lock-screen notification icon
- [x] Optional Android Quick Settings show/hide tile
- [x] Triple-tap MediaProjection screenshot with explicit system consent
- [x] Opt-in, user-triggered accessibility global actions without window-content access
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
- [ ] Confirm selected emoji glyphs render correctly on each target device and OEM font
- [ ] Confirm lock-screen notification visibility under the device owner's privacy settings
- [ ] Add and exercise the Quick Settings tile on Android 9 and a current Android device
- [ ] Triple-tap, approve capture, and confirm the PNG appears in Pictures/Ambient
- [ ] Deny screenshot consent and confirm the overlay continues without interruption
- [ ] Confirm Android blacks out protected content rather than capturing secure screens
- [ ] Enable, disable, and re-enable Ambient assistive controls
- [ ] Exercise every long-press assistive action on supported physical devices
- [ ] Complete a crash-free daily-use soak test

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
