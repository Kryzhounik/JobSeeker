"""Remove known unwanted HTML subtrees, then render the remaining page (v3).

V2 strips markup before filtering page text, so unrelated company cards,
advertising and hiring statistics lose their boundaries and remain in the
result. V3 removes those identified elements together with their descendants
before converting the rest of the HTML into readable text.

Blocks are recognized by semantic attributes or UI headings and controls, not
source-job IDs, hashed classes or fixed positions in the page.
Unrecognized elements retain their text, including company/client introductions
inside the vacancy. Recognized job-alert/similar-jobs modules start the page
tail: remove that module and everything following it, as v2's text markers did.
There is no required description container or fallback to the v2 extractor.
Only v2's Unicode repair helper is reused.

Standalone benefit cards are retained: they may contain useful job conditions,
such as relocation assistance, that are absent from the main description.

The output keeps headings, paragraph/block breaks and list markers without HTML
attributes or repeated wrapper tags. Nested empty wrappers add no extra blocks.
The HTML tree is built and modified with lxml.html.
V2's interface/control labels, AI prompts, recruiter and sign-in sections are
handled as HTML elements/cards. Consecutive repeated lines collapse as in v2.

The public readable_text(source, raw_path) contract matches v2: return only the
page text, without source, raw_file or source_url headers. The CLI reads one
HTML file and prints text; it does not write to the database. The collector
imports v3 as its active HTML cleaner.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

from lxml import html

from extract_linkedin_readable_text_v1 import SKIP_TAGS
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
    "global-nav", "a11y-text", "visually-hidden", "sr-only", "job-assessment",
}
NOISE_CLASS_PREFIXES = (
    "jobs-company__", "aiq-premium-insights-", "job-details-fit-level-card__",
    "job-details-people-who-can-help__", "hirer-card__", "scaffold-skeleton",
    "job-description-skeleton__", "contextual-sign-in-modal",
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
# Module names are stable; the source-job ID appended by LinkedIn is ignored.
TAIL_MODULE_PREFIXES = ("JobDetails_JobAlertToggle_", "JobDetailsSimilarJobsSlot_")
TAIL_COMPONENTS = {"jobAlertToggle", "similarJobs"}
TAIL_CLASSES = {"show-more-less-html__button--more"}
# V2's navigation/button labels; match controls, not arbitrary employer prose.
UI_CONTROL_LABELS = {
    "Home", "My Network", "Jobs", "Messaging", "Me", "For Business",
    "Try Premium for $0", "Apply", "Save", "Show match details",
    "Tailor my resume", "Help me stand out", "Show all", "Message",
}
UI_MESSAGE_PREFIXES = ("Get AI-powered advice on this job", "Easy Apply is now LinkedIn Apply.")
UI_CARD_CONTROLS = {
    "Use AI to assess how you fit": {"Show match details", "Tailor my resume", "Help me stand out"},
    # Removing a hiring card also removes its Job poster/Message labels.
    "People you can reach out to": {"Message"},
    "Meet the hiring team": {"Message"},
}
PERSONALIZED_TIPS_TITLE = "Get personalized tips to stand out to hirers"
PERSONALIZED_TIPS_DESCRIPTION = "Find jobs where you’re a top applicant and tailor your resume with the help of AI."
INLINE_TAGS = {
    "a", "abbr", "b", "bdi", "bdo", "cite", "code", "data", "del", "dfn",
    "em", "i", "ins", "kbd", "label", "mark", "q", "rp", "rt", "ruby",
    "s", "samp", "small", "span", "strong", "sub", "sup", "time", "u", "var",
}


def _label(node: html.HtmlElement) -> str:
    return " ".join("".join(node.itertext()).split())


def _inside_description(node: html.HtmlElement) -> bool:
    """Protect authored wording when matching text-based UI signatures."""
    for parent in (node, *node.iterancestors()):
        if {"jobs-box__html-content", "show-more-less-html__markup"}.intersection(parent.get("class", "").split()):
            return True
        if parent.get("data-sdui-component", "").rsplit(".", 1)[-1] == "aboutTheJob":
            return True
        if any(parent.get(key, "").startswith("JobDetails_AboutTheJob_") for key in ("id", "componentkey")):
            return True
    return False


def _remove_ui_cards(root: html.HtmlElement) -> None:
    """Recognize older cards by their heading plus controls, not hash classes."""
    for heading in root.xpath(".//h2 | .//h3"):
        controls = UI_CARD_CONTROLS.get(_label(heading))
        if not controls or _inside_description(heading):
            continue
        for card in heading.iterancestors():
            if card.tag in {"body", "html"} or len(card.xpath(".//h1 | .//h2 | .//h3")) != 1:
                break
            if any(_label(control) in controls for control in card.xpath(".//a | .//button | .//*[@role='button']")):
                card.tail = " " + (card.tail or "")
                card.drop_tree()
                break
    # This SDUI promotion uses paragraphs instead of a heading and its
    # auto-component UUID/classes change. Match the complete card signature.
    for title in root.xpath(".//p[normalize-space(.)=$title]", title=PERSONALIZED_TIPS_TITLE):
        card = title.getparent()
        if (_inside_description(title) or card is None
                or not card.xpath("./p[normalize-space(.)=$description]", description=PERSONALIZED_TIPS_DESCRIPTION)
                or not card.xpath(".//a[contains(@href, '/premium/')]")):
            continue
        card.tail = " " + (card.tail or "")
        card.drop_tree()


def _remove_promoted_badges(root: html.HtmlElement) -> None:
    """Remove the promotion badge, keeping location and adjacent metadata."""
    badges = root.xpath(".//span[normalize-space(.)=$label or normalize-space(.)=concat($label, ' ·')]",
                        label="Promoted by hirer")
    for badge in badges:
        if _inside_description(badge):
            continue
        following = badge.getnext()
        if following is not None and _label(following) == "·":
            following.drop_tree()
        badge.tail = " " + re.sub(r"^\s*·\s*", " ", badge.tail or "")
        badge.drop_tree()


def _is_noise(node: html.HtmlElement) -> bool:
    if (node.tag in SKIP_TAGS or node.tag == "nav" or node.get("role") == "navigation"
            or node.get("aria-label") == "Skip navigation menu"):
        return True
    classes = node.get("class", "").split()
    if NOISE_CLASSES.intersection(classes) or any(
        name.startswith(NOISE_CLASS_PREFIXES) for name in classes
    ):
        return True
    if "artdeco-card" in classes and not _inside_description(node):
        headings = node.xpath(".//h1 | .//h2 | .//h3")
        if len(headings) == 1 and _label(headings[0]) == "Assessing your job match":
            return True
    if any(node.get(key, "").startswith(NOISE_MODULE_PREFIXES)
           for key in ("id", "componentkey")):
        return True
    component = node.get("data-sdui-component", "").rsplit(".", 1)[-1]
    if component in NOISE_COMPONENTS or node.get("data-view-name") in NOISE_VIEW_NAMES:
        return True
    if node.get("data-testid") == "expandable-text-button":
        return True
    # V2 notification counters belong to the toast section, including nonzero
    # counts. The section's data-testid remains useful across hashed layouts.
    if node.get("data-testid") == "toasts-title" or (
        node.tag == "section" and node.xpath("./*[@data-testid='toasts-title']")
    ):
        return True
    if (node.get("role") == "dialog" and not node.xpath(".//*[@role='dialog']")
            and node.xpath(".//*[contains(@class, 'contextual-sign-in-modal')]")):
        return True
    if node.tag in {"a", "button"} or node.get("role") == "button":
        if node.get("aria-label") in {
            "More options", "Share", "Save the job", "Apply on company website",
        }:
            return True
        if re.match(r"^(?:(?:https?:)?//(?:[\w-]+\.)*linkedin\.com)?/premium/",
                    node.get("href", ""), re.IGNORECASE):
            return True
        label = _label(node)
        if not _inside_description(node) and (
            label in UI_CONTROL_LABELS
            or (label.startswith("Skip to ") and node.get("href", "").startswith("#"))
        ):
            return True
    if node.tag in {"p", "span", "div", "h2", "h3"} and not _inside_description(node):
        # Only a single text block with inline markup; never discard a larger
        # wrapper merely because its first paragraph contains a known notice.
        if all(child.tag in INLINE_TAGS or child.tag == "br" for child in node.iterdescendants()):
            label = _label(node)
            if label.startswith(UI_MESSAGE_PREFIXES) or label == "Use AI to assess how you fit":
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


def _is_tail_start(node: html.HtmlElement) -> bool:
    if any(node.get(key, "").startswith(TAIL_MODULE_PREFIXES) for key in ("id", "componentkey")):
        return True
    if node.get("data-sdui-component", "").rsplit(".", 1)[-1] in TAIL_COMPONENTS:
        return True
    if TAIL_CLASSES.intersection(node.get("class", "").split()):
        return True
    # V2's "Looking for talent?" is a recruiter advertisement, identified by
    # its sidebar and job-posting link rather than employer wording.
    return (node.tag == "aside" and not _inside_description(node)
            and _label(node).startswith("Looking for talent?")
            and bool(node.xpath(".//a[contains(@href, '/talent/job-posting-redirect/')]")))


def _trim_tail(root: html.HtmlElement) -> None:
    """Discard the first recognized tail module and all later document content."""
    boundary = next((node for node in root.iterdescendants() if _is_tail_start(node)), None)
    if boundary is None:
        return
    # Later content may be outside the module's immediate parent. Remove later
    # siblings and following text at every ancestor level, keeping earlier text.
    for node in (boundary, *boundary.iterancestors()):
        node.tail = None
        for following in list(node.itersiblings()):
            following.getparent().remove(following)
    boundary.getparent().remove(boundary)


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
    previous = ""
    for raw_line in element(root).splitlines():
        line = " ".join(repair_mojibake(raw_line).split())
        if line:
            if line == previous:
                continue
            previous = line
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
    _remove_ui_cards(root)
    _remove_promoted_badges(root)
    _prune(root)
    _trim_tail(root)
    return _render(root)


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
