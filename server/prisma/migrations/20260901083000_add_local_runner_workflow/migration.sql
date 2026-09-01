-- AlterEnum
ALTER TYPE "DownloadStatus" ADD VALUE IF NOT EXISTS 'WAITING_FOR_LOCAL_RUNNER';
ALTER TYPE "DownloadStatus" ADD VALUE IF NOT EXISTS 'CLAIMED';
ALTER TYPE "DownloadStatus" ADD VALUE IF NOT EXISTS 'WAITING_FOR_USER';
ALTER TYPE "DownloadStatus" ADD VALUE IF NOT EXISTS 'DOWNLOADING';
ALTER TYPE "DownloadStatus" ADD VALUE IF NOT EXISTS 'COMPLETED';

-- AlterTable
ALTER TABLE "DownloadJob"
ADD COLUMN "runnerId" TEXT,
ADD COLUMN "runnerMessage" TEXT,
ADD COLUMN "leaseExpiresAt" TIMESTAMP(3),
ADD COLUMN "completedAt" TIMESTAMP(3);

-- CreateIndex
CREATE INDEX "DownloadJob_status_leaseExpiresAt_createdAt_idx"
ON "DownloadJob"("status", "leaseExpiresAt", "createdAt");
