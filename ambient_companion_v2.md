# Ambient Companion — V2 Product & Engineering Specification

**Version:** V2
**Depends on:** Completed and device-tested V1
**Primary platform:** Android
**Stack:** Kotlin + Jetpack Compose + native Android services
**Core direction:** Make the companion feel more alive by connecting it to both the user's environment and the current state of the phone.

---

## 1. V2 Vision

V1 proved the core product:

> A small floating Android companion can react to time and weather, show short messages, respond to gestures, and provide lightweight assistive actions.

V2 should evolve that into:

> **A premium animated companion that understands the user's day and the phone itself.**

Examples:

```text
Battery: 12%

      🪫
    (╥﹏╥)

"Feed me 🔌"
```

```text
Charging starts

      ⚡
    (•ᴗ•)

"Charging up!"
```

```text
Headphones connected

      🎧
    (⌐■ᴗ■)

"Music time?"
```

```text
Friday evening

      ✨
    \(^ᴗ^)/

"Weekend energy!"
```

V2 is not about adding a huge number of features. It is about making the existing companion feel more expressive, context-aware, customizable, and polished.

---

## 2. V2 Main Goals

V2 has six primary goals:

1. Replace the V1 emoji-first appearance with a premium animated mascot.
2. Add phone-state awareness: battery, charging, connectivity, audio devices, and power saver.
3. Add a reusable context/rule engine instead of hard-coded state combinations.
4. Add quiet hours, active hours, and simple day/week awareness.
5. Add personality modes, message packs, themes, and lightweight local interaction memory.
6. Preserve V1 reliability, privacy, assistive actions, and battery-conscious behavior.

---

## 3. V2 Scope

### Must ship

- Premium animated companion renderer
- Emoji fallback renderer
- Reusable animation state machine
- Battery percentage awareness
- Low/critical/full battery states
- Charging start/stop/full reactions
- System power-saver awareness
- Online/offline awareness
- Wi-Fi state where safely available
- Wired/USB/Bluetooth audio-output awareness where platform permissions allow
- Day-of-week and weekend context
- User-configurable weekend days
- Quiet hours
- Active hours / sleep behavior
- Context rule engine
- Temporary event-reaction queue
- Event cooldowns and deduplication
- Personality modes
- Local message packs
- Lightweight interaction memory
- Theme/accessory system
- Improved long-press quick menu
- Customizable quick actions
- Better preview/debug screen
- V1 → V2 settings migration
- Battery/resource modes
- Release QA and multi-day soak testing

### Nice to have, but not a release blocker

- Bundled holiday/date reactions
- Edge-peek animation
- Start-after-reboot option
- Optional anonymous crash/quality analytics
- More than one premium visual theme

---

## 4. Explicitly Out of Scope for V2

Do **not** add these in V2:

- Generative AI chat
- Voice assistant
- Cloud accounts
- Cross-device sync
- Social features
- Marketplace
- Coins/rewards
- Pet leveling
- Screen-content reading
- Notification-content reading
- Contact access
- Microphone/camera awareness
- Location history
- App-usage surveillance
- User behavior profiling
- Full automation scripting
- iOS version
- User-uploaded 3D models
- Real-time LLM-generated messages

These can be considered later only if they still support the core ambient-companion idea.

---

## 5. Product Positioning

### V1

> A tiny companion that reacts to time and weather.

### V2

> A tiny companion that understands your day and your phone.

That distinction should guide all V2 decisions.

---

# PART I — PREMIUM COMPANION

## 6. Premium Mascot Upgrade

The biggest visible V2 change should be the companion itself.

V1 currently supports a static emoji appearance and curated emoji picker. V2 should add one official premium mascot that looks consistent across all contexts.

### Visual direction

- Soft 3D
- Rounded
- Cute
- Small
- Premium
- Friendly
- Clear silhouette
- Subtle glossy/clay material
- Soft lighting
- Minimal facial features
- Readable at 64–90dp

Avoid overly detailed characters. The companion must still look good when very small.

---

## 7. Mascot Asset Strategy

Recommended V2 pipeline:

```text
Blender
   ↓
Base 3D mascot
   ↓
Rig + animate
   ↓
Transparent render
   ↓
Animated WebP / efficient animation asset
   ↓
Android renderer
```

Do not begin V2 with full real-time 3D rendering.

The user only needs to perceive the companion as a premium moving 3D character. Pre-rendered animation is safer for:

- Battery
- APK complexity
- GPU usage
- Cross-device consistency
- Development time

Rive may be evaluated for some effects, but only if visual quality remains strong.

---

## 8. Required V2 Animations

Create reusable animations instead of unique animations for every context.

Minimum:

```text
idle
blink
look_left
look_right
tap_happy
double_tap_surprised
drag
edge_land
sleep
wake_up
charging
battery_low
battery_full
headphones
network_lost
network_restored
rain
cold
hot
weekend
message_show
message_hide
state_transition
```

