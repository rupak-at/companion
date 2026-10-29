import test from "node:test";
import assert from "node:assert/strict";
import { connectionSettings, permissionOrigins, socialLink } from "../core.js";
import { settings } from "./helpers.js";

test("accepts social share hosts, trims fragments, and preserves meaningful query parameters", () => {
  for (const [url, provider] of [
    ["https://vm.tiktok.com/abc", "TikTok"], ["https://www.instagram.com/reel/abc/?id=1#fragment", "Instagram"],
    ["https://m.facebook.com/watch/?v=123", "Facebook"], ["https://fb.watch/abc/", "Facebook"],
  ]) {
    const result = socialLink(` ${url} `);
    assert.equal(result.provider, provider);
    assert.equal(result.url, url.split("#")[0]);
  }
});

test("rejects lookalike domains, unsafe schemes, credentials, ports, and site homepages", () => {
  for (const url of ["https://instagram.com.evil.test/reel/1", "https://eviltiktok.com/a", "javascript:alert(1)",
    "http://tiktok.com/a", "https://user:pass@tiktok.com/a", "https://tiktok.com:8080/a", "https://instagram.com/", "not a URL"]) {
    assert.throws(() => socialLink(url), undefined, url);
  }
});

test("normalizes settings and grants only configured hosts, including local development", () => {
  const parsed = connectionSettings({ ...settings, apiUrl: " http://localhost:8080/ " });
  assert.equal(parsed.apiUrl, "http://localhost:8080");
  assert.deepEqual(permissionOrigins(parsed), ["http://localhost/*", "https://project.supabase.co/*"]);
  assert.throws(() => connectionSettings({ ...settings, apiUrl: "http://192.168.1.10:8080" }));
  assert.throws(() => connectionSettings({ ...settings, apiUrl: "https://user:pass@example.com" }));
  assert.throws(() => connectionSettings({ ...settings, supabaseUrl: "https://project.supabase.co/?key=secret" }));
});

test("rejects privileged Supabase keys and accepts a legacy anon client key", () => {
  const jwt = (role) => `header.${Buffer.from(JSON.stringify({ role })).toString("base64url")}.signature`;
  assert.equal(connectionSettings({ ...settings, publicKey: jwt("anon") }).publicKey, jwt("anon"));
  for (const publicKey of [jwt("service_role"), jwt("authenticated"), "sb_secret_do_not_ship", "invalid", "a.@@@.b"]) {
    assert.throws(() => connectionSettings({ ...settings, publicKey }));
  }
});
