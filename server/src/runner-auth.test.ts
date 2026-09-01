import { describe, expect, it } from "vitest";
import { isRunnerAuthorized } from "./runner-auth.js";

describe("runner authentication", () => {
  const token = "a-secure-local-runner-token-with-32-chars";

  it("accepts the configured bearer token", () => {
    expect(isRunnerAuthorized(`Bearer ${token}`, token)).toBe(true);
  });

  it("rejects missing configuration and incorrect tokens", () => {
    expect(isRunnerAuthorized(`Bearer ${token}`, undefined)).toBe(false);
    expect(isRunnerAuthorized("Bearer wrong", token)).toBe(false);
    expect(isRunnerAuthorized(undefined, token)).toBe(false);
  });
});
