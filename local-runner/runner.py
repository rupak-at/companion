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

from runner_support import (
    available_download_path,
    is_savefrom_page,
    is_processing_error,
    link_key,
    read_completed_links,
    read_env_value,
    read_link_file,
    record_completed_link,
    remove_link_from_file,
    score_download_candidate,
)

DEFAULT_SAVEFROM_URL = "https://en1.savefrom.net/16Em/download-from-tiktok"
DEFAULT_DOWNLOAD_DIR = Path.home() / "Downloads" / "Ambient Companion"
RUNNER_DIRECTORY = Path(__file__).resolve().parent
DEFAULT_COMPLETED_LINKS_FILE = RUNNER_DIRECTORY / "downloaded_links.txt"
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
    '#sf_submit:visible',
    '.sf-button:visible',
    '[class*="submit" i]:visible',
    '[role="button"]:has-text("Download"):visible',
    'a:has-text("Download"):visible',
    'button:has-text("Search")',
    'button:has-text("Download")',
    'input[type="submit"][value*="Search" i]',
    'input[type="submit"][value*="Download" i]',
    'button[type="submit"]',
    'input[type="submit"]',
)
FORM_SUBMIT_SELECTORS = ('button:visible', 'input[type="submit"]:visible', 'input[type="button"]:visible')
DOWNLOAD_SELECTORS = (
    'a[download]:visible',
    'a[href*=".mp4"]:visible',
    'a.link-download:visible',
    'a.download:visible',
    '#sf_result a:visible',
    '[class*="result"] a:visible',
)
ERROR_SELECTORS = (
    '#sf_result [class*="error" i]:visible',
    '#sf_result [class*="alert" i]:visible',
    '[class*="result"] [class*="error" i]:visible',
    '[role="alert"]:visible',
    "text=/try again|something went wrong|could not process|couldn't process/i",
)


