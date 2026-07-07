#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request
from urllib.request import urlopen


ROOT = Path(__file__).resolve().parents[1]
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/149.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.8,ru;q=0.6",
}


def raw_name(url: str, ext: str) -> str:
    linkedin = re.search(r"/jobs/view/(\d+)/?", url)
    if linkedin:
        return linkedin.group(1) + "." + ext

    slug = Path(urlparse(url).path).name or "page"
    return re.sub(r"[^a-zA-Z0-9_.-]+", "_", slug) + "." + ext


def fetch(url: str) -> str:
    request = Request(url, headers=HEADERS)
    with urlopen(request, timeout=30) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def validate_content(source: str, content: str) -> None:
    if source.lower() != "linkedin":
        return

    details_loaded = any(
        marker in content
        for marker in (
            "About the job",
            "Role Overview",
            "Requirements",
            "Key Responsibilities",
        )
    )
    still_loading = "In progress" in content or "progressbar" in content
    if still_loading and not details_loaded:
        raise SystemExit(
            "LinkedIn raw page looks incomplete: details are still loading."
        )
    if not details_loaded:
        raise SystemExit(
            "LinkedIn raw page looks incomplete: job details were not found."
        )


def save_content(
    *,
    source: str,
    url: str,
    content: str,
    out_dir: Path,
    ext: str,
    force: bool,
) -> Path:
    validate_content(source, content)

    pages_dir = out_dir / "pages"
    pages_dir.mkdir(parents=True, exist_ok=True)
    path = pages_dir / raw_name(url, ext)
    if path.exists() and not force:
        print(f"skip existing {path}")
        return path
    path.write_text(content, encoding="utf-8")
    print(f"saved {source} {url} -> {path}")
    return path


def main() -> None:
    parser = argparse.ArgumentParser(description="Save one raw vacancy page.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--out-dir")
    parser.add_argument("--content-file")
    parser.add_argument("--stdin", action="store_true")
    parser.add_argument("--ext", default="html")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    out_dir = Path(args.out_dir) if args.out_dir else ROOT / "data" / "raw" / args.source
    if args.content_file:
        content = Path(args.content_file).read_text(encoding="utf-8", errors="replace")
    elif args.stdin:
        content = sys.stdin.read()
    else:
        if args.source.lower() == "linkedin":
            raise SystemExit(
                "LinkedIn raw pages must come from the logged-in browser. "
                "Use --content-file or --stdin."
            )
        content = fetch(args.url)

    save_content(
        source=args.source,
        url=args.url,
        content=content,
        out_dir=out_dir,
        ext=args.ext,
        force=args.force,
    )


if __name__ == "__main__":
    main()
