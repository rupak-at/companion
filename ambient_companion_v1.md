# Ambient Companion — V1 Product & Engineering Specification

## 1. Project Summary

**Ambient Companion** is a small Android application that places a cute, premium-looking animated companion on top of the phone UI.

The companion reacts to the user's real-world context, especially:

- Time of day
- Weather
- Temperature
- Approximate location
- Sunrise / sunset state

The companion is not a wallpaper and it does not replace the Android launcher icon.

It behaves more like a lightweight floating character or chat-head that can appear on the home screen and, when allowed, over other apps.

The main experience is intentionally simple:

> A tiny animated companion appears, looks appropriate for the current environment, occasionally shows a short positive message, and reacts when the user taps or drags it.

Example:

```text
7:30 AM + clear weather

      ☀️
    (•ᴗ•)
   *small bounce*

"Have a great day!"
```

At night:

```text
10:45 PM

      🌙
    (-ᴗ-) zZ

"Rest well 🌙"
```

During rain:

```text
      ☂️
    (•ᴗ•)

"Take an umbrella!"
```

---

# 2. Why This Project Exists

Most weather apps are information-heavy.

They show:

- Forecasts
- Charts
- Maps
- Humidity
- Wind
- Pressure
- Hourly weather
- Alerts

Ambient Companion takes the opposite approach.

It answers one simple question:

> Can the phone feel a little more alive based on what is happening around the user?

The goal is not maximum information.

The goal is:

- Delight
- Personality
- Small interactions
- Context awareness
- Premium visual design

---

# 3. Core Product Idea

The application has one primary mascot.

The same mascot changes its:

- Expression
- Accessories
- Lighting
- Animation
- Message
- Mood

depending on context.

The mascot should remain recognizable across every state.

Do not create completely unrelated icons for every weather state.

Instead:

```text
One Mascot
    │
    ├── Morning version
    ├── Sunny version
    ├── Rainy version
    ├── Evening version
    ├── Night version
    ├── Hot version
    └── Cold version
```

This creates a recognizable identity for the application.

---

# 4. V1 Goal

The V1 goal is:

> Build a reliable Android floating companion that automatically changes its appearance based on time and weather and responds to simple user interactions.

A successful V1 should feel polished even if it contains only a small number of states.

---

# 5. V1 Scope

V1 includes:

- Android only
- Floating overlay companion
- Drag anywhere on screen
- Edge snapping
- Tap reaction
- Double-tap reaction
- Small message bubble
- Automatic time-of-day detection
- Approximate location
- Weather retrieval
- Temperature-based behavior
- Sunrise / sunset support
- Animated mascot states
- Manual enable / disable
- Manual companion position reset
- Manual refresh
- Basic settings
- Local caching
- Background context refresh
- Foreground service when overlay is active

---

# 6. Explicitly Out of Scope for V1

Do **not** build these yet:

- AI chatbot
- Voice assistant
- User accounts
- Cloud synchronization
- Social features
- Pet leveling system
- Coins
- Rewards
- Marketplace
- Multiple mascots
- GPS history
- Maps
- Full weather forecast UI
- Calendar integration
- Notifications based on productivity
- Health tracking
- Step counting
- Music integration
- App usage monitoring
- Smart recommendations
- Generative AI messages
- Real-time 3D rendering
- iOS version

These can be future versions.

---

# 7. Target Platform

## Android

Recommended:

- Minimum Android: Android 9+
- Target latest stable Android SDK available during implementation

V1 should prioritize Android because the core interaction depends heavily on Android system APIs such as overlay windows and foreground services.

---

# 8. Recommended Tech Stack

## Primary Language

**Kotlin**

Why:

- Best Android platform integration
- Strong support for services
- Easy access to system APIs
- Good lifecycle support
- Better fit than React Native for an overlay-heavy app

---

## UI

**Jetpack Compose**

Use for:

- Main application UI
- Settings
- Permission onboarding
- Preview screen
- Companion gallery
- Debug screen

---

## Floating Overlay

Use native Android:

