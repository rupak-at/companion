import { describe, expect, it } from "vitest";
import { parseJobId } from "./request-validation.js";

describe("request validation", () => {
  it("accepts UUID job IDs", () => {
    expect(parseJobId("52996eb8-4a58-474f-8e6c-e75aba10c230")).toBe("52996eb8-4a58-474f-8e6c-e75aba10c230");
  });

  it("rejects values that Prisma cannot query as UUIDs", () => {
    expect(parseJobId("nonexistent")).toBeNull();
    expect(parseJobId("../other-file")).toBeNull();
  });
});
