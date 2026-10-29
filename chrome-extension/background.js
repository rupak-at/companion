import { CompanionClient } from "./client.js";
import { LinkQueue } from "./queue.js";
import { permissionOrigins } from "./core.js";

const storage = chrome.storage.local;
const alarmName = "companion-send-links";
const menuId = "save-to-companion";
const patterns = ["https://*.tiktok.com/*", "https://*.instagram.com/*", "https://*.facebook.com/*", "https://*.fb.watch/*"];

async function badge() {
  const { settings, queue = [], history = [] } = await storage.get(["settings", "queue", "history"]);
  const failed = queue.some((item) => item.status === "failed");
  const needsSetup = !settings && queue.length > 0;
  const text = failed || needsSetup ? "!" : queue.length ? String(queue.length) : history.length ? "✓" : "";
  await chrome.action.setBadgeBackgroundColor({ color: failed || needsSetup ? "#9d432a" : queue.length ? "#8a6727" : "#466046" });
  await chrome.action.setBadgeText({ text });
  const title = needsSetup ? "Open Companion Settings to connect your server"
    : failed ? "A link needs attention. Open Companion to retry."
      : queue.length ? `${queue.length} link(s) waiting to send` : history.length ? "Link saved to Companion" : "Save to Companion";
  await chrome.action.setTitle({ title });
}

const queue = new LinkQueue({ storage, client: new CompanionClient({ storage }), changed: badge });
// Restrict stored sessions to extension pages and the service worker.
const ready = storage.setAccessLevel({ accessLevel: "TRUSTED_CONTEXTS" });

async function schedule() {
  await ready;
  const { settings, queue: items } = await queue.state();
  if (items.some((item) => item.status === "pending") && settings) {
    if (!(await chrome.alarms.get(alarmName))) await chrome.alarms.create(alarmName, { periodInMinutes: 1 });
  } else await chrome.alarms.clear(alarmName);
}

async function flush() {
  await ready;
  await schedule(); // Persist a wake-up before attempting network work.
  await queue.flush();
  await schedule();
}

function backgroundFlush() { void flush().catch(() => {}); }

async function install() {
  await chrome.contextMenus.removeAll();
  chrome.contextMenus.create({ id: menuId, title: "Save to Companion", contexts: ["link"], targetUrlPatterns: patterns });
  chrome.contextMenus.create({ id: `${menuId}-page`, title: "Save this page to Companion", contexts: ["page"], documentUrlPatterns: patterns });
  await badge();
  backgroundFlush();
}

chrome.runtime.onInstalled.addListener(() => { void install(); });
chrome.runtime.onStartup.addListener(() => { backgroundFlush(); void badge(); });
chrome.alarms.onAlarm.addListener((alarm) => { if (alarm.name === alarmName) backgroundFlush(); });
chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (![menuId, `${menuId}-page`].includes(info.menuItemId)) return;
  void (async () => {
    try {
      await ready;
      await queue.save(info.linkUrl || info.pageUrl || tab?.url || "");
      backgroundFlush();
    } catch (error) {
      await chrome.action.setBadgeText({ text: "!" });
      await chrome.action.setBadgeBackgroundColor({ color: "#9d432a" });
      await chrome.action.setTitle({ title: error.message });
    }
  })();
});

chrome.runtime.onMessage.addListener((message, sender, respond) => {
  const pages = [chrome.runtime.getURL("popup.html"), chrome.runtime.getURL("options.html")];
  if (sender.id !== chrome.runtime.id || !pages.includes(sender.url)) return false;
  void (async () => {
    await ready;
    switch (message.type) {
      case "STATE": {
        const state = await queue.state();
        // The popup doesn't need configuration keys or session credentials.
        return { configured: Boolean(state.settings), queue: state.queue, history: state.history };
      }
      case "SETTINGS": return { settings: (await queue.state()).settings };
      case "SAVE": {
        const item = await queue.save(message.url);
        backgroundFlush();
        return { item };
      }
      case "CONFIGURE": {
        if (sender.url !== chrome.runtime.getURL("options.html")) throw new Error("Open Settings to change your connection.");
        const origins = permissionOrigins(message.settings);
        if (!(await chrome.permissions.contains({ origins }))) throw new Error("Allow access to both servers before saving settings.");
        await queue.configure(message.settings);
        await queue.retry();
        backgroundFlush();
        return {};
      }
      case "RETRY": await queue.retry(); backgroundFlush(); return {};
      case "REMOVE": await queue.remove(message.id); await schedule(); return {};
      default: throw new Error("Unknown Companion action.");
    }
  })().then((data) => respond({ ok: true, ...data }), (error) => respond({ ok: false, error: error.message || "Companion could not complete this action." }));
  return true;
});
