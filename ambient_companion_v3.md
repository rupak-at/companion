# Ambient Companion — V3 Product & Engineering Specification

**Version:** V3  
**Depends on:** Completed and stable V2  
**Primary platform:** Android  
**Stack:** Kotlin + Jetpack Compose + native Android services  
**Core direction:** Evolve the companion from environment/device-aware into a lightweight, deterministic, screen-aware mini assistant.

---

## 1. V3 Vision

V1 made the companion aware of the world around the user:

- Time
- Weather
- Location

V2 made the companion aware of the phone:

- Battery
- Charging
- Connectivity
- Headphones
- Quiet hours
- Personality

V3 should make the companion aware of what the user is currently doing on the screen:

- Current app
- Screen type
- Keyboard state
- Scrollable content
- Focused input
- Fullscreen media
- Active-use duration
- Continuous scrolling
- Sensitive screens

The V3 product statement is:

> **A small screen-aware Android companion that quietly understands what the user is doing, stays out of the way, offers small useful actions, and gives occasional wellbeing nudges — without becoming an AI chatbot.**

---

## 2. V3 Is Not an AI Assistant

V3 should **not** become:

- A chatbot
- A voice assistant
- An LLM client
- A screen-content summarizer
- A system that autonomously clicks through apps
- A system that makes decisions on behalf of the user
- A background surveillance tool
- A general automation engine

V3 should remain:

- Deterministic
- Rule-based
- Local-first
- User-triggered
- Privacy-conscious
- Small
- Fast
- Useful

---

## 3. Main V3 Goals

1. Add a Screen Context Engine.
2. Make the companion intelligently avoid obstructing the UI.
3. Add contextual actions based on the current screen.
4. Add per-app companion behavior profiles.
5. Add lightweight active-use and scrolling awareness.
6. Add privacy-aware Sensitive Screen Mode.
7. Add an Attention Engine so the companion stays useful instead of annoying.

---

## 4. V3 Core Product Evolution

```text
V1
Environment-aware
    ↓
V2
Environment + device-aware
    ↓
V3
Environment + device + screen + activity-aware
```

The companion can now understand:

```text
Where am I?
What type of screen is this?
Is the user typing?
Is the user scrolling?
Am I blocking something?
Has the user been here for a long time?
Should I stay quiet?
Is this screen sensitive?
What actions are useful here?
```

---

## 5. V3 Scope

### Must Ship

- Screen Context Engine
- Current-app awareness
- Screen-type classification
- Keyboard/focused-input awareness
- Scrollability detection
- Fullscreen/media awareness
- Smart companion repositioning
- Obstruction avoidance
- Per-app profiles
- App-category defaults
- Sensitive Screen Mode
- Contextual action wheel
- User-triggered screen actions
- Active-session tracking
- Continuous-scroll tracking
- App-open frequency tracking
- Digital wellbeing reactions
- Attention Engine
- Local-only session counters
- Screen context debugger
- V2 → V3 migration
- Physical-device QA
- Accessibility permission onboarding
- Privacy disclosure inside app

### Nice to Have

- Optional notification metadata awareness
- Edge-peek behavior based on screen mode
- One-time "Inspect this screen" action
- Manual break timer
- App session summary in settings
- Per-app custom reaction thresholds

---

## 6. Explicitly Out of Scope for V3

Do not include:

- LLM integration
- Free-form AI responses
- Autonomous clicking
- Autonomous form completion
- Reading passwords
- Reading OTP codes
- Reading banking data
- Continuous screenshots
- Continuous OCR
- Screen recording
- Cloud screen analysis
- Notification message-content analysis by default
- Contact access
- Microphone monitoring
- Camera monitoring
- User behavior profiling
- Remote analytics of screen content
- Browser-history inspection
- Full parental-control system
- App blocking by force
- Cross-device sync

---

# PART I — SCREEN CONTEXT ENGINE

## 7. Screen Context Overview

V3 introduces a new context source:

```text
ScreenContextSource
```

It should derive high-level metadata from Android accessibility events and the current accessibility tree.

The goal is not to store the screen.

The goal is to derive:

```text
current package
screen type
focused input presence
keyboard state
scrollability
fullscreen state
sensitive state
available actions
important bounds
confidence
```

---

## 8. Screen Context Model

```kotlin
data class ScreenContext(
    val packageName: String?,
    val appCategory: AppCategory,
    val screenType: ScreenType,
    val isKeyboardVisible: Boolean,
    val hasFocusedInput: Boolean,
    val isScrollable: Boolean,
    val isFullScreen: Boolean,
    val isSensitive: Boolean,
    val orientation: ScreenOrientation,
    val confidence: ContextConfidence,
    val availableActions: Set<ScreenAction>,
    val importantBounds: List<Rect>
)
```

---

## 9. Screen Types

```kotlin
enum class ScreenType {
    UNKNOWN,
    HOME,
    ARTICLE,
    LIST,
    CHAT,
    FORM,
    LOGIN,
    SEARCH,
    SETTINGS,
    MEDIA,
    DIALOG,
    GRID
}
```

Do not create dozens of screen types in V3.

---

## 10. Screen Classification Rules

Examples:

```text
Many text nodes
+
scrollable container
+
few input fields
→ ARTICLE
```

```text
multiple editable fields
+
submit-like control
→ FORM
```

