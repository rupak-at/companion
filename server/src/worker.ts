import { createWriteStream } from "node:fs";
import { mkdir, rm, stat } from "node:fs/promises";
import { basename, join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { Worker } from "bullmq";
import { config } from "./config.js";
import { prisma } from "./services.js";
import { assertSafeRedirect, validatePublicUrl } from "./url-security.js";
import { ExtractorError, extractProviderMedia } from "./provider-extractor.js";

const MAX_BYTES = 250 * 1024 * 1024;
const MAX_REDIRECTS = 5;

new Worker("media-downloads", async (queueJob) => {
  const record = await prisma.downloadJob.findUniqueOrThrow({ where: { id: queueJob.data.jobId } });
  await prisma.downloadJob.update({ where: { id: record.id }, data: { status: "PROCESSING", progress: 5 } });
  const directory = join(config.DOWNLOAD_DIR, record.id);
  await mkdir(directory, { recursive: true, mode: 0o700 });
  try {
    let url = await validatePublicUrl(record.sourceUrl);
    if (record.provider === "TIKTOK" || record.provider === "INSTAGRAM") {
      url = await resolveProviderRedirects(url);
      const extracted = await extractProviderMedia(url, directory);
      const output = await stat(extracted.filePath);
      await prisma.downloadJob.update({ where: { id: record.id }, data: {
        status: "READY", progress: 100, fileName: extracted.fileName, filePath: extracted.filePath,
        sizeBytes: output.size, mimeType: "video/mp4", expiresAt: new Date(Date.now() + 30 * 60_000),
      } });
      return;
    }
    let response: Response | undefined;
    for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
      response = await fetch(url, { redirect: "manual", signal: AbortSignal.timeout(180_000) });
      if (![301, 302, 303, 307, 308].includes(response.status)) break;
      const location = response.headers.get("location");
      if (!location || hop === MAX_REDIRECTS) throw new Error("TOO_MANY_REDIRECTS");
      url = await assertSafeRedirect(url, location);
    }
    if (!response?.ok || !response.body) throw new Error(`UPSTREAM_${response?.status ?? "FAILED"}`);
    const length = Number(response.headers.get("content-length") ?? 0);
    if (length > MAX_BYTES) throw new Error("FILE_TOO_LARGE");
    const rawName = basename(url.pathname) || `download-${record.id}`;
    const fileName = rawName.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 120);
    const filePath = join(directory, fileName);
    let received = 0;
    const source = Readable.fromWeb(response.body as never);
    source.on("data", (chunk: Buffer) => { received += chunk.length; if (received > MAX_BYTES) source.destroy(new Error("FILE_TOO_LARGE")); });
    await pipeline(source, createWriteStream(filePath, { mode: 0o600 }));
    const output = await stat(filePath);
    await prisma.downloadJob.update({ where: { id: record.id }, data: {
      status: "READY", progress: 100, fileName, filePath, sizeBytes: output.size,
      mimeType: response.headers.get("content-type"), expiresAt: new Date(Date.now() + 30 * 60_000),
    } });
  } catch (error) {
    await rm(directory, { recursive: true, force: true });
    const code = error instanceof ExtractorError ? error.code : (error as Error).message;
    const message = code === "HUMAN_VERIFICATION_REQUIRED"
      ? "This provider requires manual verification. Try again later or use a direct media link."
      : "The media could not be downloaded.";
    await prisma.downloadJob.update({ where: { id: record.id }, data: { status: "FAILED", errorCode: code, errorMessage: message } });
    throw error;
  }
}, { connection: { url: config.REDIS_URL }, concurrency: 2 });

async function resolveProviderRedirects(initialUrl: URL): Promise<URL> {
  let url = initialUrl;
  for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
    const response = await fetch(url, { redirect: "manual", signal: AbortSignal.timeout(30_000) });
    const location = response.headers.get("location");
    await response.body?.cancel();
    if (![301, 302, 303, 307, 308].includes(response.status)) return url;
    if (!location || hop === MAX_REDIRECTS) throw new Error("TOO_MANY_REDIRECTS");
    url = await assertSafeRedirect(url, location);
  }
  throw new Error("TOO_MANY_REDIRECTS");
}
