# Ambient Companion — Project Status

Last reviewed: 2026-08-25

## Current status

The repository-implementable V2 feature set is complete. Ambient Companion is version
`0.2.0` and combines weather/time context with battery, charging, power saver, network,
audio output, schedules, personality, themes, message packs, and local interaction memory.

Automated unit tests, Android lint, and debug APK assembly pass. V2 cannot be called a
production release until the physical-device, battery, soak, and signed-release gates in
`V2_ACCEPTANCE.md` are completed.

## V2 implementation completed

### Companion and animation

- [x] Added the premium soft-3D mascot and retained emoji fallback mode.
- [x] Added `AnimatedAssetRenderer` and `EmojiRenderer` behind `CompanionRenderer`.
- [x] Added reusable idle, blink, look, tap, surprise, drag, landing, sleep, wake,
  charging, battery, audio, network, weather, weekend, message, and transition behaviors.
- [x] Routed live gestures and events through a priority-aware animation state machine.
- [x] Added reduced-motion, screen-off suspension, and Normal/Battery Saver/Minimal modes.

### Context and rules

- [x] Added callback-driven battery, charging, power-saver, network/Wi-Fi, audio-output,
  and day/week sources without inspecting audio, media, notifications, or screen content.
- [x] Added low-battery hysteresis, charging-cycle transitions, full-charge reactions,
  four-second network debounce, and per-event cooldowns.
- [x] Added a deterministic rule engine for environment, battery, charging, schedules,
  weekend context, and temporary events.
- [x] Added a priority queue capped at three events with deduplication, expiry, critical
  preemption, and persistent-state restoration.
- [x] Added boundary-driven time, sunrise, sunset, quiet-hours, and active-hours refresh.

### Schedules, personality, and customization

- [x] Added configurable cross-midnight quiet hours and active hours.
- [x] Added sleep-in-place, edge-peek, and hide-completely outside-hours behavior.
- [x] Added configurable weekend days.
- [x] Added Cheerful, Calm, Playful, and Quiet personalities.
- [x] Added Default, Minimal, Motivational, Cute, and Funny local message packs.
- [x] Added daily tap memory, long-gap/rapid-tap reactions, non-repetition, copy length
  limits, and personality-aware automatic-message frequency.
- [x] Added Default, Night Glow, Warm Sunset, Cloud, and Mono themes plus contextual
  scarf, umbrella, sleep-cap, headphones, and charging-spark accessories.

### Controls, migration, and reliability

- [x] Added configurable quick actions capped at four.
- [x] Added long-press quiet mode and temporary hide for 15 minutes, one hour, until
  evening, or until tomorrow.
- [x] Added an interactive V2 context/rule preview and live hidden rule debugger.
- [x] Added a V1-to-V2 preference migration and one-time “What’s new in V2” flow.
- [x] Added opt-in boot restoration and preserved position/process/rotation behavior.
- [x] Added optional Android 12+ Bluetooth permission gating; denial disables only
  Bluetooth headphone awareness.
- [x] Added resource-aware weather work intervals and cancellation when weather is off.
- [x] Added the requested launcher artwork and retained its source image in Git.

## Automated verification

- [x] Unit tests cover context selection, migration, schedules, battery hysteresis,
  animation priority, queue behavior, message behavior, rule conflicts, and gestures.
- [x] `./gradlew testDebugUnitTest`
- [x] `./gradlew lintDebug`
- [x] `./gradlew assembleDebug`

## Physical release validation still required

- [ ] Android 9/API 28 and current Android device tests.
- [ ] Restrictive OEM overlay/background behavior test.
- [ ] Bright, dark, and busy wallpaper legibility checks.
- [ ] Wired, USB, and Bluetooth output tests on supported hardware.
- [ ] Charging-cycle, hysteresis, full-charge, and network-flapping tests.
- [ ] Rotation, process death, lock/unlock, reboot, and permission-revocation tests.
- [ ] 24-hour battery measurements for all resource modes.
- [ ] Multi-day crash-free daily-use soak.
- [ ] Release signing and signed APK/AAB production validation.

See `V2_ACCEPTANCE.md` for the device test matrix and evidence fields.
