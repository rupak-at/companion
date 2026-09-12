import "dotenv/config";
import { z } from "zod";

export const config = z.object({
  DATABASE_URL: z.string().url(),
  DIRECT_URL: z.string().url(),
  SUPABASE_URL: z.string().url(),
  SUPABASE_ANON_KEY: z.string().min(20),
  REDIS_URL: z.string().url(),
  DOWNLOAD_DIR: z.string().default("/tmp/ambient-downloads"),
  PUBLIC_BASE_URL: z.string().url(),
  LOCAL_RUNNER_TOKEN: z.string().min(32).optional(),
  FIREBASE_PROJECT_ID: z.string().min(1).optional(),
  FIREBASE_CLIENT_EMAIL: z.string().email().optional(),
  FIREBASE_PRIVATE_KEY: z.string().min(1).optional(),
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
}).parse(process.env);
