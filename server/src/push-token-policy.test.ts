import { describe, expect, it } from "vitest";
import { staleInvalidTokenValues } from "./push-token-policy.js";

describe("staleInvalidTokenValues", () => {
  it("preserves the most recently registered token when Firebase rejects it", () => {
    expect(staleInvalidTokenValues(
      [{ id: "current", token: "current-token" }],
      ["messaging/registration-token-not-registered"],
    )).toEqual([]);
  });

  it("removes permanently invalid older tokens but keeps the current token", () => {
    expect(staleInvalidTokenValues(
      [
        { id: "current", token: "current-token" },
        { id: "stale", token: "stale-token" },
      ],
      ["messaging/invalid-registration-token", "messaging/registration-token-not-registered"],
    )).toEqual(["stale-token"]);
  });

  it("does not remove stale tokens for transient Firebase errors", () => {
    expect(staleInvalidTokenValues(
      [
        { id: "current", token: "current-token" },
        { id: "stale", token: "stale-token" },
      ],
      [undefined, "messaging/internal-error"],
    )).toEqual([]);
  });
});