- `WindowManager`
- `TYPE_APPLICATION_OVERLAY`
- `SYSTEM_ALERT_WINDOW`

The overlay contains the companion animation and message bubble.

---

## Background Processing

Use:

- Foreground Service for active overlay
- WorkManager for periodic non-urgent refresh tasks

---

## Location

Use:

- Android Fused Location Provider
- Approximate location by default

Precise location should not be required.

---

## Weather API

Recommended for V1:

**Open-Meteo**

Why:

- Easy to use
- No API key required for basic use
- Supports current weather
- Supports temperature
- Supports weather codes
- Supports sunrise / sunset data

---

## Networking

Use:

- Retrofit
- OkHttp
- Kotlin Serialization or Moshi

Recommended:

```text
Retrofit
+
OkHttp
+
Kotlin Serialization
```

---

## Local Storage

Use:

- DataStore Preferences

Store:

- Overlay enabled state
- Last companion position
- Last weather response
- Last weather fetch time
- User settings
- Message preferences

Room is unnecessary for V1.

---

# 9. Suggested Project Structure

```text
app/
│
├── data/
│   ├── location/
│   │   ├── LocationProvider.kt
│   │   └── LocationResult.kt
│   │
│   ├── weather/
│   │   ├── WeatherApi.kt
│   │   ├── WeatherRepository.kt
│   │   ├── WeatherDto.kt
│   │   └── WeatherMapper.kt
│   │
│   └── preferences/
│       └── AppPreferences.kt
│
├── domain/
│   ├── model/
│   │   ├── CompanionContext.kt
│   │   ├── CompanionState.kt
│   │   ├── TimePeriod.kt
│   │   └── WeatherCondition.kt
│   │
│   ├── engine/
│   │   ├── ContextEngine.kt
│   │   ├── TimeClassifier.kt
│   │   ├── WeatherClassifier.kt
│   │   └── MessageSelector.kt
│   │
│   └── repository/
│       └── ContextRepository.kt
│
├── overlay/
│   ├── CompanionOverlayService.kt
│   ├── OverlayController.kt
│   ├── OverlayPositionManager.kt
│   ├── GestureHandler.kt
│   └── CompanionView.kt
│
├── animation/
│   ├── AnimationController.kt
│   ├── CompanionAnimation.kt
│   └── AnimationAssets.kt
│
├── worker/
│   └── ContextRefreshWorker.kt
│
├── ui/
│   ├── onboarding/
│   ├── home/
│   ├── settings/
│   ├── preview/
│   └── debug/
│
└── MainActivity.kt
```

---

# 10. Companion Context Model

Create one central context object.

```kotlin
data class CompanionContext(
    val timePeriod: TimePeriod,
    val weather: WeatherCondition,
    val temperatureCelsius: Double?,
    val isDay: Boolean,
    val sunrise: Long?,
    val sunset: Long?
)
```

---

# 11. Time Period Model

```kotlin
enum class TimePeriod {
    MORNING,
    DAY,
    EVENING,
    NIGHT
}
```

Initial V1 rules:

```text
05:00 - 10:59 → MORNING
11:00 - 16:59 → DAY
17:00 - 20:59 → EVENING
21:00 - 04:59 → NIGHT
```

Later, sunrise and sunset can override these boundaries.

Recommended final logic:

```text
before sunrise          → NIGHT
sunrise → 10:59         → MORNING
11:00 → sunset - 2h     → DAY
sunset - 2h → sunset+1h → EVENING
after sunset+1h         → NIGHT
```

---

# 12. Weather Model

Keep weather categories simple.

```kotlin
enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    RAIN,
    STORM,
    FOG,
    SNOW,
    UNKNOWN
}
```

Do not create a unique state for every API weather code.

Map detailed API codes into these categories.

---

# 13. Temperature Category

```kotlin
enum class TemperatureFeeling {
    VERY_COLD,
    COLD,
    COMFORTABLE,
    WARM,
    HOT
}
```

Suggested starting rules:

