import { z } from "zod";

const jobIdSchema = z.string().uuid();

export function parseJobId(value: string): string | null {
  const result = jobIdSchema.safeParse(value);
  return result.success ? result.data : null;
}
