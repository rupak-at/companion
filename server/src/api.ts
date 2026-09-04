import Fastify from "fastify";
import { createReadStream } from "node:fs";
import { realpath, rm } from "node:fs/promises";
import { resolve, sep } from "node:path";
import { z } from "zod";
import type { Prisma } from "@prisma/client";
import { authenticate } from "./auth.js";
import { config } from "./config.js";
import { downloadsQueue, prisma } from "./services.js";
import { classifyUrl, validatePublicUrl } from "./url-security.js";
import { parseJobId } from "./request-validation.js";
import { isRunnerAuthorized } from "./runner-auth.js";

const app = Fastify({ logger: true, bodyLimit: 16_384 });
const createBody = z.object({ url: z.string().url().max(2048), format: z.enum(["video", "image"]).optional() });
const runnerIdentity = z.object({ runnerId: z.string().trim().min(3).max(80).regex(/^[a-zA-Z0-9._-]+$/) });
const runnerUpdate = z.object({
  runnerId: runnerIdentity.shape.runnerId,
  status: z.enum(["WAITING_FOR_USER", "DOWNLOADING", "COMPLETED", "FAILED"]),
  message: z.string().trim().max(500).optional(),
  errorCode: z.string().trim().max(80).optional(),
});
const leaseDurationMs = 30 * 60_000;

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
  const usesLocalRunner = provider === "TIKTOK" || provider === "INSTAGRAM";
  if (!usesLocalRunner) {
    const active = await prisma.downloadJob.count({ where: { userId, status: { in: ["QUEUED", "PROCESSING"] } } });
    if (active >= 2) return reply.code(429).send({ error: "TOO_MANY_ACTIVE_JOBS" });
  }
  const job = await prisma.downloadJob.create({ data: {
    userId, sourceUrl: url.href, provider, status: usesLocalRunner ? "WAITING_FOR_LOCAL_RUNNER" : "QUEUED",
  } });
  if (!usesLocalRunner) {
    await downloadsQueue.add("download", { jobId: job.id }, { jobId: job.id, attempts: 2, removeOnComplete: 100, removeOnFail: 100 });
  }
  return reply.code(202).send({ jobId: job.id, status: job.status });
});

app.get<{ Params: { jobId: string } }>("/api/v1/downloads/:jobId", async (request, reply) => {
  const userId = await authenticate(request.headers.authorization);
  if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });
  const jobId = parseJobId(request.params.jobId);
  if (!jobId) return reply.code(400).send({ error: "INVALID_JOB_ID" });
  const job = await prisma.downloadJob.findFirst({ where: { id: jobId, userId } });
  if (!job) return reply.code(404).send({ error: "NOT_FOUND" });
  return {
    jobId: job.id, status: job.status, progress: job.progress, provider: job.provider, title: job.title,
    downloadUrl: job.status === "READY" ? `${config.PUBLIC_BASE_URL}/api/v1/downloads/${job.id}/file` : undefined,
    expiresAt: job.expiresAt, completedAt: job.completedAt, runnerMessage: job.runnerMessage,
    errorCode: job.errorCode, errorMessage: job.errorMessage,
  };
});

app.post("/api/v1/runner/jobs/claim", async (request, reply) => {
  if (!isRunnerAuthorized(request.headers.authorization, config.LOCAL_RUNNER_TOKEN)) {
    return reply.code(config.LOCAL_RUNNER_TOKEN ? 401 : 503).send({ error: config.LOCAL_RUNNER_TOKEN ? "UNAUTHORIZED" : "RUNNER_NOT_CONFIGURED" });
  }
  const parsed = runnerIdentity.safeParse(request.body);
  if (!parsed.success) return reply.code(400).send({ error: "INVALID_RUNNER" });
  const now = new Date();
  const resumableStatuses = ["CLAIMED", "WAITING_FOR_USER", "DOWNLOADING"] as const;
  const claimable: Prisma.DownloadJobWhereInput = { OR: [
    { status: "WAITING_FOR_LOCAL_RUNNER" },
    { runnerId: parsed.data.runnerId, status: { in: [...resumableStatuses] } },
    { status: { in: [...resumableStatuses] }, leaseExpiresAt: { lt: now } },
  ] };
  const candidate = await prisma.downloadJob.findFirst({ where: claimable, orderBy: { createdAt: "asc" } });
  if (!candidate) return reply.code(204).send();
  const claimed = await prisma.downloadJob.updateMany({
    where: { id: candidate.id, ...claimable },
    data: {
      status: "CLAIMED", runnerId: parsed.data.runnerId, runnerMessage: "Claimed by local browser runner",
      leaseExpiresAt: new Date(Date.now() + leaseDurationMs), errorCode: null, errorMessage: null,
    },
  });
  if (claimed.count !== 1) return reply.code(409).send({ error: "JOB_ALREADY_CLAIMED" });
  return { jobId: candidate.id, sourceUrl: candidate.sourceUrl, provider: candidate.provider };
});

app.post<{ Params: { jobId: string } }>("/api/v1/runner/jobs/:jobId/status", async (request, reply) => {
  if (!isRunnerAuthorized(request.headers.authorization, config.LOCAL_RUNNER_TOKEN)) {
    return reply.code(config.LOCAL_RUNNER_TOKEN ? 401 : 503).send({ error: config.LOCAL_RUNNER_TOKEN ? "UNAUTHORIZED" : "RUNNER_NOT_CONFIGURED" });
  }
  const jobId = parseJobId(request.params.jobId);
  if (!jobId) return reply.code(400).send({ error: "INVALID_JOB_ID" });
  const parsed = runnerUpdate.safeParse(request.body);
  if (!parsed.success) return reply.code(400).send({ error: "INVALID_RUNNER_UPDATE" });
  const existing = await prisma.downloadJob.findFirst({ where: { id: jobId, runnerId: parsed.data.runnerId } });
  if (!existing) return reply.code(404).send({ error: "RUNNER_JOB_NOT_FOUND" });
  const completed = parsed.data.status === "COMPLETED";
  const failed = parsed.data.status === "FAILED";
  const job = await prisma.downloadJob.update({ where: { id: jobId }, data: {
    status: parsed.data.status,
    progress: completed ? 100 : parsed.data.status === "DOWNLOADING" ? 80 : existing.progress,
    runnerMessage: parsed.data.message,
    leaseExpiresAt: completed || failed ? null : new Date(Date.now() + leaseDurationMs),
    completedAt: completed ? new Date() : null,
    errorCode: failed ? parsed.data.errorCode ?? "LOCAL_RUNNER_FAILED" : null,
    errorMessage: failed ? parsed.data.message ?? "The local browser runner failed." : null,
  } });
  return { jobId: job.id, status: job.status };
});

app.get<{ Params: { jobId: string } }>("/api/v1/downloads/:jobId/file", async (request, reply) => {
  const userId = await authenticate(request.headers.authorization);
  if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });
  const jobId = parseJobId(request.params.jobId);
  if (!jobId) return reply.code(400).send({ error: "INVALID_JOB_ID" });
  const job = await prisma.downloadJob.findFirst({ where: { id: jobId, userId } });
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
  const jobId = parseJobId(request.params.jobId);
  if (!jobId) return reply.code(400).send({ error: "INVALID_JOB_ID" });
  const job = await prisma.downloadJob.findFirst({ where: { id: jobId, userId } });
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