```text
< 5°C      → VERY_COLD
5–14°C     → COLD
15–25°C    → COMFORTABLE
26–31°C    → WARM
> 31°C     → HOT
```

These values can later become configurable.

---

# 14. Companion State

The Context Engine transforms real-world context into a visual state.

Example:

```kotlin
enum class CompanionState {
    MORNING_CLEAR,
    MORNING_CLOUDY,
    MORNING_RAIN,

    DAY_CLEAR,
    DAY_CLOUDY,
    DAY_RAIN,
    DAY_HOT,

    EVENING_CLEAR,
    EVENING_CLOUDY,
    EVENING_RAIN,

    NIGHT_CLEAR,
    NIGHT_CLOUDY,
    NIGHT_RAIN,
    NIGHT_SLEEP,

    COLD,
    STORM,
    FOG
}
```

Do not exceed roughly **16–20 states in V1**.

---

# 15. Recommended V1 States

## Time-Based

1. Morning Clear
2. Morning Cloudy
3. Day Clear
4. Day Cloudy
5. Evening Clear
6. Evening Cloudy
7. Night Clear
8. Night Sleep

## Weather-Based

9. Morning Rain
10. Day Rain
11. Evening Rain
12. Night Rain
13. Storm
14. Fog

## Temperature-Based

15. Hot
16. Cold

This is enough for V1.

---

# 16. State Priority

Some conditions should override others.

Recommended priority:

```text
Storm
  ↓
Rain
  ↓
Fog
  ↓
Extreme temperature
  ↓
Time + normal weather
```

Example:

```text
Time: 2 PM
Weather: Thunderstorm
Temperature: 32°C

Result:

STORM

not:

DAY_HOT
```

---

# 17. Context Engine

Pseudo-code:

```kotlin
fun determineState(context: CompanionContext): CompanionState {

    if (context.weather == WeatherCondition.STORM) {
        return CompanionState.STORM
    }

    if (context.weather == WeatherCondition.RAIN) {
        return when (context.timePeriod) {
            MORNING -> CompanionState.MORNING_RAIN
            DAY -> CompanionState.DAY_RAIN
            EVENING -> CompanionState.EVENING_RAIN
            NIGHT -> CompanionState.NIGHT_RAIN
        }
    }

    if (
        context.temperatureCelsius != null &&
        context.temperatureCelsius > 31
    ) {
        return CompanionState.DAY_HOT
    }

    return determineNormalState(context)
}
```

Keep this engine independent from Android UI code.

That will make it easy to test.

---

# 18. V1 Mascot Design Direction

The character should look:

- Cute
- Small
- Premium
- Soft
- Friendly
- Recognizable
- Minimal
- Non-distracting

Recommended visual style:

```text
3D clay
+
soft glossy material
+
rounded shapes
+
subtle glass elements
+
soft shadows
+
high-quality lighting
```

Avoid:

- Hyper-realistic humans
- Complex animals
- Detailed hair
- Very thin shapes
- Busy accessories
- Too many colors

The mascot needs to remain readable at roughly app-icon size.

---

# 19. Mascot Strategy

Use one base mascot.

Example concept:

```text
Small floating rounded orb

Features:

• two eyes
• tiny mouth
• small floating hands
• interchangeable accessories
• soft body deformation
```

Then modify it.

Morning:

```text
yellow glow
tiny sun rays
happy eyes
```

Rain:

```text
umbrella
blue lighting
tiny droplets
```

Night:

```text
purple tint
sleepy eyes
small moon
```

Cold:

```text
scarf
shivering motion
```

Hot:

```text
sweat drop
slow exhausted motion
```

---

# 20. Animation Strategy

For V1, do **not** render actual 3D models in real time.

Create animations in Blender and export them.

Recommended pipeline:

```text
Blender
   ↓
3D mascot
   ↓
Animate
   ↓
Render transparent sequence
   ↓
Convert to efficient mobile format
   ↓
Android
```

Possible runtime formats:

- Animated WebP
- Animated AVIF if support is reliable
- Lottie for vector-like scenes
- Rive if the mascot can be represented cleanly there

