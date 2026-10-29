import { socialLink } from "./core.js";
import { notice, send } from "./ui.js";

const $ = (id) => document.getElementById(id);
const openSettings = () => chrome.runtime.openOptionsPage();
$("settings").addEventListener("click", openSettings);
$("connect").addEventListener("click", openSettings);

function row(item, pending) {
  const li = document.createElement("li");
  const content = document.createElement("div");
  const link = document.createElement("a");
  link.href = socialLink(item.url).url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = item.provider;
  link.title = item.url;
  const path = document.createElement("span");
  const parsed = new URL(item.url);
  path.className = "link-path";
  path.textContent = parsed.hostname + parsed.pathname + parsed.search;
  const status = document.createElement("span");
  status.className = `link-status${item.status === "failed" ? " error" : ""}`;
  status.textContent = pending ? item.message || "Waiting to send" : item.duplicate ? "Already saved to your server" : "Saved to your server";
  content.append(link, path, status);
  li.append(content);
  if (pending) {
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "text-button remove";
    remove.textContent = "Remove";
    remove.setAttribute("aria-label", `Remove pending ${item.provider} link`);
    remove.addEventListener("click", async () => {
      try { await send({ type: "REMOVE", id: item.id }); await render(); }
      catch (error) { notice($("notice"), error.message, true); }
    });
    li.append(remove);
  }
  return li;
}

async function render() {
  const state = await send({ type: "STATE" });
  $("setup").hidden = state.configured;
  $("retry").hidden = !state.configured || !state.queue.length;
  $("queue-summary").textContent = state.queue.length ? `${state.queue.length} waiting on this laptop` : state.history.length ? "Recent saves from this browser" : "";
  $("pending").replaceChildren(...state.queue.map((item) => row(item, true)));
  $("recent").replaceChildren(...state.history.slice(0, 5).map((item) => row(item, false)));
  $("empty").hidden = Boolean(state.queue.length || state.history.length);
}

$("save-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("save").disabled = true;
  try {
    const { item } = await send({ type: "SAVE", url: $("url").value });
    const state = await send({ type: "STATE" });
    const text = item.status === "saved" ? "Already saved to Companion."
      : item.alreadySaved ? "This link is already in your local queue."
        : state.configured ? "Kept on this laptop. Sending to Companion…"
          : "Kept on this laptop. Connect your server to send it.";
    notice($("notice"), text);
    await render();
  } catch (error) { notice($("notice"), error.message, true); }
  finally { $("save").disabled = false; }
});

$("retry").addEventListener("click", async () => {
  try {
    await send({ type: "RETRY" });
    notice($("notice"), "Retrying your pending links…");
    await render();
  } catch (error) { notice($("notice"), error.message, true); }
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== "local") return;
  if (changes.history?.newValue?.[0]?.id !== changes.history?.oldValue?.[0]?.id && changes.history?.newValue?.[0]) {
    notice($("notice"), changes.history.newValue[0].duplicate ? "Already saved to Companion ✓" : "Link saved to Companion ✓");
  }
  if (changes.queue || changes.history || changes.settings) void render().catch((error) => notice($("notice"), error.message, true));
});

try {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  try { $("url").value = socialLink(tab?.url || "").url; } catch { /* Leave space for a pasted link. */ }
  await render();
} catch (error) { notice($("notice"), error.message, true); }