def environment_flag(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() not in {"0", "false", "no", "off"}


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


def find_submit_control(page: Page, url_input):
    for label in ("Download", "Search"):
        try:
            role_button = page.get_by_role("button", name=re.compile(f"^{label}$", re.IGNORECASE)).first
            if role_button.is_visible(timeout=500):
                return role_button
        except Exception:
            continue
    try:
        form = url_input.locator("xpath=ancestor::form[1]")
        submit = first_visible(form, FORM_SUBMIT_SELECTORS)
        if submit is not None:
            return submit
    except Exception:
        pass
    input_box = url_input.bounding_box()
    nearby_controls = []
    for label in ("Download", "Search"):
        matches = page.get_by_text(label, exact=True)
        try:
            count = matches.count()
        except Exception:
            continue
        for index in range(count):
            try:
                text = matches.nth(index)
                clickable = text.locator(
                    "xpath=ancestor-or-self::*[self::button or self::a or self::input or @role='button' "
                    "or contains(translate(@class, 'BUTTON', 'button'), 'button')][1]"
                )
                if not clickable.is_visible():
                    continue
                box = clickable.bounding_box()
                if box is None:
                    continue
                if input_box is None:
                    distance = index
                else:
                    horizontal_gap = abs(box["x"] - (input_box["x"] + input_box["width"]))
                    vertical_gap = abs((box["y"] + box["height"] / 2) - (input_box["y"] + input_box["height"] / 2))
                    distance = horizontal_gap + vertical_gap * 5
                nearby_controls.append((distance, clickable))
            except Exception:
                continue
    if nearby_controls:
        return min(nearby_controls, key=lambda candidate: candidate[0])[1]
    return first_visible(page, SUBMIT_SELECTORS)


def wait_for_captcha(page: Page, api: Any, job_id: str, deadline: float) -> bool:
    if not captcha_visible(page):
        return False
    api.update(job_id, "WAITING_FOR_USER", "Human verification is open in the local Chromium window.")
    notify_user("Solve the verification directly in Chromium. The runner will continue automatically when it closes.")
    while captcha_visible(page):
        if time.monotonic() >= deadline:
            raise RuntimeError("Timed out waiting for human verification")
        time.sleep(1)
    api.update(job_id, "DOWNLOADING", "Verification completed; continuing SaveFrom processing.")
    print("Verification completed; continuing.")
    return True


def visible_processing_error(page: Page) -> str | None:
    for frame in page.frames:
        for selector in ERROR_SELECTORS:
            locator = frame.locator(selector)
            try:
                for index in range(locator.count()):
                    candidate = locator.nth(index)
                    if candidate.is_visible():
                        message = (candidate.inner_text() or "").strip()
                        if is_processing_error(message):
                            return message
            except Exception:
                continue
    return None


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
                    details = link.evaluate(
                        "element => ({ inResult: Boolean(element.closest('#sf_result, [class*=result], [class*=media]')) })"
                    )
                    score = score_download_candidate(
                        label,
                        href,
                        link.get_attribute("download") is not None,
                        bool(details["inResult"]),
                    )
                    if score is not None:
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
        target = available_download_path(download_dir, name, fallback_name)
        target.write_bytes(response.body())
        return target
    finally:
        response.dispose()


def close_ad_popup(popup: Page) -> None:
    try:
        popup.wait_for_load_state("domcontentloaded", timeout=5_000)
        print(f"Closed popup/redirect tab: {popup.url}")
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
        target = available_download_path(download_dir, download.suggested_filename)
        download.save_as(target)
        saved_files.append(target)
        print(f"Downloaded: {target}")

    page.on("download", save_download)
    page.on("popup", close_ad_popup)
    try:
        deadline = time.monotonic() + 10 * 60
        page.goto(savefrom_url, wait_until="domcontentloaded", timeout=60_000)
        if not is_savefrom_page(page.url):
            raise RuntimeError(f"SaveFrom redirected before input to an unexpected site: {page.url}")

        retry_reason: str | None = None
        for submission_attempt in range(1, 3):
            while True:
                wait_for_captcha(page, api, job_id, deadline)
                url_input = first_visible(page, URL_INPUT_SELECTORS)
                if url_input is not None:
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError("SaveFrom URL input did not become available")
                time.sleep(1)

            url_input.fill(source_url)
            submit = find_submit_control(page, url_input)
            if submit is None:
                print("Submit control was not detected; submitting the URL with Enter.")
                url_input.press("Enter")
            else:
                details = submit.evaluate(
                    "element => ({ tag: element.tagName, id: element.id, className: String(element.className || ''), text: element.textContent.trim() })"
                )
                print(f"Clicking SaveFrom submit control: {details}")
                submit.click()
            print(f"Submitted link to SaveFrom (attempt {submission_attempt}/2); waiting for processing.")
            api.update(job_id, "DOWNLOADING", "SaveFrom is processing the submitted link.")
            attempt_started = time.monotonic()
            last_wait_log = attempt_started
            retry_reason = None

            while time.monotonic() < deadline and not saved_files:
                wait_for_captcha(page, api, job_id, deadline)
                if page.url != "about:blank" and not is_savefrom_page(page.url):
                    redirected_to = page.url
                    print(f"Returning from unexpected processing redirect: {redirected_to}")
                    page.go_back(wait_until="domcontentloaded", timeout=30_000)
                    continue

                processing_error = visible_processing_error(page) if time.monotonic() - attempt_started >= 2 else None
                if processing_error:
                    retry_reason = processing_error
                    print(f"SaveFrom processing error: {processing_error}")
                    break

                download_control = find_download_control(page)
                if download_control is not None:
                    print("Processed result detected; using its download control.")
                    api.update(job_id, "DOWNLOADING", "Processed result found; starting the download.")
                    direct_file = fetch_generated_download(context, download_control, download_dir, f"{job_id}.mp4")
                    if direct_file is not None:
                        saved_files.append(direct_file)
                        print(f"Downloaded generated media directly: {direct_file}")
                        break

                    result_url = page.url
                    for click_attempt in range(1, 3):
                        print(f"Clicking processed download control (attempt {click_attempt}/2).")
                        download_control.click(timeout=10_000)
                        click_deadline = min(deadline, time.monotonic() + 20)
                        while time.monotonic() < click_deadline and not saved_files:
                            wait_for_captcha(page, api, job_id, deadline)
                            if page.url not in ("about:blank", result_url):
                                redirected_to = page.url
                                print(f"Returning from download redirect: {redirected_to}")
                                page.go_back(wait_until="domcontentloaded", timeout=30_000)
                                break
                            time.sleep(1)
                        if saved_files:
                            break
                        for open_page in context.pages:
                            if open_page != page and not open_page.is_closed():
                                open_page.close()
                        reacquire_deadline = min(deadline, time.monotonic() + 30)
                        download_control = None
                        while time.monotonic() < reacquire_deadline and download_control is None:
                            wait_for_captcha(page, api, job_id, deadline)
                            download_control = find_download_control(page)
                            if download_control is None:
                                time.sleep(1)
                        if download_control is None:
                            break
                    if not saved_files:
                        raise RuntimeError("Processed result was found, but no download started after redirect handling")
                    break

                if time.monotonic() - last_wait_log >= 15:
                    print("Still waiting for SaveFrom to finish processing...")
                    last_wait_log = time.monotonic()
                time.sleep(1)

            if saved_files:
                break
            if retry_reason and submission_attempt == 1:
                print("Retrying the link once after SaveFrom's processing error.")
                page.goto(savefrom_url, wait_until="domcontentloaded", timeout=60_000)
                continue
            if retry_reason:
                raise RuntimeError(f"SaveFrom could not process the link after one retry: {retry_reason}")
            break

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
    parser.add_argument(
        "--download-dir",
        type=Path,
        default=Path(os.getenv("RUNNER_DOWNLOAD_DIR") or DEFAULT_DOWNLOAD_DIR),
        help="Download destination (overrides RUNNER_DOWNLOAD_DIR)",
    )
    parser.add_argument("--links-file", type=Path, help="Process one URL per line without using the backend queue")
    parser.add_argument(
        "--start-minimized",
        action=argparse.BooleanOptionalAction,
        default=environment_flag("RUNNER_START_MINIMIZED", True),
        help="Start Chromium minimized (default; use --no-start-minimized to keep it visible)",
    )
    parser.add_argument("--once", action="store_true", help="Exit when no queued job is available")
    return parser.parse_args()


def load_runner_environment() -> None:
    local_env = RUNNER_DIRECTORY / ".env"
    for key in (
        "RUNNER_API_URL",
        "RUNNER_ID",
        "SAVEFROM_URL",
        "RUNNER_DOWNLOAD_DIR",
        "RUNNER_START_MINIMIZED",
        "LOCAL_RUNNER_TOKEN",
    ):
        value = read_env_value(local_env, key)
        if value is not None:
            os.environ.setdefault(key, value)


def main() -> int:
    load_runner_environment()
    args = parse_args()
    if not is_savefrom_page(args.savefrom_url):
        print("SAVEFROM_URL must use savefrom.net or one of its subdomains.", file=sys.stderr)
        return 2

    args.profile_dir = args.profile_dir.expanduser().resolve()
    args.download_dir = args.download_dir.expanduser().resolve()
    args.profile_dir.mkdir(parents=True, exist_ok=True)
    args.download_dir.mkdir(parents=True, exist_ok=True)

    batch_links: list[str] | None = None
    if args.links_file:
        try:
            args.links_file = args.links_file.expanduser().resolve()
            batch_links = read_link_file(args.links_file)
            completed_keys = read_completed_links(DEFAULT_COMPLETED_LINKS_FILE)
            already_completed = [link for link in batch_links if link_key(link) in completed_keys]
            for completed_link in already_completed:
                remove_link_from_file(args.links_file, completed_link)
            batch_links = [link for link in batch_links if link_key(link) not in completed_keys]
            if already_completed:
                print(f"Removed {len(already_completed)} previously downloaded link(s) from the input file.")
        except (OSError, ValueError) as error:
            print(f"Cannot read links file: {error}", file=sys.stderr)
            return 2
        if not batch_links:
            print("No pending URLs remain in the links file.")
            return 0
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
            args=["--start-minimized"] if args.start_minimized else [],
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
                        record_completed_link(DEFAULT_COMPLETED_LINKS_FILE, source_url)
                        remove_link_from_file(args.links_file, source_url)
                        print(f"Recorded completion and removed link from {args.links_file}")
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
