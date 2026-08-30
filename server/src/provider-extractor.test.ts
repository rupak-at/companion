import { describe, expect, it } from "vitest";
import { extractorErrorCode } from "./provider-extractor.js";

describe("provider extractor errors", () => {
  it("recognizes human-verification failures", () => {
    expect(extractorErrorCode("ERROR: Sign in to confirm you're not a bot")).toBe("HUMAN_VERIFICATION_REQUIRED");
    expect(extractorErrorCode("A CAPTCHA challenge is required")).toBe("HUMAN_VERIFICATION_REQUIRED");
  });

  it("keeps ordinary provider errors distinct", () => {
    expect(extractorErrorCode("Video unavailable")).toBe("EXTRACTOR_FAILED");
  });
});
