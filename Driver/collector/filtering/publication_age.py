"""Publication age from vacancy metadata, never from its description."""

from datetime import date, datetime, timezone
from html.parser import HTMLParser
import re


HEADER_CLASSES = {
    "job-details-jobs-unified-top-card__tertiary-description-container",
    "jobs-unified-top-card__primary-description",
    "jobs-unified-top-card__posted-date",
    "posted-time-ago__text",
}
VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "wbr"}
RELATIVE_AGE = re.compile(r"\b(\d+)\+?\s+(minute|hour|day|week|month|year)s?\s+ago\b", re.I)


class PublicationDateParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.depth = 0
        self.parts: list[str] = []
        self.dates: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        in_header = self.depth > 0 or bool(HEADER_CLASSES.intersection((values.get("class") or "").split()))
        if values.get("itemprop") == "datePosted" or (tag == "time" and in_header):
            value = values.get("datetime") or values.get("content")
            if value:
                self.dates.append(value)
        if in_header and tag not in VOID_TAGS:
            self.depth += 1

    def handle_endtag(self, tag: str) -> None:
        if self.depth and tag not in VOID_TAGS:
            self.depth -= 1

    def handle_data(self, data: str) -> None:
        if self.depth:
            self.parts.append(data)


def publication_age_days(html: str, today: date | None = None) -> int | None:
    parser = PublicationDateParser()
    parser.feed(html)
    today = today or datetime.now(timezone.utc).date()
    for value in parser.dates:
        try:
            return (today - date.fromisoformat(value[:10])).days
        except ValueError:
            continue
    match = RELATIVE_AGE.search(" ".join(parser.parts))
    if match:
        days_per_unit = {"minute": 0, "hour": 0, "day": 1, "week": 7, "month": 30, "year": 365}
        return int(match[1]) * days_per_unit[match[2].lower()]
    return None