Optional:

```text
wave
peek
spin
tiny_jump
stretch
```

---

## 9. Animation State Machine

Do not trigger animations independently from random UI callbacks.

Use a centralized state machine.

Example:

```text
IDLE
 │
 ├── TAP → HAPPY → IDLE
 ├── DOUBLE TAP → SURPRISED → IDLE
 ├── DRAG → DRAGGING → LAND → IDLE
 ├── CHARGING → CHARGING_IDLE
 ├── LOW BATTERY → TIRED_IDLE
 ├── NIGHT → SLEEPY_IDLE
 └── SCREEN OFF → PAUSED
```

### Priority

```text
Critical state
   ↓
Direct user interaction
   ↓
State transition
   ↓
Automatic reaction
   ↓
Idle
```

Example:

If battery becomes low while the user is dragging:

```text
finish drag
→ edge land
→ switch to low-battery visual
```

Never interrupt drag unexpectedly.

---

## 10. Renderer Abstraction

Introduce a renderer abstraction so V2 can support both animated and lightweight modes.

```kotlin
interface CompanionRenderer {
    fun setState(state: CompanionVisualState)
    fun play(animation: AnimationId)
    fun setAccessory(accessory: AccessoryId?)
    fun setOpacity(value: Float)
    fun pause()
    fun resume()
}
```

Implementations:

```text
AnimatedAssetRenderer
EmojiRenderer
```

Keep V1 emoji mode as a compatibility and battery-saving fallback.

---

# PART II — DEVICE CONTEXT

## 11. Device Context Model

Create a dedicated device-state model.

```kotlin
data class DeviceContext(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isBatteryFull: Boolean,
    val isPowerSaveMode: Boolean,
    val isNetworkAvailable: Boolean,
    val isWifiConnected: Boolean?,
    val isHeadphonesConnected: Boolean,
    val audioOutputType: AudioOutputType,
    val dayOfWeek: DayOfWeek,
    val isWeekend: Boolean
)
```

---

## 12. Combined Ambient Context

V2 should combine V1 environment context and V2 device context.

```kotlin
data class AmbientContext(
    val environment: CompanionContext,
    val device: DeviceContext,
    val preferences: CompanionPreferences,
    val recentInteractions: InteractionSummary
)
```

The rule engine consumes this single object.

---

## 13. Battery Awareness

Recommended categories:

```kotlin
enum class BatteryState {
    CRITICAL,
    LOW,
    NORMAL,
    HIGH,
    FULL,
    CHARGING
}
```

Suggested starting thresholds:

```text
0–10%   → CRITICAL
11–20%  → LOW
21–79%  → NORMAL
80–99%  → HIGH
100%    → FULL
```

Charging should be modeled separately so that battery percentage remains available.

### Reactions

Critical:

```text
      🪫
    (×﹏×)

"Need power..."
```

Low:

```text
      🪫
    (╥﹏╥)

"Feed me 🔌"
```

Charging:

```text
      ⚡
    (•ᴗ•)

"Charging up!"
```

Full:

```text
      🔋
    \(^ᴗ^)/

"All full!"
```

---

## 14. Battery Threshold Rules

Do not react every time battery percentage changes.

Example:

```text
21% → 20%
trigger LOW once
```

Then:

```text
20% → 19%
do nothing
```

Use hysteresis:

```text
enter low battery at <= 20%
leave low battery at >= 25%
```

Charging start should trigger once per charging cycle.

Full battery should trigger once when crossing into full state.

---

## 15. Power Saver Awareness

When Android power saver is enabled:

- Reduce decorative animations
- Increase weather refresh interval
- Pause unnecessary automatic reactions
- Keep direct user interaction
- Prefer lower-cost renderer behavior

Optional one-time message:

```text
"Saving energy 🌱"
```

Do not repeatedly show it.

---

## 16. Connectivity Awareness

Use Android connectivity callbacks rather than polling.

Model:

```kotlin
enum class NetworkState {
    ONLINE,
    OFFLINE,
    UNKNOWN
}
```

Possible reactions:

Offline:

```text
      📡
    (•︵•)

"Lost connection?"
```

Restored:

```text
      ✨
    (•ᴗ•)

"Back online!"
```

### Debounce

Network state may flap.

Use:

```text
callback
→ wait 3–5 seconds
→ confirm state
→ emit event
```

Add a cooldown so unstable connections do not spam the user.

Default V2 setting:

```text
Connectivity reactions: OFF
```

Users can enable them.

---

## 17. Headphone / Audio Output Awareness

Use platform audio-device APIs instead of constant Bluetooth scanning.

Detect where supported:

- Wired headphones
- USB audio
- Bluetooth audio output

Recommended APIs:

- `AudioManager`
- `AudioDeviceCallback`

Do not inspect the audio content or currently playing media in V2.

Reaction:

