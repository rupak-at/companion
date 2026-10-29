import { connectionSettings, permissionOrigins } from "./core.js";
import { notice, send } from "./ui.js";

const $ = (id) => document.getElementById(id);
$("settings-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("save-settings").disabled = true;
  try {
    const settings = connectionSettings({ apiUrl: $("api-url").value, supabaseUrl: $("supabase-url").value, publicKey: $("public-key").value });
    // Request optional hosts directly from this user gesture, before messaging the worker.
    const allowed = await chrome.permissions.request({ origins: permissionOrigins(settings) });
    if (!allowed) throw new Error("Allow access to your Companion server and Supabase project to send links.");
    await send({ type: "CONFIGURE", settings });
    notice($("notice"), "Connection settings saved. Save a link from the toolbar to try them. Pending links will retry now.");
  } catch (error) { notice($("notice"), error.message, true); }
  finally { $("save-settings").disabled = false; }
});

try {
  const { settings } = await send({ type: "SETTINGS" });
  if (settings) {
    $("api-url").value = settings.apiUrl;
    $("supabase-url").value = settings.supabaseUrl;
    $("public-key").value = settings.publicKey;
  }
} catch (error) { notice($("notice"), error.message, true); }