```text
password field
+
username/email field
→ LOGIN
```

```text
message-list-like structure
+
editable input near bottom
→ CHAT
```

```text
large media area
+
few controls
+
fullscreen
→ MEDIA
```

---

## 11. Screen Classifier Interface

```kotlin
interface ScreenClassifier {
    fun classify(snapshot: SanitizedScreenSnapshot): ScreenClassification
}
```

---

## 12. Classification Result

```kotlin
data class ScreenClassification(
    val type: ScreenType,
    val confidence: ContextConfidence,
    val reasons: List<String>
)
```

Example:

```text
type = FORM
confidence = HIGH

reasons:
- 4 editable fields
- 1 submit-like button
- focused input exists
```

---

## 13. Context Confidence

```kotlin
enum class ContextConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

Rules:

```text
HIGH
→ enable context-specific actions

MEDIUM
→ enable only safe actions

LOW
→ use generic companion mode
```

If V3 is uncertain, it should do less.

---

## 14. Current-App Awareness

Track:

- package name
- foreground state
- session start
- category
- user profile

Do not require reading app content just to know the package.

---

## 15. App Categories

```kotlin
enum class AppCategory {
    BROWSER,
    MESSAGING,
    SOCIAL,
    VIDEO,
    MUSIC,
    READING,
    GAME,
    FINANCE,
    SHOPPING,
    SYSTEM,
    PRODUCTIVITY,
    OTHER
}
```

Category can come from:

- Built-in known-app mappings
- User override
- Safe heuristic fallback

---

## 16. Per-App Profiles

Available modes:

```text
Normal
Small
Quiet
Edge Peek
Hidden
Privacy
```

Example:

```text
YouTube
→ Edge Peek

Facebook
→ Normal

Banking app
→ Privacy

Game
→ Hidden

Messaging app
→ Small
```

User override always wins.

---

## 17. App Profile Model

```kotlin
data class AppProfile(
    val packageName: String,
    val displayMode: CompanionDisplayMode,
    val allowMessages: Boolean,
    val allowContextActions: Boolean,
    val allowWellbeingReactions: Boolean,
    val sensitiveOverride: Boolean?
)
```

---

# PART II — SMART POSITIONING

## 18. Smart Repositioning

The companion should automatically move when it obstructs important UI.

Priority areas:

- Focused text input
- Keyboard
- Dialog
- Primary action button
- Navigation area
- Screen cutout
- System bars

---

## 19. Avoid-Zone Model

```kotlin
data class AvoidZone(
    val bounds: Rect,
    val priority: Int,
    val source: AvoidZoneSource
)
```

Examples:

```text
Keyboard         priority 100
Focused input    priority 90
Dialog           priority 80
Primary action   priority 70
```

---

## 20. Repositioning Behavior

```text
detect overlap
→ find nearest valid position
→ animate movement
→ preserve side preference if possible
```

Do not teleport abruptly.

---

## 21. Smart Move Animation

Recommended:

```text
duration:
180–300ms

curve:
ease-out

optional reaction:
tiny hop / slide
```

No message by default.

---

## 22. Keyboard Awareness

When the keyboard opens:

```text
reduce usable screen area
→ recompute companion bounds
→ move companion if necessary
```

If the focused input is near the companion:

```text
move away silently
```

Optional rare playful reaction:

```text
"Oops!"
```

Default:

```text
no bubble
```

---

## 23. Fullscreen Media Behavior

Default:

```text
edge peek
```

When fullscreen ends:

```text
restore previous display mode
```

---

## 24. Dialog Awareness

When a dialog appears:

- Move away from center
- Reduce companion opacity if configured
- Do not show automatic bubbles
- Do not cover confirmation actions

---

# PART III — SENSITIVE SCREEN MODE

## 25. Sensitive Screen Philosophy

V3 must treat some screens as private by design.

Examples:

- Password entry
- PIN entry
- Authentication
- Banking
- Secure system surfaces
- Payment forms
- User-configured sensitive apps

---

## 26. Sensitive Screen Detection

Possible signals:

```text
password field present
secure-window behavior
known finance category
user profile = Privacy
authentication-like screen structure
```

Do not depend on one signal only.

---

## 27. Sensitive Screen Mode Behavior

When sensitive mode is active:

```text
companion shrinks or edge-peeks
automatic messages OFF
context text analysis OFF
screenshot shortcut OFF
screen-derived logging OFF
temporary content cache cleared
context actions restricted
```

Direct generic actions may remain:

```text
Back
Home
Hide
```

---

## 28. Sensitive Context Model

```kotlin
data class SensitiveContext(
    val isSensitive: Boolean,
    val reasons: Set<SensitiveReason>
)
```

---

## 29. Sensitive Reasons

```kotlin
enum class SensitiveReason {
    PASSWORD_FIELD,
    PIN_FIELD,
    FINANCE_APP,
    USER_PROFILE,
    AUTHENTICATION_SCREEN,
    SECURE_WINDOW,
    UNKNOWN_SECURE_STATE
}
```

---

## 30. Raw Screen Data Rule

V3 should enforce:

```text
Accessibility tree
    ↓
process locally
    ↓
derive metadata
    ↓
