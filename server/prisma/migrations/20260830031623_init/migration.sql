-- CreateEnum
CREATE TYPE "DownloadStatus" AS ENUM ('QUEUED', 'PROCESSING', 'READY', 'FAILED', 'EXPIRED');

-- CreateEnum
CREATE TYPE "LinkProvider" AS ENUM ('DIRECT_VIDEO', 'DIRECT_IMAGE', 'TIKTOK', 'INSTAGRAM');

-- CreateTable
CREATE TABLE "DownloadJob" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "sourceUrl" TEXT NOT NULL,
    "provider" "LinkProvider" NOT NULL,
    "status" "DownloadStatus" NOT NULL DEFAULT 'QUEUED',
    "progress" INTEGER NOT NULL DEFAULT 0,
    "title" TEXT,
    "mimeType" TEXT,
    "fileName" TEXT,
    "filePath" TEXT,
    "sizeBytes" BIGINT,
    "errorCode" TEXT,
    "errorMessage" TEXT,
    "expiresAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DownloadJob_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "DownloadJob_userId_createdAt_idx" ON "DownloadJob"("userId", "createdAt");

-- CreateIndex
CREATE INDEX "DownloadJob_status_expiresAt_idx" ON "DownloadJob"("status", "expiresAt");
