import { connectionSettings, retryDelay, socialLink } from "./core.js";

// Only the service worker writes this state. Local mutations are serialized; network
// requests run outside the lock so another link can be saved while offline.
export class LinkQueue {
  constructor({ storage, client, now = Date.now, id = () => crypto.randomUUID(), changed = async () => {} }) {
    Object.assign(this, { storage, client, now, id, changed });
    this.lock = Promise.resolve();
    this.flushing = null;
    this.activeId = null;
    this.configuring = false;
  }

  exclusive(action) {
    const result = this.lock.then(action);
    this.lock = result.catch(() => {});
    return result;
  }

  async state() {
    const { settings = null, queue = [], history = [] } = await this.storage.get(["settings", "queue", "history"]);
    return { settings, queue, history };
  }

  async save(raw) {
    const link = socialLink(raw);
    return this.exclusive(async () => {
      const state = await this.state();
      const existing = [...state.queue, ...state.history].find((item) => item.url === link.url);
      if (existing) return { ...existing, alreadySaved: true };
      if (state.queue.length >= 100) throw new Error("Your local queue is full. Send or remove pending links before saving more.");
      const item = { ...link, id: this.id(), createdAt: this.now(), status: "pending", attempts: 0, nextAttempt: 0 };
      await this.storage.set({ queue: [...state.queue, item] });
      await this.changed();
      return item;
    });
  }

  async configure(raw) {
    const settings = connectionSettings(raw);
    await this.exclusive(async () => {
      const state = await this.state();
      const changedServer = state.settings && (state.settings.apiUrl !== settings.apiUrl || state.settings.supabaseUrl !== settings.supabaseUrl);
      if (this.flushing || this.configuring) throw new Error("A link is being sent. Try saving settings again in a moment.");
      if (changedServer && state.queue.length) throw new Error("Send or remove your pending links before changing servers.");
      this.configuring = true;
      try {
        await this.storage.set({ settings });
        if (changedServer) await this.storage.remove(["session", "history"]);
        await this.changed();
      } finally { this.configuring = false; }
    });
    return settings;
  }

  async remove(id) {
    return this.exclusive(async () => {
      if (id === this.activeId) throw new Error("This link is being sent. Try again in a moment.");
      const state = await this.state();
      await this.storage.set({ queue: state.queue.filter((item) => item.id !== id) });
      await this.changed();
    });
  }

  async retry() {
    await this.exclusive(async () => {
      const state = await this.state();
      await this.storage.set({ queue: state.queue.map((item) => ({ ...item, status: "pending", nextAttempt: 0, message: "" })) });
      await this.changed();
    });
  }

  flush() {
    if (this.configuring) return Promise.resolve();
    if (!this.flushing) this.flushing = this.sendBatch().finally(() => { this.flushing = null; this.activeId = null; });
    return this.flushing;
  }

  async sendBatch() {
    for (let count = 0; count < 3; count++) {
      const selected = await this.exclusive(async () => {
        const { settings, queue } = await this.state();
        const item = queue.find((entry) => entry.status === "pending" && entry.nextAttempt <= this.now());
        if (!settings || !item) return null;
        this.activeId = item.id;
        return { settings, item };
      });
      if (!selected) break;
      let result, failure;
      try { result = await this.client.submit(selected.settings, selected.item.url); }
      catch (error) { failure = error; }
      await this.exclusive(async () => {
        const { queue, history } = await this.state();
        const item = queue.find((entry) => entry.id === selected.item.id);
        if (!item) return;
        if (failure) {
          item.attempts += 1;
          item.status = failure.retryable ? "pending" : "failed";
          item.message = failure.message || "Could not send the link. Retry from the popup.";
          item.nextAttempt = this.now() + retryDelay(item.attempts);
          await this.storage.set({ queue });
        } else {
          await this.storage.set({
            queue: queue.filter((entry) => entry.id !== item.id),
            history: [{ ...item, ...result, status: "saved", savedAt: this.now(), message: "" }, ...history].slice(0, 20),
          });
        }
        this.activeId = null;
        await this.changed();
      });
      // A network/auth outage affects the whole queue; don't hammer the server.
      if (failure) break;
    }
  }
}