discard raw content
```

Do not persist:

- message text
- account data
- form values
- passwords
- OTP
- personal documents

Persist only metadata required for behavior.

---

# PART IV — CONTEXTUAL ACTIONS

## 31. Contextual Action Wheel

Long press should open context-dependent actions.

Example:

```text
           ↑ Top

      Back  🐦  Screenshot

          Hide 1h
```

---

## 32. Generic Actions

Possible:

- Back
- Home
- Recents
- Notifications
- Quick Settings
- Hide companion
- Refresh context
- Open app
- Screenshot

Only show supported actions.

---

## 33. Article Actions

For ARTICLE:

```text
Scroll top
Scroll bottom
Back
Hide
Quiet 30 min
```

---

## 34. Form Actions

For FORM:

```text
Previous field
Next field
Hide keyboard
Back
Hide companion
```

Do not auto-submit.

---

## 35. Chat Actions

For CHAT:

```text
Hide keyboard
Back
Quiet mode
Hide companion
```

Do not read or send message content.

---

## 36. Media Actions

For MEDIA:

```text
Hide companion
Edge peek
Screenshot
Home
```

---

## 37. Sensitive Screen Actions

For sensitive screens:

```text
Back
Home
Hide companion
```

No screenshot.

No content-derived action.

---

## 38. Action Resolver

```kotlin
interface ScreenActionResolver {
    fun resolve(context: ScreenContext): Set<ScreenAction>
}
```

---

## 39. User Trigger Rule

All V3 context actions should be user-triggered.

```text
screen context
→ actions offered
→ user chooses
→ action runs
```

Do not automatically click through screens.

---

# PART V — DIGITAL WELLBEING AWARENESS

## 40. Wellbeing Feature Goal

V3 should notice when the user has been actively engaged for a long time.

Examples:

- Long scrolling
- Long social-media session
- Repeated app opening
- Long reading session

The companion can react with light, optional messages.

---

## 41. WellbeingContext

```kotlin
data class WellbeingContext(
    val currentAppPackage: String?,
    val currentSessionDurationMs: Long,
    val activeSessionDurationMs: Long,
    val continuousScrollDurationMs: Long,
    val scrollEventCount: Int,
    val appOpenCountToday: Int,
    val appActiveMinutesToday: Int,
    val idleDurationMs: Long
)
```

---

## 42. Active Time vs Foreground Time

Do not treat foreground time as active time.

Bad:

```text
Facebook open for 1 hour
→ assume user scrolled 1 hour
```

Correct:

```text
foreground
+
screen on
+
recent interaction
+
scroll/input events
→ active session
```

---

## 43. Session States

```kotlin
enum class SessionState {
    ACTIVE,
    IDLE,
    PAUSED,
    ENDED
}
```

---

## 44. Session Start

Start session when:

```text
app enters foreground
AND
screen is on
```

Active time increments only when recent interaction exists.

---

## 45. Session Pause

Pause active time when:

```text
no interaction for 2–3 minutes
```

Suggested V3 default:

```text
idle threshold = 3 minutes
```

---

## 46. Session End

End current app session when:

```text
screen locks
OR
different app remains foreground > 5 minutes
OR
device becomes inactive for a long period
```

---

## 47. Continuous Scroll Tracking

Track scroll events.

Example:

```text
SCROLL
SCROLL
SCROLL
pause 8 sec
SCROLL
SCROLL
```

The continuous-scroll timer remains active while meaningful scrolling continues.

---

## 48. Scroll Session Reset

Suggested:

```text
no scroll for 3 minutes
→ reset continuous-scroll session
```

---

## 49. Long-Scroll Reactions

Default thresholds:

```text
30 min
→ subtle

45 min
→ normal

60 min
→ stronger

90 min
→ optional playful
```

These should be configurable.

---

## 50. Playful Reactions

30 minutes:

```text
"Still scrolling? 👀"
```

45 minutes:

```text
"That's a lot of scrolling."
```

60 minutes:

```text
"My eyes hurt watching this 😭"
```

90 minutes:

```text
"Are we trapped here? 😂"
```

---

## 51. Gentle Reactions

30 minutes:

```text
"Maybe look away for a moment?"
```

60 minutes:

```text
"A short break might feel nice 👀"
```

---

## 52. Minimal Reactions

```text
30 min scrolling
60 min active
```

---

## 53. Wellbeing Reaction Styles

Settings:

```text
Gentle
Playful
Minimal
Off
```

---

## 54. Companion Fatigue Animation

Instead of only text:

```text
0–15 min
happy idle

30 min
neutral idle

45 min
slightly tired

60 min
tired eyes

90 min
dramatic playful exhausted pose
```

This is cosmetic only.

---

## 55. App-Open Frequency

Track:

```text
packageName
openCountToday
```

Possible rules:

```text
1st open
→ silence

5th open
→ maybe playful reaction

10th open
→ optional stronger playful reaction
```

Example:

```text
"We meet again 👀"
```

---

## 56. App-Specific Reactions

Examples:

Social app:

```text
"Back to scrolling? 👀"
```

Video app:

```text
"Another video?"
```

Browser:

```text
"That's a lot of reading."
```

Settings:

```text
"What are we changing now?"
```

Play Store:

```text
"Another app? 🤨"
```

Do not shame the user.

---

## 57. Wellbeing Settings

```text
Digital Wellbeing
────────────────────

