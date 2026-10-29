import test from "node:test";
import assert from "node:assert/strict";
import { CompanionClient } from "../client.js";
import { memory, settings, link, response } from "./helpers.js";

const session = { supabaseUrl: settings.supabaseUrl, accessToken: "old-access", refreshToken: "old-refresh", expiresAt: 999_999 };
const issued = { access_token: "new-access", refresh_token: "new-refresh", expires_in: 3600 };

test("first save creates and persists an anonymous session before authenticated submission", async () => {
  const storage = memory();
  const calls = [];
  const client = new CompanionClient({ storage, now: () => 1000, fetcher: async (url, init) => {
    calls.push({ url, init });
    if (url.endsWith("/signup")) return response(200, issued);
    assert.equal((await storage.get("session")).session.refreshToken, "new-refresh");
    return response(202, { jobId: "job-1", duplicate: false });
  } });
  assert.deepEqual(await client.submit(settings, link), { jobId: "job-1", duplicate: false });
  assert.equal(calls[0].init.headers.apikey, settings.publicKey);
  assert.equal(calls[0].init.headers.Authorization, `Bearer ${settings.publicKey}`);
  assert.equal(calls[1].init.headers.Authorization, "Bearer new-access");
  assert.equal(calls[1].url, `${settings.apiUrl}/api/v1/downloads`);
  assert.deepEqual(JSON.parse(calls[1].init.body), { url: link, format: "video" });
  assert.equal(calls[1].init.credentials, "omit");
  assert.equal(calls[1].init.redirect, "error");
});

test("a rejected access token refreshes the existing session and retries the save once", async () => {
  const storage = memory({ session });
  const calls = [];
  const client = new CompanionClient({ storage, now: () => 1000, fetcher: async (url, init) => {
    calls.push({ url, init });
    if (calls.length === 1) return response(401, { error: "UNAUTHORIZED" });
    if (url.includes("grant_type=refresh_token")) return response(200, issued);
    return response(202, { jobId: "existing-job", duplicate: true });
  } });
  assert.equal((await client.submit(settings, link)).duplicate, true);
  assert.equal(calls.length, 3);
  assert.equal(JSON.parse(calls[1].init.body).refresh_token, session.refreshToken);
  assert.equal(calls[2].init.headers.Authorization, "Bearer new-access");
});

test("temporary refresh errors retain the existing identity for a later retry", async () => {
  const storage = memory({ session: { ...session, expiresAt: 0 } });
  let count = 0;
  const client = new CompanionClient({ storage, fetcher: async () => { count++; return response(503, {}); } });
  await assert.rejects(client.submit(settings, link), (error) => error.retryable === true);
  assert.equal(count, 1);
  assert.equal((await storage.get("session")).session.refreshToken, session.refreshToken);
});

test("only known expired/revoked refresh errors permit a new anonymous identity", async () => {
  const storage = memory({ session: { ...session, expiresAt: 0 } });
  const urls = [];
  const client = new CompanionClient({ storage, fetcher: async (url) => {
    urls.push(url);
    if (url.includes("grant_type")) return response(400, { error_code: "refresh_token_not_found" });
    if (url.endsWith("signup")) return response(200, issued);
    return response(202, { jobId: "job-new" });
  } });
  await client.submit(settings, link);
  assert.equal(urls.length, 3);
  assert.equal((await storage.get("session")).session.refreshToken, issued.refresh_token);
});

test("401 after a refresh is a permanent error and does not loop", async () => {
  let calls = 0;
  const client = new CompanionClient({ storage: memory({ session }), now: () => 1000, fetcher: async (url) => {
    calls++;
    return url.includes("grant_type") ? response(200, issued) : response(401, {});
  } });
  await assert.rejects(client.submit(settings, link), (error) => error.retryable === false);
  assert.equal(calls, 3);
});

test("network failure, rate limiting, and server outages are retryable; malformed success is not", async () => {
  for (const status of [408, 429, 500, 503]) {
    const client = new CompanionClient({ storage: memory({ session }), now: () => 1000, fetcher: async () => response(status, {}) });
    await assert.rejects(client.submit(settings, link), (error) => error.retryable === true);
  }
  const offline = new CompanionClient({ storage: memory({ session }), now: () => 1000, fetcher: async () => { throw new TypeError("offline"); } });
  await assert.rejects(offline.submit(settings, link), (error) => error.retryable === true);
  const malformed = new CompanionClient({ storage: memory({ session }), now: () => 1000, fetcher: async () => response(200, {}) });
  await assert.rejects(malformed.submit(settings, link), (error) => error.retryable === false && /job ID/.test(error.message));
});

test("a Supabase project change never sends the previous project's refresh token", async () => {
  const client = new CompanionClient({ storage: memory({ session }), fetcher: async (url) => {
    assert.equal(url, "https://other.supabase.co/auth/v1/signup");
    return response(200, issued);
  } });
  await client.token({ ...settings, supabaseUrl: "https://other.supabase.co" });
});
