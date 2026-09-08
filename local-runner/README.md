# Local browser runner

This optional runner processes TikTok, Instagram, and Facebook jobs in a visible Chromium window on the user's computer. It fills the queued URL into SaveFrom, watches for a browser download, and reports status to the backend.

The runner opens SaveFrom's TikTok-specific page, submits with its Search control, and searches the main result and embedded frames for a generated download. Unexpected advertising popups are closed. If the main tab is redirected away from the configured SaveFrom domain, the runner returns to SaveFrom and records a user-visible warning on the job.

It does **not** solve CAPTCHA, upload screenshots, transmit CAPTCHA answers, or copy browser cookies to the server. When verification appears, the job becomes `WAITING_FOR_USER`; solve it directly in Chromium and the runner continues automatically when the dialog closes.

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

Use `--once` to process one available job or exit. Downloads are saved to `~/Downloads/Ambient Companion/` unless `RUNNER_DOWNLOAD_DIR` is set:

```sh
export RUNNER_DOWNLOAD_DIR="/path/to/my/videos"
.venv/bin/python runner.py
```

To keep this setting across terminal sessions, create the ignored `local-runner/.env` file from `.env.example` and set `RUNNER_DOWNLOAD_DIR` there. Existing shell environment values take priority. An explicit `--download-dir /another/path` takes priority over both. All options support `~` and relative paths. The persistent browser profile stays under `local-runner/.local/` and is ignored by Git.

Chromium runs headlessly by default and reuses the same page for the entire batch, so there is no desktop browser window that can take focus from your work. For each later link, the runner clears and replaces the URL in the existing SaveFrom input instead of reloading the page; it also ignores the previous video's result while the replacement is processing. Advertising tabs are closed immediately without waiting for them to load. If SaveFrom requires a CAPTCHA, the runner keeps the link pending, sends a desktop notification, and exits with instructions. Restart it with `--no-headless` to complete the verification in a visible window; headed mode starts minimized unless `RUNNER_START_MINIMIZED=false` or `--no-start-minimized` is used.

To solve a CAPTCHA for a links file, restart the same command in visible mode:

```sh
.venv/bin/python runner.py --no-headless --links-file /absolute/path/to/available_links.txt
```

Open Chromium from the taskbar after the notification, solve the CAPTCHA, and leave the window open while that download completes. Then stop the visible runner with `Ctrl+C` and restart the normal command to continue without a desktop window.

## Download URLs from a text file

Put one complete URL on each line. Blank lines, comment lines beginning with `#`, and duplicate URLs are ignored. The runner never modifies this input file. After each successful download, the URL is appended to the ignored `local-runner/downloaded_links.txt` ledger. On restart, links already recorded in the ledger are skipped without being downloaded again, while every original line remains available in your source file. Then run:

```sh
cd local-runner
.venv/bin/python runner.py --links-file /absolute/path/to/available_links.txt
```

This standalone mode does not require the backend, Docker, or `LOCAL_RUNNER_TOKEN`. It uses the same visible browser, CAPTCHA assistance, redirect handling, persistent profile, and download directory as queued jobs. Use `RUNNER_DOWNLOAD_DIR` or `--download-dir /path/to/folder` to select another destination.

The filename suggested by the media response/browser is preserved after unsafe filesystem characters are removed. Existing files are never overwritten; a collision is saved with `_2`, `_3`, and so on.

SaveFrom is a third-party website whose interface and terms can change. The user remains responsible for using it only for content they are permitted to download.