App awareness              ON
Long-scroll reminders      ON
First reminder             30 min
Strong reminder            60 min
App-open reactions         ON
Daily totals               OFF
Reaction style             Playful
```

---

## 58. Excluded Apps

Allow user to exclude apps from:

- Wellbeing tracking
- Contextual messages
- App-open counts

---

## 59. Daily Reset

Reset:

```text
appOpenCountToday
appActiveMinutesToday
daily reaction limits
```

at local day boundary.

---

# PART VI — BREAK ASSISTANCE

## 60. Optional Break Prompt

After long active use:

```text
"Take a 2-minute break?"

[ Start ] [ Later ]
```

---

## 61. Break Timer

If user chooses Start:

```text
2-minute timer
companion becomes calm
no automatic messages
```

At end:

```text
"Welcome back 👋"
```

Do not forcibly block the current app.

---

## 62. Break Settings

```text
Break suggestions
[ ON ]

Default break
[ 2 min ]

Auto-start
[ OFF ]
```

Never auto-start a break without user action.

---

# PART VII — ATTENTION ENGINE

## 63. Why V3 Needs an Attention Engine

V3 has many triggers:

- Weather
- Battery
- Charging
- Network
- Headphones
- App changes
- Keyboard
- Scrolling
- Screen type
- Notifications
- Wellbeing

Without attention control, the companion becomes annoying.

---

## 64. Attention Levels

```kotlin
enum class AttentionLevel {
    SILENT,
    SUBTLE,
    NORMAL,
    IMPORTANT
}
```

---

## 65. SILENT Examples

- Keyboard opened
- Dialog opened
- Fullscreen media
- Focused input changed
- Screen type changed

Behavior:

```text
move
resize
change actions
no bubble
```

---

## 66. SUBTLE Examples

- Headphones connected
- Network restored
- First long-scroll threshold

Behavior:

```text
small animation
maybe no message
```

---

## 67. NORMAL Examples

- 60-minute scroll reminder
- Weekend reaction
- Weather change

Behavior:

```text
animation
short bubble
```

---

## 68. IMPORTANT Examples

- Critical battery

Behavior:

```text
visible reaction
message allowed despite normal cooldown
```

---

## 69. Attention Decision

```kotlin
data class AttentionDecision(
    val level: AttentionLevel,
    val allowMessage: Boolean,
    val allowAnimation: Boolean,
    val allowPositionChange: Boolean
)
```

---

## 70. Attention Inputs

Consider:

- Quiet hours
- Screen sensitivity
- Current app profile
- Recent message time
- Event importance
- User personality
- Resource mode
- Screen on/off

---

# PART VIII — OPTIONAL NOTIFICATION CONTEXT

## 71. Notification Awareness

Optional V3 feature.

Use only with explicit separate user enablement.

Default goal:

```text
notification count
source package
importance/category
```

Do not rely on message body content.

---

## 72. NotificationContext

```kotlin
data class NotificationContext(
    val unreadCountEstimate: Int,
    val recentSourcePackages: Set<String>,
    val hasHighImportanceNotification: Boolean
)
```

---

## 73. Notification Reactions

Example:

```text
many notifications arrive
→ subtle glance animation
```

Optional message:

```text
"Busy phone!"
```

Long press:

```text
Open notifications
```

---

# PART IX — SANITIZED SCREEN PROCESSING

## 74. SanitizedScreenSnapshot

Do not pass raw AccessibilityNodeInfo trees around the app.

```kotlin
data class SanitizedScreenSnapshot(
    val packageName: String?,
    val nodeCount: Int,
    val editableCount: Int,
    val buttonCount: Int,
    val textNodeCount: Int,
    val scrollableCount: Int,
    val passwordFieldCount: Int,
    val focusedInputBounds: Rect?,
    val importantBounds: List<Rect>,
    val hasDialogLikeStructure: Boolean,
    val hasLargeMediaSurface: Boolean
)
```

---

## 75. What Not To Store

Do not persist:

- Actual field text
- Message body
- Article contents
- Password values
- Search queries
- OTP values
- Bank balances
- Personal names
- Private documents

---

## 76. Ephemeral Processing

```text
event
→ node tree
→ sanitized snapshot
→ raw node references released
```

---

# PART X — ARCHITECTURE

## 77. AmbientContext V3

```kotlin
data class AmbientContext(
    val environment: EnvironmentContext,
    val device: DeviceContext,
    val screen: ScreenContext,
    val wellbeing: WellbeingContext,
    val interaction: InteractionSummary,
    val notification: NotificationContext?,
    val preferences: CompanionPreferences
)
```

---

## 78. Suggested V3 Source Layout

```text
app/
│
├── data/
│   ├── screen/
│   │   ├── ScreenContextSource.kt
│   │   ├── AccessibilityEventProcessor.kt
│   │   ├── ScreenSnapshotBuilder.kt
│   │   ├── KeyboardDetector.kt
│   │   └── FullscreenDetector.kt
│   │
│   ├── wellbeing/
│   │   ├── SessionTracker.kt
│   │   ├── ScrollTracker.kt
│   │   ├── AppOpenTracker.kt
│   │   └── WellbeingRepository.kt
│   │
│   ├── profile/
│   │   ├── AppProfileRepository.kt
│   │   └── AppCategoryResolver.kt
│   │
│   └── notification/
│       └── NotificationContextSource.kt
│
├── domain/
│   ├── screen/
│   │   ├── ScreenContext.kt
│   │   ├── ScreenClassifier.kt
│   │   ├── SensitiveScreenDetector.kt
│   │   ├── ScreenActionResolver.kt
│   │   └── ObstructionResolver.kt
│   │
│   ├── wellbeing/
│   │   ├── WellbeingContext.kt
│   │   ├── SessionRuleEngine.kt
│   │   └── WellbeingReactionEngine.kt
│   │
│   ├── attention/
│   │   ├── AttentionEngine.kt
│   │   └── AttentionDecision.kt
│   │
│   └── rule/
│       ├── ScreenRules.kt
│       ├── WellbeingRules.kt
│       └── PrivacyRules.kt
│
├── accessibility/
│   ├── CompanionAccessibilityService.kt
│   ├── AccessibilityConfig.kt
│   └── AccessibilityCapabilityGate.kt
│
├── overlay/
│   ├── SmartPositionController.kt
│   ├── AvoidZoneResolver.kt
│   └── ContextActionWheel.kt
│
├── ui/
│   ├── screenawareness/
│   ├── appprofiles/
│   ├── wellbeing/
│   ├── privacy/
│   └── debug/
│
└── ...
```

---

## 79. V3 Data Flow

```text
Accessibility events
        ↓
