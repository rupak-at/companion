# Ambient Companion — Link Download Feature Specification

**Feature:** Companion Link Downloader  
**Target version:** V3 / V3.x  
**Primary platform:** Android  
**Backend:** Self-hosted server / VPS  
**Client stack:** Kotlin + Jetpack Compose + native Android APIs  
**Core idea:** Let the user intentionally send a copied or shared supported media URL to the companion, which submits it to a self-hosted backend for processing and then saves the resulting file to the Android device.

---

## 1. Feature Summary

Ambient Companion can become more useful in V3 by supporting a small contextual utility:

> When the user has a supported video/media URL, the companion can send that URL to a self-hosted server, let the server process/download the media, and then save the result to the phone.

Example user flow:

```text
TikTok / Browser / Supported App
   ↓
Share
   ↓
Ambient Companion
   ↓
🐦 "Download this video?"
   ↓
Download
   ↓
Self-hosted server
   ↓
Process media
   ↓
Return downloadable file
   ↓
Android DownloadManager
   ↓
Saved ✓
```

A secondary flow can support copied links:

```text
Copy link
   ↓
Tap / long-press companion
   ↓
Download copied link
   ↓
Read clipboard after explicit user action
   ↓
Validate URL
   ↓
Send to server
```

---

## 2. Product Goal

The goal is **not** to build a generic downloader app.

The goal is to make the companion more useful by offering a relevant action when the user intentionally gives it a media link.

The feature should feel like:

```text
User has a supported link
      ↓
Companion understands link type
      ↓
Companion offers a small action
      ↓
User confirms
      ↓
Download happens
```

The companion remains the interaction layer.

---

## 3. Recommended Primary UX

The primary integration should use Android's share system.

```text
TikTok / Browser / Other supported app
      ↓
Share
      ↓
Ambient Companion
      ↓
Preview card
      ↓
Download
      ↓
Progress
      ↓
Saved
```

Why this should be primary:

- More reliable than background clipboard monitoring
- Explicit user intent
- Better privacy
- Easier to explain
- Fits Android platform behavior
- No need to constantly inspect clipboard contents

---

## 4. Secondary UX — Copied Link

A second workflow may be supported:

```text
User copies link
      ↓
User taps companion
      ↓
Quick action:
"Download copied link"
      ↓
App reads clipboard
      ↓
Validates URL
      ↓
Shows confirmation
      ↓
Submits job
```

Do **not** continuously monitor the clipboard in the background.

---

## 5. Companion Interaction Examples

### Supported link detected via Share

```text
      🐦

"Want me to save this?"

[ Download ] [ Cancel ]
```

### Download starts

```text
      🐦
     (•̀ᴗ•́)و

"Getting it..."
```

### Download complete

```text
      🐦✨
     \(^ᴗ^)/

"Saved!"
```

Actions:

```text
[ Open ] [ Share ]
```

### Download failed

```text
      🐦
     (•︵•)

"Couldn't get that one."
```

Actions:

```text
[ Retry ] [ Close ]
```

---

## 6. Feature Scope

### Must Have

- Android share target
- Clipboard quick action
- URL validation
- Provider detection
- Supported-domain check
- Confirmation UI
- Self-hosted API integration
- Background server-side job processing
- Download status polling or push updates
- File download to Android
- Progress state
- Success/failure companion reactions
- Retry
- Temporary server-file cleanup
- Server-side rate limiting
- Request authentication
- Server-side URL security validation

### Nice to Have

- Multiple supported providers
- Media metadata preview
- File-quality selection
- Audio-only option
- Download history
- Recently downloaded list
- Auto-delete history
- Signed temporary URLs
- Download progress notification
- Companion-specific animation while processing

---

## 7. Explicitly Out of Scope

Do not include in the first version:

- Continuous clipboard surveillance
- Automatic download immediately after copy
- Downloading without user confirmation
- DRM bypass
- Protected/private-account media bypass
- Account credential scraping
- Cookie theft
- Login-session extraction from other apps
- Downloading content the user does not have permission to download
- Unlimited public anonymous backend usage
- Arbitrary remote command execution
- Arbitrary URL proxying
- Permanent server-side file storage by default

---

## 8. Supported Link Types

Create a classifier.

