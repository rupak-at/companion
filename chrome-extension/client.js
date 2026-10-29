import { RequestError } from "./core.js";

const expiredSessionCodes = new Set(["refresh_token_not_found", "refresh_token_already_used", "session_expired", "session_not_found"]);

export class CompanionClient {
  constructor({ storage, fetcher = fetch, now = Date.now }) {
    this.storage = storage;
    this.fetcher = fetcher;
    this.now = now;
  }

  async request(url, body, headers = {}) {
    let response;
    try {
      response = await this.fetcher(url, {
        method: "POST", headers: { "Content-Type": "application/json", ...headers },
        body: JSON.stringify(body), signal: AbortSignal.timeout(12_000),
        credentials: "omit", redirect: "error", cache: "no-store",
      });
    } catch {
      throw new RequestError("Waiting for connection. Check that your server is running and site access is allowed in Settings.", true);
    }
    const data = await response.json().catch(() => ({}));
    return { ok: response.ok, status: response.status, data: data ?? {} };
  }

  error(response, auth = false) {
    const { status, data } = response;
    const retryable = status === 408 || status === 429 || status >= 500;
    let message = `Server rejected the link (HTTP ${status}). Check Settings, then retry.`;
    if (retryable) message = `Server temporarily unavailable (HTTP ${status}). Your link will retry automatically.`;
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
      response = await this.request(`${settings.supabaseUrl}/auth/v1/token?grant_type=refresh_token`, { refresh_token: current.refreshToken }, headers);
      if (!response.ok && !expiredSessionCodes.has(response.data.error_code)) throw this.error(response, true);
    }
    if (!response?.ok) response = await this.request(`${settings.supabaseUrl}/auth/v1/signup`, {}, headers);
    if (!response.ok) throw this.error(response, true);
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
    const send = () => this.request(`${settings.apiUrl}/api/v1/downloads`, { url, format: "video" }, { Authorization: `Bearer ${token}` });
    let response = await send();
    if (response.status === 401) {
      token = await this.token(settings, token);
      response = await send();
    }
    if (!response.ok) throw this.error(response);
    if (typeof response.data.jobId !== "string" || !response.data.jobId) {
      throw new RequestError("The server returned no job ID. Check the server URL, then retry.");
    }
    return { jobId: response.data.jobId, duplicate: response.data.duplicate === true };
  }
}
