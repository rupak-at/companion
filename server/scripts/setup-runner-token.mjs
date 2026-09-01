import { randomBytes } from "node:crypto";
import { chmod, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const argumentIndex = process.argv.indexOf("--target");
const envPath = resolve(argumentIndex >= 0 ? process.argv[argumentIndex + 1] : ".env");
const force = process.argv.includes("--force");
let contents;
try {
  contents = await readFile(envPath, "utf8");
} catch (error) {
  if (error.code !== "ENOENT") throw error;
  contents = "";
}

const existing = contents.match(/^LOCAL_RUNNER_TOKEN=["']?([^"'\r\n]+)["']?$/m)?.[1];
if (existing && existing.length >= 32 && !force) {
  console.log("LOCAL_RUNNER_TOKEN is already configured; no changes made.");
  process.exit(0);
}

const line = `LOCAL_RUNNER_TOKEN="${randomBytes(32).toString("hex")}"`;
contents = /^LOCAL_RUNNER_TOKEN=.*$/m.test(contents)
  ? contents.replace(/^LOCAL_RUNNER_TOKEN=.*$/m, line)
  : `${contents.trimEnd()}${contents.trim() ? "\n" : ""}${line}\n`;
await writeFile(envPath, contents, { encoding: "utf8", mode: 0o600 });
await chmod(envPath, 0o600);
console.log("Configured LOCAL_RUNNER_TOKEN without printing it.");