```kotlin
enum class LinkType {
    TIKTOK,
    INSTAGRAM,
    YOUTUBE,
    FACEBOOK,
    X_TWITTER,
    DIRECT_VIDEO,
    DIRECT_IMAGE,
    WEB,
    UNKNOWN
}
```

Only expose download actions for providers that are actually implemented and supported.

---

## 9. Provider Model

```kotlin
data class MediaProvider(
    val type: LinkType,
    val displayName: String,
    val allowedDomains: Set<String>,
    val supportsVideo: Boolean,
    val supportsAudio: Boolean,
    val supportsImage: Boolean
)
```

---

## 10. Example Provider Rules

Example starting support:

```text
TikTok
→ video

Instagram
→ video/image where supported

YouTube
→ only if your implementation and use case comply with applicable platform rules

Direct MP4/WebM
→ direct file save

Direct image
→ save image

Unknown website
→ no download action
```

The app should never assume every URL is downloadable.

---

## 11. URL Classification Flow

```text
Incoming URL
    ↓
Normalize URL
    ↓
Parse hostname
    ↓
Resolve known short-link provider if necessary
    ↓
Verify final destination
    ↓
LinkClassifier
    ↓
Provider
    ↓
Available actions
```

---

## 12. URL Normalization

Client-side normalization should:

- Trim whitespace
- Remove surrounding quotes
- Reject malformed URLs
- Allow only HTTPS by default
- Normalize host casing
- Preserve required query parameters
- Avoid blindly decoding potentially dangerous values

Example:

```kotlin
fun normalizeUrl(input: String): Uri?
```

---

## 13. Android Share Target

Register Ambient Companion to receive shared text/URLs.

Typical flow:

```text
ACTION_SEND
 type = text/plain
      ↓
Extract shared text
      ↓
Find URL
      ↓
Validate
      ↓
Open compact download confirmation UI
```

Do not require the floating overlay to be currently enabled.

---

## 14. Share Target UX

Suggested compact activity/sheet:

```text
┌──────────────────────────┐
│ Ambient Companion        │
│                          │
│ TikTok video detected    │
│                          │
│ [ thumbnail if available]│
│                          │
│ Download video?          │
│                          │
│ [ Cancel ] [ Download ]  │
└──────────────────────────┘
```

The mascot can appear at the top for identity.

---

## 15. Clipboard Quick Action

Long-press companion:

```text
┌──────────────────────────┐
│ Download copied link     │
│ Refresh context          │
│ Quick actions            │
│ Hide                     │
└──────────────────────────┘
```

When selected:

```text
read clipboard
      ↓
extract URL
      ↓
validate
      ↓
show confirmation
```

If clipboard does not contain a URL:

```text
🐦 "I couldn't find a link."
```

---

## 16. Clipboard Privacy Rule

Clipboard should be read only after an explicit user action.

Do not:

```text
poll clipboard
monitor every copy event
upload clipboard automatically
store unrelated clipboard contents
```

Only extract and keep the URL needed for the requested action.

---

## 17. Client Architecture

Suggested package structure:

```text
download/
│
├── share/
│   ├── ShareReceiverActivity.kt
│   └── SharedUrlExtractor.kt
│
├── clipboard/
│   └── ClipboardLinkReader.kt
│
├── classifier/
│   ├── LinkClassifier.kt
│   ├── LinkType.kt
│   └── ProviderRegistry.kt
│
├── data/
│   ├── DownloadApi.kt
│   ├── DownloadRepository.kt
│   └── DownloadStatusMapper.kt
│
├── domain/
│   ├── DownloadRequest.kt
│   ├── DownloadJob.kt
│   ├── DownloadStatus.kt
│   └── MediaMetadata.kt
│
├── worker/
│   └── FileDownloadWorker.kt
│
├── storage/
│   └── AndroidFileSaver.kt
│
└── ui/
    ├── DownloadConfirmScreen.kt
    ├── DownloadProgressScreen.kt
    └── DownloadResultScreen.kt
```

---

## 18. Download Request Model

```kotlin
data class CreateDownloadRequest(
    val url: String,
    val preferredFormat: String? = null,
    val audioOnly: Boolean = false
)
```

---

## 19. Download Job Model

```kotlin
data class DownloadJob(
    val id: String,
    val provider: LinkType,
    val status: DownloadStatus,
    val title: String?,
    val thumbnailUrl: String?,
    val createdAt: Instant
)
```

---

## 20. Download Status

