# Local browser runner

This optional runner processes TikTok, Instagram, and Facebook jobs in a visible Chromium window on the user's computer. It fills the queued URL into SaveFrom, watches for a browser download, and reports status to the backend.

The runner opens SaveFrom's TikTok-specific page, submits with its Search control, and searches the main result and embedded frames for a generated download. Unexpected advertising popups are closed. If the main tab is redirected away from the configured SaveFrom domain, the runner returns to SaveFrom and records a user-visible warning on the job.

It does **not** solve CAPTCHA, upload screenshots, transmit CAPTCHA answers, or copy browser cookies to the server. When verification appears, the job becomes `WAITING_FOR_USER`; solve it directly in Chromium and the runner continues automatically when the dialog closes.

## Daily use: no extra arguments

After completing setup below, create or edit `local-runner/.env` with these settings (preserve any other existing entries):

```dotenv
RUNNER_DOWNLOAD_DIR=/media/rupak/USB/db_v
RUNNER_HEADLESS=false
RUNNER_START_MINIMIZED=false
```

From the project root, start the runner:

```sh
cd local-runner
.venv/bin/python runner.py
```

This opens Chromium visibly, lets you solve CAPTCHA directly, and saves videos to the configured folder. Set `RUNNER_HEADLESS=true` for background operation; set `RUNNER_START_MINIMIZED=true` to start a visible browser minimized. Shell environment variables override `.env`, and command-line flags override both.

After code or `.env` changes, stop the old runner with `Ctrl+C` and start it again using the same command. An already running Python process does not load these updates. Run only one instance with the same browser profile. The updated runner prints `Browser download started; waiting for the file to finish.` when a download begins.

## Backend connection failures

A `P1001` error mentioning Supabase means the backend cannot reach its database. A video already reported as `Downloaded:` is saved locally; an error claiming the next job does not remove it.

During normal queue operation, temporary claim failures (network errors, timeouts, HTTP 408/429/5xx) retry after 5, 10, 20, 40, then 60 seconds until the backend recovers. The browser stays open. Persistent database failures still require restoring the backend's database connectivity. Authentication and other permanent claim errors exit with a concise message. `--once` exits on a claim failure instead of retrying. This recovery applies to claiming jobs; it does not retry failed status updates.

Once Chromium starts a download, slow transfers can continue beyond the ten-minute SaveFrom processing window. The runner refreshes the database job lease every five minutes until the file finishes. The direct media fallback waits up to ten minutes for a response. If the network causes the download to fail, the job becomes `FAILED`; run `runner.py retry --once` to try it again.

## Setup

1. Generate the shared runner token without displaying it:

   ```sh
   cd server
   npm run runner:setup
   ```

2. Recreate the API so it receives the token and apply database migrations:

   ```sh
   npx prisma migrate deploy
   docker compose up --build -d --force-recreate api
   ```

3. Install the local runner outside Docker:

   ```sh
   cd ../local-runner
   python3 -m venv .venv
   .venv/bin/pip install -r requirements.txt
   .venv/bin/playwright install chromium
   ```

4. Run it when there are queued social-media jobs:

   ```sh
   .venv/bin/python runner.py
   ```

Use `--once` to drain available jobs and exit when the queue is empty. Downloads are saved to `~/Downloads/Ambient Companion/` unless `RUNNER_DOWNLOAD_DIR` is set:

```sh
export RUNNER_DOWNLOAD_DIR="/path/to/my/videos"
.venv/bin/python runner.py
```

To retry database jobs that failed previously, run `.venv/bin/python runner.py retry --once` (or use `--retry --once` without `--links-file`). The API requeues failed TikTok, Instagram, and Facebook jobs once before the runner starts claiming jobs. It prints the number requeued and also processes other queued jobs. A job that fails again waits for another retry invocation. When `--links-file` is present, `--retry` instead processes only the locally saved failed-link ledger described below. Rebuild the server API after updating it; an older API returns a clear unsupported-retry error.

To keep this setting across terminal sessions, create the ignored `local-runner/.env` file from `.env.example` and set `RUNNER_DOWNLOAD_DIR` there. Existing shell environment values take priority. An explicit `--download-dir /another/path` takes priority over both. All options support `~` and relative paths. The persistent browser profile stays under `local-runner/.local/` and is ignored by Git.

Chromium runs headlessly by default and reuses the same page for the entire batch, so there is no desktop browser window that can take focus from your work. For each later link, the runner clears and replaces the URL in the existing SaveFrom input instead of reloading the page; it also ignores the previous video's result while the replacement is processing. Advertising tabs are closed immediately without waiting for them to load. If SaveFrom requires a CAPTCHA, the runner keeps the link pending, sends a desktop notification, and exits with instructions. Restart it with `--no-headless` to complete the verification in a visible window; headed mode starts minimized unless `RUNNER_START_MINIMIZED=false` or `--no-start-minimized` is used.