```text
      🎧
    (⌐■ᴗ■)

"Music time?"
```

Play a short temporary reaction, then return to the persistent context state.

---

# PART III — DAY AND SCHEDULE CONTEXT

## 18. Day-of-Week Awareness

Use the local device date.

Examples:

Monday morning:

```text
"New week ✨"
```

Friday evening:

```text
"Weekend energy!"
```

Saturday/Sunday-style behavior should not be globally hard-coded.

---

## 19. Configurable Weekend Days

Allow users to choose their weekend.

Example:

```text
Weekend days

[✓] Friday
[✓] Saturday
[ ] Sunday
```

This keeps the app useful across different countries and work schedules.

---

## 20. Quiet Hours

Add configurable quiet hours.

Example:

```text
10:30 PM → 7:00 AM
```

During quiet hours:

- Companion can remain visible
- Automatic bubbles are disabled
- Playful automatic reactions stop
- Idle animation becomes calmer
- Direct taps still work
- Sleep mode may activate

Must support ranges that cross midnight.

---

## 21. Active Hours

Optional active-hours mode:

```text
7:00 AM → 11:00 PM
```

Outside active hours user can choose:

```text
Sleep in place
Peek from edge
Hide completely
```

Default:

```text
Sleep in place
```

---

## 22. Sleep Mode

Example:

```text
screen edge

       🌙
    (-ᴗ-) zZ
```

Sleep mode should still allow direct interaction unless the user selected full hide.

---

## 23. Optional Holiday Layer

This is not a release blocker.

If included:

- Keep bundled local metadata
- No calendar-account permission
- No cloud account
- User can disable it
- Unsupported regions simply receive no holiday behavior

---

# PART IV — RULE ENGINE

## 24. Why V2 Needs a Rule Engine

V1 can manage a limited number of combinations using direct priority checks.

V2 introduces:

- Weather
- Time
- Battery
- Charging
- Connectivity
- Audio
- Weekend
- Quiet hours
- User interaction

Without a rule engine this can become a large and fragile nested `when` block.

---

## 25. Context Rule Interface

Recommended shape:

```kotlin
interface ContextRule {
    val id: String
    val priority: Int

    fun matches(context: AmbientContext): Boolean

    fun result(context: AmbientContext): CompanionEffect
}
```

---

## 26. Companion Effect

Separate persistent states from temporary reactions.

```kotlin
sealed interface CompanionEffect {

    data class Persistent(
        val behavior: CompanionBehavior
    ) : CompanionEffect

    data class Temporary(
        val behavior: CompanionBehavior,
        val durationMs: Long
    ) : CompanionEffect
}
```

Examples:

Persistent:

```text
Night
Rain
Low battery
```

Temporary:

```text
Headphones connected
Charging started
Network restored
Tap reaction
```

---

## 27. Companion Behavior

```kotlin
data class CompanionBehavior(
    val visualState: CompanionVisualState,
    val idleAnimation: AnimationId,
    val reaction: AnimationId?,
    val messagePool: MessagePoolId?,
    val accessory: AccessoryId?,
    val mood: CompanionMood
)
```

The rule engine decides behavior. The renderer only displays it.

---

## 28. Suggested Rule Priority

```text
100  critical battery / critical system state
90   charging-related state
85   direct event reaction
80   storm
70   rain / snow / fog
60   temperature extreme
50   quiet/sleep schedule
40   headphone or network temporary event
30   weekend / day-of-week mood
20   normal time + weather
10   cosmetic theme
```

These numbers are only starting points. Tests should define the final intended order.

---

## 29. Example Rule Resolution

### Case A

```text
Night
Rain
Battery 8%
```

Persistent result:

```text
CRITICAL_BATTERY
```

Theme may still use night colors.

---

### Case B

```text
Rain
Charging starts
```

Persistent:

```text
RAIN
```

Temporary:

```text
CHARGING_START
```

After reaction completes:

```text
return to RAIN
```

---

### Case C

```text
Quiet hours
Headphones connected
```

Result:

- No automatic message
- Optional subtle visual reaction
- Return to sleep/quiet state

---

## 30. Temporary Event Queue

Events may happen close together.

Example:

```text
charging starts
headphones connect
network returns
```

Do not play all of them endlessly.

Rules:

- Maximum queue size: 3
- Critical events can preempt
- Duplicate events collapse
- Similar low-priority events can be dropped
- Events expire after a short TTL

---

## 31. Event Cooldowns

Starting values:

```text
Network change:
10 minutes

Headphones connected:
30 minutes

Charging start:
once per plug-in cycle

Low battery:
until recovery threshold crossed

Weekend greeting:
once per day

Automatic weather message:
30–60 minutes
```

---

# PART V — PERSONALITY

## 32. Personality Modes

V2 personality is curated behavior, not AI.

Ship four:

```text
Cheerful
Calm
Playful
Quiet
```

---

