import { describe, expect, it } from "vitest";
import { classifyUrl, isPrivateAddress } from "./url-security.js";

describe("URL security", () => {
  it("does not trust lookalike provider domains", () => {
    expect(classifyUrl(new URL("https://tiktok.com.attacker.example/v"))).toBeNull();
    expect(classifyUrl(new URL("https://vm.tiktok.com/v"))).toBe("TIKTOK");
    expect(classifyUrl(new URL("https://www.facebook.com/reel/123"))).toBe("FACEBOOK");
    expect(classifyUrl(new URL("https://fb.watch/abc"))).toBe("FACEBOOK");
    expect(classifyUrl(new URL("https://facebook.com.attacker.example/v"))).toBeNull();
  });

  it("blocks private, loopback, link-local, mapped, and multicast addresses", () => {
    for (const ip of ["127.0.0.1", "10.1.2.3", "172.20.1.1", "192.168.1.1", "169.254.169.254", "::1", "fd00::1", "::ffff:127.0.0.1", "224.0.0.1"]) {
      expect(isPrivateAddress(ip), ip).toBe(true);
    }
    expect(isPrivateAddress("8.8.8.8")).toBe(false);
  });
});
