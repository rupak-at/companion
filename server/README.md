# Ambient download server

This service uses Supabase Auth, Supabase Postgres through Prisma, and Redis/BullMQ. Direct HTTPS media URLs run in the container worker. TikTok and Instagram jobs enter `WAITING_FOR_LOCAL_RUNNER` for the optional visible browser runner in `../local-runner`.

The local runner can fill the submitted URL and observe a browser download. If SaveFrom presents verification, the job becomes `WAITING_FOR_USER`; the user completes it directly in the persistent Chromium window. CAPTCHA screenshots, answers, browser cookies, and credentials are never uploaded to this service.

1. Create a Supabase project and copy `.env.example` to `.env`.
2. Use the Supabase transaction-pooler URL for `DATABASE_URL` and the session/direct URL for `DIRECT_URL`.
3. Create the first migration during development with `npm install && npx prisma migrate dev --name init`.
4. Generate a separate local-runner token with `npm run runner:setup`.
5. Build and start the API, worker, and Redis with `docker compose up --build -d`.
6. Follow `../local-runner/README.md` to install and start the visible runner.
7. Put Caddy or Nginx in front of the loopback-only API port and terminate TLS there.

Run database migrations from a one-off container before deploying a new version:

```sh
docker compose run --rm api npx prisma migrate deploy
```

The Android app should send the signed-in user's Supabase access token as `Authorization: Bearer <token>`. Never put the Supabase service-role key in the Android app.

Production deployment still needs TLS, IP/user rate limiting at the reverse proxy, and a scheduled cleanup task for expired rows/files. Files are streamed only through the authenticated job-owner route; do not expose the worker directory directly.