## 33. Personality Effects

Personality changes:

- Message wording
- Emoji use
- Automatic message frequency
- Idle animation frequency
- Reaction intensity

### Cheerful

```text
"Let's go! ✨"
"You've got this!"
```

### Calm

```text
"Good morning."
"Take it easy."
```

### Playful

```text
"Still awake? 👀"
"Feed me 🔌"
"That tickles!"
```

### Quiet

- Very few automatic messages
- Small/subtle movement
- Minimal bubbles

---

## 34. Lightweight Interaction Memory

Store only enough to make reactions less repetitive.

```kotlin
data class InteractionSummary(
    val tapsToday: Int,
    val lastTapAt: Long?,
    val lastReactionId: String?,
    val lastAutomaticMessageAt: Long?,
    val lastMessageId: String?
)
```

Possible uses:

First tap after a long gap:

```text
"Hey 👋"
```

Rapid tapping:

```text
"Okay okay 😂"
```

Late-night repeated tap:

```text
"Still awake? 👀"
```

Do not build a long-term behavior profile.

---

## 35. Gesture Coordination

V1 already has:

- Tap
- Double tap
- Triple-tap screenshot
- Long press
- Drag

V2 should centralize recognition to avoid conflicts.

Priority:

```text
Drag
→ Long press
→ Triple tap
→ Double tap
→ Single tap
```

Do not have separate components independently deciding gestures.

---

# PART VI — MESSAGES, THEMES, ACCESSORIES

## 36. Message Packs

Ship local curated packs:

```text
Default
Minimal
Motivational
Cute
Funny
```

Example JSON-like structure:

```json
{
  "id": "cute",
  "morning": [
    "Morning sunshine ☀️",
    "Let's have a nice day!"
  ],
  "lowBattery": [
    "Feed me 🔌",
    "Tiny power nap?"
  ]
}
```

No server is required.

---

## 37. Message Rules

Automatic messages should:

- Prefer <= 40 characters
- Use max 2 lines
- Avoid repeating the same message twice
- Respect quiet hours
- Respect global automatic-message setting
- Respect per-context cooldowns
- Never pretend to know private information

---

## 38. Theme System

Built-in V2 themes could include:

```text
Default
Night Glow
Warm Sunset
Cloud
Mono
```

A theme may change:

- Companion lighting
- Bubble appearance
- Small effects
- In-app accents
- Accessory material

Do not build a theme marketplace in V2.

---

## 39. Accessories

Automatic accessories can include:

```text
Scarf
Umbrella
Sleep cap
Headphones
Sunglasses
Charging spark
```

Prefer one primary accessory at a time.

Avoid clutter.

---

## 40. Customization Settings

Suggested screen:

```text
Companion
────────────
Renderer
Personality
Theme
Message pack
Size
Inactive opacity

Behavior
────────────
Automatic messages
Quiet hours
Active hours
Edge snapping
Edge peek
Reduced motion

Device context
────────────
Battery reactions
Charging reactions
Connectivity reactions
Headphone reactions
Weekend reactions

Actions
────────────
Quick actions
Triple-tap screenshot
Assistive actions
```

---

# PART VII — QUICK INTERACTIONS

## 41. Long-Press Menu

Upgrade V1 menu:

```text
┌──────────────────────┐
│ Hide for 1 hour      │
│ Quiet mode           │
│ Refresh context      │
│ Quick actions        │
│ Open app             │
└──────────────────────┘
```

---

## 42. Temporary Hide

Options:

```text
15 minutes
1 hour
Until evening
Until tomorrow
```

Store:

```text
hiddenUntil
```

The service should respect the timestamp without unnecessary restart loops.

---

## 43. Quick Actions Palette

Let the user choose up to four actions.

Available V2 actions may include existing V1 capabilities:

```text
Back
Home
Recents
Notifications
Quick Settings
Lock
Power dialog
Screenshot
Hide companion
Refresh context
Open app
```

Only show actions supported by the current device/API/permission state.

---

## 44. Assistive Security Boundary

Continue V1's safe approach:

- Do not read content from other apps
- Do not inspect passwords
- Do not bypass protected surfaces
- Do not silently activate privileged features
- Do not capture protected content
- Require explicit user enablement for optional assistive actions

---

# PART VIII — ARCHITECTURE

## 45. Suggested V2 Source Layout

