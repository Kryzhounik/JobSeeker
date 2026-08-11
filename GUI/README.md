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

## Refilter

The refilter workflow uses:

`python Tools/filter_database.py`

The utility loads each `jobs.title` and its stored readable text, passes the
result to the collector filter's top-level `filter(Vacancy)` method, and returns
a JSON list containing each rejected job's ID, title, and filter reason. It
never changes the database itself.

The top `Refilter` button collects rejected jobs and immediately deletes them
through the database layer. The bottom `Refilter detail` button only collects
them and opens a confirmation window with `Title`, `Fit`, `Original`, and
`Match` columns. Its contents are read-only text, so any substring can be
selected with the mouse and copied with `Ctrl+C`. Clicking a title without
dragging opens the vacancy `source_url`; clicking the `Fit` header toggles
ascending/descending sorting. `Confirm` deletes that displayed set; `Cancel` or
closing the window leaves the database unchanged.
