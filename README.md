# Ambient Companion

Ambient Companion is an Android app that places a small, friendly character above the
phone UI. The companion can be dragged and tucked against a screen edge, reacts to taps,
and eventually adapts its mood to the time, weather, temperature, and daylight.

The V1 principle is simple: make the phone feel a little more alive without becoming
distracting, invasive, or battery hungry.

## V1 experience

- A lightweight floating companion using Android's overlay APIs
- Dragging, safe screen bounds, smooth edge snapping, and saved position
- Tap, double-tap, and long-press reactions with short message bubbles
- Morning, day, evening, and night personalities
- Optional approximate-location weather context with offline fallback
- A foreground service while the overlay is visible
- Simple onboarding, controls, settings, state previews, and developer overrides
- Accessible sizing, reduced motion, and optional automatic messages

Accounts, chatbots, voice, social features, progression systems, and multiple mascots are
intentionally outside V1.

## Technology

- Kotlin
- Jetpack Compose for the application UI
- `WindowManager` and `TYPE_APPLICATION_OVERLAY` for the companion
- DataStore for settings and cached state
- WorkManager for infrequent background refreshes
- Fused Location Provider with approximate location by default
- Retrofit, OkHttp, and Kotlin Serialization for Open-Meteo data

The code is organized around a UI-independent context engine so time and weather rules
can be tested without Android framework dependencies.

## V1 implementation status

The complete V1 feature set is implemented:

- Premium five-step onboarding with explained location and overlay permissions
- Draggable foreground overlay with safe clamping, edge snapping, rotation handling,
  normalized position persistence, live reset, and service restoration
- Idle, tap, double-tap, drag, state-transition, and reduced-motion behavior
- Contextual message bubbles with repeat prevention and automatic-message throttling
- Long-press actions for refresh, opening the app, and hiding the companion
- Tested time, sunrise/sunset, weather-code, temperature, and priority rules
- Approximate location or manual Kathmandu fallback
- Open-Meteo current conditions, one-hour refresh, three-hour cache, and offline fallback
- Screen-off animation suspension and WorkManager background refresh
- Home, settings, all-state preview, and hidden developer override screens
- Small, medium, and large companion sizes plus message/weather/motion controls

See [V1_ACCEPTANCE.md](V1_ACCEPTANCE.md) for the verification matrix. The code-level
acceptance suite passes; physical-device and battery checks remain release QA because no
device or emulator is bundled with the repository.

## Development

The project targets Android 9 (API 28) and newer. Once the Android scaffold is present,
open the repository in Android Studio and use its bundled JDK. Keep `local.properties`
local to your machine.

Typical checks:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Repository automation rules live in [AGENTS.md](AGENTS.md). In particular, each coherent
change must be verified and committed separately so it can be reverted safely.

## Privacy

Ambient Companion does not need identity, precise-location history, contacts, messages,
photos, microphone, camera, or app-usage data. If weather context is enabled, current
approximate location is used only to obtain local environmental conditions.
