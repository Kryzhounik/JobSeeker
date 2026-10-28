"""Codex-specific failures shared by the JPy and process transports."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json


class UsageLimitExceededError(Exception):
    def __init__(self, message: str, retry_at: datetime | None = None) -> None:
        super().__init__(message)
        self.retry_at = retry_at

    def payload(self) -> dict:
        return {
            "code": "usage_limit",
            "message": str(self),
            "retry_at": self.retry_at.astimezone(timezone.utc).isoformat() if self.retry_at else None,
        }


def usage_limit_error(errors: list[str], now: datetime) -> UsageLimitExceededError | None:
    for text in errors:
        for line in text.splitlines():
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                event = {"message": line}
            if not isinstance(event, dict):
                continue
            details = event.get("error") or event
            if not isinstance(details, dict):
                continue
            message = str(details.get("message") or "")
            normalized = message.replace("\u2019", "'").lower()
            code = details.get("code") or details.get("type")
            if code not in {"usage_limit_reached", "usage_limit_exceeded"} and "you've hit your usage limit" not in normalized:
                continue
            reset = details.get("retry_at") or details.get("resets_at")
            retry_at = _reset_time(reset) if reset is not None else _message_time(message, now)
            diagnostics = [value for value in errors if value.startswith("CLI diagnostics: ")]
            return UsageLimitExceededError("\n".join((message, *diagnostics)), retry_at)
    return None


def _reset_time(value) -> datetime | None:
    try:
        if isinstance(value, (int, float)):
            return datetime.fromtimestamp(value, timezone.utc)
        parsed = datetime.fromisoformat(str(value))
        return parsed if parsed.tzinfo is not None else None
    except (ValueError, OverflowError, OSError):
        return None


def _message_time(message: str, now: datetime) -> datetime | None:
    marker = "try again at "
    index = message.lower().find(marker)
    if index < 0:
        return None
    value = message[index + len(marker):].strip().rstrip(".")
    for pattern in ("%I:%M %p", "%H:%M", "%b %d, %Y %I:%M %p", "%B %d, %Y %I:%M %p"):
        try:
            parsed = datetime.strptime(value, pattern)
        except ValueError:
            continue
        if parsed.year == 1900:
            parsed = now.replace(hour=parsed.hour, minute=parsed.minute, second=0, microsecond=0)
            if parsed < now.replace(second=0, microsecond=0):
                parsed += timedelta(days=1)
        else:
            parsed = parsed.replace(tzinfo=now.tzinfo)
        return parsed
    return None
