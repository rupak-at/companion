# Ambient Companion — V3 Implementation Update

Last updated: 2026-08-25
Version: `0.3.0`

## Implemented

- Local, opt-in Screen Awareness using sanitized accessibility metadata.
- Foreground app, category, screen type, confidence, keyboard, focused-input,
  scrollability, fullscreen, orientation, sensitivity, actions, and avoid-bound context.
- Deterministic ARTICLE, FORM, LOGIN, CHAT, MEDIA, LIST, SEARCH, SETTINGS, HOME, DIALOG,
  GRID, and safe UNKNOWN classification paths.
- Smooth keyboard, focused-control, dialog, system-edge, and cutout obstruction avoidance.
- Fullscreen and privacy edge-peek behavior with restoration of manual position.
- Sensitive Screen Mode for password, PIN, authentication, finance, secure, and configured apps.
- Screenshot and automatic-message suppression plus restricted actions in Privacy mode.
- User-triggered contextual actions for articles, forms, chats, media, and generic screens.
- Per-app Normal, Small, Quiet, Edge Peek, Hidden, and Privacy profiles.
- Video, game, messaging, finance, browser, system, and other category defaults.
- Local active-session, continuous-scroll, app-open, daily total, idle, and daily-reset tracking.
- Gentle, Playful, Minimal, and Off wellbeing reactions with fatigue animations.
- Silent, Subtle, Normal, and Important attention decisions integrated with quiet hours,
  sensitive mode, app profiles, personality, resource mode, and screen state.
- Home context summary, V3 permission explanation, Screen Awareness settings, wellbeing
  settings, per-app controls, privacy dashboard, activity clearing, and V3 debugger.
- V2-to-V3 settings migration with Screen Awareness disabled until explicitly enabled.

## Privacy boundaries

- Raw accessibility trees are reduced immediately to counts, booleans, bounds, and enums.
- Raw field, message, article, password, OTP, balance, query, and document text is not stored.
- V3 activity persistence contains only local package-level counts, minutes, and reaction IDs.
- No AI, cloud screen processing, OCR, continuous screenshot, screen recording, autonomous
  submission, notification-content analysis, microphone, camera, or cross-device sync exists.
- V1/V2 behavior continues when Screen Awareness is off or accessibility access is denied.

## Release status

Repository implementation is complete for the V3 must-ship software scope. Automated unit
tests pass. Android lint and debug assembly must pass on the final candidate, and the device,
battery, soak, migration, privacy review, and signed-release gates in `V3_ACCEPTANCE.md`
remain required before calling V3 production-ready.