```kotlin
enum class DownloadStatus {
    QUEUED,
    PROCESSING,
    READY,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    EXPIRED
}
```

---

## 21. Backend Architecture

Recommended:

```text
                    Internet
                       │
                       ▼
                Reverse Proxy
                 Nginx/Caddy
                       │
                       ▼
                 Download API
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
       Validation Layer       Job Queue
                                 │
                                 ▼
                              Worker
                                 │
                                 ▼
                          Temporary Storage
                                 │
                                 ▼
                         Signed Download URL
```

---

## 22. Recommended Server Stack

A practical self-hosted stack:

```text
Node.js / TypeScript
Express or Fastify
Redis
BullMQ
PostgreSQL optional
Nginx
Docker
```

Since job processing can take time, BullMQ is a good fit.

---

## 23. API Design

Recommended:

```http
POST /api/v1/downloads
GET  /api/v1/downloads/:jobId
GET  /api/v1/downloads/:jobId/file
DELETE /api/v1/downloads/:jobId
```

---

## 24. Create Job

Request:

```http
POST /api/v1/downloads
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "url": "https://example.com/media/...",
  "format": "video"
}
```

Response:

```json
{
  "jobId": "dl_01J...",
  "status": "QUEUED"
}
```

---

## 25. Job Status Response

```json
{
  "jobId": "dl_01J...",
  "status": "PROCESSING",
  "progress": 42,
  "provider": "TIKTOK",
  "title": "Example video"
}
```

When ready:

```json
{
  "jobId": "dl_01J...",
  "status": "READY",
  "progress": 100,
  "downloadUrl": "https://your-server/...temporary...",
  "expiresAt": "2026-08-30T10:00:00Z"
}
```

---

## 26. Why Use Jobs

Do not keep one HTTP request open during the entire download.

Better:

```text
POST request
→ returns job ID immediately
```

Then:

```text
client polls
or
server sends update
```

Benefits:

- Better retry behavior
- Easier timeout handling
- Easier scaling
- Better progress state
- Cleaner server architecture

---

## 27. Job Queue

Use BullMQ or equivalent.

```text
Download API
   ↓
enqueue
   ↓
Redis
   ↓
Download Worker
```

Job payload:

```ts
{
  jobId,
  userId,
  url,
  provider,
  format
}
```

---

## 28. Worker Responsibilities

Worker should:

1. Revalidate URL.
2. Resolve provider.
3. Resolve redirects safely.
4. Verify final host.
5. Start downloader/extractor.
6. Enforce timeout.
7. Enforce max size.
8. Write into isolated temp directory.
9. Verify output file.
10. Store metadata.
11. Mark job READY.
12. Schedule cleanup.

---

## 29. Downloader Integration

Use a provider/extractor abstraction.

```ts
interface MediaDownloader {
  supports(url: URL): boolean;
  download(input: DownloadInput): Promise<DownloadResult>;
}
```

Possible implementations can wrap an existing media extraction tool/library where legally and technically appropriate.

Do not bind the whole backend directly to one provider.

---

## 30. Provider Registry

```ts
const providers = [
  new TikTokDownloader(),
  new InstagramDownloader(),
  new DirectMediaDownloader()
];
```

Resolution:

```text
URL
→ first compatible provider
→ download
```

---

## 31. Safe Process Execution

Never build a shell string like:

```ts
exec(`downloader ${url}`)
```

Instead use argument arrays or direct library APIs.

Example concept:

```ts
spawn("downloader", ["--output", outputPath, validatedUrl], {
  shell: false
});
```

Even better: use a trusted library API where possible.

---

## 32. SSRF Protection

This is one of the most important backend requirements.

Because the server receives URLs, it must protect itself against Server-Side Request Forgery.

Block:

```text
localhost
127.0.0.0/8
::1
private IPv4 ranges
link-local ranges
cloud metadata IPs
internal hostnames
private IPv6
```

---

## 33. Allowed-Domain Strategy

For provider downloads, prefer an allowlist.

Example:

```text
tiktok.com
www.tiktok.com
vm.tiktok.com
vt.tiktok.com
```

Short links may redirect, but final destination must also pass validation.

---

## 34. Redirect Validation

Every redirect hop should be checked.

```text
Initial URL
   ↓
validate
   ↓
redirect
   ↓
validate destination
   ↓
redirect
   ↓
validate destination
```

Never:

```text
validate first URL
→ blindly follow any destination
```

