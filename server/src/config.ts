import { z } from "zod";

export const config = z.object({
  DATABASE_URL: z.string().url(),
  DIRECT_URL: z.string().url(),
  SUPABASE_URL: z.string().url(),
  SUPABASE_ANON_KEY: z.string().min(20),
  REDIS_URL: z.string().url(),
  DOWNLOAD_DIR: z.string().default("/tmp/ambient-downloads"),
  PUBLIC_BASE_URL: z.string().url(),
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
}).parse(process.env);
