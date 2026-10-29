# JavaFX GUI

This is the Maven project for the JavaFX version of Seeker Jobs. It targets
Java 21, uses OpenJFX controls and FXML, and has `jpy` on its classpath for the
shared Python client-data module at `GUI/client_data.py`.

`ClientData` converts JSON to Java records at the JPy boundary. Auxiliary
tables, status/availability updates, and Refilter use typed DTOs rather than
JSON nodes. Refilter selection keeps immutable `RefilterCandidate` values;
only the adapter serializes the selected list back to Python field names.
Nullable database IDs and Fit stay nullable, while nullable display strings
are normalized to empty strings. `ClientDataContractsSmoke` checks these
contracts without starting Python, opening windows, or accessing a database.
Job-list requests use `JobFilters` with six typed fields; the adapter alone
maps their Java names to Python JSON keys. Company updates have separate
LinkedIn ID, priority, and blacklist methods, so UI handlers do not choose
Python function names or pass untyped values.

## Run on Windows

Install JDK 21 and Maven 3.9 or newer. Build the double-click launcher once:

```powershell
.\build_launcher.ps1 -JavaHome 'C:\path\to\jdk-21'
```

Then double-click `SeekerJobs.exe` in this directory. The build generates this
GUI's icon from `pink_panther_transparent_final.ico`, installs the backend Maven
artifacts through `Driver`, then builds the independent GUI project. It creates
the fat `dist/seeker-gui-javafx.jar`, bundling all Java runtime
dependencies instead of loading them from the repository-root `libs/`.
This lets other Java modules be rebuilt while the GUI stays open on Windows.
It checks the database connection through jpy, runs small
GUI feature and FXML checks, and saves the JDK path in `java-home.txt`. The launcher
does not need Maven at startup and does not leave a console window. Its startup
output and errors go to a separate `logs/launch-*.log` for each run. Rebuild after code
changes or a JDK move.
The normal build does not open test windows. To run the interactive UI check,
add `-UiSmoke` to the build command; it opens the main and auxiliary windows.
The script publishes `SeekerJobs.exe` from a compiled staging file. When an older
launcher is still running, its executable is retained under `target/` until it
can be removed; no second launcher name is required in the GUI directory.
Each launch runs its own temporary copy of the last completed fat jar. Rebuilds
can run while older instances stay open: the next launch uses the new build,
and existing instances keep the old one until exit. Their copies are deleted
when Java exits. Maven clean keeps the last published jar, and publication
replaces it atomically only after packaging completes. Processes launched before
this change need one normal close to switch to this mechanism. Configuration
and Python modules remain external. See [ARCHITECTURE.md](ARCHITECTURE.md).

For development, set `JAVA_HOME`, make sure `mvn.cmd` is on `PATH`, then run:

```powershell
.\run.ps1
```

`run.ps1` builds the backend and GUI, then launches the GUI JAR. The equivalent
commands from the repository root are:

```powershell
mvn -f Driver/pom.xml install
mvn -f GUI/JavaFX/pom.xml clean package
.\GUI\JavaFX\SeekerJobs.exe
```

JavaFX is not a child or reactor module of `Driver`. It uses normal Maven
dependencies on Driver API, Collector result types, and Tools, and imports their managed
versions from the installed Driver POM. After backend changes, run `install`
again before building the GUI; both scripts do this automatically. Maven
downloads the JavaFX platform modules on the first build.
The Python/JNI library paths come from
`Driver/collector/java_linkedin/runtime.properties`.

`SeekerApplication.init()` creates a plain Spring Context on the launcher
thread and obtains one `DriverApi` from `DriverConfiguration`. It passes the
facade to the controller for Batch, direct Collect, Apply, and GPT Eval.
`stop()` closes the context; a JVM shutdown hook also covers JVM termination.
Data connection initialization still runs in the background. Progress handlers are scoped
to each call. Collector settings are read by the backend at operation start,
so changing the Config limit affects the next call on the existing facade.

The main window loads vacancies through the shared data API, displays details,
skills and full text, and supports filters, sorting, copyable text and editable
Fit/Interest. Runs, Companies, Applications, Collected and Config open as separate
windows. Status actions apply to the selected vacancies; Applied creates the
application records and links to them.

