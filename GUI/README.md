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

## LinkedIn availability check

The `Check LinkedIn` button checks LinkedIn jobs with:

- `status = New`
- calculated `score > 0`
- `source_url` under `linkedin.com`

It extracts the LinkedIn job ID from `source_url`, requests:

`https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/<job-id>`

and sets the job status to `Closed` only when the response contains
`No longer accepting applications`. Requests are spaced by 5 seconds.

The run writes an append-only JSONL log next to the GUI:

`GUI/linkedin_availability_check.log`

Each line is one JSON object. Useful events are `start`, `request`, `response`,
`closed`, `stop_error`, `popup_error`, and `done`. If LinkedIn returns `429`, a
network error, or unexpected HTML, the run stops, shows a popup, and writes the
error to this log. The log is ignored by git.
