from __future__ import annotations

import argparse
import json
import os
import re
import socket
import subprocess
import sys
import time
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urljoin, urlparse
from urllib.request import Request, urlopen

from playwright.sync_api import BrowserContext, Download, Page, TimeoutError as PlaywrightTimeout, sync_playwright

from runner_support import is_savefrom_page, read_env_value, read_link_file, remove_link_from_file, safe_filename

DEFAULT_SAVEFROM_URL = "https://en1.savefrom.net/16Em/download-from-tiktok"
CAPTCHA_SELECTORS = (
    '#output-captcha-dialog:visible',
    'iframe[src*="captcha" i]',
    'iframe[src*="recaptcha" i]',
    'iframe[src*="hcaptcha" i]',
    '[class*="captcha" i]',
    '[id*="captcha" i]',
)
URL_INPUT_SELECTORS = ('input[name="sf_url"]', '#sf_url', 'input[type="url"]', 'input[type="text"]')
SUBMIT_SELECTORS = (
    'button:has-text("Search")',
    'input[type="submit"][value*="Search" i]',
    'button[type="submit"]',
    'input[type="submit"]',
)
DOWNLOAD_SELECTORS = (
    'a[download]:visible',
    'a[href*=".mp4"]:visible',
    'a.link-download:visible',
    'a.download:visible',
    '#sf_result a:visible',
    '[class*="result"] a:visible',
    'a:has-text("Download"):visible',
)


