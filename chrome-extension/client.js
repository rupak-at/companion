import { RequestError } from "./core.js";

const expiredSessionCodes = new Set(["refresh_token_not_found", "refresh_token_already_used", "session_expired", "session_not_found"]);

export class CompanionClient {
  constructor({ storage, fetcher = fetch, now = Date.now, hasHostAccess = async () => true }) {
    this.storage = storage;
    this.fetcher = fetcher;
    this.now = now;
    this.hasHostAccess = hasHostAccess;
  }

  async request(url, body, headers = {}, service = "server") {
    let origin;
    try { origin = new URL(url).origin; } catch { origin = "the configured address"; }
    if (!(await this.hasHostAccess(url))) {
      throw new RequestError(`Chrome has not granted Companion access to ${origin}. Allow this host in chrome://extensions, then choose Retry now.`);
    }
    let response;
    try {
      response = await this.fetcher(url, {
        method: "POST", headers: { "Content-Type": "application/json", ...headers },
        body: JSON.stringify(body), signal: AbortSignal.timeout(12_000),
        credentials: "omit", redirect: "error", cache: "no-store",
      });
    } catch (error) {
      let host = "the configured address";
      try { host = new URL(url).origin; } catch { /* Use the safe generic label. */ }
      const timedOut = error?.name === "TimeoutError";
      throw new RequestError(
        timedOut ? `Connection to ${host} timed out. Check that ${service} is online and the URL is correct.`
          : `Could not connect to ${service} at ${host} (${error?.name || "network error"}: ${error?.message || "request failed"}). Check the browser's network/extension access for this host.`,
        true,
      );
    }
    const data = await response.json().catch(() => ({}));
    return { ok: response.ok, status: response.status, data: data ?? {} };
  }

  error(response, auth = false, requestUrl) {
    const { status, data } = response;
    let host = "the configured address";
    try { host = new URL(requestUrl).origin; } catch { /* Keep the generic label. */ }
    const retryable = status === 408 || status === 429 || status >= 500;
    let message = `${auth ? "Supabase" : "Companion API"} at ${host} returned HTTP ${status}. Check its URL and service status, then retry.`;
    if (retryable) message = `${auth ? "Supabase" : "Companion API"} at ${host} is temporarily unavailable (HTTP ${status}). Your link will retry automatically.`;
    if (data.error === "UNSUPPORTED_PROVIDER" || data.error === "INVALID_URL") message = "The server rejected this link. Copy a direct link to the post or video.";
    if (status === 401 || status === 403) message = "Sign-in was rejected. Check the Supabase project and public key in Settings, then retry.";
    if (auth && !retryable) message = `Could not sign in (HTTP ${status}). Enable anonymous sign-ins in Supabase and check the public key. Projects requiring sign-in CAPTCHA are not supported.`;
    return new RequestError(message, retryable);
  }

  async token(settings, rejectedToken) {
    const { session } = await this.storage.get("session");
    const current = session?.supabaseUrl === settings.supabaseUrl ? session : null;
    if (current?.accessToken && current.expiresAt > this.now() + 60_000 && current.accessToken !== rejectedToken) return current.accessToken;
    const headers = { apikey: settings.publicKey, Authorization: `Bearer ${settings.publicKey}` };
    let response;
    if (current?.refreshToken) {
      const url = `${settings.supabaseUrl}/auth/v1/token?grant_type=refresh_token`;
      response = await this.request(url, { refresh_token: current.refreshToken }, headers, "Supabase");
      if (!response.ok && !expiredSessionCodes.has(response.data.error_code)) throw this.error(response, true, url);
    }
    if (!response?.ok) {
      const url = `${settings.supabaseUrl}/auth/v1/signup`;
      response = await this.request(url, {}, headers, "Supabase");
      if (!response.ok) throw this.error(response, true, url);
    }
    const data = response.data;
    if (typeof data.access_token !== "string" || typeof data.refresh_token !== "string") {
      throw new RequestError("Supabase returned no session. Enable anonymous sign-ins in your project.");
    }
    await this.storage.set({ session: {
      supabaseUrl: settings.supabaseUrl, accessToken: data.access_token, refreshToken: data.refresh_token,
      expiresAt: this.now() + (Number(data.expires_in) || 3600) * 1000,
    } });
    return data.access_token;
  }

  async submit(settings, url) {
    let token = await this.token(settings);
    const endpoint = `${settings.apiUrl}/api/v1/downloads`;
    const send = () => this.request(endpoint, { url, format: "video" }, { Authorization: `Bearer ${token}` }, "Companion API");
    let response = await send();
    if (response.status === 401) {
      token = await this.token(settings, token);
      response = await send();
    }
    if (!response.ok) throw this.error(response, false, endpoint);
    if (typeof response.data.jobId !== "string" || !response.data.jobId) {
      throw new RequestError("The server returned no job ID. Check the server URL, then retry.");
    }
    return { jobId: response.data.jobId, duplicate: response.data.duplicate === true };
  }
}
