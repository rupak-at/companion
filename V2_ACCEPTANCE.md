# V2 Acceptance and Release QA

Use this document to record release evidence. Repository checks are complete; unchecked
items require physical hardware or release credentials and must not be inferred from a
successful debug build.

## Automated repository gates

- [x] Premium mascot and emoji renderer paths compile.
- [x] Animation priority and drag deferral are unit-tested.
- [x] Battery hysteresis and critical/full precedence are unit-tested.
- [x] Rule conflicts and automatic-message suppression are unit-tested.
- [x] Event queue bounds, deduplication, expiry, priority, clearing, and cooldowns are tested.
- [x] Cross-midnight scheduling is unit-tested.
- [x] Message non-repetition, length, and Quiet frequency are unit-tested.
- [x] Small-drag and tap-window gesture behavior is unit-tested.
- [x] V1 settings migration defaults and preserved values are unit-tested.
- [x] Android lint completes with zero errors.
- [x] Debug APK assembly succeeds.

## Device record

For each run record: date, tester, device model, Android version, OEM skin, build commit,
result, battery-optimization state, and issue link if applicable.

## Visual and gestures

- [ ] Premium mascot is clear at every supported size in portrait and landscape.
- [ ] Emoji fallback works with target OEM fonts.
- [ ] Idle, blink/look, tap, double tap, drag, landing, sleep, and transitions are smooth.
- [ ] Triple-tap screenshot, long press, slow double tap, and small drags do not conflict.
- [ ] Reduced motion removes decorative movement.
- [ ] Bright, dark, and busy wallpapers remain readable.
- [ ] Large display/font scaling, cutouts, rounded corners, and both navigation modes pass.

## Battery and charging

- [ ] Battery percentage matches the system.
- [ ] Low triggers once at 20% and remains low through 24%.
- [ ] Low clears at 25%; critical triggers at 10%.
- [ ] Charging start/stop and full charge trigger once per transition.
- [ ] Normal, Battery Saver, and Minimal show the expected renderer/activity.
- [ ] Android power saver temporarily reduces work without changing the saved mode.

## Network and audio

- [ ] Offline/restored reactions occur only when enabled.
- [ ] Debounce and cooldown prevent network-flap spam.
- [ ] Weather cache/time-only behavior works offline.
- [ ] Wired, USB, Bluetooth Classic, and BLE output are tested where supported.
- [ ] Bluetooth grant and denial affect only Bluetooth awareness.
- [ ] No media/audio content is inspected.

## Schedule and customization

- [ ] Quiet hours work across midnight and suppress automatic bubbles.
- [ ] Direct taps remain available during quiet hours.
- [ ] Active-hours sleep, peek, and hide modes restore at the correct boundary.
- [ ] Weekend-day and timezone changes resolve correctly.
- [ ] Every personality, message pack, theme, and accessory is visually reviewed.
- [ ] Every configurable quick action is exercised.
- [ ] All four temporary-hide choices restore correctly.

## Reliability and migration

- [ ] Upgrade a real V1 install and verify preserved overlay, position, size, opacity,
  weather, messages, motion, screenshot, Quick Settings, and assistive behavior.
- [ ] Verify process death, activity closure, rotation, screen off/on, and recreation.
- [ ] Revoke location, Bluetooth, notification, and accessibility permissions individually.
- [ ] Test opt-in restoration after reboot.
- [ ] Test Android 9, current Android, and a restrictive OEM build.
- [ ] Complete 24-hour battery measurements and a multi-day crash-free soak.

## Release

- [x] Final `./gradlew test lint assembleDebug` passed on 2026-08-25; debug APK produced.
- [ ] Review privacy and permission copy on-device.
- [ ] Configure approved release signing outside Git.
- [ ] Produce and smoke-test a signed release APK/AAB.
- [ ] Capture distribution screenshots and store copy if distribution is planned.