```text
app/
│
├── data/
│   ├── battery/
│   ├── network/
│   ├── audio/
│   ├── schedule/
│   ├── location/
│   ├── weather/
│   └── preferences/
│
├── domain/
│   ├── context/
│   │   ├── AmbientContext.kt
│   │   ├── DeviceContext.kt
│   │   └── InteractionSummary.kt
│   │
│   ├── rule/
│   │   ├── ContextRule.kt
│   │   ├── RuleEngine.kt
│   │   ├── BatteryRules.kt
│   │   ├── WeatherRules.kt
│   │   ├── AudioRules.kt
│   │   └── ScheduleRules.kt
│   │
│   ├── behavior/
│   │   ├── CompanionBehavior.kt
│   │   ├── CompanionMood.kt
│   │   ├── AnimationId.kt
│   │   └── AccessoryId.kt
│   │
│   └── message/
│       ├── MessageEngine.kt
│       ├── MessagePack.kt
│       └── MessageThrottle.kt
│
├── overlay/
│   ├── CompanionOverlayService.kt
│   ├── OverlayController.kt
│   ├── OverlayGestureController.kt
│   ├── QuickMenuController.kt
│   └── QuickActionController.kt
│
├── renderer/
│   ├── CompanionRenderer.kt
│   ├── AnimatedAssetRenderer.kt
│   └── EmojiRenderer.kt
│
├── animation/
│   ├── AnimationStateMachine.kt
│   ├── AnimationScheduler.kt
│   └── AnimationAssets.kt
│
├── worker/
│   ├── ContextRefreshWorker.kt
│   └── MaintenanceWorker.kt
│
├── ui/
│   ├── home/
│   ├── settings/
│   ├── customization/
│   ├── preview/
│   └── debug/
│
└── MainActivity.kt
```

---

## 46. Reactive Event Flow

Prefer `StateFlow` / `SharedFlow`.

```text
Battery Flow
Network Flow
Audio Flow
Time Flow
Weather Flow
Interaction Flow
      │
      ▼
Context Aggregator
      │
      ▼
AmbientContext
      │
      ▼
Rule Engine
      │
      ▼
CompanionBehavior
      │
      ▼
Animation State Machine
      │
      ├── Renderer
      └── Message Engine
```

---

## 47. Context Sources

Recommended independent sources:

```text
BatteryContextSource
NetworkContextSource
AudioContextSource
TimeContextSource
WeatherContextSource
LocationContextSource
ScheduleContextSource
InteractionContextSource
```

Each should be independently unit-testable.

---

## 48. Avoid Polling

Use system callbacks where available.

Battery:
- Battery/system events

Network:
- `ConnectivityManager.NetworkCallback`

Audio:
- `AudioDeviceCallback`

Time:
- Schedule next time/sun boundary

Weather:
- Periodic refresh only

Do not wake the app every minute to recompute everything.

---

## 49. Time Boundary Scheduling

Instead of continuously checking:

```text
Current state: MORNING
Next relevant boundary: 11:00
```

Schedule a refresh near that boundary.

If sunrise/sunset is available, schedule those boundaries too.

---

## 50. Background Refresh

Recommended V2 strategy:

```text
Weather:
60–90 minutes normally

Weather during unstable conditions:
30–60 minutes if justified

Phone state:
event-driven

Time:
boundary-driven
```

---

# PART IX — RESOURCE AND BATTERY MANAGEMENT

## 51. Resource Modes

Add:

```text
Normal
Battery Saver
Minimal
```

### Normal

- Premium renderer
- Normal refresh
- Full allowed animations

### Battery Saver

- Fewer idle effects
- Longer weather interval
- Lower animation activity

### Minimal

- Emoji renderer
- No decorative animations
- Event-only reactions

---

## 52. Automatic Resource Behavior

If Android power saver turns on:

```text
temporarily use Battery Saver behavior
```

When it turns off:

```text
restore user's selected mode
```

Do not overwrite their stored preference.

---

## 53. Screen-Off Behavior

```text
screen off
→ pause renderer
→ stop decorative timers
```

On unlock:

```text
refresh latest context if stale
→ select current persistent state
→ resume
```

Do not replay every event that happened while the screen was off.

---

## 54. Overlay Reliability

V2 must test:

- Activity closed
- Process death
- Service recreation
- Overlay permission revocation
- Rotation
- Screen lock/unlock
- OEM background restrictions
- Device reboot
- App upgrade

Do not aggressively fight Android when the OS stops the service.

---

## 55. Optional Reboot Start

Setting:

```text
Start companion after reboot
[ OFF ]
```

Default OFF.

If enabled:

- Use supported boot handling
- Respect current permissions
- Do not launch a visible activity automatically
- Do not bypass Android background rules

---

# PART X — PRIVACY

## 56. V2 Privacy Model

Remain local-first.

```text
Device state         → local
Interaction summary  → local
Personality          → local
Message selection    → local
Rules                → local
Weather request      → network
```

Do not store:

- Location history
- Contacts
- Screen contents
- Audio content
- Notification text
- App usage history
- User conversations

---

## 57. Permission Strategy

Request optional permissions only when a feature needs them.

Example:

```text
User enables Bluetooth audio awareness
→ explain purpose
→ request permission if required
```

Do not front-load every optional permission during onboarding.

If denied:

```text
disable that feature only
```

Everything else should continue working.

---

# PART XI — V1 TO V2 MIGRATION

