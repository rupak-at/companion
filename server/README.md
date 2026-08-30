# Ambient download server

This service uses Supabase Auth, Supabase Postgres through Prisma, and Redis/BullMQ. It accepts direct HTTPS media URLs and runs TikTok/Instagram links through a pinned `yt-dlp` provider adapter. CAPTCHA solving, cookies, credentials, private-account access, and browser-session scraping are not part of this service.

If a provider requests login, bot confirmation, or CAPTCHA, the job becomes `FAILED` with `errorCode: HUMAN_VERIFICATION_REQUIRED`. The client should explain that verification cannot be automated and suggest retrying later or sharing a direct media URL. This avoids making the backend dependent on SaveFrom or another CAPTCHA-protected webpage.

1. Create a Supabase project and copy `.env.example` to `.env`.
2. Use the Supabase transaction-pooler URL for `DATABASE_URL` and the session/direct URL for `DIRECT_URL`.
3. Create the first migration during development with `npm install && npx prisma migrate dev --name init`.
4. Build and start the API, worker, and Redis with `docker compose up --build -d`.
5. Put Caddy or Nginx in front of the loopback-only API port and terminate TLS there.

Run database migrations from a one-off container before deploying a new version:

```sh
docker compose run --rm api npx prisma migrate deploy
```

The Android app should send the signed-in user's Supabase access token as `Authorization: Bearer <token>`. Never put the Supabase service-role key in the Android app.

Production deployment still needs TLS, IP/user rate limiting at the reverse proxy, a cleanup task for expired rows/files, and an authenticated file-streaming route. Do not expose the worker directory directly.