Runs displays `linkedin_collection_runs`, newest first: Run ID, overall status,
stage, start/finish times, accepted count, and stop reason. Selecting a row shows
collection status, collection finish time, and the full message below the table.
The divider between the table and Details can be dragged vertically; the status
bar stays at the bottom of the window.
Refresh reloads the data in the background; Run IDs and details are copyable.
Resume calls `DriverApi.resume(runId, handler)` for the selected run in a
background task. It is disabled without a selection, for running/complete runs,
during a table refresh, and while Collect, Batch, or Resume is executing.
Resume uses the main window's shared top-right progress area with its own badge
and the same snapshot display, completion/error states, and Codex-limit handling
as Batch. Finishing or stopping refreshes the Runs and Collected windows when open.

The main window and Runs share `WindowStatusBar`: status/count text is always
at the bottom right with common padding and styling. Other table windows can
reuse this footer rather than positioning their own count labels.

In the main and Collected Title columns, select text and right-click to add
only that fragment to the title blacklist; right-click without a selection to
add the whole title. The bottom Black titles button opens the active file in
Notepad. Reason code cells show descriptions on hover, and the column header
shows the full code reference. Window sizes, table sorts and the Collected
stage filter are saved in `gui-settings.json`.

Collect launches the configured Java LinkedIn collector in the background.
A collapsible panel inside the main window shows Run ID, Stage, Action, the
backend's ordered fields, and Details. Its top-right toolbar toggle shows the
backend's `progress` summary (stage and counters), not the current action, and
expands or minimizes the panel. The usual status text is at the bottom right.
The panel retains the last snapshot on completion or error. Completion changes
only its visual state; the GUI never recalculates or replaces reported counters.
Error details include the failed search or vacancy URL. Card-materialization
errors show the failing card's canonical LinkedIn URL in the same text block.
Collection runs independently of other GUI actions; Collect and Batch are
disabled while either is running. The run ID is sent before browser startup so an interrupted run
can still be identified. Collection does not start the analysis workflow.

Batch runs the complete `DriverApi.processBatch("linkedin")` workflow in a
background task: collection, agent relevance filtering, job facts, candidate
fit, job interest, and saving. Its operation-scoped handler receives
`ProgressSnapshot`; Orchestrator handles Collector events internally. Collect
and Batch use the same `SnapshotProgressPane`. The backend supplies display-ready
stage/action labels and all metrics, choosing which fields belong to the current
stage. Each snapshot replaces the previous fields in iteration order, removing
absent rows. The GUI neither translates stage codes nor interprets outcomes,
and does not infer Saved from the workflow's processed count. Collection completion alone does not
mark Batch complete. Failures retain the run ID and accumulated counters;
finished/failed panels can be dismissed just like Collect. Batch uses the
direct Driver Java API. Other GUI views remain usable. The Batch button has
a brighter green than Collect.

### Collector connection

`backend.properties` selects `collector.mode`:

- `direct` (default): calls `DriverApi.collectBatch(handler)` in the GUI JVM and
  receives immutable `ProgressSnapshot` values through a Java callback.
  GUI data access and Collector share the process-wide `JpyRuntime`.
- `process`: launches the external collector JAR with `collect-batch`. The CLI
  encodes the same progress snapshots as `COLLECTOR_PROGRESS ` JSON lines on
  stderr and writes the final `CollectionReport` to stdout.

Both adapters implement `CollectorClient`; the controller and progress panel
do not depend on the transport. The configuration is read at each Collect
start. An unknown mode is reported as an error rather than falling back.
The direct adapter delegates to the shared Java API; process mode remains a
diagnostic option, not the default path. No server protocol is introduced.
Python stdout redirection belongs only to CLI entry points, not to the direct
library calls.

Collect, Apply, and GPT Eval use the session's shared `DriverApi` inside their
existing background tasks. Construction does not start a browser or Python.
Apply and GPT Eval call the Java API directly,
not a CLI; GPT Eval passes the project root without changing JVM properties.
Database operations remain in `ClientData`.

## Background-process panels