## 58. Preserve Existing V1 Settings

On upgrade preserve:

- Overlay enabled state
- Companion position
- Companion size
- Inactive opacity
- Edge snapping
- Weather enabled state
- Message settings
- Reduced motion
- Quick Settings behavior
- Screenshot configuration
- Assistive actions

---

## 59. Settings Schema Migration

Example:

```kotlin
const val SETTINGS_SCHEMA_VERSION = 2
```

Flow:

```text
read V1 settings
→ map V2 defaults
→ preserve old values
→ write version 2
```

Migration must have automated tests.

---

## 60. Suggested New Defaults

```text
Premium renderer: ON
Emoji fallback: available

Battery reactions: ON
Charging reactions: ON
Headphone reactions: ON
Connectivity reactions: OFF
Weekend reactions: ON

Quiet hours: OFF
Active hours: OFF
Edge peek: OFF

Personality: Playful
Message pack: Default
Theme: Default
Resource mode: Normal
```

Do not silently enable any new permission-dependent capability.

---

# PART XII — APP EXPERIENCE

## 61. Existing User "What's New" Flow

Do not rerun full onboarding.

Show:

```text
What's new in V2

✓ Animated companion
✓ Battery & charging reactions
✓ Headphone reactions
✓ Quiet hours
✓ New personalities and themes

[ Continue ]
```

Only ask for newly needed optional permissions when the corresponding feature is enabled.

---

## 62. V2 Home Screen

Keep it simple.

```text
┌──────────────────────────────┐
│ Ambient Companion            │
│                              │
│          [Mascot]            │
│                              │
│       Playful • Night        │
│      Rain • Battery 74%      │
│                              │
│ Companion          [ ON ]    │
│                              │
│ Customize                    │
│ Preview                      │
│ Settings                     │
│                              │
└──────────────────────────────┘
```

Do not turn the home screen into a dashboard full of sensors.

---

## 63. Preview Screen

V2 preview should simulate:

```text
Time
Weather
Temperature
Battery %
Charging
Network
Headphones
Weekend
Quiet hours
Personality
Theme
```

Show:

```text
Persistent rule:
NIGHT_RAIN

Temporary event:
HEADPHONES_CONNECTED

Accessory:
HEADPHONES

Message:
"Music time?"
```

---

## 64. Hidden Rule Debugger

Show:

```text
Current AmbientContext
Active rules
Rule priorities
Winning persistent rule
Temporary event queue
Current animation
Current accessory
Current renderer
Last message
Cooldowns
Resource mode
```

This is important for V2 development.

---

# PART XIII — TESTING

## 65. Rule Engine Tests

Cover combinations like:

```text
night + rain + low battery
day + charging + headphones
storm + full battery
quiet hours + low battery
weekend + offline
screen off + charging
low battery + network loss
rain + headphone event
```

Assert:

- Winning persistent state
- Temporary effect
- Message allowance
- Accessory
- Cooldown behavior

---

## 66. Gesture Tests

Verify no conflict between:

- Single tap
- Double tap
- Triple-tap screenshot
- Rapid tap streak
- Long press
- Drag

Particularly test slow double taps and small drags.

---

## 67. UI / Display Test Matrix

Test:

- Bright wallpaper
- Dark wallpaper
- Busy wallpaper
- Small display
- Large display
- High display scaling
- Large font scaling
- Gesture navigation
- 3-button navigation
- Cutout/notch
- Rounded corners
- Portrait
- Landscape

---

## 68. Battery Test Matrix

Test at least:

```text
Premium renderer + Normal mode
Premium renderer + Battery Saver
Emoji renderer + Minimal mode
Screen on
Screen off
Weather enabled
Weather disabled
```

Measure over meaningful periods, not a five-minute test.

---

## 69. OEM Testing

At minimum test:

- One Android 9/API 28-class device
- One recent Android device
- One restrictive OEM build if available

Watch for:

- Foreground service restrictions
- Overlay removal
- Battery optimization
- Boot behavior
- Quick Settings tile behavior
- Accessibility service behavior
- MediaProjection screenshot behavior

---

# PART XIV — DEVELOPMENT MILESTONES

## 70. Milestone 1 — Renderer Upgrade

Build:

- V2 preference schema
- `CompanionRenderer`
- Premium mascot
- Idle
- Tap
- Double tap
- Drag
- Edge landing
- Night
- Rain
- Emoji fallback

**Success:** All V1 core behavior works using the premium mascot.

---

## 71. Milestone 2 — Device Context

Build:

- Battery source
- Charging source
- Power saver source
- Network source
- Audio output source
- Day/week source

**Success:** Context values can be observed independently without UI code.

---

## 72. Milestone 3 — Rule Engine

Build:

- `ContextRule`
- `CompanionEffect`
- Rule resolver
- Persistent state selection
- Temporary event queue
- Cooldowns
- Deduplication
- Unit tests

