from __future__ import annotations

import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse


def safe_filename(name: str, fallback: str = "downloaded-video.mp4") -> str:
    cleaned = re.sub(r"[^a-zA-Z0-9._-]+", "_", Path(name).name).strip("._")
    return (cleaned[:140] or fallback)


def available_download_path(directory: Path, suggested_name: str, fallback: str = "downloaded-video.mp4") -> Path:
    filename = safe_filename(suggested_name, fallback)
    candidate = directory / filename
    if not candidate.exists():
        return candidate
    stem = Path(filename).stem
    suffix = Path(filename).suffix
    copy_number = 2
    while True:
        candidate = directory / f"{stem}_{copy_number}{suffix}"
        if not candidate.exists():
            return candidate
        copy_number += 1


def is_savefrom_page(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower().rstrip(".")
    return host == "savefrom.net" or host.endswith(".savefrom.net")


def is_savefrom_interstitial(url: str) -> bool:
    parsed = urlparse(url)
    return is_savefrom_page(url) and parsed.path.lower().rstrip("/").endswith("/user.php")


def read_env_value(path: Path, key: str) -> str | None:
    if not path.exists():
        return None
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        if name.strip() == key:
            return value.strip().strip('"\'')
    return None


def read_link_file(path: Path) -> list[str]:
    links: list[str] = []
    seen: set[str] = set()
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), start=1):
        link = raw_line.strip()
        if not link or link.startswith("#"):
            continue
        parsed = urlparse(link)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError(f"Invalid URL on line {line_number}: {link}")
        if link not in seen:
            seen.add(link)
            links.append(link)
    return links


def link_key(link: str) -> str:
    video_id = re.search(r"/video/(\d+)", link)
    return f"video:{video_id.group(1)}" if video_id else link


def score_download_candidate(label: str, href: str, has_download_attribute: bool, in_result: bool) -> int | None:
    combined = f"{label} {href}".lower()
    if not href or href == "#" or any(word in combined for word in ("install", "helper", "app now")):
        return None
    watermark_free = any(word in combined for word in ("no watermark", "without watermark", "watermark-free", "nowm"))
    if "watermark" in combined and not watermark_free:
        return None
    is_media_url = bool(re.search(r"\.mp4(?:$|[?#])", href, re.IGNORECASE))
    if not has_download_attribute and not is_media_url and not (in_result and "download" in label.lower()):
        return None
    score = 250 if watermark_free else 0
    score += 100 if has_download_attribute else 0
    score += 90 if is_media_url else 0
    score += 60 if in_result else 0
    score += 35 if "download" in label.lower() else 0
    return score


def is_processing_error(message: str) -> bool:
    normalized = " ".join(message.lower().split())
    return any(
        phrase in normalized
        for phrase in (
            "try again",
            "something went wrong",
            "could not process",
            "couldn't process",
            "link is invalid",
            "video was not found",
            "unable to download",
        )
    )


def read_completed_links(path: Path) -> set[str]:
    if not path.exists():
        return set()
    return {link_key(line.strip()) for line in path.read_text(encoding="utf-8-sig").splitlines() if line.strip()}


def record_completed_link(path: Path, link: str) -> None:
    if link_key(link) in read_completed_links(path):
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as completed:
        completed.write(f"{link}\n")
        completed.flush()
        os.fsync(completed.fileno())


def remove_link_from_file(path: Path, completed_link: str) -> None:
    original = path.read_text(encoding="utf-8-sig")
    remaining_lines = [line for line in original.splitlines(keepends=True) if line.strip() != completed_link]
    mode = path.stat().st_mode
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary:
            temporary.write("".join(remaining_lines))
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary_name = temporary.name
        os.chmod(temporary_name, mode)
        os.replace(temporary_name, path)
    finally:
        if temporary_name and os.path.exists(temporary_name):
            os.unlink(temporary_name)