New background-process progress views extend `BackgroundProcessPane`, as
`SnapshotProgressPane` does, and receive the main controller's shared
`backgroundProcesses` area. The base constructor takes a title and field
names and creates the copyable fields, details area, toolbar toggle, and
collapsible panel. All toggles appear at the top right; selecting one panel
collapses the previously selected panel.

Expanded panels can be resized by dragging their left/bottom edges or the
bottom-left corner. The right/top position remains anchored, Details grows
with the panel, and manual dimensions survive minimization within the session.
Panel size is constrained by the main window's available area.

Collect/Batch use `SnapshotProgressPane.update(ProgressSnapshot)` for universal
rendering. `replaceExtraFields(...)` replaces the ordered dynamic rows without
altering the fixed fields. Fixed-form panels may continue to use `setField(...)`.

Subclasses call `beginProcess()`, `setField(...)`, `setDetails(...)`,
`setSummary(...)`, and `setContext(...)` on the JavaFX Application Thread.
`setProcessState(...)` selects `RUNNING` (light green), `FAILED` (red), `WAITING`
(yellow), or `COMPLETED` (green). Finished and failed processes show dismiss
buttons on both the badge and panel. Either button hides both, and starting
the next run shows them again. Only the toolbar badge uses state colors;
the expanded panel keeps the normal application theme. Waiting must be reported explicitly by the
process or its controller; the base view does not guess whether it has hung.
Changing state or minimizing preserves the displayed statistics and details.
Execution remains in the existing background tasks and runner classes.

Visual state and operation completion are separate: `setProcessState(...)`
changes appearance, while `finishProcess(...)` also enables dismissal.
An ordinary WAITING panel is still active. A Codex usage limit stops the
Collect/Batch task and releases its buttons, but leaves a yellow WAITING panel
with the last stage, action, metrics, reason, and retry time when known.
Both dismiss buttons work for that stopped panel. A manual restart resets it;
no automatic retry is scheduled. Limits are identified by
`UsageLimitExceededException`, not by matching error messages.

## Other Actions

Check LinkedIn checks the original vacancy
pages for scored New vacancies and rechecks unavailable/closed records,
sequentially with an anonymous HTTP client. The interval starts at five seconds,
doubles after each 429 (retrying the same vacancy), and halves after ten
consecutive successful checks, between five seconds and two minutes. A 429
after a two-minute pause stops the check. Availability is stored separately
from working statuses: a 404 increments the failure count, three consecutive
404 checks mark the source deleted, and a valid open vacancy resets the count.
Explicitly closed pages are marked closed. Rate limits, authentication pages,
and network errors do not change availability. The Unavailable checkbox shows
closed, unavailable, and deleted records; they are hidden when it is unchecked.
Its JSONL log is
`GUI/JavaFX/linkedin_availability_check.log`. The completion/stopped dialog shows
elapsed time, checked/total vacancies, the resulting availability counts,
skipped records, HTTP requests and 429 responses. Response logs include the
final URL and Retry-After; completion logs contain the same run totals.
Refilter uses the existing
`Tools.filter_database` filter implementation through `GUI.client_data` and
`jpy`. Refilter detail previews transitions and applies only checked rows.

GPT Eval keeps the full answer in its shared top-right panel instead of opening
a separate result window. The panel contains the vacancy URL and a copyable,
wrapped answer; exception details appear only on failure. If saving fails after
evaluation, the received answer remains readable alongside the error.

Apply opens the selected vacancy in the shared logged-in browser, starts
application preparation in the background, and leaves the form tab open for
review and manual submission. Apply is disabled for vacancies marked Easy Apply
by the collector. Like GPT Eval, it shows an Apply badge and expandable panel in
the shared top-right progress area, retaining the original Job ID/URL and the
backend result or error. The badge is light green while preparing, green when
the task returns, and red on an exception; a finished preparation is not a claim
that an application was submitted. Finished panels can be dismissed or resized
like the other background panels. Approved answers can be added to
`Data/applier/approved_answers.md`.

The client-data API accepts a database path and primitive values, returning
structured records as JSON. JavaFX calls it through `jpy` and does not open
SQLite directly. Database and network work for these actions runs off the
JavaFX Application Thread.