**Success:** Multiple simultaneous contexts always resolve predictably.

---

## 73. Milestone 4 — Schedule

Build:

- Quiet hours
- Active hours
- Sleep mode
- Weekend configuration
- Cross-midnight handling

**Success:** Companion behavior correctly changes across a full day.

---

## 74. Milestone 5 — Personality

Build:

- Personality modes
- Message packs
- Interaction summary
- Rapid-tap reaction
- Message throttling

**Success:** Companion feels distinct without AI.

---

## 75. Milestone 6 — Customization

Build:

- Themes
- Accessories
- Renderer choice
- Resource mode
- Quick-action customization
- V2 preview screen

**Success:** Users can personalize behavior without overwhelming settings.

---

## 76. Milestone 7 — Reliability

Build/test:

- Service recovery
- Screen-off handling
- Reboot option
- Event deduplication
- Position restoration
- Settings migration
- OEM background behavior

**Success:** Existing V1 reliability is preserved.

---

## 77. Milestone 8 — Release QA

Complete:

- Full unit suite
- Lint
- Build
- V1 → V2 migration test
- Android 9 physical QA
- Current Android physical QA
- 24-hour battery validation
- Multi-day soak
- Release signing/build

---

# PART XV — RECOMMENDED BUILD ORDER

## 78. Exact Development Order

```text
1. Freeze current V1 branch/release state.

2. Add V2 preference schema migration.

3. Create CompanionRenderer abstraction.

4. Implement one premium animated mascot.

5. Port V1 idle/tap/drag/state behavior to the renderer.

6. Keep EmojiRenderer as fallback.

7. Create AnimationStateMachine.

8. Test animation battery/memory usage.

9. Add BatteryContextSource.

10. Add charging/full/low/critical events.

11. Add PowerSaverContextSource.

12. Add NetworkContextSource.

13. Add AudioContextSource.

14. Add DayWeekContextSource.

15. Create AmbientContext aggregator.

16. Create ContextRule interface.

17. Port V1 time/weather selection into rules.

18. Add battery/charging rules.

19. Add network/audio temporary rules.

20. Build temporary event queue.

21. Add cooldown/deduplication system.

22. Add quiet hours.

23. Add active hours and sleep behavior.

24. Add weekend configuration.

25. Add personality modes.

26. Add message packs.

27. Add lightweight InteractionSummary.

28. Upgrade gesture coordinator.

29. Add theme/accessory system.

30. Upgrade long-press quick menu.

31. Add customizable quick actions.

32. Upgrade preview/debug screen.

33. Add Normal/Battery Saver/Minimal resource modes.

34. Optimize background/event handling.

35. Test V1 → V2 upgrade.

36. Perform physical-device QA.

37. Run 24-hour battery test.

38. Run multi-day soak test.

39. Fix issues and repeat verification.

40. Produce signed V2 release build.
```

---

# PART XVI — V2 ACCEPTANCE CHECKLIST

## 79. Visual

- [ ] Premium mascot renders correctly.
- [ ] Emoji fallback still works.
- [ ] Idle is smooth.
- [ ] Tap reaction works.
- [ ] Double-tap reaction works.
- [ ] Drag animation works.
- [ ] Edge landing works.
- [ ] State transitions are smooth.
- [ ] Night/rain/battery visuals are distinguishable.
- [ ] Companion looks good over bright wallpaper.
- [ ] Companion looks good over dark wallpaper.
- [ ] Reduced-motion mode works.

## 80. Battery / Charging

- [ ] Battery percentage is detected.
- [ ] Low threshold reacts once.
- [ ] Critical threshold works.
- [ ] Low-state hysteresis works.
- [ ] Charging start reacts once.
- [ ] Charging stop updates correctly.
- [ ] Full charge reacts once.
- [ ] Power saver lowers background/animation activity.

## 81. Network

- [ ] Online/offline state works.
- [ ] Events are debounced.
- [ ] Network flapping does not spam.
- [ ] Weather fallback still works offline.
- [ ] Connectivity reactions respect user setting.

## 82. Audio

- [ ] Wired audio detection works where supported.
- [ ] USB audio works where supported.
- [ ] Bluetooth audio works where permission/API allows.
- [ ] Connection reaction is cooldown-protected.
- [ ] Media/audio content is never inspected.

## 83. Schedule

- [ ] Quiet hours work.
- [ ] Quiet hours work across midnight.
- [ ] Automatic bubbles stop during quiet hours.
- [ ] Direct interaction remains available.
- [ ] Active hours work.
- [ ] Sleep mode works.
- [ ] Weekend days are configurable.
- [ ] Timezone changes do not break schedules.

## 84. Rule Engine

- [ ] Persistent rule selection is deterministic.
- [ ] Temporary reactions return to persistent state.
- [ ] Priority conflicts are unit-tested.
- [ ] Event queue is bounded.
- [ ] Duplicate events collapse.
- [ ] Expired events are dropped.
- [ ] Cooldowns work.
- [ ] Debug screen exposes resolved state.