To solve a CAPTCHA for a links file, restart the same command in visible mode:

```sh
.venv/bin/python runner.py --no-headless --links-file /absolute/path/to/available_links.txt
```

Open Chromium from the taskbar after the notification, solve the CAPTCHA, and leave the window open while that download completes. Then stop the visible runner with `Ctrl+C` and restart the normal command to continue without a desktop window.

## Download URLs from a text file

Put one complete URL on each line. Blank lines, comment lines beginning with `#`, and duplicate URLs are ignored. The runner never modifies this input file. After each successful download, the URL is appended to the ignored `local-runner/downloaded_links.txt` ledger. Failed URLs are saved in the ignored `local-runner/failed_links.txt` ledger. Normal runs skip both completed and previously failed URLs, while every original line remains available in your source file. Then run:

```sh
cd local-runner
.venv/bin/python runner.py --links-file /absolute/path/to/available_links.txt
```

Retry only the URLs saved in `failed_links.txt` with:

```sh
.venv/bin/python runner.py --links-file /absolute/path/to/available_links.txt --retry
```

Saved failures are processed in ledger order and removed from `failed_links.txt` after a successful download. If one fails again, it remains available for the next explicit retry. The supplied links file selects standalone mode and is not modified; retry mode intentionally processes only the saved failure ledger.

This standalone mode uses the same visible browser, CAPTCHA assistance, redirect handling, persistent profile, and download directory as queued jobs. It needs no backend unless you enable mobile CAPTCHA pushes. Use `RUNNER_DOWNLOAD_DIR` or `--download-dir /path/to/folder` to select another destination.

To send a mobile push when a file link needs CAPTCHA, deploy the current server API and configure `local-runner/.env` with:

```dotenv
RUNNER_API_URL=https://your-vps-api.example.com
RUNNER_PUSH_USER_ID=your-phone-supabase-user-uuid
```

`RUNNER_PUSH_USER_ID` must be the Supabase Auth user UUID associated with the phone's registered device token. Find it in the `DeviceToken` table's `userId` column (or the matching Supabase Auth user). The runner uses `LOCAL_RUNNER_TOKEN` from its environment or `server/.env`; this must match the token configured on the VPS. The VPS also needs its Firebase credentials, and the Android app must have registered its device token and have notification permission. You can pass `--api-url` and `--push-user-id` instead of the two URL/user settings above. The runner sends only the CAPTCHA message, not the link or a fabricated database job ID. If push delivery fails, it prints the reason and leaves local CAPTCHA handling available.

The server preserves the most recently registered token for that user when a push fails, while still removing older tokens that Firebase permanently rejects. If the server reports `NO_REGISTERED_DEVICE`, open the Android app once to register its current token, then confirm that `RUNNER_PUSH_USER_ID` matches that token row's `userId`.

Once a processed result appears, the runner clicks its download control first and checks for browser events every 250 ms while waiting. Once a browser download starts, retries stop and the runner waits for that file to finish; additional download events for the same job are cancelled. A direct media request is used only if the browser download does not start after the click retries.

The direct request sends the SaveFrom page as its referrer and accepts MP4 data even when the converter labels it with a generic content type. If it still fails, the runner prints the HTTP status or content type so a generated result is not mistaken for a downloadable file.

After a processed result click, the runner follows up to three Download actions on SaveFrom converter pages, whether they replace the main tab or open a new one. It waits briefly for each page to present the next action or start a browser download. Unrelated advertising tabs are closed; if the handoff fails, the runner returns to the original result and tries its direct URL fallback.

If SaveFrom's generated link returns an error (including HTTP 404) or SaveFrom cannot process the link, the runner also tries the original social-media URL with `yt-dlp`. Install the updated `requirements.txt` in the local runner environment after pulling this change: `.venv/bin/pip install -r requirements.txt`. A completed original-URL download is recorded in the same way as a browser download.

If SaveFrom reports “link not found” or another recognized processing error after submitting a URL, the runner submits the same URL once more. If that attempt also fails, it reports the failure instead of retrying indefinitely.

The filename suggested by the media response/browser is preserved after unsafe filesystem characters are removed. Existing files are never overwritten; a collision is saved with `_2`, `_3`, and so on.

SaveFrom is a third-party website whose interface and terms can change. The user remains responsible for using it only for content they are permitted to download.
