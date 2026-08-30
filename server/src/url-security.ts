import dns from "node:dns/promises";
import { isIP } from "node:net";

const allowedHosts = ["tiktok.com", "instagram.com"];
const mediaExtensions = new Set(["mp4", "webm", "mov", "jpg", "jpeg", "png", "webp"]);

export type Provider = "TIKTOK" | "INSTAGRAM" | "DIRECT_VIDEO" | "DIRECT_IMAGE";

export function classifyUrl(url: URL): Provider | null {
  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  const extension = url.pathname.split(".").pop()?.toLowerCase() ?? "";
  if (hostMatches(host, "tiktok.com")) return "TIKTOK";
  if (hostMatches(host, "instagram.com")) return "INSTAGRAM";
  if (["mp4", "webm", "mov"].includes(extension)) return "DIRECT_VIDEO";
  if (["jpg", "jpeg", "png", "webp"].includes(extension)) return "DIRECT_IMAGE";
  return null;
}

export async function validatePublicUrl(raw: string): Promise<URL> {
  const url = new URL(raw);
  if (url.protocol !== "https:" || url.username || url.password || url.port) throw new Error("INVALID_URL");
  if (!classifyUrl(url)) throw new Error("UNSUPPORTED_PROVIDER");
  await assertPublicHost(url.hostname);
  return url;
}

export async function assertSafeRedirect(from: URL, destination: string): Promise<URL> {
  const next = new URL(destination, from);
  if (next.protocol !== "https:" || next.username || next.password || next.port) throw new Error("UNSAFE_REDIRECT");
  const initialProvider = classifyUrl(from);
  const nextProvider = classifyUrl(next);
  const directMedia = mediaExtensions.has(next.pathname.split(".").pop()?.toLowerCase() ?? "");
  if (!nextProvider || (nextProvider !== initialProvider && !directMedia)) throw new Error("UNSAFE_REDIRECT");
  await assertPublicHost(next.hostname);
  return next;
}

async function assertPublicHost(hostname: string): Promise<void> {
  if (hostname === "localhost" || hostname.endsWith(".local") || hostname.endsWith(".internal")) throw new Error("PRIVATE_HOST");
  const addresses = isIP(hostname) ? [{ address: hostname }] : await dns.lookup(hostname, { all: true, verbatim: true });
  if (addresses.length === 0 || addresses.some(({ address }) => isPrivateAddress(address))) throw new Error("PRIVATE_HOST");
}

function hostMatches(host: string, allowed: string): boolean {
  return host === allowed || host.endsWith(`.${allowed}`);
}

export function isPrivateAddress(address: string): boolean {
  const value = address.toLowerCase();
  if (value === "::1" || value === "::" || value.startsWith("fe80:") || value.startsWith("fc") || value.startsWith("fd")) return true;
  const mapped = value.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/)?.[1];
  const ipv4 = mapped ?? (isIP(value) === 4 ? value : null);
  if (!ipv4) return false;
  const [a, b] = ipv4.split(".").map(Number);
  return a === 0 || a === 10 || a === 127 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || a >= 224;
}