For realistic premium 3D, animated WebP is the safest V1 option.

---

# 21. Required Animations

Each state does not need completely unique animation.

Create reusable animations.

Required:

```text
idle
blink
tap_happy
double_tap_surprised
drag
sleep
weather_reaction
message_appear
message_hide
```

Optional:

```text
wave
spin
jump
peek
```

---

# 22. Animation Timing

Idle animation:

```text
3–6 seconds
loop
```

Blink:

```text
random every 4–9 seconds
```

Tap reaction:

```text
0.5–1.2 seconds
```

Message:

```text
show for 2–4 seconds
```

Avoid constant aggressive motion.

The character should feel alive without becoming annoying.

---

# 23. Floating Overlay Behaviour

The companion appears using an Android overlay window.

Expected behavior:

```text
User enables companion
      ↓
Foreground service starts
      ↓
Overlay window created
      ↓
Companion displayed
      ↓
User drags companion
      ↓
Position updated
      ↓
Release
      ↓
Snap to nearest edge
```

---

# 24. Overlay Size

Recommended starting size:

```text
Companion visual:

64dp – 90dp
```

Message bubble:

```text
120dp – 200dp wide
```

The overlay touch area can be slightly larger than the visual.

---

# 25. Drag Behaviour

On touch down:

```text
record starting position
```

On movement:

```text
move overlay
```

On release:

```text
calculate nearest screen edge
animate to edge
save final position
```

Position must survive app restart.

---

# 26. Edge Snapping

Example:

```text
x < screenWidth / 2

→ snap left

else

→ snap right
```

Add smooth animation:

```text
150–250ms
```

---

# 27. Tap Behaviour

Single tap:

```text
play happy reaction
+
show context message
```

Example:

```text
(•ᴗ•)

"Good evening ✨"
```

---

# 28. Double-Tap Behaviour

Double tap:

```text
play surprised reaction
+
show playful message
```

Examples:

```text
"Hey! 😳"
```

```text
"That tickles!"
```

```text
"I'm awake 👀"
```

---

# 29. Long Press

V1 long press can open a small quick menu.

Example:

```text
┌──────────────────┐
│ Hide             │
│ Refresh weather  │
│ Open app         │
└──────────────────┘
```

Do not add more actions yet.

---

# 30. Message Bubble

Message bubble design:

- Small
- Rounded
- Semi-transparent
- Minimal
- Maximum 1–2 lines
- Auto disappears

Example:

```text
       ☀️
     (•ᴗ•)

╭──────────────────╮
│ Good morning! ☀️ │
╰──────────────────╯
```

---

# 31. Message Philosophy

Messages should feel like:

- Companion dialogue
- Short positive nudges
- Context-aware reactions

Not like:

- Weather report
- Chatbot response
- Notification

Avoid:

```text
The current temperature in Kathmandu is 22 degrees Celsius
with a 60 percent chance of rainfall.
```

Prefer:

```text
Rainy today ☔
```

or:

```text
Umbrella time!
```

---

# 32. Message Categories

## Morning

```text
Good morning ☀️
Have a great day!
You've got this ✨
Morning! 👋
```

## Day

```text
Hope your day's going well!
Keep going 💪
Beautiful day ☀️
```

## Evening

```text
Good evening ✨
Slow down a little 🌆
Nice evening, huh?
```

## Night

```text
Rest well 🌙
Still awake? 👀
Sleepy time 😴
Good night ✨
```

## Rain

```text
Umbrella time ☔
Stay dry!
Rainy mood 🌧️
```

## Hot

```text
Stay hydrated 💧
It's hot out there!
Water break?
```

## Cold

```text
Stay warm 🧣
Brrr... ❄️
Cozy weather!
```

---

# 33. Message Selection Rules

Avoid repeating the same message constantly.

Store:

```text
lastMessageId
lastMessageTimestamp
```

Rules:

