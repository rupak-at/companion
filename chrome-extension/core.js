export function socialLink(raw) {
  let url;
  try { url = new URL(raw.trim()); } catch { throw new Error("Paste a complete TikTok, Instagram, or Facebook link."); }
  if (url.protocol !== "https:" || url.username || url.password || url.port || url.href.length > 2048) {
    throw new Error("Use an HTTPS social link without a username, password, or custom port.");
  }
  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  const matches = (domain) => host === domain || host.endsWith(`.${domain}`);
  const provider = matches("tiktok.com") ? "TikTok" : matches("instagram.com") ? "Instagram"
    : matches("facebook.com") || matches("fb.watch") ? "Facebook" : null;
  if (!provider) throw new Error("Only TikTok, Instagram, and Facebook links are supported.");
  if (url.pathname === "/" && !url.search) throw new Error("Open a specific post or video, or paste its copied link.");
  url.hostname = host;
  url.hash = "";
  return { url: url.href, provider };
}

function serverUrl(raw, label) {
  let url;
  try { url = new URL(raw.trim()); } catch { throw new Error(`Enter a complete ${label} URL.`); }
  const local = ["localhost", "127.0.0.1"].includes(url.hostname);
  if ((url.protocol !== "https:" && !(local && url.protocol === "http:")) || url.username || url.password || url.search || url.hash) {
    throw new Error(`${label} must use HTTPS (HTTP is allowed on localhost), with no credentials, query, or fragment.`);
  }
  return url.href.replace(/\/+$/, "");
}

export function connectionSettings(raw) {
  const apiUrl = serverUrl(raw.apiUrl ?? "", "Companion server");
  const supabaseUrl = serverUrl(raw.supabaseUrl ?? "", "Supabase project");
  const publicKey = (raw.publicKey ?? "").trim();
  let isPublic = publicKey.startsWith("sb_publishable_") && publicKey.length > 15;
  if (publicKey.split(".").length === 3) {
    try {
      const segment = publicKey.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
      const payload = JSON.parse(atob(segment.padEnd(Math.ceil(segment.length / 4) * 4, "=")));
      isPublic = payload.role === "anon";
    } catch { isPublic = false; }
  }
  if (!isPublic) throw new Error("Use the Supabase publishable or anon key. Secret and service-role keys are not accepted.");
  return { apiUrl, supabaseUrl, publicKey };
}

export function permissionOrigins(settings) {
  return [...new Set([settings.apiUrl, settings.supabaseUrl].map((value) => {
    const url = new URL(value);
    return `${url.protocol}//${url.hostname}/*`;
  }))];
}

export class RequestError extends Error {
  constructor(message, retryable = false) {
    super(message);
    this.retryable = retryable;
  }
}

export function retryDelay(attempts) {
  return Math.min(60 * 60_000, 60_000 * 2 ** Math.min(attempts - 1, 6));
}
