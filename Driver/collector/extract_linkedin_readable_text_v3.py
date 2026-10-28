"""Remove known unwanted HTML subtrees, then render the remaining page (v3).

V2 strips markup before filtering page text, so unrelated company cards,
advertising and hiring statistics lose their boundaries and remain in the
result. V3 removes those identified elements together with their descendants
before converting the rest of the HTML into readable text.

Removal rules use element attributes, not the position/order of page sections.
Unrecognized elements retain their text, including company/client introductions
inside the vacancy. There is no required description container, line-based
content filter, tail cutoff or fallback to the v2 extractor. Only v2's Unicode
repair helper is reused.

Standalone benefit cards are retained: they may contain useful job conditions,
such as relocation assistance, that are absent from the main description.

The output keeps headings, paragraph/block breaks and list markers without HTML
attributes or repeated wrapper tags. Nested empty wrappers add no extra blocks.
The HTML tree is built and modified with lxml.html.

The public readable_text(source, raw_path) contract matches v2, including source
metadata. The CLI reads one HTML file and prints text; it does not write to the
database. The collector still imports v2 until explicitly switched to v3.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

from lxml import html

from extract_linkedin_readable_text_v1 import SKIP_TAGS, source_url
from extract_linkedin_readable_text_v2 import repair_mojibake


# These identify LinkedIn UI modules, not arbitrary occurrences of words such
# as "company", "benefits" or "Premium" in the employer's vacancy text.
NOISE_CLASSES = {
    "jobs-company", "job-details-fit-level-card",
    "job-details-fit-level-card-feedback__container",
    "job-details-how-you-match-card__container",
    "job-details-jobs-unified-top-card__sticky-header",
    "job-details-jobs-unified-top-card__top-buttons",
    "jobs-similar-jobs", "jobs-s-apply", "jobs-apply-button", "jobs-save-button",
    "global-nav", "a11y-text", "visually-hidden", "sr-only",
}
NOISE_CLASS_PREFIXES = (
    "jobs-company__", "aiq-premium-insights-", "job-details-fit-level-card__",
    "job-details-people-who-can-help__", "hirer-card__", "scaffold-skeleton",
    "job-description-skeleton__",
)
NOISE_MODULE_PREFIXES = (
    "JobDetails_AboutTheCompany_", "JobDetails_PremiumApplicantInsights_",
    "JobDetails_PremiumCompanyInsights_", "JobMatchRef_",
    "JobDetailsPeopleWhoCanHelpSlot_",
)
NOISE_COMPONENTS = {
    "aboutTheCompanyForJobDetails", "premiumApplicantInsightsForJobDetails",
    "premiumCompanyInsightsForJobDetails", "jobMatch", "peopleWhoCanHelp",
}
NOISE_VIEW_NAMES = {
    "job-details-about-company-module", "premium-jdp-company-insights-upsell",
}
INLINE_TAGS = {
    "a", "abbr", "b", "bdi", "bdo", "cite", "code", "data", "del", "dfn",
    "em", "i", "ins", "kbd", "label", "mark", "q", "rp", "rt", "ruby",
    "s", "samp", "small", "span", "strong", "sub", "sup", "time", "u", "var",
}


def _is_noise(node: html.HtmlElement) -> bool:
    if node.tag in SKIP_TAGS or node.tag == "nav" or node.get("role") == "navigation":
        return True
    classes = node.get("class", "").split()
    if NOISE_CLASSES.intersection(classes) or any(
        name.startswith(NOISE_CLASS_PREFIXES) for name in classes
    ):
        return True
    if any(node.get(key, "").startswith(NOISE_MODULE_PREFIXES)
           for key in ("id", "componentkey")):
        return True
    component = node.get("data-sdui-component", "").rsplit(".", 1)[-1]
    if component in NOISE_COMPONENTS or node.get("data-view-name") in NOISE_VIEW_NAMES:
        return True
    if node.get("data-testid") == "expandable-text-button":
        return True
    if node.tag in {"a", "button"}:
        if node.get("aria-label") in {
            "More options", "Share", "Save the job", "Apply on company website",
        }:
            return True
        if re.match(r"^(?:(?:https?:)?//(?:[\w-]+\.)*linkedin\.com)?/premium/",
                    node.get("href", ""), re.IGNORECASE):
            return True
    return False


def _prune(root: html.HtmlElement) -> None:
    """Remove recognized subtrees anywhere; retain every other element/text."""
    for child in list(root):
        if _is_noise(child):
            # drop_tree preserves following text; separate adjacent words too.
            child.tail = " " + (child.tail or "")
            child.drop_tree()
        else:
            _prune(child)


def _render(root: html.HtmlElement) -> str:
    def element(node: html.HtmlElement, marker: str = "- ") -> str:
        if node.tag == "br":
            return "\n"
        if node.tag == "hr":
            return "\n\n---\n\n"
        parts = [re.sub(r"\s+", " ", node.text or "")]
        number = int(node.get("start", "1")) if node.tag == "ol" and re.fullmatch(
            r"-?\d+", node.get("start", "1")
        ) else 1
        for child in node:
            item_marker = f"{number}. " if node.tag == "ol" else "- "
            parts.append(element(child, item_marker))
            parts.append(re.sub(r"\s+", " ", child.tail or ""))
            if child.tag == "li":
                number += 1
        content = "".join(parts)
        if node.tag in INLINE_TAGS:
            return content
        if not content.strip():
            return ""
        if node.tag == "li":
            return "\n" + marker + content.strip().replace("\n", "\n  ") + "\n"
        if re.fullmatch(r"h[1-6]", node.tag):
            return "\n\n" + "#" * int(node.tag[1]) + " " + content.strip() + "\n\n"
        if node.tag in {"ul", "ol"}:
            return "\n\n" + content.strip("\n") + "\n\n"
        if node.tag == "tr":
            cells = [element(child).strip() for child in node if child.tag in {"td", "th"}]
            return "\n" + " | ".join(cells) + "\n" if cells else content
        if node.tag in {"td", "th"}:
            return content.strip()
        # Unknown wrappers keep both their text and their block boundary.
        # Stripping edge breaks makes div > div > div collapse naturally.
        return "\n\n" + content.strip() + "\n\n"

    lines: list[str] = []
    for raw_line in element(root).splitlines():
        line = " ".join(repair_mojibake(raw_line).split())
        if line:
            # Keep indentation of nested list items and their continuations.
            indent = len(raw_line) - len(raw_line.lstrip(" "))
            lines.append(" " * indent + line)
        elif lines and lines[-1]:
            lines.append("")
    return "\n".join(lines).strip() + "\n"


def readable_text(source: str, raw_path: Path) -> str:
    """Read one HTML file, drop known noise and render all remaining content."""
    raw_html = raw_path.read_text(encoding="utf-8", errors="replace")
    root = html.document_fromstring(raw_html if raw_html.strip() else "<html><body></body></html>",
                                    parser=html.HTMLParser(remove_comments=True, remove_pis=True))
    _prune(root)
    text = _render(root)
    metadata = [f"source: {source}", f"raw_file: {raw_path.as_posix()}"]
    url = source_url(source, raw_path)
    if url:
        metadata.append(f"source_url: {url}")
    return "\n".join(metadata) + "\n\n" + text


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract readable text v3 from one LinkedIn HTML file.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--input", type=Path, required=True)
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="strict")
    sys.stdout.write(readable_text(args.source, args.input))


if __name__ == "__main__":
    main()