class RunnerApi:
    def __init__(self, base_url: str, token: str, runner_id: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.runner_id = runner_id

    def claim(self) -> dict[str, Any] | None:
        return self._request("POST", "/api/v1/runner/jobs/claim", {"runnerId": self.runner_id}, allow_empty=True)

    def update(self, job_id: str, status: str, message: str, error_code: str | None = None) -> None:
        payload = {"runnerId": self.runner_id, "status": status, "message": message}
        if error_code:
            payload["errorCode"] = error_code
        self._request("POST", f"/api/v1/runner/jobs/{job_id}/status", payload)

    def _request(self, method: str, path: str, payload: dict[str, Any], allow_empty: bool = False) -> dict[str, Any] | None:
        request = Request(
            f"{self.base_url}{path}",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"},
            method=method,
        )
        try:
            with urlopen(request, timeout=20) as response:
                body = response.read()
                return json.loads(body) if body else None
        except HTTPError as error:
            if allow_empty and error.code == 204:
                return None
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Runner API returned HTTP {error.code}: {detail}") from error
        except URLError as error:
            raise RuntimeError(f"Cannot reach runner API: {error.reason}") from error


class LocalBatchStatus:
    def update(self, job_id: str, status: str, message: str, error_code: str | None = None) -> None:
        suffix = f" ({error_code})" if error_code else ""
        print(f"[{job_id}] {status}: {message}{suffix}", flush=True)


def notify_user(message: str) -> None:
    print(f"\nACTION NEEDED: {message}\n", flush=True)
    try:
        subprocess.run(["notify-send", "Ambient Companion", message], check=False, timeout=5)
    except (FileNotFoundError, subprocess.SubprocessError):
        pass


def first_visible(page: Page, selectors: tuple[str, ...]):
    for selector in selectors:
        locator = page.locator(selector).first
        try:
            if locator.is_visible(timeout=500):
                return locator
        except PlaywrightTimeout:
            continue
    return None


def captcha_visible(page: Page) -> bool:
    return first_visible(page, CAPTCHA_SELECTORS) is not None


def find_download_control(page: Page):
    candidates = []
    for frame in page.frames:
        for selector in DOWNLOAD_SELECTORS:
            locator = frame.locator(selector)
            try:
                count = locator.count()
            except Exception:
                continue
            for index in range(count):
                link = locator.nth(index)
                try:
                    href = link.get_attribute("href") or ""
                    label = (link.inner_text() or "").strip()
                    if not link.is_visible() or not href or href == "#":
                        continue
                    combined = f"{label} {href}"
                    if any(word in combined.lower() for word in ("install", "helper", "app now")):
                        continue
                    watermark_free = any(word in combined.lower() for word in ("no watermark", "without watermark", "watermark-free", "nowm"))
                    explicitly_watermarked = "watermark" in combined.lower() and not watermark_free
                    if explicitly_watermarked:
                        continue
                    score = 250 if watermark_free else 0
                    score += 100 if link.get_attribute("download") is not None else 0
                    score += 90 if ".mp4" in href.lower() else 0
                    score += 35 if "download" in label.lower() else 0
                    if score >= 35:
                        candidates.append((score, link))
                except Exception:
                    continue
    return max(candidates, key=lambda candidate: candidate[0])[1] if candidates else None


def fetch_generated_download(context: BrowserContext, control, download_dir: Path, fallback_name: str) -> Path | None:
    href = control.get_attribute("href") or ""
    if not href or href.startswith(("blob:", "javascript:")):
        return None
    media_url = urljoin(control.evaluate("element => element.baseURI"), href)
    try:
        response = context.request.get(media_url, timeout=120_000, fail_on_status_code=False)
    except Exception:
        return None
    try:
        content_type = response.headers.get("content-type", "")
        if not response.ok or not (content_type.startswith("video/") or "octet-stream" in content_type):
            return None
        disposition = response.headers.get("content-disposition", "")
        encoded_name = re.search(r"filename\*=UTF-8''([^;]+)", disposition, re.IGNORECASE)
        plain_name = re.search(r'filename="?([^";]+)', disposition, re.IGNORECASE)
        url_name = Path(urlparse(response.url).path).name
        name = unquote(encoded_name.group(1)) if encoded_name else plain_name.group(1) if plain_name else url_name
        target = download_dir / safe_filename(name, fallback_name)
        target.write_bytes(response.body())
        return target
    finally:
        response.dispose()


def close_ad_popup(popup: Page) -> None:
    try:
        popup.wait_for_load_state("domcontentloaded", timeout=5_000)
        if popup.url != "about:blank" and not is_savefrom_page(popup.url):
            print(f"Closed unexpected popup: {popup.url}")
            popup.close()
    except PlaywrightTimeout:
        if not popup.is_closed():
            popup.close()


def process_job(context: BrowserContext, api: Any, job: dict[str, Any], savefrom_url: str, download_dir: Path) -> None:
    job_id = str(job["jobId"])
    source_url = str(job["sourceUrl"])
    page = context.new_page()
    saved_files: list[Path] = []

    def save_download(download: Download) -> None:
        target = download_dir / safe_filename(download.suggested_filename)
        download.save_as(target)
        saved_files.append(target)
        print(f"Downloaded: {target}")

    page.on("download", save_download)
    page.on("popup", close_ad_popup)
    try:
        page.goto(savefrom_url, wait_until="domcontentloaded", timeout=60_000)
        if not is_savefrom_page(page.url):
            raise RuntimeError(f"SaveFrom redirected before input to an unexpected site: {page.url}")

        url_input = first_visible(page, URL_INPUT_SELECTORS)
        if url_input is None:
            api.update(job_id, "WAITING_FOR_USER", "Paste the queued link into SaveFrom and start processing, then return to the runner.")
            notify_user("SaveFrom input was not detected. Complete the paste/process step in Chromium, then press Enter here.")
            input()
        else:
            url_input.fill(source_url)
            submit = first_visible(page, SUBMIT_SELECTORS)
            if submit is None:
                url_input.press("Enter")
            else:
                submit.click()

        deadline = time.monotonic() + 10 * 60
        prompted_for_manual_download = False
        captcha_was_visible = False
        while time.monotonic() < deadline and not saved_files:
            if page.url != "about:blank" and not is_savefrom_page(page.url):
                redirected_to = page.url
                api.update(job_id, "WAITING_FOR_USER", "An unexpected advertising redirect was blocked in the local browser.")
                print(f"Blocked unexpected main-page redirect: {redirected_to}")
                page.go_back(wait_until="domcontentloaded", timeout=30_000)
                continue
            if captcha_visible(page):
                if not captcha_was_visible:
                    captcha_was_visible = True
                    api.update(job_id, "WAITING_FOR_USER", "Human verification is open in the local Chromium window.")
                    notify_user("Solve the verification directly in Chromium. The runner will continue automatically when it closes.")
                time.sleep(1)
                continue
            if captcha_was_visible:
                captcha_was_visible = False
                api.update(job_id, "DOWNLOADING", "Verification completed; looking for the generated download.")

            download_control = find_download_control(page)
            if download_control is not None:
                api.update(job_id, "DOWNLOADING", "Download control found in the local browser.")
                direct_file = fetch_generated_download(context, download_control, download_dir, f"{job_id}.mp4")
                if direct_file is not None:
                    saved_files.append(direct_file)
                    print(f"Downloaded generated media directly: {direct_file}")
                    continue
                download_control.click()
                time.sleep(2)
                continue

            if not prompted_for_manual_download and time.monotonic() + 30 < deadline:
                prompted_for_manual_download = True
                api.update(job_id, "WAITING_FOR_USER", "Finish the visible SaveFrom result/download step in Chromium.")
                notify_user("If SaveFrom has shown a result, click its real Download button in Chromium. The runner will capture the download.")
            time.sleep(2)

        if not saved_files:
            raise RuntimeError("No browser download was captured within ten minutes")
        api.update(job_id, "COMPLETED", f"Saved locally as {saved_files[-1].name}")
    except Exception as error:
        api.update(job_id, "FAILED", str(error)[:500], "LOCAL_BROWSER_FAILED")
        raise
    finally:
        if not page.is_closed():
            page.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Visible local browser runner for Ambient Companion downloads")
    parser.add_argument("--api-url", default=os.getenv("RUNNER_API_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--runner-id", default=os.getenv("RUNNER_ID", f"{socket.gethostname()}-browser"))
    parser.add_argument("--savefrom-url", default=os.getenv("SAVEFROM_URL", DEFAULT_SAVEFROM_URL))
    parser.add_argument("--profile-dir", type=Path, default=Path(".local/browser-profile"))
    parser.add_argument("--download-dir", type=Path, default=Path.home() / "Downloads" / "Ambient Companion")
    parser.add_argument("--links-file", type=Path, help="Process one URL per line without using the backend queue")
    parser.add_argument("--once", action="store_true", help="Exit when no queued job is available")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not is_savefrom_page(args.savefrom_url):
        print("SAVEFROM_URL must use savefrom.net or one of its subdomains.", file=sys.stderr)
        return 2

    args.profile_dir.mkdir(parents=True, exist_ok=True)
    args.download_dir.mkdir(parents=True, exist_ok=True)

    batch_links: list[str] | None = None
    if args.links_file:
        try:
            args.links_file = args.links_file.expanduser().resolve()
            batch_links = read_link_file(args.links_file)
        except (OSError, ValueError) as error:
            print(f"Cannot read links file: {error}", file=sys.stderr)
            return 2
        if not batch_links:
            print("The links file contains no URLs.", file=sys.stderr)
            return 2
        api: Any = LocalBatchStatus()
    else:
        repo_env = Path(__file__).resolve().parent.parent / "server" / ".env"
        token = os.getenv("LOCAL_RUNNER_TOKEN") or read_env_value(repo_env, "LOCAL_RUNNER_TOKEN")
        if not token or len(token) < 32:
            print("LOCAL_RUNNER_TOKEN is missing or shorter than 32 characters. Add the same token to server/.env.", file=sys.stderr)
            return 2
        api = RunnerApi(args.api_url, token, args.runner_id)
    with sync_playwright() as playwright:
        context = playwright.chromium.launch_persistent_context(
            user_data_dir=args.profile_dir,
            headless=False,
            accept_downloads=True,
        )
        try:
            if batch_links is not None:
                failures = 0
                for position, source_url in enumerate(batch_links, start=1):
                    match = re.search(r"/video/(\d+)", source_url)
                    job_id = match.group(1) if match else f"batch-{position}"
                    print(f"\n[{position}/{len(batch_links)}] Processing {source_url}")
                    try:
                        process_job(context, api, {"jobId": job_id, "sourceUrl": source_url}, args.savefrom_url, args.download_dir)
                        remove_link_from_file(args.links_file, source_url)
                        print(f"Removed completed link from {args.links_file}")
                    except Exception as error:
                        failures += 1
                        print(f"Skipped {source_url}: {error}", file=sys.stderr)
                print(f"Batch finished: {len(batch_links) - failures} completed, {failures} failed.")
                return 1 if failures else 0
            while True:
                job = api.claim()
                if not job:
                    if args.once:
                        print("No queued local-runner jobs.")
                        return 0
                    time.sleep(5)
                    continue
                print(f"Claimed {job['jobId']}: {job['sourceUrl']}")
                try:
                    process_job(context, api, job, args.savefrom_url, args.download_dir)
                except Exception as error:
                    print(f"Job failed: {error}", file=sys.stderr)
        finally:
            context.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
