# JobSeeker GUI

Purpose: user-facing viewer for inspecting SQLite results and manually triaging
job status.

The GUI is for the human user. It is not an automation driver for Codex/agents.

Agents must not use the GUI to run the pipeline, inspect batch state, validate
data, or mutate records. Use project files, SQLite queries, and workflow scripts
instead:

- LinkedIn raw files: `../Data/raw/linkedin/pages/*.html`
- database: `../Data/jobs.sqlite`
- views/schema: `../Driver/db/schema.sql`
- save entrypoint: `../Driver/db/save.py`

Only open the GUI when the user explicitly asks to view the interface or debug a
GUI-specific problem.
