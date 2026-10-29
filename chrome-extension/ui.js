export async function send(message) {
  const response = await chrome.runtime.sendMessage(message);
  if (!response?.ok) throw new Error(response?.error || "Companion did not respond. Reload the extension and try again.");
  return response;
}

export function notice(element, text, error = false) {
  element.textContent = text;
  element.classList.toggle("error", error);
}
