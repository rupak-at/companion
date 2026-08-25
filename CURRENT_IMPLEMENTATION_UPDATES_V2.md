# Ambient Companion — Current Implementation Updates

Last updated: 2026-08-25  
Current version: `0.2.0`  
Minimum Android version: Android 9 (API 28)

## Current status

The repository-level V2 implementation is complete. The app builds a floating Android
companion that reacts to user gestures, time, weather, schedules, and device state while
keeping its behavior local-first and battery-conscious.

Automated unit tests, Android lint, and debug APK assembly have passed. Physical-device,
battery, long-running stability, and signed-release testing are still required before a
production release.

## Implemented features

### Floating companion

- Draggable companion overlay with safe screen-bound clamping.
- Optional edge snapping and saved normalized position.
- Position restoration after rotation and process recreation.
- Small, medium, and large companion sizes.
- Adjustable inactive opacity with touch-to-wake behavior.
- Premium artwork renderer with an emoji/Minimal fallback.
- Selectable companion artwork with dedicated thumbnails.
- Smooth idle, blink, look, tap, surprise, drag, landing, sleep, wake, charging,
  battery, audio, network, weather, weekend, message, and transition behaviors.
- Reduced-motion support and screen-off animation suspension.

### Gestures and interactions

- Tap and double-tap reactions.
- Long-press action panel.
- Drag and edge-placement behavior.
- Rapid-tap and long-gap interaction reactions.
- Temporary hide options for 15 minutes, one hour, until evening, or until tomorrow.
- Long-press quiet mode.
- Configurable quick actions, limited to four.
- Triple-tap screenshot flow using Android's MediaProjection permission dialog.

### Context-aware behavior

- Morning, day, evening, and night context.
- Sunrise and sunset awareness.
- Current weather and temperature context through Open-Meteo.
- Approximate-location weather with a manual Kathmandu fallback.
- Offline weather cache and time-only fallback behavior.
- Battery percentage, low battery, critical battery, charging, and full-charge reactions.
- Android power-saver awareness.
- Network offline/restored reactions with debounce and cooldown controls.
- Wired, USB, Bluetooth Classic, and BLE audio-output awareness where supported.
- Weekend-aware behavior.
- Deterministic priority rules for persistent context and temporary events.
- Bounded event queue with deduplication, expiry, preemption, and state restoration.

### Scheduling and resource use

- Configurable quiet hours, including schedules that cross midnight.
- Configurable active hours.
- Sleep-in-place, edge-peek, and hide-completely outside active hours.
- Configurable weekend days.
- Boundary-based refreshes for time, sunrise, sunset, quiet hours, and active hours.
- Resource modes: Normal, Battery Saver, and Minimal.
- Low-frequency, resource-aware background weather work.
- Weather work cancellation when weather features are disabled.

### Personalization

- Cheerful, Calm, Playful, and Quiet personalities.
- Default, Minimal, Motivational, Cute, and Funny local message packs.
- Default, Night Glow, Warm Sunset, Cloud, and Mono themes.
- Contextual scarf, umbrella, sleep-cap, headphones, and charging-spark accessories.
- Local daily interaction memory.
- Message repeat prevention, length limits, cooldowns, and personality-aware frequency.
- Interactive context/rule preview.
- Hidden live rule debugger and developer overrides.

### App controls and Android integrations

- Compose-based onboarding, home, settings, customization, and preview screens.
- Guided overlay, location, notification, Bluetooth, screenshot, and accessibility setup.
- Foreground companion service with a public status notification.
- Quick Settings tile for showing or hiding the companion.
- Optional boot restoration.
- Optional assistive actions for Back, Home, Recents, notifications, Quick Settings,
  lock screen, and the power dialog.
- V1-to-V2 settings migration.
- One-time "What's new in V2" flow.

### Privacy and safety boundaries

- No account or cloud identity is required.
- No generative AI, microphone, camera, contacts, messages, or app-usage access.
- Approximate location is used only when weather is enabled.
- Audio-output awareness does not inspect media or audio content.
- Accessibility integration does not read window content or accessibility events.
- Android secure surfaces remain protected; the app does not attempt to bypass them.

## Automated verification completed

- Unit tests for context selection and priority rules.
- Unit tests for schedules and cross-midnight behavior.
- Unit tests for battery hysteresis and charging/full-charge precedence.
- Unit tests for event queue limits, deduplication, expiry, and cooldowns.
- Unit tests for animation priority and drag deferral.
- Unit tests for gestures and tap-window behavior.
- Unit tests for message selection and non-repetition.
- Unit tests for V1-to-V2 settings migration.
- Android lint passes with zero errors.
- Debug APK assembly succeeds.

## Still pending before production release

- Physical testing on Android 9, a current Android version, and a restrictive OEM device.
- Overlay, gesture, typography, and wallpaper-legibility checks on real devices.
- Real-device charging, battery hysteresis, network, and audio-output tests.
- Rotation, process-death, lock/unlock, reboot, and permission-revocation testing.
- 24-hour battery measurements for every resource mode.
- Multi-day crash-free soak testing.
- Release signing configuration and signed APK/AAB validation.
- Final privacy copy review and distribution screenshots, if publishing is planned.

## Scope intentionally not implemented

- User accounts or cloud sync.
- Chatbot or generative-AI conversations.
- Voice features.
- Social features.
- Progression systems.

