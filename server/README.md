# Ambient download server

This service uses Supabase Auth, Supabase Postgres through Prisma, and Redis/BullMQ. V1 intentionally accepts only direct HTTPS media URLs. Provider adapters can be added only when they use a permitted, stable API or extractor; CAPTCHA solving and browser-session scraping are not part of this service.

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
