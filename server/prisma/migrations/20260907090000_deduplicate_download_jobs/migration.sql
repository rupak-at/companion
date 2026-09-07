-- Remove later copies before enforcing uniqueness. The oldest job remains the
-- canonical record returned for that user's source URL.
DELETE FROM "DownloadJob" AS duplicate
USING "DownloadJob" AS canonical
WHERE duplicate."userId" = canonical."userId"
  AND duplicate."sourceUrl" = canonical."sourceUrl"
  AND (
    duplicate."createdAt" > canonical."createdAt"
    OR (
      duplicate."createdAt" = canonical."createdAt"
      AND duplicate."id"::text > canonical."id"::text
    )
  );

-- CreateIndex
CREATE UNIQUE INDEX "DownloadJob_userId_sourceUrl_key"
ON "DownloadJob"("userId", "sourceUrl");