---

## 35. File Limits

Define hard limits.

Example starting values:

```text
Maximum output: 250 MB
Maximum processing time: 3 minutes
Maximum redirect count: 5
Maximum concurrent jobs per user: 2
```

Tune after real testing.

---

## 36. Rate Limiting

Example:

```text
20 job submissions/hour/user
2 concurrent jobs/user
```

Also rate-limit by IP if the endpoint is public.

---

## 37. Authentication

Do not expose a completely open downloader endpoint on the internet.

Options:

```text
App account token
Device token
API key tied to your app
Authenticated personal account
```

For a personal-only project, a securely stored user token may be enough.

---

## 38. Server File Storage

Default should be temporary.

Example:

```text
READY file TTL: 30 minutes
```

After expiry:

```text
delete file
mark job EXPIRED
```

Do not retain downloaded videos forever by default.

---

## 39. Storage Layout

Example:

```text
/tmp/ambient-downloads/
    └── <job-id>/
        ├── output.mp4
        └── metadata.json
```

Each job gets an isolated directory.

---

## 40. File Naming

Use safe generated file names.

Bad:

```text
<raw remote title>.mp4
```

Better:

```text
sanitized-title_jobid.mp4
```

Remove path separators and dangerous characters.

---

## 41. Temporary Download URLs

Prefer short-lived URLs.

Example:

```text
expires in 10 minutes
```

Optionally sign them.

Do not expose raw server filesystem paths.

---

## 42. Android Download Flow

When job becomes READY:

```text
downloadUrl
   ↓
Android DownloadManager
   ↓
Downloads/Ambient Companion/
   ↓
file saved
```

---

## 43. Android File Destination

Suggested:

```text
Downloads/Ambient Companion/
```

Example:

```text
Downloads/Ambient Companion/video-name.mp4
```

Use scoped-storage-compatible APIs.

---

## 44. File Completion

After Android confirms the file is saved:

```text
job status locally = COMPLETED
```

Companion reaction:

```text
🐦✨

"Saved!"
```

Actions:

```text
Open
Share
Dismiss
```

---

## 45. Progress UX

Companion should not permanently show a large progress UI.

Possible behavior:

```text
Processing
→ small spinner ring around mascot
```

Tap:

```text
42%
```

When left alone:

```text
companion returns to normal idle
```

The job continues in background.

---

## 46. Progress States

Suggested companion states:

```text
DOWNLOAD_QUEUED
DOWNLOAD_PROCESSING
DOWNLOAD_READY
DOWNLOAD_SAVING
DOWNLOAD_SUCCESS
DOWNLOAD_FAILED
```

---

## 47. Progress Messages

Queued:

```text
"Got it!"
```

Processing:

```text
"Working on it..."
```

Saving:

```text
"Saving..."
```

Complete:

```text
"Saved!"
```

Failed:

```text
"Couldn't get that one."
```

---

## 48. Avoid Excessive Polling

If polling status:

```text
first 10 seconds: every 2 seconds
after that: every 5 seconds
```

Stop polling when:

```text
READY
FAILED
EXPIRED
```

WebSocket, SSE, or push can be considered later.

---

## 49. Background Android Handling

If the user leaves the app:

```text
WorkManager / foreground download as required
```

The actual remote processing remains on the server.

Android only needs to:

```text
track job
download finished file
```

---

## 50. Download Notification

For longer downloads:

```text
Ambient Companion
Downloading video…
42%
```

Action:

```text
Cancel
```

---

## 51. Cancellation

Client:

```http
DELETE /api/v1/downloads/:jobId
```

Server should:

- cancel queued job if possible
- stop active worker if safe
- delete partial files
- mark cancelled state

---

## 52. Retry Behavior

Suggested maximum:

```text
2 retries
```

Do not endlessly retry provider failures.

---

## 53. Error Categories

```kotlin
enum class DownloadError {
    UNSUPPORTED_URL,
    INVALID_URL,
    PRIVATE_MEDIA,
    MEDIA_UNAVAILABLE,
    PROVIDER_ERROR,
    NETWORK_ERROR,
    FILE_TOO_LARGE,
    PROCESS_TIMEOUT,
    SERVER_BUSY,
    AUTH_ERROR,
    UNKNOWN
}
```

---

## 54. User-Friendly Errors

Unsupported:

```text
"I don't know how to save this link yet."
```

