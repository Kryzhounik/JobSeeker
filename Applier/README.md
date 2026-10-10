# Applier

Applier prepares job-application forms opened from the existing JavaFX GUI.
It shares the logged-in browser with the independently runnable LinkedIn
collector. The first prototype uses the existing Codex Proxy to run Codex CLI
with Playwright MCP and fill the opened form. The form stays open for human
review, completion of missing answers, and submission. Site-specific adapters
can be added later.

This directory currently contains design documents only. The behavior is not
implemented yet. `REQUIREMENTS.md` defines what must work;
`ARCHITECTURE.md` is the temporary implementation design; `PLAN.md` lists the
prototype execution steps.