Sanitized Screen Snapshot
        ↓
Screen Classifier
        ↓
ScreenContext
        │
        ├────────────┐
        │            │
Scroll events     App changes
        │            │
        ↓            ↓
Wellbeing trackers
        │
        ▼
WellbeingContext
        │
        └────────────┐
                     ▼
               AmbientContext
                     │
             ┌───────┴────────┐
             ▼                ▼
         Rule Engine      Attention Engine
             │                │
             └───────┬────────┘
                     ▼
              CompanionBehavior
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
Smart Position               Action Resolver
        │                         │
        ▼                         ▼
Floating Companion        Context Action Wheel
```

---

# PART XI — ACCESSIBILITY SERVICE

## 80. Accessibility Service Role

Responsibilities:

```text
foreground app changes
screen event observation
focused input state
scroll events
screen structure metadata
available accessibility actions
```

---

## 81. Accessibility Service Must Not

Do not use it to:

- Harvest screen text
- Store private content
- Auto-click through apps
- Submit forms automatically
- Read passwords
- Perform hidden automation

---

## 82. Permission Onboarding

Flow:

```text
Screen Awareness
      ↓
Explain what it does
      ↓
Explain what is NOT collected
      ↓
Show examples
      ↓
Enable Screen Awareness
      ↓
Android accessibility settings
```

---

## 83. Permission Explanation

Suggested copy:

```text
Screen Awareness lets your companion understand simple screen structure,
such as whether you're typing, scrolling, viewing a form, or watching
fullscreen media.

Raw screen content is not stored.
Sensitive screens are handled in privacy mode.
```

---

## 84. Feature Gating

If accessibility permission is denied:

```text
V1 + V2 features continue working
```

Disable only:

- Screen classification
- Smart obstruction avoidance
- Scroll tracking
- Per-screen actions
- Some wellbeing features

---

# PART XII — PRIVACY DASHBOARD

## 85. Privacy Dashboard

Show:

```text
Screen Awareness          ON
Sensitive Screen Mode     ON
Wellbeing Tracking        ON
Notification Awareness    OFF
Raw screen storage        NEVER
Cloud screen processing   OFF
```

---

## 86. Clear Local Activity Data

Button:

```text
Clear V3 activity data
```

Clears:

- Daily app counts
- Session totals
- Scroll totals
- Recent wellbeing state

Does not affect:

- General app preferences
- Companion position
- Themes

---

## 87. Excluded Apps

Add:

```text
Never inspect these apps
```

For excluded apps:

```text
no screen classification
no scroll tracking
no app-open reactions
generic companion behavior only
```

---

# PART XIII — APP-SPECIFIC BEHAVIOR

## 88. App Profile UI

Example:

```text
Facebook
────────────────
Companion mode      Normal
Wellbeing           ON
Messages            Playful
Screen actions      ON
Privacy mode        OFF
```

---

## 89. Category Defaults

```text
Video
→ Edge Peek

Game
→ Hidden

Messaging
→ Small

Finance
→ Privacy

Browser
→ Normal

System
→ Quiet
```

User can override each app.

---

# PART XIV — V3 HOME / SETTINGS UX

## 90. Home Screen Additions

Keep it simple.

Add:

```text
Screen awareness
ON / OFF

