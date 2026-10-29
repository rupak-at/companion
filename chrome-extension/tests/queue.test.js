import test from "node:test";
import assert from "node:assert/strict";
import { LinkQueue } from "../queue.js";
import { RequestError } from "../core.js";
import { memory, settings, link } from "./helpers.js";

function fixture(initial = { settings }, submit = async () => ({ jobId: "job-1", duplicate: false })) {
  let now = 1000, sequence = 0;
  const storage = memory(initial);
  const queue = new LinkQueue({ storage, client: { submit }, now: () => now, id: () => `local-${++sequence}` });
  return { queue, storage, advance(ms) { now += ms; } };
}

test("saves locally without setup and sends the persisted link after configuration", async () => {
  let calls = 0;
  const f = fixture({}, async () => { calls++; return { jobId: "job-1" }; });
  await f.queue.save(link);
  await f.queue.flush();
  assert.equal(calls, 0);
  assert.equal((await f.queue.state()).queue.length, 1);
  await f.queue.configure(settings);
  await f.queue.flush();
  const state = await f.queue.state();
  assert.equal(state.queue.length, 0);
  assert.equal(state.history[0].jobId, "job-1");
});

test("concurrent saves do not lose links and exact duplicates are kept once", async () => {
  const f = fixture();
  await Promise.all([f.queue.save(link), f.queue.save(`${link}2`), f.queue.save(link)]);
  assert.equal((await f.queue.state()).queue.length, 2);
  await f.queue.flush();
  const duplicate = await f.queue.save(link);
  assert.equal(duplicate.alreadySaved, true);
  assert.equal(duplicate.status, "saved");
  assert.equal((await f.queue.state()).queue.length, 0);
});

test("transient failures persist across a worker restart and respect backoff", async () => {
  let calls = 0;
  const f = fixture({ settings }, async () => { calls++; throw new RequestError("offline", true); });
  await f.queue.save(link);
  await f.queue.flush();
  await f.queue.flush();
  assert.equal(calls, 1);
  const stored = (await f.queue.state()).queue[0];
  assert.equal(stored.status, "pending");
  assert.equal(stored.attempts, 1);
  f.advance(60_000);
  const restarted = new LinkQueue({ storage: f.storage, now: () => stored.nextAttempt, client: { submit: async () => ({ jobId: "resumed" }) } });
  await restarted.flush();
  assert.equal((await restarted.state()).history[0].jobId, "resumed");
});

test("permanent failures wait for an explicit retry", async () => {
  let fail = true, calls = 0;
  const f = fixture({ settings }, async () => {
    calls++;
    if (fail) throw new RequestError("invalid config");
    return { jobId: "fixed" };
  });
  await f.queue.save(link);
  await f.queue.flush();
  f.advance(3_600_000);
  await f.queue.flush();
  assert.equal(calls, 1);
  assert.equal((await f.queue.state()).queue[0].status, "failed");
  fail = false;
  await f.queue.retry();
  await f.queue.flush();
  assert.equal((await f.queue.state()).history[0].jobId, "fixed");
});

test("saving a second link stays responsive during a slow submission; flushes do not overlap", async () => {
  let finish;
  const waiting = new Promise((resolve) => { finish = resolve; });
  let calls = 0;
  const f = fixture({ settings }, async () => { calls++; await waiting; return { jobId: `job-${calls}` }; });
  const first = await f.queue.save(link);
  const flush = f.queue.flush();
  await f.queue.exclusive(async () => {});
  await f.queue.save(`${link}2`);
  assert.equal((await f.queue.state()).queue.length, 2);
  await assert.rejects(f.queue.remove(first.id), /being sent/);
  const concurrent = f.queue.flush();
  finish();
  await Promise.all([flush, concurrent]);
  assert.equal(calls, 2);
  assert.equal((await f.queue.state()).history.length, 2);
});

test("prevents redirecting pending links to another server, and clears identity/history after a safe switch", async () => {
  const f = fixture({ settings, session: { refreshToken: "old" }, history: [{ url: `${link}9` }] });
  const item = await f.queue.save(link);
  await assert.rejects(f.queue.configure({ ...settings, apiUrl: "https://other.example.com" }), /pending links/);
  await f.queue.remove(item.id);
  await f.queue.configure({ ...settings, apiUrl: "https://other.example.com" });
  assert.deepEqual(await f.storage.get(["session", "history"]), {});
});

test("a batch is bounded and a crash after server acceptance can retry the exact same URL", async () => {
  const seen = [];
  const f = fixture({ settings }, async (_settings, url) => { seen.push(url); return { jobId: "server-job", duplicate: seen.length > 1 }; });
  for (let i = 0; i < 4; i++) await f.queue.save(`${link}${i}`);
  await f.queue.flush();
  assert.equal(seen.length, 3);
  assert.equal((await f.queue.state()).queue.length, 1);
  // The unconfirmed local item remains durable; the API's unique constraint handles replay.
  await f.queue.flush();
  assert.equal(seen.length, 4);
  assert.equal((await f.queue.state()).queue.length, 0);
});

test("bounds local pending storage and confirmed history", async () => {
  const f = fixture();
  for (let i = 0; i < 100; i++) await f.queue.save(`${link}${i}`);
  await assert.rejects(f.queue.save(`${link}overflow`), /full/);
  for (let i = 0; i < 8; i++) await f.queue.flush();
  assert.equal((await f.queue.state()).history.length, 20);
});
