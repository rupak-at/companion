import { timingSafeEqual } from "node:crypto";

export function isRunnerAuthorized(
  header: string | undefined,
  expectedToken: string | undefined
): boolean {
  const match = header?.match(/^Bearer (.+)$/i);

  const supplied = match?.[1] ?? "";

  // console.log("[RUNNER AUTH DIAG]", {
  //   expectedConfigured: Boolean(expectedToken),
  //   expectedLength: expectedToken?.length,
  //   suppliedLength: supplied.length,
  //   expectedHasWhitespace: expectedToken !== expectedToken?.trim(),
  //   suppliedHasWhitespace: supplied !== supplied.trim(),
  //   lengthMatches: supplied.length === expectedToken?.length,
  // });

  if (!expectedToken || !match) return false;

  const suppliedBuffer = Buffer.from(supplied);
  const expectedBuffer = Buffer.from(expectedToken);

  return suppliedBuffer.length === expectedBuffer.length &&
    timingSafeEqual(suppliedBuffer, expectedBuffer);
}