Current context
Reading • Browser
```

Do not turn home into a monitoring dashboard.

---

## 91. Screen Awareness Settings

```text
Screen Awareness
────────────────────
Enabled                  ON
Smart repositioning      ON
Context actions          ON
Per-app profiles
Sensitive Screen Mode    ON
```

---

## 92. Wellbeing Settings

```text
Digital Wellbeing
────────────────────
Active-session tracking  ON
Long-scroll reminders    ON
App-open reactions       ON
First reminder           30 min
Strong reminder          60 min
Reaction style           Playful
Excluded apps
```

---

## 93. Privacy Settings

```text
Privacy
────────────────────
Sensitive Screen Mode    ON
Never inspect apps
Clear activity data
Notification awareness   OFF
```

---

# PART XV — DEBUGGING

## 94. Screen Context Debugger

Show:

```text
Current package
App category
Screen type
Confidence
Keyboard visible
Focused input
Scrollable
Fullscreen
Sensitive
Avoid zones
Available actions
```

---

## 95. Wellbeing Debugger

Show:

```text
Current session
Foreground duration
Active duration
Scroll duration
Scroll events
Idle duration
App open count today
Last wellbeing reaction
Cooldown remaining
```

---

## 96. Rule Explanation

Debug screen should answer:

```text
Why did companion move?
Why did companion hide?
Why did message appear?
Why was message suppressed?
```

Example:

```text
Message suppressed:
- quiet hours
- attention level = SILENT
```

---

# PART XVI — PERFORMANCE

## 97. Accessibility Event Throttling

Accessibility events can be frequent.

Do not rebuild full screen context for every event.

Use:

```text
event coalescing
debounce
targeted refresh
```

Example:

```text
scroll events
→ update scroll tracker
→ do not rebuild entire tree each time
```

---

## 98. Context Refresh Levels

Use:

```text
LIGHT
MEDIUM
FULL
```

LIGHT:

```text
scroll count
keyboard change
```

MEDIUM:

```text
focus change
window content change
```

FULL:

```text
new app
new window
major screen transition
```

---

## 99. Battery Behavior

If power saver is enabled:

```text
reduce screen-tree inspection frequency
disable decorative screen reactions
keep privacy/sensitive detection
keep direct actions
```

---

# PART XVII — TESTING

## 100. Screen Classification Tests

Create deterministic fixtures for:

```text
ARTICLE
FORM
LOGIN
CHAT
MEDIA
LIST
SETTINGS
UNKNOWN
```

Verify classifier confidence.

---

## 101. Smart Position Tests

Test:

- Keyboard opens
- Keyboard closes
- Focused field near left
- Focused field near right
- Dialog center
- Rotation
- Cutout
- Gesture navigation
- Small screen
- Large screen

---

## 102. Sensitive Screen Tests

Verify:

- Password field triggers privacy mode
- PIN-like form triggers privacy mode where intended
- User-defined privacy app triggers privacy mode
- Screenshot action removed
- Messages suppressed
- Raw text not persisted
- Exiting sensitive screen restores normal state

---

## 103. Wellbeing Tests

Test:

```text
30 min active scroll
45 min active scroll
60 min active scroll
90 min active scroll
idle pause
screen lock
app switch
return to app
midnight reset
excluded app
```

---

## 104. App-Open Count Tests

Verify:

```text
same app reopening
quick app switching
process recreation
day reset
excluded app
```

---

## 105. Attention Engine Tests

Examples:

```text
keyboard open
→ SILENT

fullscreen video
→ SILENT

headphones connected
→ SUBTLE

60 min scroll
→ NORMAL

critical battery
→ IMPORTANT
```

---

## 106. Accessibility Permission Tests

Test:

```text
grant
deny
revoke while service active
re-enable
device reboot
app update
```

V1/V2 features must continue when permission is absent.

---

# PART XVIII — MILESTONES

## 107. Milestone 1 — Screen Awareness Foundation

Build:

- Accessibility service
- Sanitized snapshot builder
- Current-app detection
- ScreenContext model
- Hidden debugger

Success:

> V3 can reliably identify the current package and basic screen structure without storing raw content.

---

## 108. Milestone 2 — Screen Classifier

Build:

- ScreenClassifier
- ARTICLE
- FORM
- LOGIN
- CHAT
- MEDIA
- LIST
- UNKNOWN
- Confidence system

Success:

> The app can classify common screens with predictable rules.

---

## 109. Milestone 3 — Smart Positioning

Build:

- Keyboard detection
- Focused-input bounds
- Avoid zones
- Smart repositioning
- Fullscreen edge peek

Success:

> The companion gets out of the user's way automatically.

---

## 110. Milestone 4 — Sensitive Mode

Build:

- SensitiveScreenDetector
- Privacy profiles
- Screenshot suppression
- Message suppression
- Raw-content handling rules

Success:

> V3 safely reduces behavior on sensitive screens.

---

## 111. Milestone 5 — Context Actions

Build:

- ScreenActionResolver
- Context action wheel
- Article actions
- Form navigation actions
- Generic actions
- Sensitive action restrictions

Success:

> The companion offers useful actions without autonomous control.

---

## 112. Milestone 6 — Wellbeing Engine

Build:

- App session tracker
- Active-time tracker
- Scroll tracker
- App-open count
- Long-scroll thresholds
- Reaction styles
- Daily reset

Success:

> The companion can distinguish active scrolling from simply leaving an app open.

---

## 113. Milestone 7 — Attention Engine

Build:

- Attention levels
- Message suppression
- Quiet-hours integration
- Sensitive-screen integration
- Per-event cooldowns

Success:

> V3 remains useful and non-annoying.

---

## 114. Milestone 8 — Per-App Profiles

Build:

- Category resolver
- App profile UI
- Excluded apps
- Category defaults
- User overrides

Success:

> Users control how the companion behaves in specific apps.

---

## 115. Milestone 9 — Optional Notification Metadata

Only after core V3 is stable.

Build:

- Permission flow
- Notification metadata source
- Minimal reactions
- Notification action

Success:

> Notification context works without turning into message surveillance.

---

## 116. Milestone 10 — Release QA

Complete:

- Android 9 testing
- Current Android testing
- Restrictive OEM testing
- Accessibility revocation testing
- 24-hour battery testing
- Multi-day soak testing
- V2 → V3 migration testing
- Privacy review
- Release build

---

# PART XIX — RECOMMENDED BUILD ORDER

## 117. Exact Development Order

```text
1. Freeze V2 release branch.

