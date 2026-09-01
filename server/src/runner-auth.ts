import { timingSafeEqual } from "node:crypto";

export function isRunnerAuthorized(header: string | undefined, expectedToken: string | undefined): boolean {
  if (!expectedToken) return false;
  const match = header?.match(/^Bearer (.+)$/i);
  if (!match) return false;
  const supplied = Buffer.from(match[1]);
  const expected = Buffer.from(expectedToken);
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}
