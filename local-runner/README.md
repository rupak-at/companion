# Local browser runner

This optional runner processes TikTok and Instagram jobs in a visible Chromium window on the user's computer. It fills the queued URL into SaveFrom, watches for a browser download, and reports status to the backend.

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

Use `--once` to process one available job or exit. Downloads are saved to `~/Downloads/Ambient Companion/`. The persistent browser profile stays under `local-runner/.local/` and is ignored by Git.

## Download URLs from a text file

Put one complete URL on each line. Blank lines, comment lines beginning with `#`, and duplicate URLs are ignored. After each successful download, that URL is removed atomically from the file; failed and unprocessed URLs remain so the same command can safely resume later. Then run:

```sh
cd local-runner
.venv/bin/python runner.py --links-file /absolute/path/to/available_links.txt
```

This standalone mode does not require the backend, Docker, or `LOCAL_RUNNER_TOKEN`. It uses the same visible browser, CAPTCHA assistance, redirect handling, persistent profile, and download directory as queued jobs. Use `--download-dir /path/to/folder` to select another destination.

SaveFrom is a third-party website whose interface and terms can change. The user remains responsible for using it only for content they are permitted to download.