## 85. Personality / Messages

- [ ] All personalities feel different.
- [ ] Quiet personality significantly reduces chatter.
- [ ] Message packs switch correctly.
- [ ] Same message does not repeat consecutively.
- [ ] Rapid taps are handled intentionally.
- [ ] Daily interaction counters reset.
- [ ] Automatic messages respect quiet hours.

## 86. Migration

- [ ] V1 overlay preference preserved.
- [ ] V1 position preserved.
- [ ] V1 size preserved.
- [ ] V1 opacity preserved.
- [ ] V1 weather setting preserved.
- [ ] V1 message setting preserved.
- [ ] V1 screenshot setting preserved.
- [ ] V1 assistive-action settings preserved.
- [ ] New optional permissions are not assumed.
- [ ] Migration does not crash.

## 87. Reliability

- [ ] Activity can close while overlay stays correct.
- [ ] Process recreation is handled.
- [ ] Screen off pauses unnecessary work.
- [ ] Unlock restores correct state.
- [ ] Rotation remains correct.
- [ ] Overlay permission revocation is handled.
- [ ] Location permission revocation is handled.
- [ ] OEM task cleanup behavior tested.
- [ ] Optional reboot restoration tested.
- [ ] 24-hour battery test completed.
- [ ] Multi-day daily-use soak completed.
- [ ] Release build is crash-free in normal use.

---

# PART XVII — DEFINITION OF DONE

## 88. V2 Is Complete When

```text
✓ V1 behavior remains stable

✓ the companion is a premium animated mascot

✓ emoji mode remains available as fallback

✓ time and weather continue to work

✓ battery and charging affect the companion

✓ power saver reduces resource usage

✓ audio-device changes can trigger reactions

✓ connectivity can optionally affect the companion

✓ weekend/day context works

✓ quiet hours and sleep behavior work

✓ personality modes work without AI

✓ message packs and themes work

✓ lightweight interaction memory prevents repetition

✓ all contexts resolve through a tested rule engine

✓ temporary reactions do not conflict with persistent states

✓ V1 users can upgrade without losing settings

✓ battery use is validated on real hardware

✓ the app survives normal multi-day Android use
```

---

# PART XVIII — EXAMPLE V2 DAY

## 7:15 AM

```text
Clear morning
Battery 78%

      ☀️
    (•ᴗ•)

"Good morning!"
```

## 9:10 AM

Headphones connect:

```text
      🎧
    (⌐■ᴗ■)

"Music time?"
```

After a few seconds, it returns to its normal morning state.

## 12:30 PM

Battery reaches 20%:

```text
      🪫
    (╥﹏╥)

"Feed me 🔌"
```

## 12:35 PM

Charging starts:

```text
      ⚡
    (•ᴗ•)

"Charging up!"
```

## 5:50 PM

Rain begins:

```text
      ☂️
    (•ᴗ•)

"Umbrella time ☔"
```

## 8:30 PM

Configured weekend begins:

```text
      ✨
    \(^ᴗ^)/

"Weekend energy!"
```

## 11:00 PM

Quiet hours:

```text
      🌙
    (-ᴗ-) zZ
```

No automatic message bubble. Direct tap still works.

---

# 89. Main Engineering Principle

Keep these concerns separate:

```text
What is happening?
        ↓
AmbientContext

What should happen?
        ↓
Rule Engine

How should it look?
        ↓
CompanionBehavior + Renderer

What should it say?
        ↓
Message Engine
```

Do **not** let the overlay service become the place where all product logic lives.

---

# 90. Main Product Principle

Before adding a V2 feature, ask:

> Does this make the tiny companion feel naturally more aware of the user's day or phone?

If yes, consider it.

If it turns the project into:

- a chatbot,
- a monitoring tool,
- a giant automation app,
- or a social platform,

it does not belong in V2.

---

# 91. Recommended First V2 Sprint

Start with only:

```text
1. V1 → V2 settings migration
2. CompanionRenderer abstraction
3. One premium animated mascot
4. Idle animation
5. Tap animation
6. Drag animation
7. Night animation
8. Rain animation
9. Emoji fallback
10. Animation battery/performance test
```

### Sprint success condition

> The current V1 application behaves exactly as before, but the floating companion finally looks and moves like the premium 3D-style character originally envisioned.

Only after this works should battery, charging, headphones, connectivity, and personality be added.

---

# 92. Final V2 Product Statement

V1 makes the screen feel alive because the companion understands the outside environment.

V2 makes the **companion itself feel alive** because it also understands the phone.

The finished V2 should feel like:

> **A tiny premium animated character that quietly lives on Android, reacts to the weather, time, battery, charging, headphones, connectivity, and rhythm of the day — while staying lightweight, private, useful, and non-distracting.**
