import { cert, getApps, initializeApp, getApp } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import { prisma } from "./services.js";
import { config } from "./config.js";
import { staleInvalidTokenValues } from "./push-token-policy.js";

function firebaseApp() {
  if (!config.FIREBASE_PROJECT_ID || !config.FIREBASE_CLIENT_EMAIL || !config.FIREBASE_PRIVATE_KEY) return null;
  return getApps()[0] ?? initializeApp({
    credential: cert({
      projectId: config.FIREBASE_PROJECT_ID,
      clientEmail: config.FIREBASE_CLIENT_EMAIL,
      privateKey: config.FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n"),
    }),
  });
}

export async function notifyCaptchaRequired(userId: string, jobId: string | undefined, message: string): Promise<{ sent: number; reason?: string }> {
  const app = firebaseApp();
  if (!app) return { sent: 0, reason: "FCM_NOT_CONFIGURED" };
  const devices = await prisma.deviceToken.findMany({
    where: { userId },
    orderBy: { updatedAt: "desc" },
  });
  if (devices.length === 0) return { sent: 0, reason: "NO_REGISTERED_DEVICE" };
  const result = await getMessaging(app).sendEachForMulticast({
    tokens: devices.map((device) => device.token),
    data: { type: "CAPTCHA_REQUIRED", ...(jobId ? { jobId } : {}), title: "Verification required", body: message },
    notification: { title: "Verification required", body: message },
  });
  const staleInvalidTokens = staleInvalidTokenValues(
    devices,
    result.responses.map((response) => response.error?.code),
  );
  if (staleInvalidTokens.length > 0) {
    await prisma.deviceToken.deleteMany({ where: { token: { in: staleInvalidTokens } } });
  }
  return {
    sent: result.successCount,
    reason: result.failureCount > 0
      ? result.responses.filter((response) => response.error).map((response) => response.error?.code).join(",")
      : undefined,
  };
}