- Do not repeat the same message twice in a row.
- Do not automatically show a message more than once every 30–60 minutes.
- Tapping the companion can always trigger a message.
- Automatic messages must be optional.

---

# 34. Location Permission Strategy

Ask only for approximate location.

Onboarding explanation:

```text
We use your approximate location only to understand
local weather and sunrise/sunset conditions.
```

Provide manual fallback:

```text
Use approximate location
or
Choose city manually
```

If permission is denied:

- App still works using time
- Weather-dependent states are disabled
- User can manually select a city later

---

# 35. Overlay Permission Onboarding

The overlay permission needs clear explanation.

Example flow:

```text
Screen 1
Meet your companion

↓

Screen 2
Allow weather context

↓

Screen 3
Allow floating companion

↓

Android overlay settings

↓

Return to app

↓

Companion appears
```

Do not request every permission immediately on first launch.

Explain why first.

---

# 36. Foreground Service

When the companion is active:

```text
CompanionOverlayService
```

Responsibilities:

- Keep overlay alive
- Maintain WindowManager view
- Receive context updates
- Change animation
- Handle hide/show
- Restore state after service recreation

The foreground service notification should be minimal.

Example:

```text
Ambient Companion is active
```

Action:

```text
Hide
```

---

# 37. Weather Refresh Strategy

Do not call the weather API continuously.

Recommended:

```text
Normal:

every 30–60 minutes
```

Also refresh when:

- User manually refreshes
- Significant location change occurs
- App starts after stale cache
- Network becomes available after failure

---

# 38. Cache Strategy

Store last successful weather data.

Example:

```kotlin
data class CachedWeather(
    val weather: WeatherCondition,
    val temperature: Double,
    val sunrise: Long,
    val sunset: Long,
    val fetchedAt: Long
)
```

If the network fails:

```text
cache age < 3 hours

→ use cached weather
```

Otherwise:

```text
fallback to time-based companion
```

---

# 39. Offline Behaviour

The companion must still work offline.

Offline context:

```text
time of day
+
cached weather if available
```

If weather is unavailable:

```text
Morning → Morning mascot
Day → Day mascot
Evening → Evening mascot
Night → Night mascot
```

Never show an error popup over other apps.

---

# 40. Context Refresh Flow

```text
Timer / Worker
      ↓
Get approximate location
      ↓
Load weather
      ↓
Read local time
      ↓
Calculate sunrise/sunset
      ↓
Build CompanionContext
      ↓
ContextEngine
      ↓
CompanionState
      ↓
OverlayController
      ↓
Change animation if needed
```

---

# 41. Avoid Unnecessary State Changes

Do not reload the mascot every refresh.

Example:

```text
Previous state:

MORNING_CLEAR

New state:

MORNING_CLEAR
```

Result:

```text
do nothing
```

Only switch animation when the actual state changes.

---

# 42. App Home Screen

The main app should remain very simple.

Suggested layout:

```text
┌────────────────────────────┐
│ Ambient Companion          │
│                            │
│          [Mascot]          │
│                            │
│       Good evening ✨      │
│                            │
│       Cloudy • 21°C        │
│                            │
│  Companion       [ ON ]    │
│                            │
│  Refresh                   │
│  Preview states            │
│  Settings                  │
│                            │
└────────────────────────────┘
```

---

# 43. Preview Screen

A developer/user preview is extremely useful.

Show all states:

```text
Morning Clear
Morning Rain
Day Clear
Day Cloudy
Day Rain
Evening
Night
Storm
Hot
Cold
```

Tapping one temporarily previews the animation.

This will greatly speed up development.

---

# 44. Settings Screen

V1 settings:

```text
Companion
[ON/OFF]

Messages
[ON/OFF]

Automatic messages
[ON/OFF]

Weather context
[ON/OFF]

Location
Automatic

Companion size
Small / Medium / Large

Reset position

Refresh weather

About
```

Do not create 30 settings.

---

# 45. Debug Screen

Add a hidden debug screen during development.

Allow manual overrides:

```text
Time:
Morning / Day / Evening / Night

Weather:
Clear / Cloudy / Rain / Storm / Fog

Temperature:
Custom value

Force State:
Dropdown
```

This will save hours of waiting for real weather/time conditions.

---

# 46. Basic Data Flow Architecture

```text
              ┌─────────────────┐
              │ LocationProvider│
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │ WeatherRepository│
              └────────┬────────┘
                       │
              ┌────────▼─────────┐
              │ ContextRepository│
              └────────┬─────────┘
                       │
              ┌────────▼────────┐
              │  ContextEngine  │
              └────────┬────────┘
                       │
                 CompanionState
                       │
              ┌────────▼────────┐
              │OverlayController│
              └────────┬────────┘
                       │
                       ▼
                 Animated Mascot
```

---

# 47. Error Handling

## Weather API failure

```text
Use cache
```

If no cache:

```text
Use time-only state
```

---

## Location unavailable

```text
Use previous location
```

If none:

```text
Use time-only state
```

---

## Overlay permission revoked

Stop service and show status inside app.

---

## Foreground service killed

Restore safely when the user opens the app again.

Do not aggressively restart endlessly.

---

# 48. Battery Considerations

This application must be lightweight.

Avoid:

- GPS polling
- Continuous network calls
- Real-time 3D rendering
- 60 FPS animation when unnecessary
- Constant background timers
- Frequent wakeups

Prefer:

- Approximate cached location
- 30–60 minute weather refresh
- Low-complexity animation
- Suspend animation when screen is off
- Pause unnecessary work while companion is hidden

---

# 49. Screen-Off Behaviour

When the screen turns off:

```text
pause companion animation
```

Weather refresh can still happen through scheduled background work when needed.

When screen turns on:

```text
resume animation
+
re-evaluate context if stale
```

---

# 50. First-Run User Experience

## Step 1 — Welcome

```text
Meet your tiny ambient companion.
```

Animation preview.

Button:

```text
Get started
```

---

## Step 2 — Context

```text
Your companion changes with time and weather.
```

Button:

```text
Continue
```

---

## Step 3 — Location

Explain approximate location.

Buttons:

```text
Allow approximate location
Choose city manually
```

---

## Step 4 — Floating Permission

Explain:

```text
To stay with you on your home screen and over apps,
your companion needs permission to appear on top.
```

Button:

```text
Allow floating companion
```

---

## Step 5 — Done

Mascot appears.

Message:

```text
Hey! 👋
```

---

# 51. V1 Interaction Matrix

| Action | Behaviour |
|---|---|
| Tap | Happy reaction + message |
| Double tap | Surprised reaction |
| Drag | Move companion |
| Release | Snap to nearest edge |
| Long press | Quick menu |
| Screen off | Pause animation |
| Weather changes | Change state |
| Time period changes | Change state |
| Overlay disabled | Remove companion |

---

# 52. Suggested Companion State Table

| Context | State | Visual |
|---|---|---|
| Morning + clear | MORNING_CLEAR | Warm sun glow |
| Morning + cloudy | MORNING_CLOUDY | Soft cloud |
| Morning + rain | MORNING_RAIN | Umbrella |
| Day + clear | DAY_CLEAR | Bright cheerful |
| Day + cloudy | DAY_CLOUDY | Neutral cloud |
| Day + rain | DAY_RAIN | Rain drops |
| Hot | DAY_HOT | Sweat animation |
| Evening + clear | EVENING_CLEAR | Orange/purple glow |
| Evening + cloudy | EVENING_CLOUDY | Sunset cloud |
| Evening + rain | EVENING_RAIN | Dark rain |
| Night + clear | NIGHT_CLEAR | Moon + stars |
| Night + cloudy | NIGHT_CLOUDY | Dark cloud |
| Night + rain | NIGHT_RAIN | Moon + rain |
| Late night | NIGHT_SLEEP | Sleeping animation |
| Cold | COLD | Scarf/shiver |
| Storm | STORM | Nervous/lightning |
| Fog | FOG | Misty appearance |

---

# 53. State Change Animation

