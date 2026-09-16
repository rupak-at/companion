const permanentlyInvalidCodes = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

type RegisteredDevice = {
  id: string;
  token: string;
};

/**
 * Return invalid stale tokens while preserving the first (most recently
 * registered) device. Firebase can reject a current token before Android has
 * delivered its replacement, so deleting that row would leave CAPTCHA alerts
 * with no registered destination.
 */
export function staleInvalidTokenValues(
  devicesNewestFirst: RegisteredDevice[],
  responseCodes: Array<string | undefined>,
): string[] {
  const currentDeviceId = devicesNewestFirst[0]?.id;
  return devicesNewestFirst
    .filter((device, index) => (
      device.id !== currentDeviceId && permanentlyInvalidCodes.has(responseCodes[index] ?? "")
    ))
    .map((device) => device.token);
}
