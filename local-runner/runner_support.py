from __future__ import annotations

import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse


def safe_filename(name: str, fallback: str = "downloaded-video.mp4") -> str:
    cleaned = re.sub(r"[^a-zA-Z0-9._-]+", "_", Path(name).name).strip("._")
    return (cleaned[:140] or fallback)


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