Unavailable:

```text
"That video isn't available."
```

Too large:

```text
"That one's too large to download."
```

Server busy:

```text
"I'm busy right now. Try again soon."
```

Do not expose raw stack traces.

---

## 55. Link Preview

Optional but recommended.

Before download, show:

```text
thumbnail
title
provider
media type
```

Do not require metadata preview for the first release.

---

## 56. Quality Selection

Future enhancement:

```text
Video quality
Auto
720p
1080p
```

For the first implementation, `Auto` is enough.

---

## 57. Audio-Only Mode

Possible later option:

```text
Save as audio
```

Keep out of the first implementation unless easy.

---

## 58. Download History

Optional local-only history:

```kotlin
data class LocalDownloadHistory(
    val jobId: String,
    val provider: LinkType,
    val title: String?,
    val localUri: String?,
    val completedAt: Instant
)
```

Do not store original URLs forever unless needed.

---

## 59. History Privacy

Default retention suggestion:

```text
7 days
```

Settings:

```text
Download history: ON/OFF
Auto clear: 1 day / 7 days / Never
```

---

## 60. Companion Quick Action Integration

V3 action wheel may include:

```text
Download copied link
```

Do not continuously inspect clipboard just to decide whether the action should appear.

---

## 61. Screen Context Integration

If V3 knows the user is in a video/social app, the action wheel may show:

```text
Download copied link
```

but clipboard access occurs only after tap.

---

## 62. Attention Engine Integration

Download events should use attention levels.

Queued:

```text
SUBTLE
```

Complete:

```text
NORMAL
```

Failed:

```text
NORMAL
```

Server temporarily unavailable:

```text
SUBTLE
```

---

## 63. Sensitive Screen Integration

When V3 Sensitive Screen Mode is active:

```text
Download copied link action
→ hidden by default
```

The user can leave the sensitive screen and use the action normally.

---

## 64. Offline Behavior

If user selects Download while offline:

```text
🐦 "No internet right now."
```

For first version, manual retry is simplest.

---

## 65. Server Offline Behavior

If the self-hosted server is unavailable:

```text
"Downloader server is offline."
```

Provide:

```text
Retry
```

Do not silently retry forever.

---

## 66. Server Health Endpoint

Add:

```http
GET /health
```

Response:

```json
{
  "status": "ok"
}
```

Optional:

```json
{
  "queue": "ok",
  "storage": "ok"
}
```

---

## 67. Configuration

Android config:

```text
DOWNLOAD_API_BASE_URL
```

Example:

```text
https://downloads.example.com
```

Do not hard-code secrets into the APK.

---

## 68. TLS

Production server must use HTTPS.

Recommended:

```text
Nginx/Caddy
+
Let's Encrypt
```

Do not send URLs or tokens over plain HTTP.

---

## 69. API Token Storage

Use Android secure storage where appropriate.

Do not store tokens in:

```text
plain text files
source code
Git repository
```

---

## 70. Backend Environment Variables

Example:

```env
PORT=8080
REDIS_URL=redis://redis:6379
DOWNLOAD_TEMP_DIR=/tmp/ambient-downloads
MAX_FILE_SIZE_MB=250
JOB_TTL_MINUTES=30
MAX_CONCURRENT_PER_USER=2
```

Secrets should be environment-managed.

---

## 71. Docker Architecture

Example:

```text
docker-compose.yml

api
worker
redis
nginx
```

Optional:

```text
postgres
```

---

## 72. Example Docker Flow

```text
Android
   ↓ HTTPS
Nginx
   ↓
API container
   ↓
Redis
   ↓
Worker container
   ↓
Temp volume
```

---

## 73. Logging

Log:

```text
jobId
provider
status
duration
output size
error category
```

Do not log:

```text
authorization tokens
private media data
raw cookies
sensitive query data
```

Consider minimizing original URLs in logs.

---

## 74. Monitoring

Useful metrics:

```text
jobs created
jobs completed
jobs failed
average processing time
queue depth
storage usage
provider failure rate
```

No need for complex observability in the first release.

---

## 75. Cleanup Worker

Run cleanup periodically.

```text
find expired jobs
→ delete files
→ delete partial files
→ update job state
```

Also clean orphaned temp directories.

---

## 76. Database Requirement

For a simple personal deployment, job state may initially live in Redis.

For a more durable deployment:

```text
PostgreSQL
```

