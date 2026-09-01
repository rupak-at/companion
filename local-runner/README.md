# Local browser runner

This optional runner processes TikTok and Instagram jobs in a visible Chromium window on the user's computer. It fills the queued URL into SaveFrom, watches for a browser download, and reports status to the backend.

It does **not** solve CAPTCHA, upload screenshots, transmit CAPTCHA answers, or copy browser cookies to the server. When verification appears, the job becomes `WAITING_FOR_USER` and the runner waits while the user completes it directly in Chromium.

## Setup

1. Add the same random token (at least 32 characters) to `server/.env`:

   ```env
   LOCAL_RUNNER_TOKEN="replace-with-a-long-random-token"
   ```

2. Recreate the API so it receives the token and apply database migrations:

   ```sh
   cd server
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

SaveFrom is a third-party website whose interface and terms can change. The user remains responsible for using it only for content they are permitted to download.