2. Add V3 settings schema.

3. Add Screen Awareness feature flag.

4. Create accessibility service.

5. Implement current package detection.

6. Create SanitizedScreenSnapshot.

7. Add screen context debugger.

8. Add ScreenClassifier.

9. Implement UNKNOWN / ARTICLE / FORM first.

10. Add LOGIN sensitive detection.

11. Add CHAT / MEDIA / LIST.

12. Add keyboard detection.

13. Add focused-input bounds.

14. Add AvoidZone model.

15. Add SmartPositionController.

16. Add fullscreen edge-peek behavior.

17. Build SensitiveScreenMode.

18. Disable screenshot/context messages in privacy mode.

19. Add per-app profile storage.

20. Add app category resolver.

21. Add ContextActionWheel.

22. Add generic actions.

23. Add ARTICLE actions.

24. Add FORM actions.

25. Add sensitive action restrictions.

26. Build SessionTracker.

27. Track foreground session.

28. Add active interaction timer.

29. Add ScrollTracker.

30. Add continuous-scroll timer.

31. Add AppOpenTracker.

32. Add WellbeingContext.

33. Add wellbeing thresholds.

34. Add playful/gentle/minimal reactions.

35. Add companion fatigue animations.

36. Add AttentionEngine.

37. Integrate quiet hours.

38. Integrate sensitive mode.

39. Add excluded apps.

40. Add privacy dashboard.

41. Add clear-activity-data action.

42. Test V2 → V3 migration.

43. Run full physical-device QA.

44. Run 24-hour battery test.

45. Run multi-day soak test.

46. Review permission/disclosure copy.

47. Produce signed V3 release build.
```

---

# PART XX — V3 ACCEPTANCE CHECKLIST

## 118. Screen Awareness

- [ ] Current foreground package detected.
- [ ] Screen context updates after app switch.
- [ ] Keyboard state detected.
- [ ] Focused input state detected.
- [ ] Scrollable state detected.
- [ ] Fullscreen state detected.
- [ ] Screen classifier returns confidence.
- [ ] Low-confidence screens fall back safely.

---

## 119. Smart Positioning

- [ ] Companion avoids keyboard.
- [ ] Companion avoids focused input.
- [ ] Companion avoids important controls where possible.
- [ ] Companion avoids dialogs.
- [ ] Fullscreen media triggers edge behavior.
- [ ] Position restores after fullscreen.
- [ ] Rotation does not break smart position.
- [ ] Manual drag still works.

---

## 120. Sensitive Mode

- [ ] Password screen triggers privacy behavior.
- [ ] User-defined privacy app works.
- [ ] Screenshot action disabled.
- [ ] Automatic messages disabled.
- [ ] Raw screen text is not persisted.
- [ ] Context actions are restricted.
- [ ] Exiting sensitive mode restores prior behavior.

---

## 121. Context Actions

- [ ] Generic action wheel works.
- [ ] Article actions appear only when appropriate.
- [ ] Form actions appear only when appropriate.
- [ ] Sensitive actions are restricted.
- [ ] Unsupported actions are hidden.
- [ ] User must explicitly trigger every action.

---

## 122. Wellbeing

- [ ] Foreground time differs from active time.
- [ ] Idle threshold pauses active timer.
- [ ] Screen lock ends/pauses session.
- [ ] App switch updates session correctly.
- [ ] Scroll duration resets after inactivity.
- [ ] 30-minute threshold works.
- [ ] 60-minute threshold works.
- [ ] App-open count works.
- [ ] Daily reset works.
- [ ] Excluded apps are ignored.
- [ ] Reaction style changes wording.

---

## 123. Attention Engine

- [ ] Keyboard events are silent.
- [ ] Fullscreen events are silent.
- [ ] Long-scroll reminders use correct level.
- [ ] Critical battery still works.
- [ ] Quiet hours suppress normal reactions.
- [ ] Sensitive mode suppresses messages.
- [ ] Cooldowns prevent spam.

---

## 124. Permissions

- [ ] Screen Awareness disabled by default for upgraded users until explicitly enabled.
- [ ] Accessibility explanation shown before system settings.
- [ ] Denying permission keeps V1/V2 working.
- [ ] Revoking permission is handled.
- [ ] Notification awareness requires separate enablement if included.

---

## 125. Privacy

- [ ] No raw accessibility text stored.
- [ ] No passwords stored.
- [ ] No OTP values stored.
- [ ] No cloud screen processing.
- [ ] Activity data can be cleared.
- [ ] Apps can be excluded.
- [ ] Privacy dashboard reflects real settings.

---

## 126. Migration

- [ ] V2 overlay settings preserved.
- [ ] V2 companion position preserved.
- [ ] V2 themes preserved.
- [ ] V2 personalities preserved.
- [ ] V2 quick actions preserved.
- [ ] V2 quiet hours preserved.
- [ ] New V3 permissions are not assumed.
- [ ] Upgrade does not crash.

---

# PART XXI — DEFINITION OF DONE

## 127. V3 Is Complete When

```text
✓ V2 remains stable

