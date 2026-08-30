import { createClient } from "@supabase/supabase-js";
import { config } from "./config.js";

const supabase = createClient(config.SUPABASE_URL, config.SUPABASE_ANON_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
});

export async function authenticate(header: string | undefined): Promise<string | null> {
  const match = header?.match(/^Bearer (.+)$/i);
  if (!match) return null;
  const { data, error } = await supabase.auth.getUser(match[1]);
  return error ? null : data.user?.id ?? null;
}
