# Applier

Applier prepares job-application forms opened from the existing JavaFX GUI.
It shares the logged-in browser with the independently runnable LinkedIn
collector. Form-specific adapters can handle known sites; Codex CLI with
Playwright MCP handles forms without an adapter. The filled form stays open for
human review and submission.

This directory currently contains design documents only. The behavior is not
implemented yet. `REQUIREMENTS.md` defines what must work;
`ARCHITECTURE.md` is the temporary implementation design.
