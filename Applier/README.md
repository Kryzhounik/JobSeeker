# Applier

Applier prepares job-application forms opened from the existing JavaFX GUI.
It shares the logged-in browser with the independently runnable LinkedIn
collector. Applier first looks for an adapter: a Playwright script written for
the specific application form. If no adapter matches, it uses the existing
Codex Proxy to run Codex CLI with Playwright MCP. There are no adapter scripts
yet, so the first prototype exercises the Codex path. The form stays open for
human review, completion of missing answers, and submission.

This directory currently contains design documents only. The behavior is not
implemented yet. `REQUIREMENTS.md` defines what must work;
`ARCHITECTURE.md` is the temporary implementation design; `PLAN.md` lists the
prototype execution steps.
