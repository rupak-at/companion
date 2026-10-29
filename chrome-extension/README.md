# Save to Companion — Chrome extension

Save TikTok, Instagram, and Facebook links from your laptop to the same **server**
used by the Android Companion app. The existing local runner downloads them later.
The extension only submits links; it does not download videos in your browsing tab.

## 1. Install in Chrome

No build, npm install, or Chrome Web Store account is needed.

1. Keep this `chrome-extension` folder somewhere permanent. Chrome loads its files
   directly, so moving or deleting it breaks the installation.
2. Open `chrome://extensions` in Chrome or Chromium.
3. Turn on **Developer mode** in the top-right corner.
4. Click **Load unpacked** and select this folder (the one containing
   `manifest.json`), not the project root.
5. Open the browser's Extensions menu (puzzle icon), then pin **Save to Companion**.
6. Click the Companion icon and select **Settings**.

On this development laptop, the folder is
`/home/rup/projects/companion/chrome-extension`.

## 2. Connect to your existing server

Enter these three values in the extension's Settings page:

```text
Companion server URL:      http://localhost:8080
Supabase project URL:      https://YOUR_PROJECT.supabase.co
Supabase public/anon key:  sb_publishable_YOUR_PUBLIC_KEY
```

This is a **Settings form example**, not an `.env` file. Replace every placeholder
with the values from your real project. For a deployed API, replace localhost
with its HTTPS public URL.

| Extension setting | Existing project setting | Example |
| --- | --- | --- |
| Companion server URL | `DOWNLOAD_API_BASE_URL` on Android; the API's reachable base URL | `http://localhost:8080` for this laptop, or `https://companion.example.com` |
| Supabase project URL | `SUPABASE_URL` | `https://YOUR_PROJECT.supabase.co` |
| Supabase publishable or anon key | `SUPABASE_ANON_KEY` | Your public client key from Supabase project settings |

Use the actual values for your current installation. You can find the Android
settings in the ignored root `local.properties`, or the server's settings in its
ignored `.env`. Enter them through the extension UI; do not put them in source files.
Never enter `LOCAL_RUNNER_TOKEN`, a Supabase secret key, or a service-role key here.

For reference, these are the matching local configuration examples:

```properties
# local.properties (Android emulator example; ignored by Git)
DOWNLOAD_API_BASE_URL=http://10.0.2.2:8080
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=sb_publishable_YOUR_PUBLIC_KEY
```

```dotenv
# local-runner/.env (laptop example; ignored by Git)
RUNNER_API_URL=http://127.0.0.1:8080
RUNNER_DOWNLOAD_DIR=/absolute/path/to/downloads
RUNNER_HEADLESS=false
RUNNER_START_MINIMIZED=false
```

The runner reads `LOCAL_RUNNER_TOKEN` from its environment or `server/.env`.
Generate it with `cd server && npm run runner:setup`; never copy it into the
extension. The server's full template is [server/.env.example](../server/.env.example),
and the runner template is [local-runner/.env.example](../local-runner/.env.example).

Click **Save connection** and allow Chrome's request to access the two configured
hosts. Access is requested only for those hosts; HTTP is permitted only for
`localhost` and `127.0.0.1`. Chrome host permissions cover all ports on a host.
Use HTTPS for a deployed server. No CORS change is needed in the API: requests
originate from the extension service worker with host permission.

**Important:** `http://10.0.2.2:8080` is an Android emulator address. On the laptop,
use `http://localhost:8080` when the API runs on that laptop. The server URL is the
base URL, without `/api/v1/downloads` at the end.

In Supabase Authentication settings, enable **anonymous sign-ins**. The extension
creates its own anonymous session on the first submission and refreshes it when
needed. Supabase projects requiring CAPTCHA during anonymous signup need an
additional authentication flow; this extension does not implement that flow.

Saving settings stores the connection; the first link submission verifies sign-in
and API access. If setup is wrong, the link remains locally visible with an error.

### How this connects with the phone

The phone and this extension have **separate anonymous identities**. Both submit
jobs to the same API/database. The current runner claims waiting social-media jobs
across users, so no phone pairing, Android update, runner-token sharing, or server
change is needed for this personal installation.

This is not a shared phone/browser library: recent saves are local to the browser,
server duplicate detection is per user, and phone notifications are tied to the
phone's identity. Saving the same URL on both devices can create two jobs. Clearing
extension storage, reinstalling, switching Supabase projects, or recovering an
expired/revoked anonymous identity can create a new browser identity as well.

## 3. Save a link

### From the current page

1. Open the specific video, reel, or post in your browser.
2. Click the pinned Companion icon. Its URL is filled in automatically.
3. Click **Save to Companion**.

If you are browsing a feed or profile, use the platform's **Copy link** action for
the particular post, then paste that link into the popup. The extension does not
inspect the page or infer which video is playing. HTTPS links on `tiktok.com`,
`instagram.com`, `facebook.com`, `fb.watch`, and their subdomains are accepted;
the social platform and runner determine whether the linked content can download.