Do not abruptly replace assets.

When state changes:

```text
Old animation
     ↓
fade / scale down
     ↓
150 ms
     ↓
new animation
     ↓
scale / fade in
```

Total:

```text
300–500 ms
```

---

# 54. Companion Position

Store normalized position rather than only pixels.

Example:

```text
xPercent = x / screenWidth
yPercent = y / screenHeight
```

This helps survive:

- Rotation
- Different resolutions
- Display changes

---

# 55. Rotation Handling

V1 recommendation:

When orientation changes:

```text
recalculate overlay bounds
+
restore normalized position
```

Do not let the character appear off-screen.

---

# 56. Safe Screen Boundaries

Respect:

- Status bar
- Navigation area
- Display cutouts
- Rounded corners

Always clamp position within usable screen bounds.

---

# 57. Accessibility

V1 should support:

- Large companion size
- Reduced motion
- Disable automatic messages
- Hide companion quickly

Reduced motion mode:

```text
replace bounce / spin
with
fade / small scale change
```

---

# 58. Privacy Principles

V1 should avoid collecting unnecessary data.

Do not collect:

- Location history
- User identity
- Contacts
- App usage
- Messages
- Photos
- Microphone
- Camera

Location should only be used for current environmental context.

---

# 59. Permissions

Expected permissions may include:

```text
INTERNET
ACCESS_COARSE_LOCATION
FOREGROUND_SERVICE
SYSTEM_ALERT_WINDOW
```

Depending on Android version and implementation, additional foreground-service declarations may be required.

Avoid precise location unless absolutely necessary.

---

# 60. Development Milestones

## Milestone 1 — Static Overlay

Build:

- Android project
- Basic Compose activity
- Overlay permission
- Floating static image
- Drag
- Edge snap

Success:

> A static mascot can float over the home screen and apps.

---

## Milestone 2 — Animation

Add:

- Idle animation
- Tap animation
- Double tap
- Message bubble

Success:

> The mascot feels interactive.

---

## Milestone 3 — Time Context

Add:

- Time classifier
- Morning
- Day
- Evening
- Night

Success:

> Character changes based on local time.

---

## Milestone 4 — Weather Context

Add:

- Approximate location
- Open-Meteo
- Weather mapper
- Cache
- Context engine

Success:

> Character changes automatically with weather.

---

## Milestone 5 — Background Refresh

Add:

- Foreground service
- WorkManager
- State updates
- Screen on/off handling

Success:

> Companion remains reliable without unnecessary battery usage.

---

## Milestone 6 — Polish

Add:

- Premium animations
- Smooth state transitions
- Settings
- Preview mode
- Better messages
- Reduced motion
- Permission UX

Success:

> V1 is ready for actual daily use.

---

# 61. Suggested Development Order

Build in this exact order:

```text
1. New Kotlin Android project

2. Jetpack Compose setup

3. Overlay permission screen

4. Static floating circle

5. Dragging

6. Edge snapping

7. Replace circle with mascot image

8. Animated mascot

9. Tap reaction

10. Message bubble

11. Context models

12. Time classifier

13. State engine

14. Time-based state switching

15. Location provider

16. Weather API

17. Weather mapping

18. Cached weather

19. Weather-driven state switching

20. Foreground service

21. Background refresh

22. Settings

23. Preview/debug mode

24. Final animation polish

25. Battery testing

26. Device testing
```

Do **not** start with the weather API.

First prove that the floating character experience works.

---

# 62. V1 Testing Checklist

## Overlay

- [ ] Overlay appears
- [ ] Overlay disappears correctly
- [ ] Drag works
- [ ] Edge snapping works
- [ ] Position persists
- [ ] Companion stays inside screen bounds
- [ ] Rotation does not break position

## Interaction

- [ ] Single tap works
- [ ] Double tap works
- [ ] Long press works
- [ ] Message auto hides
- [ ] Animations do not conflict

## Context