Suggested job record:

```text
id
userId
provider
status
createdAt
updatedAt
expiresAt
outputSize
errorCode
```

Do not store unnecessary media data.

---

## 77. Security Checklist

- [ ] HTTPS only.
- [ ] Auth required.
- [ ] URL scheme validated.
- [ ] Provider domains allowlisted.
- [ ] Redirect destinations revalidated.
- [ ] Private IP ranges blocked.
- [ ] Cloud metadata endpoints blocked.
- [ ] Shell interpolation avoided.
- [ ] Job rate limits enabled.
- [ ] Concurrent jobs limited.
- [ ] File size limited.
- [ ] Processing timeout enabled.
- [ ] Temporary files cleaned.
- [ ] Tokens excluded from logs.
- [ ] Output filenames sanitized.
- [ ] Download URLs expire.
- [ ] Unsupported providers rejected safely.

---

## 78. Android Privacy Checklist

- [ ] Clipboard read only after explicit user action.
- [ ] Share target clearly shows received provider/link context.
- [ ] URL is not uploaded until user confirms.
- [ ] Clipboard content unrelated to the URL is not retained.
- [ ] Download history can be disabled.
- [ ] History can be cleared.
- [ ] Sensitive Screen Mode hides the feature.
- [ ] No continuous clipboard monitoring.
- [ ] No continuous screen capture.

---

## 79. Acceptance Criteria — Share Flow

- [ ] Ambient Companion appears in Android share sheet.
- [ ] Shared supported URL is extracted.
- [ ] Unsupported text is rejected safely.
- [ ] Confirmation screen appears.
- [ ] Cancel sends nothing.
- [ ] Download creates server job.
- [ ] Processing status updates.
- [ ] Ready file downloads to device.
- [ ] Companion shows success.
- [ ] File opens successfully.

---

## 80. Acceptance Criteria — Clipboard Flow

- [ ] User manually selects Download copied link.
- [ ] Clipboard URL is extracted.
- [ ] Non-URL clipboard content is rejected.
- [ ] URL is not sent before confirmation.
- [ ] Clipboard is not monitored in background.
- [ ] Supported URL submits successfully.
- [ ] Sensitive Screen Mode suppresses the action.

---

## 81. Acceptance Criteria — Backend

- [ ] Valid supported URL creates job.
- [ ] Unsupported provider returns safe error.
- [ ] Private-network URL is blocked.
- [ ] Redirect to private network is blocked.
- [ ] Oversized file is stopped.
- [ ] Long-running job times out.
- [ ] Concurrent job limit works.
- [ ] Rate limit works.
- [ ] Failed job cleans partial files.
- [ ] Expired output is deleted.
- [ ] Auth is enforced.

---

## 82. Acceptance Criteria — Android Download

- [ ] File saves under Downloads.
- [ ] Duplicate filename handled safely.
- [ ] Progress notification works if enabled.
- [ ] Cancel works.
- [ ] Open action works.
- [ ] Share action works.
- [ ] Download survives activity closure.
- [ ] Network interruption produces a useful failure state.

---

## 83. Milestone 1 — Share Target

Build:

```text
ACTION_SEND receiver
URL extraction
LinkClassifier
confirmation UI
fake backend response
```

Success:

> User can share a link to Ambient Companion and see a correct confirmation screen.

---

## 84. Milestone 2 — Backend Job API

Build:

```text
POST /downloads
GET /downloads/:id
Redis
BullMQ
dummy worker
```

Success:

> Android can create and observe a server-side job.

---

## 85. Milestone 3 — First Provider

Implement one provider first.

Recommended:

```text
TikTok or direct media
```

Success:

> One real supported URL can be processed end-to-end.

Do not implement five providers at once.

---

## 86. Milestone 4 — Android File Save

Build:

```text
READY status
DownloadManager
save file
open/share actions
```

Success:

> Processed media reaches the Android Downloads folder.

---

## 87. Milestone 5 — Companion Integration

Add:

```text
processing animation
success animation
failure animation
action-wheel shortcut
clipboard manual action
```

Success:

> The feature feels like part of Ambient Companion, not a separate downloader.

---

## 88. Milestone 6 — Security Hardening

Implement:

```text
SSRF protection
domain allowlist
redirect validation
rate limiting
auth
timeout
file limits
cleanup
```

Success:

> The server safely handles untrusted URLs.

