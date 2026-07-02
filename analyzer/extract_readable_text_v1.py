from __future__ import annotations

import argparse
import re
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SKIP_TAGS = {"head", "script", "style", "svg", "noscript", "template"}
BLOCK_TAGS = {
    "address",
    "article",
    "aside",
    "blockquote",
    "br",
    "dd",
    "div",
    "dl",
    "dt",
    "fieldset",
    "figcaption",
    "figure",
    "footer",
    "form",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "header",
    "hr",
    "li",
    "main",
    "nav",
    "ol",
    "p",
    "pre",
    "section",
    "table",
    "td",
    "th",
    "tr",
    "ul",
}
TAIL_MARKERS = (
    "Set alert for similar jobs",
    "More jobs",
    "Looking for talent?",
)


class TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.skip_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in SKIP_TAGS:
            self.skip_depth += 1
            return
        if self.skip_depth == 0 and tag in BLOCK_TAGS:
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in SKIP_TAGS and self.skip_depth > 0:
            self.skip_depth -= 1
            return
        if self.skip_depth == 0 and tag in BLOCK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data: str) -> None:
        if self.skip_depth == 0:
            self.parts.append(data)

    def text(self) -> str:
        return "".join(self.parts)


def normalize_text(text: str) -> str:
    text = text.replace("\xa0", " ")
    lines = []
    previous = ""
    for raw_line in text.splitlines():
        line = re.sub(r"\s+", " ", raw_line).strip()
        if not line:
            continue
        if line == previous:
            continue
        lines.append(line)
        previous = line
    for index, line in enumerate(lines):
        if line in TAIL_MARKERS:
            lines = lines[:index]
            break
    return "\n".join(lines).strip() + "\n"


def source_url(source: str, raw_path: Path) -> str:
    if source == "linkedin" and raw_path.stem.isdigit():
        return f"https://www.linkedin.com/jobs/view/{raw_path.stem}/"
    return ""


def readable_text(source: str, raw_path: Path) -> str:
    parser = TextExtractor()
    parser.feed(raw_path.read_text(encoding="utf-8", errors="replace"))
    text = normalize_text(parser.text())
    metadata = [
        f"source: {source}",
        f"raw_file: {raw_path.as_posix()}",
    ]
    url = source_url(source, raw_path)
    if url:
        metadata.append(f"source_url: {url}")
    return "\n".join(metadata) + "\n\n" + text


def input_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path]
    return sorted(input_path.glob("*.html"))


def output_path(raw_path: Path, output_dir: Path) -> Path:
    return output_dir / (raw_path.stem + ".txt")


def convert(source: str, input_path: Path, output_dir: Path, force: bool, limit: int) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    paths = input_paths(input_path)
    if limit > 0:
        paths = paths[:limit]

    for raw_path in paths:
        out_path = output_path(raw_path, output_dir)
        if out_path.exists() and not force:
            print(f"skip existing {out_path}")
            continue

        html_chars = raw_path.stat().st_size
        text = readable_text(source, raw_path)
        out_path.write_text(text, encoding="utf-8")
        text_chars = len(text)
        approx_tokens = max(1, text_chars // 4)
        print(
            f"saved {out_path} "
            f"html_chars={html_chars} text_chars={text_chars} "
            f"approx_tokens={approx_tokens}"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract readable text from raw job HTML.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--input")
    parser.add_argument("--out-dir")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args()

    input_path = (
        Path(args.input)
        if args.input
        else ROOT / "data" / "raw" / args.source / "pages"
    )
    output_dir = (
        Path(args.out_dir)
        if args.out_dir
        else ROOT / "data" / "readable" / args.source / "pages"
    )
    convert(args.source, input_path, output_dir, args.force, args.limit)


if __name__ == "__main__":
    main()