- [ ] Morning state
- [ ] Day state
- [ ] Evening state
- [ ] Night state
- [ ] Clear weather
- [ ] Cloudy weather
- [ ] Rain
- [ ] Storm
- [ ] Hot
- [ ] Cold

## Network

- [ ] Weather loads online
- [ ] Cached weather works
- [ ] Offline mode works
- [ ] API failure does not crash app

## Permissions

- [ ] Location denied
- [ ] Overlay denied
- [ ] Overlay permission revoked later
- [ ] App still opens without permissions

## Device

- [ ] Screen off/on
- [ ] App backgrounded
- [ ] Service restart
- [ ] Device reboot behavior tested
- [ ] Battery use checked

---

# 63. V1 Acceptance Criteria

V1 is complete when:

1. User installs the application.
2. User completes onboarding.
3. User grants overlay permission.
4. Companion appears.
5. Companion can be dragged.
6. Companion snaps to an edge.
7. Companion plays an idle animation.
8. Companion reacts when tapped.
9. Companion shows small messages.
10. Companion changes between morning/day/evening/night.
11. Approximate location can be used.
12. Weather is fetched.
13. Rain/clear/cloudy/etc. influence the companion.
14. App works when offline using fallback context.
15. Settings can disable the companion.
16. State survives normal app restarts.
17. Battery use is reasonable.
18. No crashes occur during ordinary daily use.

---

# 64. Definition of "Done"

Do not call V1 finished because the architecture is complete.

The project is done when it feels good to use.

The final V1 should have:

```text
✓ reliable overlay
✓ smooth dragging
✓ polished edge snapping
✓ beautiful mascot
✓ smooth animation
✓ useful contextual states
✓ tiny friendly messages
✓ stable background behavior
✓ low battery impact
```

---

# 65. Recommended Initial Asset Set

Create these first:

```text
01_idle_day.webp
02_idle_night.webp
03_morning.webp
04_evening.webp
05_rain.webp
06_cloudy.webp
07_hot.webp
08_cold.webp
09_storm.webp
10_sleep.webp

reaction_tap.webp
reaction_surprised.webp
reaction_drag.webp
```

Reuse animations wherever possible.

Do not produce 30 assets before the engineering prototype works.

---

# 66. Visual Quality Checklist

Every mascot animation should be checked at real phone size.

Ask:

- Can I understand it at 64dp?
- Are the eyes visible?
- Is the silhouette recognizable?
- Does transparency look clean?
- Does it look good over bright wallpapers?
- Does it look good over dark wallpapers?
- Does it feel premium?
- Is motion subtle enough?

---

# 67. Recommended V1 Branding Direction

Working names:

- Ambient
- Moodi
- Lumi
- Mello
- Nimbo
- Orbit
- Bloop
- Momo
- Aura
- Pebble

A short friendly name works best.

---

# 68. Possible V1 Taglines

```text
A tiny friend for your day.
```

```text
A companion that feels the weather.
```

```text
Your day, in a tiny mood.
```

```text
A little life on your screen.
```

---

# 69. Future V2 Ideas

Not part of V1.

Possible V2 context:

- Battery level
- Charging
- Headphones connected
- Wi-Fi state
- Day of week
- Holidays
- Phone idle duration
- Custom schedules
- Multiple companion personalities
- More reactions
- Interaction memory

Example:

```text
Battery < 15%

Mascot:

(╥﹏╥) 🪫

"Feed me 🔌"
```

---

# 70. Future V3 Ideas

Possible later direction:

- Companion progression
- Unlockable accessories
- Multiple mascots
- Custom user-created states
- Seasonal themes
- Smart routines
- Optional AI-generated dialogue
- Cross-device sync
- Community animation packs
- Creator marketplace

But V1 should remain completely independent from these ideas.

---

# 71. Final Product Principle

Whenever deciding whether to add something, ask:

> Does this make the tiny companion feel more alive?

If the answer is no, it probably does not belong in V1.

The strongest version of this project is not a giant assistant.

It is:

> **A beautiful, lightweight, context-aware little character that quietly lives on the user's phone and makes small moments feel nicer.**
