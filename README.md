# Ambient Companion

Ambient Companion is an Android app that places a small, friendly character above the
phone UI. The companion can be dragged and tucked against a screen edge, reacts to taps,
and adapts its mood to the environment, phone state, and rhythm of the day.

V2 makes the companion feel alive without becoming distracting, invasive, or battery
hungry. Version `0.2.0` is local-first and does not use generative AI or cloud accounts.

## V2 experience

- Premium soft-3D animated mascot with an emoji/Minimal fallback
- Battery, charging, full-charge, power-saver, connectivity, and audio-output awareness
- Deterministic context rules with bounded temporary reactions and cooldowns
- Configurable quiet hours, active hours, sleep behavior, and weekend days
- Cheerful, Calm, Playful, and Quiet personalities with five local message packs
- Five themes, contextual accessories, local interaction memory, and reduced motion
- Configurable quick actions and four temporary-hide durations
- Interactive context/rule preview and live hidden rule debugger
- Resource-aware, callback-driven operation with boundary-scheduled time refresh
- V1 settings migration and one-time V2 introduction

Repository implementation and automated verification are complete. Physical-device,
battery, soak, and signed-release validation remain; see
[V2_ACCEPTANCE.md](V2_ACCEPTANCE.md).

## V1 experience

- A lightweight floating companion using Android's overlay APIs
- Free dragging anywhere within safe screen bounds, optional edge snapping, and saved position
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
- `WindowManager` and a user-enabled accessibility overlay for the companion
- DataStore for settings and cached state
- WorkManager for infrequent background refreshes
- Fused Location Provider with approximate location by default
- Retrofit, OkHttp, and Kotlin Serialization for Open-Meteo data

The code is organized around UI-independent context, schedule, message, gesture,
animation, and rule engines so product behavior can be unit-tested without Android UI.

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
- Optional static emoji companion with a curated emoji picker
- Adjustable inactive opacity with automatic fade and touch-to-wake behavior
- Public lock-screen notification with a dedicated Ambient status icon
- Optional Quick Settings tile for showing or hiding the companion from system controls
- Triple-tap screenshot capture through Android's system MediaProjection consent flow
- Optional assistive controls for Back, Home, Recents, notifications, Quick Settings,
  lock screen, and the power dialog from the companion's long-press panel

Triple-tapping the floating companion opens Android's official screen-capture confirmation.
After approval, one screenshot is saved to `Pictures/Ambient`. Android may black out secure,
banking, password, incognito, or DRM-protected content. Capture permission is controlled by
the operating system and is not silently bypassed or retained by the app.

Assistive controls are disabled by default. Enabling them opens Android Accessibility
Settings, where the user must explicitly activate `Ambient assistive controls`. The service
does not retrieve window content and ignores accessibility events; it performs only the
global action the user selects. The floating companion continues to use the normal Android
overlay permission, so enabling assistive controls does not bypass protected lock-screen or
authentication surfaces.

## Android system UI behavior

The companion floats over the launcher and ordinary applications after the user grants
overlay permission. Android intentionally prevents third-party application overlays from
drawing above the secure lock screen, notification shade, permission dialogs, and other
protected system surfaces. Ambient does not misuse Accessibility Services to bypass that
security boundary.

In protected areas, Ambient remains accessible through its public foreground-service
notification and the optional Quick Settings tile. Lock-screen visibility still follows
the notification privacy choices configured by the device owner.

See [V1_ACCEPTANCE.md](V1_ACCEPTANCE.md) for the historical V1 matrix and
[V2_ACCEPTANCE.md](V2_ACCEPTANCE.md) for current release QA.

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