✓ the companion knows the current foreground app

✓ the companion can classify common screen structures

✓ it knows when the keyboard is visible

✓ it can detect active scrolling

✓ it can avoid focused inputs and important UI

✓ it can reduce itself during fullscreen media

✓ per-app profiles work

✓ sensitive screens activate privacy mode

✓ raw screen contents are not persisted

✓ contextual actions change with screen type

✓ every screen action remains user-triggered

✓ long-scroll awareness works

✓ active time is distinct from foreground time

✓ app-open frequency works locally

✓ wellbeing reactions are optional

✓ attention levels prevent notification fatigue

✓ V3 works without AI or cloud processing

✓ accessibility permission can be denied without breaking V1/V2

✓ battery usage is validated on real devices

✓ the app survives multi-day normal use
```

---

# PART XXII — EXAMPLE V3 DAY

## 128. Morning Reading

User opens a browser and reads an article.

```text
Screen:
ARTICLE

Companion:
normal position

Long press:
↑ Top
↓ Bottom
Back
Hide
```

No automatic message.

---

## 129. Typing

User focuses a text field.

```text
Keyboard appears.
```

Companion is covering the right side.

```text
🐦 → smoothly moves upward-left
```

No bubble.

---

## 130. Facebook Session

User opens Facebook.

First 20 minutes:

```text
silence
```

30 minutes of active scrolling:

```text
🐦 slightly tired

"Still scrolling? 👀"
```

60 minutes:

```text
🐦 tired expression

"My eyes hurt watching this 😭"
```

The user can disable this feature.

---

## 131. User Stops Using Phone

Phone remains on Facebook but sits on a desk.

After idle threshold:

```text
active timer pauses
```

The companion does not incorrectly claim the user has been scrolling.

---

## 132. Fullscreen Video

User opens a fullscreen video.

```text
🐦 slides partly off the screen edge
```

No message.

When fullscreen exits:

```text
🐦 returns
```

---

## 133. Login Screen

V3 detects a password field.

Result:

```text
Privacy Mode
```

Companion:

```text
small edge-peek
```

Disabled:

```text
screenshots
context messages
content-derived actions
```

---

## 134. App Reopened Many Times

A social app is opened for the 8th time that day.

If app-open reactions are enabled:

```text
🐦

"We meet again 👀"
```

One reaction only.

No nagging.

---

## 135. Long Press on Form

User is filling a form.

Action wheel:

```text
Previous field
Next field
Hide keyboard
Back
```

The companion never submits the form automatically.

---

# PART XXIII — MAIN ENGINEERING PRINCIPLE

## 136. Separation of Responsibilities

```text
What is on screen?
        ↓
ScreenContext

How long has the user been active?
        ↓
WellbeingContext

Is this sensitive?
        ↓
Privacy Engine

What should the companion do?
        ↓
Rule Engine

Should it call attention to itself?
        ↓
Attention Engine

Where should it be?
        ↓
Smart Position Controller

What actions are useful?
        ↓
Screen Action Resolver
```

Do not put all V3 logic inside the accessibility service.

---

## 137. Main Privacy Principle

> Derive behavior from screen metadata, not from storing screen content.

---

## 138. Main Product Principle

Before adding a V3 feature, ask:

> Does this help the companion understand what the user is doing and provide small, useful help without becoming invasive?

If it requires:

```text
reading everything
storing private text
autonomous clicking
AI interpretation
constant screenshots
```

it does not belong in V3.

---

## 139. Recommended First V3 Sprint

Start only with:

```text
1. V3 settings migration
2. Screen Awareness toggle
3. Accessibility service
4. Current app detection
5. Sanitized screen snapshot
6. Screen context debugger
7. Keyboard detection
8. Scrollable detection
9. Screen classifier: UNKNOWN / ARTICLE / FORM
10. Smart companion repositioning
```

### First Sprint Success Condition

> The V2 companion behaves exactly as before, but can now understand basic screen structure and move out of the user's way without storing screen content.

Do **not** start V3 by implementing every wellbeing feature at once.

---

## 140. Recommended Second V3 Sprint

```text
1. Sensitive Screen Mode
2. LOGIN detection
3. Per-app privacy profiles
4. Contextual action wheel
5. Article actions
6. Form actions
7. Fullscreen edge peek
```

---

## 141. Recommended Third V3 Sprint

```text
1. Active session tracker
2. Idle detection
3. Continuous scroll tracker
4. App-open counts
5. Long-scroll reactions
6. Attention Engine
7. Wellbeing settings
```

---

## 142. Final V3 Product Statement

V1 understands the environment.

V2 understands the phone.

V3 understands the **current activity**.

The finished V3 should feel like:

> **A small, premium Android sidekick that understands when the user is reading, typing, scrolling, watching, filling a form, or entering a sensitive screen; moves out of the way automatically; offers useful actions when asked; and occasionally gives lightweight wellbeing reminders — all without AI, cloud screen analysis, or autonomous control.**
