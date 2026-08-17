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

## Settings

Filters, table sorting, and the last sizes of the main and `Refilter detail`
windows are stored in `GUI/jobs_viewer_settings.json`. Resizing is saved after
a short pause and restored the next time the corresponding window opens. The
settings file is ignored by git.

## Filters

The upper-right search area contains an ID search and an `Added >=` date filter.
Dates use the European `DD.MM.YYYY` format; the filter includes jobs added on
or after that date.
Clicking a blue date link in the main table copies that date into the filter.
`Clear` resets both search fields. Runtime status messages are shown in the
bottom-right footer.

## Vacancy detail

The right side starts in `Skills` mode with the technology table and summary.
Every skills-table cell is real selectable text: drag across any substring and
press `Ctrl+C` to copy only that selection. The table headers still sort the
skills. All GUI text areas use the same reusable copyable text control.
The `Text` button replaces that entire area with the stored cleaned vacancy text
from `source_job_texts.readable_text`; the button then reads `Skills` and restores
the original view. The full text is read-only and supports normal selection and
`Ctrl+C`.

## Companies

The `Companies` button opens the normalized company list. The `Company` and
`Blacklisted` headers toggle sorting; blacklist sorting shows blacklisted rows
first on its first click. Each checkbox writes `companies.blacklisted`
immediately, without an Apply button, and is restored if the database update
fails. The window size and last sorting are saved with the other GUI settings.

Company cells in the main jobs table are links. Clicking one opens the same
window, scrolls to that company, and highlights its row.

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

The bottom `Black titles` button opens the active LinkedIn preview-title block
list in Windows Notepad:

`Driver/collector/filtering/linkedin_preview_blocked_titles.txt`

The refilter workflow uses:

`python Tools/filter_database.py`

The utility loads each `jobs.title` and its stored readable text, passes the
result to the collector filter's top-level `filter(Vacancy)` method, and returns
a JSON list containing each rejected job's ID, title, and filter reason. It
never changes the database itself.

The top `Refilter` button collects rejected jobs and immediately deletes them
through the database layer. The bottom `Refilter detail` button only collects
them and opens a confirmation window with `ID`, `Title`, `Fit`, `Original`, and
`Match` columns. Its contents are read-only text, so any substring can be
selected with the mouse and copied with `Ctrl+C`. Clicking a title without
dragging opens the vacancy `source_url`; clicking the `Fit` header toggles
ascending/descending sorting. The mouse wheel scrolls rows, and `Shift` plus the
mouse wheel scrolls horizontally. `Confirm` deletes that displayed set;
`Cancel` or closing the window leaves the database unchanged.