---

## 89. Milestone 7 — Physical QA

Test:

```text
supported share
copy link
supported short URL
unsupported URL
offline phone
server offline
server timeout
large file
cancel
duplicate download
Android app killed
screen locked
```

---

## 90. Exact Recommended Build Order

```text
1. Add LinkType and ProviderRegistry.
2. Add Android ShareReceiverActivity.
3. Extract URLs from shared text.
4. Add client-side URL validation.
5. Create confirmation screen.
6. Add fake DownloadRepository.
7. Create server project.
8. Add authenticated POST /downloads.
9. Add Redis + BullMQ.
10. Add job status endpoint.
11. Add worker abstraction.
12. Implement one provider.
13. Add temp storage.
14. Return READY state.
15. Add Android polling.
16. Add Android DownloadManager integration.
17. Add success/failure companion states.
18. Add open/share actions.
19. Add manual clipboard action.
20. Add Sensitive Screen Mode gating.
21. Add SSRF protection.
22. Add redirect validation.
23. Add file-size and timeout limits.
24. Add rate limiting.
25. Add cleanup worker.
26. Add cancellation.
27. Test short links.
28. Test server/network failures.
29. Run physical-device QA.
30. Only then add additional providers.
```

---

## 91. Suggested V3 Companion Integration

Existing V3 contextual action system:

```text
ContextActionWheel
      ↓
Generic actions
      +
Download copied link
```

Incoming Android share:

```text
ShareReceiverActivity
      ↓
Download flow
      ↓
Companion state update
```

The downloader should be a separate domain module connected to the companion behavior system.

---

## 92. Suggested Client State Machine

```text
IDLE
 ↓
URL_RECEIVED
 ↓
AWAITING_CONFIRMATION
 ↓
SUBMITTING
 ↓
QUEUED
 ↓
PROCESSING
 ↓
READY
 ↓
SAVING
 ↓
COMPLETED
```

Failure:

```text
FAILED
```

Cancellation:

```text
CANCELLED
```

---

## 93. Suggested Backend State Machine

```text
QUEUED
  ↓
VALIDATING
  ↓
PROCESSING
  ↓
READY
  ↓
EXPIRED
```

Failure:

```text
FAILED
```

Cancellation:

```text
CANCELLED
```

---

## 94. Product Principle

Before adding downloader behavior, ask:

> Did the user clearly ask the companion to process this link?

If no:

```text
do nothing
```

This feature should always be intentional.

---

## 95. Privacy Principle

> A copied/shared URL is user-provided input for one requested action, not permission to monitor the user's clipboard or browsing activity.

---

## 96. Security Principle

> Treat every submitted URL as hostile until fully validated.

---

## 97. Backend Principle

> The Android app should submit jobs; the server should do the heavy work.

Do not attempt complex provider extraction continuously inside the overlay service.

---

## 98. Recommended First Release Scope

Keep the first usable implementation narrow:

```text
✓ Android Share → Ambient Companion
✓ Manual Download copied link action
✓ One supported video provider
✓ Self-hosted authenticated API
✓ BullMQ worker
✓ Temporary files
✓ Android DownloadManager
✓ Companion processing/success/failure animations
✓ SSRF protection
✓ Rate limits
✓ File limits
✓ Cleanup
```

Skip initially:

```text
multiple quality levels
audio extraction
history
many providers
push updates
analytics
```

until the end-to-end flow is stable.

---

## 99. Definition of Done

This feature is complete when:

```text
✓ the user can share a supported link to Ambient Companion
✓ the user can manually process a copied link
✓ no background clipboard surveillance occurs
✓ unsupported URLs are rejected cleanly
✓ the server validates all URLs securely
✓ the server processes downloads asynchronously
✓ Android can observe job progress
✓ the completed file saves into Downloads
✓ the companion reacts to processing, success, and failure
✓ server files expire automatically
✓ jobs are authenticated and rate-limited
✓ the feature survives normal app/background usage
✓ V1/V2/V3 companion behavior remains unaffected when no download is active
```

---

## 100. Final Feature Statement

The final experience should feel like:

> **The user sees a video they want to save, intentionally shares or gives the copied link to the companion, the companion sends it to the user's self-hosted server, the server processes it safely in the background, and the companion returns the finished media to the Android device with a small friendly interaction.**

The companion is the interface.

The server is the worker.

The user remains in control.
