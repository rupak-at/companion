import Fastify from "fastify";
import { createReadStream } from "node:fs";
import { realpath, rm } from "node:fs/promises";
import { resolve, sep } from "node:path";
import { z } from "zod";
import { authenticate } from "./auth.js";
import { config } from "./config.js";
import { downloadsQueue, prisma } from "./services.js";
import { classifyUrl, validatePublicUrl } from "./url-security.js";

const app = Fastify({ logger: true, bodyLimit: 16_384 });
const createBody = z.object({ url: z.string().url().max(2048), format: z.enum(["video", "image"]).optional() });

app.get("/health", async () => ({ status: "ok" }));

app.post("/api/v1/downloads", async (request, reply) => {
  const userId = await authenticate(request.headers.authorization);
  if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });
  const parsed = createBody.safeParse(request.body);
  if (!parsed.success) return reply.code(400).send({ error: "INVALID_REQUEST" });

  let url: URL;
  try { url = await validatePublicUrl(parsed.data.url); }
  catch (error) { return reply.code(400).send({ error: (error as Error).message }); }
  const provider = classifyUrl(url)!;
  const active = await prisma.downloadJob.count({ where: { userId, status: { in: ["QUEUED", "PROCESSING"] } } });
  if (active >= 2) return reply.code(429).send({ error: "TOO_MANY_ACTIVE_JOBS" });

  const job = await prisma.downloadJob.create({ data: { userId, sourceUrl: url.href, provider } });
  await downloadsQueue.add("download", { jobId: job.id }, { jobId: job.id, attempts: 2, removeOnComplete: 100, removeOnFail: 100 });
  return reply.code(202).send({ jobId: job.id, status: job.status });
});

app.get<{ Params: { jobId: string } }>("/api/v1/downloads/:jobId", async (request, reply) => {
  const userId = await authenticate(request.headers.authorization);
  if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });
  const job = await prisma.downloadJob.findFirst({ where: { id: request.params.jobId, userId } });
  if (!job) return reply.code(404).send({ error: "NOT_FOUND" });
  return {
    jobId: job.id, status: job.status, progress: job.progress, provider: job.provider, title: job.title,
    downloadUrl: job.status === "READY" ? `${config.PUBLIC_BASE_URL}/api/v1/downloads/${job.id}/file` : undefined,
    expiresAt: job.expiresAt, errorCode: job.errorCode, errorMessage: job.errorMessage,
  };
});

app.get<{ Params: { jobId: string } }>("/api/v1/downloads/:jobId/file", async (request, reply) => {
  const userId = await authenticate(request.headers.authorization);
  if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });
  const job = await prisma.downloadJob.findFirst({ where: { id: request.params.jobId, userId } });
  if (!job) return reply.code(404).send({ error: "NOT_FOUND" });
  if (job.status !== "READY" || !job.filePath || !job.fileName) return reply.code(409).send({ error: "FILE_NOT_READY" });
  if (!job.expiresAt || job.expiresAt <= new Date()) {
    await prisma.downloadJob.update({ where: { id: job.id }, data: { status: "EXPIRED" } });
    return reply.code(410).send({ error: "FILE_EXPIRED" });
  }

  try {
    const storageRoot = await realpath(resolve(config.DOWNLOAD_DIR));
    const filePath = await realpath(job.filePath);
    if (!filePath.startsWith(`${storageRoot}${sep}`)) return reply.code(500).send({ error: "INVALID_FILE_PATH" });
    reply.header("Content-Disposition", `attachment; filename="${job.fileName}"`);
    reply.type(job.mimeType ?? "application/octet-stream");
    return reply.send(createReadStream(filePath));
  } catch {
    return reply.code(404).send({ error: "FILE_MISSING" });
  }
});

app.delete<{ Params: { jobId: string } }>("/api/v1/downloads/:jobId", async (request, reply) => {
  const userId = await authenticate(request.headers.authorization);
  if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });
  const job = await prisma.downloadJob.findFirst({ where: { id: request.params.jobId, userId } });
  if (!job) return reply.code(404).send({ error: "NOT_FOUND" });
  if (job.filePath) {
    const jobDirectory = resolve(config.DOWNLOAD_DIR, job.id);
    const storageRoot = resolve(config.DOWNLOAD_DIR);
    if (jobDirectory.startsWith(`${storageRoot}${sep}`)) await rm(jobDirectory, { recursive: true, force: true });
  }
  await prisma.downloadJob.delete({ where: { id: job.id } });
  return reply.code(204).send();
});

app.listen({ host: "0.0.0.0", port: config.PORT }).catch((error) => { app.log.error(error); process.exit(1); });
