# JobSeeker GUI

Purpose: user-facing viewer for inspecting SQLite results and manually triaging
job status.

## Launch

Start the GUI by double-clicking `GUI/JobsViewer.exe`. The launcher uses the
embedded Seeker Jobs icon and starts `jobs_viewer.py` through `pythonw.exe`, so
no console window remains open.

To rebuild the launcher and its multi-size Windows icon, run:

`powershell -ExecutionPolicy Bypass -File GUI/build_launcher.ps1`

The build reads `JobsViewerIcon.png`, writes `JobsViewerIcon.ico`, and embeds
the icon into `JobsViewer.exe`.

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

Filters, table sorting, and the last sizes of the main, `Refilter detail`,
`Collected`, `Companies`, `Applications`, and config windows are stored in
`GUI/jobs_viewer_settings.json`. Resizing is saved after a short pause and
restored the next time the corresponding window opens. The settings file is
ignored by git.

All table headers are sortable. Repeated clicks on the active header toggle
ascending and descending order; the header shows `^` or `v`. Native tables and
copyable text grids share the same sortable-table base behavior. Main,
Collected, Companies, and Applications sorting is restored from GUI settings.

In the main and Collected tables, double-click a `Title` cell to open
title-selection mode. Drag across any title fragment and right-click it to use
`Copy`, `Selection to blacklist`, or `Title to blacklist`. The selection action
appends only the selected fragment; the title action appends the complete title.
For the shorter path, right-click a `Title` cell directly and choose
`Title to blacklist`. Blank values and case-insensitive duplicates are not
added. These blacklist actions are available only in the `Title` column.

## Filters

The upper-right search area contains ID, `Date`, and `Reason` filters. Dates use
the European `DD.MM.YYYY` format; the date filter includes jobs added on or
after that date. `Reason` accepts comma-separated reason codes and filters them
with an SQL `IN` condition.
Clicking a blue date link in the main table copies that date into the filter.
Clicking a blue `Reason code` adds it to the filter without duplicates. `Clear`
resets all search fields. Runtime status messages are shown in the bottom-right
footer.

The small gear button in the upper-right corner opens the database-backed
configuration switches from the `config` table. Every row is rendered as a
checkbox; toggling it immediately writes text value `0` or `1` without an Apply
button. The same window contains `LinkedIn collection limit`; Enter or moving
focus away writes the positive integer directly to `limit` in
`Driver/collector/config/linkedin.properties`. The config window size is stored
with the other GUI settings.

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

The `Companies` button opens the normalized company list. The `Priority`,
`Company`, `LinkedIn ID`, `Applications`, and `Blacklisted` headers toggle
sorting. Priority is the first column and stores `0` or `1` in
`companies.priority`; it defaults to `0`. Priority and blacklist checkboxes
write to the database immediately, without an Apply button, and are restored if
the database update fails. Double-click a LinkedIn ID cell to edit it; Enter or
focus-out saves, while Escape cancels. Blank LinkedIn IDs are stored as `NULL`,
while nonblank IDs are unique. The Applications column counts application
records linked to the company's vacancies. The window size and last sorting are
saved with the other GUI settings.

The company list uses one native table. Priority and blacklist checkboxes are
created only for rows currently visible in the viewport, rather than creating
controls for every company in the database.

The form below the company list creates a company from a required name and an
optional LinkedIn ID. `Add` or Enter inserts it immediately and focuses the new
row. Duplicate names and duplicate nonblank LinkedIn IDs are rejected.

Company cells in the main jobs table are links. Clicking one opens the same
window, scrolls to that company, and highlights its row. The link text includes
the total number of application records for that company, for example
`Intellias (6)`.

## Applications

Changing one or several selected vacancies to `Applied` creates one row per
vacancy in `applications` as part of the same database transaction. The row
stores the local application date and starts with application status `Applied`.
The unique `job_id` prevents duplicates if the vacancy is marked `Applied`
again. Migration intentionally does not create rows for vacancies that were
already `Applied` before this feature was installed.

The `Applications` button opens title, company, date, and status columns. Title
links return to the main window, reveal and select that vacancy, and company
links open and focus the matching Companies row. Dates use `DD.MM.YYYY`.
In the main jobs table, an `Applied` status is a link to the matching
application row; opening it scrolls, selects, and focuses that exact record.
Application status is changed immediately by the row selector and supports
`Applied`, `Refused`, and `Confirmed`. The applications list uses one native
table and shows a single in-cell status selector for the selected row, so the
window does not create a separate set of controls for every stored application.
Every column header sorts the list, and the selected sorting is preserved.

## LinkedIn collection

The green `Collect` button is the first toolbar action. It starts the existing
Java collector with `linkedin-collector.jar batch` in a background thread, with
the project root as its working directory. The Java process has no console
window, while its Playwright browser remains visible. During collection the GUI
shows the collector's post-page `accepted X/Y` count and prevents overlapping
Collect, Check LinkedIn, and Refilter operations.

The GUI waits for that exact Java process and reads its final JSON report. A
`complete` result remains in the footer; `login_required`, `blocked`, a nonzero
exit, or malformed output produces an error popup. Collection outcomes continue
to use the collector's existing `linkedin_collection_events` database table;
the GUI does not create another collection log or a login button.

## Collected jobs

The `Collected` button opens the data saved by the collector before analysis.
The table shows source ID, processing stage, collection method, title, normalized
company, collected location, workplace, salary, and source URL. Selecting a row
shows its full cleaned text below the table; `Open` or a double-click opens the
source URL, except that a double-click on `Title` opens title-selection mode.
The `Stage` selector filters by one of the processing statuses present in the
loaded rows, or `All`; the selection is stored in GUI settings. The window has
its own `Refresh` button and also refreshes after a successful GUI collection
run.

The list reads `source_jobs`, `source_job_texts`, and `companies`. It includes
only rows whose collector metadata has been saved, so older text-only rows with
blank stage-one metadata do not fill the window. It does not require a `jobs`
row and therefore exposes collected vacancies before the analyzer starts.

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
`closed`, `skip`, `stop_error`, `popup_error`, and `done`. HTTP `404` leaves the
job status unchanged, logs `skip` with `reason: http_404`, increments the GUI's
`skipped` counter, and continues to the next job after the usual 5-second pause.
It does not show an error popup. Other HTTP errors (including `429`), network
errors, or unexpected HTML still stop the run, show a popup, and write the error
to this log. The log is ignored by git.

## Refilter

The bottom `Black titles` button opens the active LinkedIn preview-title block
list in Windows Notepad:

`Driver/collector/filtering/linkedin_preview_blocked_titles.txt`

The refilter workflow uses:

`python Tools/filter_database.py`

The utility loads each collected title and readable text from
`source_job_texts`, passes them to the collector filter's top-level
`filter(Vacancy)` method, and returns a JSON list containing each rejected
job's ID, title, and filter reason. It never changes the database itself.

The top `Refilter` button collects rejected jobs and immediately deletes them
through the database layer. The bottom `Refilter detail` button only collects
them and opens a confirmation window with a selection checkbox plus `ID`,
`Title`, `Fit`, `Original`, and `Match` columns. Every row is selected by
default; the `All` checkbox selects or clears the whole list. Its contents are
read-only text, so any substring can be selected with the mouse and copied
with `Ctrl+C`. Clicking a title without dragging opens the vacancy `source_url`;
every header toggles ascending/descending sorting. The mouse wheel scrolls
rows, and `Shift` plus the mouse wheel scrolls horizontally. `Confirm` deletes
only the selected jobs; `Cancel` or closing the window leaves the database
unchanged.
