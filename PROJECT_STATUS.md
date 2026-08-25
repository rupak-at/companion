# Ambient Companion — Project Status

Last reviewed: 2026-08-25

This document summarizes what has been completed in the project so far and what still
needs to be done before the Android V1 release. The detailed product requirements remain
in the local `ambient_companion_v1.md` brief, while testable release criteria live in
`V1_ACCEPTANCE.md`.

## Current status

The planned V1 feature set is implemented in the repository. Automated code-level checks
and the acceptance suite cover the core context logic and Android build. The main work
remaining is hands-on testing across real Android devices, battery validation, and final
release preparation.

## Work completed so far

### Project foundation

- [x] Created the native Android project in Kotlin.
- [x] Set Android 9 (API 28) as the minimum supported version.
- [x] Added Jetpack Compose for the application interface.
- [x] Added DataStore, WorkManager, location, networking, and serialization dependencies.
- [x] Kept context and state-selection logic separate from Android UI code.
- [x] Added unit tests for time, weather, temperature, priority, fallback, and message rules.

### Floating companion

- [x] Added the foreground overlay service and persistent notification.
- [x] Added overlay permission onboarding and enable/disable controls.
- [x] Added dragging within safe screen bounds and optional edge snapping.
- [x] Saved normalized companion position and corrected it after screen rotation.
- [x] Added live position reset and restoration after reopening the app.
- [x] Added tap, double-tap, long-press, drag, idle, and state-transition reactions.
- [x] Added small contextual message bubbles with throttling and repeat prevention.
- [x] Added reduced-motion behavior and screen-off animation suspension.
- [x] Added adjustable companion size and inactive opacity.
- [x] Added a static emoji appearance and curated emoji picker.

### Context and weather

- [x] Added morning, day, evening, and night classification.
- [x] Added sunrise and sunset boundary handling.
- [x] Added clear, cloudy, rain, storm, fog, snow, hot, and cold states.
- [x] Added approximate-location weather retrieval through Open-Meteo.
- [x] Added manual Kathmandu and time-only fallbacks.
- [x] Added local weather caching and graceful offline behavior.
- [x] Added hourly refresh logic and low-frequency WorkManager refresh.
- [x] Added refresh broadcasts so the active companion updates without restarting.

### App experience and controls

- [x] Added a five-step onboarding flow with permission explanations.
- [x] Added home, settings, state preview, and hidden developer override screens.
- [x] Added manual refresh and companion position reset controls.
- [x] Added settings for messages, automatic messages, weather, size, and motion.
- [x] Added a Quick Settings tile for showing and hiding the companion.
- [x] Added triple-tap screenshot capture using Android's MediaProjection consent flow.
- [x] Added optional assistive Back, Home, Recents, notifications, Quick Settings, lock,
  and power-dialog actions without reading window content.
- [x] Documented Android security boundaries for protected system surfaces.

### Verification and documentation

- [x] Added automated unit, lint, and debug-build verification commands.
- [x] Added the V1 acceptance matrix in `V1_ACCEPTANCE.md`.
- [x] Documented setup, architecture, privacy, and implemented features in `README.md`.
- [x] Generated a debug APK through the documented build process.

## Work still required

### Physical-device release QA

- [ ] Test on at least one Android 9 device and one current Android device.
- [ ] Test granting, denying, and revoking overlay and approximate-location permissions.
- [ ] Test dragging near cutouts, rounded corners, status bars, and navigation areas.
- [ ] Test rotation, process death, reopening, and saved-position restoration.
- [ ] Check that tap, double-tap, drag, triple-tap, and long-press gestures do not conflict.
- [ ] Check message readability over bright and dark wallpapers.
- [ ] Verify screen lock/unlock behavior and animation pause/resume.
- [ ] Verify cached-weather and time-only fallbacks with networking disabled.
- [ ] Test foreground-service behavior on a restrictive OEM Android build.
- [ ] Observe a full time/weather transition cycle.
- [ ] Measure idle battery use over 24 hours.
- [ ] Verify every size and emoji option on target OEM fonts and displays.
- [ ] Test the public notification and Quick Settings tile on both target Android versions.
- [ ] Test screenshot approval, denial, file output, and protected-content handling.
- [ ] Enable, disable, and exercise every optional assistive action.
- [ ] Complete a crash-free daily-use soak test.

### Final release preparation

- [ ] Fix any defects discovered during device QA and rerun the relevant checks.
- [ ] Run the full verification suite: `./gradlew test lint assembleDebug`.
- [ ] Review privacy-facing text and permission explanations on-device.
- [ ] Prepare release signing and produce a release build when distribution is approved.
- [ ] Capture final screenshots and write store/distribution copy if a public release is planned.

## Recommended next step

Begin the physical-device checklist in `V1_ACCEPTANCE.md`, recording the device model,
Android version, result, and any issue found for each test. Start with overlay permission,
dragging, gesture conflicts, rotation, and process restoration because those validate the
core floating interaction before the longer battery and soak tests.
