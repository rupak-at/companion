from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlparse


def safe_filename(name: str, fallback: str = "downloaded-video.mp4") -> str:
    cleaned = re.sub(r"[^a-zA-Z0-9._-]+", "_", Path(name).name).strip("._")
    return (cleaned[:140] or fallback)


def is_savefrom_page(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower().rstrip(".")
    return host == "savefrom.net" or host.endswith(".savefrom.net")


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
