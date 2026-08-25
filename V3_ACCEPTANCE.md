# V3 Acceptance and Release QA

Last updated: 2026-08-25
Build version: `0.3.0`

This document separates repository-verifiable V3 work from tests that require physical
hardware, elapsed time, or release credentials. Unchecked device gates must not be inferred
from a successful debug build.

## Automated repository gates

- [x] Foreground package and sanitized screen-context models are implemented.
- [x] ARTICLE, FORM, LOGIN, CHAT, MEDIA, LIST, SETTINGS, and UNKNOWN fixtures are tested.
- [x] Classification confidence and low-confidence generic actions are tested.
- [x] Password, finance, authentication, secure-window, and user-profile privacy signals exist.
- [x] A user override cannot disable password-field protection.
- [x] Sensitive contexts remove screenshot and content-derived actions.
- [x] Smart positioning avoids keyboard, focused controls, dialogs, system edges, and cutouts where exposed.
- [x] Context actions remain behind an explicit user selection.
- [x] Foreground and recent-interaction active durations are distinct.
- [x] Three-minute active-idle and continuous-scroll resets are tested.
- [x] App-open counting and local-day reset are tested.
- [x] Gentle, Playful, Minimal, and Off wellbeing styles are implemented.
- [x] Attention decisions cover silent, subtle, normal, important, quiet, sensitive, and cooldown inputs.
- [x] V2 settings migrate to schema V3 with Screen Awareness disabled.
- [x] Per-app modes, category defaults, inspection exclusions, and wellbeing exclusions are implemented.
- [x] Privacy dashboard and local V3 activity-data clearing are implemented.
- [x] Accessibility denial leaves V1/V2 settings and environmental/device behavior available.
- [x] Screen and wellbeing context are exposed in the hidden debugger without raw text.

## Privacy inspection

- [x] Accessibility node trees remain inside the snapshot builder/action invocation.
- [x] Persisted V3 activity contains only date, package, counts, minutes, and reaction identifiers.
- [x] Field values, messages, articles, queries, passwords, OTPs, balances, and documents are not persisted.
- [x] No screenshot, OCR, recording, notification-content, cloud-screen, AI, microphone, or camera pipeline was added.
- [x] Screen Awareness is explicitly opt-in for upgraded users.
- [x] Sensitive screens suppress automatic messages and screenshots.
- [x] Excluded apps bypass classification or wellbeing tracking as configured.

## Automated commands

- [x] `./gradlew testDebugUnitTest`
- [x] `./gradlew lintDebug`
- [x] `./gradlew assembleDebug`

Final combined run passed on 2026-08-25 with `./gradlew testDebugUnitTest lintDebug assembleDebug`.

## Physical-device screen awareness

For every run record date, tester, device, Android version, OEM skin, commit, result,
battery-optimization state, and issue link.

- [ ] Android 9/API 28 device.
- [ ] Current Android device.
- [ ] Restrictive OEM device.
- [ ] Foreground package updates after app, dialog, and keyboard transitions.
- [ ] ARTICLE, FORM, LOGIN, CHAT, MEDIA, LIST, SEARCH, SETTINGS, HOME, GRID, and UNKNOWN sampling.
- [ ] Keyboard open/close and focused-field avoidance on both screen sides.
- [ ] Dialog, primary action, cutout, status bar, and gesture-navigation avoidance.
- [ ] Smooth 180–300 ms movement without overwriting the manually saved position.
- [ ] Fullscreen media enters edge peek and restores afterward.
- [ ] Manual dragging and rotation remain stable after smart movement.

## Physical-device privacy and actions

- [ ] Password, PIN, payment, finance, and user-configured private screens enter Privacy mode.
- [ ] Privacy mode exits cleanly and restores the prior app mode.
- [ ] Screenshot is unavailable on every sampled sensitive screen.
- [ ] Automatic bubbles do not appear on sensitive screens or during quiet hours.
- [ ] Back, Home, and Hide remain available in Privacy mode.
- [ ] Article top/bottom, form field navigation, keyboard hide, and generic actions work only after tapping.
- [ ] Unsupported accessibility actions are hidden or fail safely.
- [ ] Accessibility grant, denial, live revocation, re-enable, reboot, and app update are tested.
- [ ] V1/V2 behavior remains usable with Screen Awareness off.

## Physical-device wellbeing

- [ ] Foreground-but-idle time does not increase active time after three minutes.
- [ ] Screen lock pauses active tracking.
- [ ] App switching and returning produce correct sessions and open counts.
- [ ] Continuous scrolling resets after three minutes without scroll events.
- [ ] Configured first and strong reminder thresholds fire once per local day/app threshold.
- [ ] Gentle, Playful, Minimal, and Off behavior is reviewed.
- [ ] Excluded apps generate no session, scroll, or open reaction data.
- [ ] Local midnight resets daily totals and reaction limits.
- [ ] Clear V3 activity data clears both memory and persistent metadata.

## Reliability and release

- [ ] Upgrade a real V2 installation and verify overlay, position, artwork, theme, personality,
  quick actions, schedules, and resource settings are preserved.
- [ ] Verify new V3 permission is not assumed after upgrade.
- [ ] Test process death, activity closure, rotation, lock/unlock, and reboot restoration.
- [ ] Complete 24-hour battery measurements with Screen Awareness off and on.
- [ ] Complete a multi-day crash-free daily-use soak.
- [ ] Review accessibility and privacy disclosure copy on-device.
- [ ] Configure approved release signing outside Git.
- [ ] Produce and smoke-test a signed release APK/AAB.
