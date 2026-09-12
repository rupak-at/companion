import { cert, getApps, initializeApp, getApp } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import { prisma } from "./services.js";
import { config } from "./config.js";

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

export async function notifyCaptchaRequired(userId: string, jobId: string, message: string): Promise<void> {
  const app = firebaseApp();
  if (!app) return;
  const devices = await prisma.deviceToken.findMany({ where: { userId } });
  if (devices.length === 0) return;
  const result = await getMessaging(app).sendEachForMulticast({
    tokens: devices.map((device) => device.token),
    data: { type: "CAPTCHA_REQUIRED", jobId, title: "Verification required", body: message },
    notification: { title: "Verification required", body: message },
  });
  const invalidTokens = devices.filter((_, index) => {
    const error = result.responses[index].error;
    return error?.code === "messaging/registration-token-not-registered" || error?.code === "messaging/invalid-registration-token";
  });
  if (invalidTokens.length > 0) {
    await prisma.deviceToken.deleteMany({ where: { token: { in: invalidTokens.map((device) => device.token) } } });
  }
}
