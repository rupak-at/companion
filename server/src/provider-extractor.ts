import { spawn } from "node:child_process";
import { readdir, stat } from "node:fs/promises";
import { join } from "node:path";

const MAX_BYTES = 250 * 1024 * 1024;
const VERIFY_PATTERNS = /captcha|confirm you(?:'re| are) not a bot|login required|sign in to confirm|checkpoint required/i;

export class ExtractorError extends Error {
  constructor(public readonly code: string, message: string) {
    super(message);
  }
}

export async function extractProviderMedia(url: URL, directory: string): Promise<{ filePath: string; fileName: string }> {
  const outputTemplate = join(directory, "%(title).80B_%(id)s.%(ext)s");
  const args = [
    "--ignore-config",
    "--no-playlist",
    "--no-part",
    "--restrict-filenames",
    "--max-filesize", "250M",
    "--socket-timeout", "30",
    "--retries", "2",
    "--fragment-retries", "2",
    "--no-write-comments",
    "--no-write-info-json",
    "--no-write-thumbnail",
    "-f", "best[ext=mp4]/best",
    "-o", outputTemplate,
    url.href,
  ];

  const stderr = await runExtractor(args);
  const files = (await readdir(directory)).filter((name) => !name.endsWith(".part"));
  if (files.length !== 1) throw new ExtractorError("EXTRACTOR_NO_OUTPUT", stderr || "Extractor produced no file");
  const fileName = files[0];
  const filePath = join(directory, fileName);
  if ((await stat(filePath)).size > MAX_BYTES) throw new ExtractorError("FILE_TOO_LARGE", "The extracted file exceeds 250 MB");
  return { filePath, fileName };
}

function runExtractor(args: string[]): Promise<string> {
  return new Promise((resolve, reject) => {
    const child = spawn("yt-dlp", args, { shell: false, stdio: ["ignore", "ignore", "pipe"] });
    let stderr = "";
    const timeout = setTimeout(() => child.kill("SIGKILL"), 180_000);
    child.stderr.on("data", (chunk: Buffer) => { stderr = (stderr + chunk.toString()).slice(-16_384); });
    child.on("error", (error) => { clearTimeout(timeout); reject(new ExtractorError("EXTRACTOR_UNAVAILABLE", error.message)); });
    child.on("close", (code, signal) => {
      clearTimeout(timeout);
      if (code === 0) return resolve(stderr);
      if (signal === "SIGKILL") return reject(new ExtractorError("EXTRACTOR_TIMEOUT", "Provider extraction timed out"));
      if (VERIFY_PATTERNS.test(stderr)) return reject(new ExtractorError("HUMAN_VERIFICATION_REQUIRED", "The provider requested login or human verification"));
      reject(new ExtractorError("EXTRACTOR_FAILED", stderr.split("\n").filter(Boolean).at(-1) ?? `Extractor exited with code ${code}`));
    });
  });
}

export function extractorErrorCode(stderr: string): string {
  return VERIFY_PATTERNS.test(stderr) ? "HUMAN_VERIFICATION_REQUIRED" : "EXTRACTOR_FAILED";
}