### Without opening a link

Right-click a supported hyperlink and select **Save to Companion**. On supported
pages you can also right-click the background and select **Save this page to
Companion**. On a video/player, the platform may replace the browser menu; use
the toolbar or paste method in that case.

### Paste a copied link

Click Companion, paste a complete post URL, and click **Save to Companion**.
Clipboard access is manual: the extension never monitors or reads your clipboard.

## 4. Understand the status

| Status | Meaning |
| --- | --- |
| Waiting to send / numbered toolbar badge | The link is stored on this laptop but not yet confirmed by the server. |
| Saved to your server / green check badge | The API returned a job ID. The runner can now pick it up. This does not mean the video has downloaded. |
| Already saved | The exact URL already exists in the local queue/recent history or under this browser user's server identity. |
| Error / `!` badge | Open the popup, read the error, fix Settings if needed, and select **Retry now**. |

Recent history retains the last 20 confirmed saves (the popup shows five). Duplicate
checks preserve query parameters, so different share/tracking URLs for the same
video are not guaranteed to deduplicate. This version does not poll download status.

### Offline or server stopped

Links are written to `chrome.storage.local` before any network request. Temporary
network failures and HTTP 408/429/5xx responses retry automatically while Chrome
is running. The background alarm wakes once a minute only when there are pending,
configured links. Per-link backoff grows from one minute up to one hour. Requests
time out after 12 seconds; at most three links are attempted per batch.

Closing Chrome pauses sending. Pending links survive browser restarts, and sending
resumes when Chrome starts. **Retry now** bypasses the wait. Permanent request or
authentication errors wait for a manual retry. Up to 100 pending/failed links are
kept; remove unwanted pending links in the popup to free space. You cannot remove
a link during its active submission or change server URLs while links are pending.
Removing a pending link does not delete a job already accepted by the server.

## 5. Start downloads on the laptop

Use your existing runner setup. From the project root:

```sh
cd local-runner
.venv/bin/python runner.py
```

The runner uses its own ignored `.env` and downloads to its configured
`RUNNER_DOWNLOAD_DIR`. If verification is needed, follow the runner's visible-browser
instructions. The extension never receives the runner token, browser cookies, or
CAPTCHA answers. See [the runner guide](../local-runner/README.md) and
[server setup](../server/README.md) if those services are not configured yet.

## Troubleshooting

- **Waiting for connection:** start the API, check its reachable URL, and check that
  Chrome still permits access to the configured hosts. Save connection settings
  again to request permission. Press **Retry now** after fixing it.
- **Could not sign in:** check the Supabase URL/public key, enable anonymous sign-ins,
  and make sure this project's signup policy does not require CAPTCHA.
- **Sign-in was rejected:** the extension's Supabase project must match the API's
  Supabase project. Changing just the public key does not link different projects.
- **Saved, but no file:** start the local runner and check its terminal. The green
  check confirms link submission, not download completion.
- **Right-click option missing:** right-click an actual supported HTTPS link, or use
  the popup with a copied post URL.
- **Duplicate failed job:** the API returns the existing job for an identical URL.
  Retrying a confirmed server job is handled by `runner.py retry --once`, not by
  the extension's **Retry now** (which retries unsent local links).
- **Extension changed after an update:** open `chrome://extensions`, click **Reload**
  on Save to Companion, and close/reopen its popup and Settings page. Reloading
  preserves storage. Removing the extension clears local settings and pending links.

## Privacy and development

Only explicitly saved URLs are sent to your Companion API. Supabase receives
anonymous sign-in/refresh requests. No content scripts, analytics, page scraping,
cookie access, clipboard access, or browsing-history collection are included.
Settings, authentication tokens, pending links, and recent history stay in this
Chrome profile's local extension storage, restricted to trusted extension contexts.
This is local storage, not an encrypted credential vault or Chrome Sync backup.

The toolbar popup needs `activeTab` to prefill the URL after you click it;
`contextMenus` adds the save menu; `storage` persists links/session; `alarms` handles
retries. Optional host permissions are granted at setup for your server and Supabase.
All scripts/assets ship in this folder; there are no runtime dependencies or remote scripts.

Run the dependency-free behavioral tests with Node.js 22 or newer:

```sh
cd chrome-extension
npm test
```

Before using a real queue, manually check toolbar saving, right-click saving,
duplicate saving, offline recovery, Chrome restart recovery, and a completed
runner download. Automated tests use fake servers/storage and do not submit live
jobs. The browser UI smoke test used during development also avoids real accounts.

Implementation references: [Chrome cross-origin requests](https://developer.chrome.com/docs/extensions/develop/concepts/network-requests),
[optional permissions](https://developer.chrome.com/docs/extensions/reference/api/permissions),
[Supabase anonymous sign-ins](https://supabase.com/docs/guides/auth/auth-anonymous).
