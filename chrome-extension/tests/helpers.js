export function memory(initial = {}) {
  const data = structuredClone(initial);
  return {
    async get(keys) {
      if (typeof keys === "string") keys = [keys];
      return structuredClone(Object.fromEntries(keys.filter((key) => key in data).map((key) => [key, data[key]])));
    },
    async set(values) { Object.assign(data, structuredClone(values)); },
    async remove(keys) { for (const key of keys) delete data[key]; },
  };
}

export const settings = {
  apiUrl: "https://companion.example.com", supabaseUrl: "https://project.supabase.co", publicKey: "sb_publishable_test_client_key",
};
export const link = "https://www.tiktok.com/@example/video/123";
export const response = (status, data) => ({ ok: status >= 200 && status < 300, status, async json() { return data; } });
