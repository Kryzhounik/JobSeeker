# JavaFX GUI

This is the Maven project for the JavaFX version of Seeker Jobs. It targets
Java 21, uses OpenJFX controls and FXML, and has `jpy` on its classpath for the
shared Python client-data module at `GUI/client_data.py`.

## Run on Windows

Install JDK 21 and Maven 3.9 or newer. Build the double-click launcher once:

```powershell
.\build_launcher.ps1 -JavaHome 'C:\path\to\jdk-21'
```

Then double-click `SeekerJobs.exe` in this directory. The build generates this
GUI's icon from `pink_panther_transparent_final.ico`, installs the backend Maven
artifacts through `Driver`, then builds the independent GUI project. It creates
the thin `seeker-gui-javafx.jar` in this directory and copies runtime dependencies
to the repository-root `libs/`. It checks the database connection through jpy, runs small
GUI feature and FXML checks, and saves the JDK path in `java-home.txt`. The launcher
does not need Maven at startup and does not leave a console window. Its startup
output and errors go to `launch.log` beside the executable. Rebuild after code
changes or a JDK move.
The normal build does not open test windows. To run the interactive UI check,
add `-UiSmoke` to the build command; it opens the main and auxiliary windows.
If `SeekerJobs.exe` is open during a rebuild, the script writes
`SeekerJobs-updated.exe` beside it instead; both launch the current JavaFX
JAR through `javaw -jar`, with dependencies resolved from its manifest.

For development, set `JAVA_HOME`, make sure `mvn.cmd` is on `PATH`, then run:

```powershell
.\run.ps1
```

`run.ps1` builds the backend and GUI, then launches the GUI JAR. The equivalent
commands from the repository root are:

```powershell
mvn -f Driver/pom.xml install
mvn -f GUI/JavaFX/pom.xml clean package
java -jar GUI/JavaFX/seeker-gui-javafx.jar
```

JavaFX is not a child or reactor module of `Driver`. It uses normal Maven
dependencies on Collector, Applier, ChatEvaluator, and Tools, and imports their managed
versions from the installed Driver POM. After backend changes, run `install`
again before building the GUI; both scripts do this automatically. Maven
downloads the JavaFX platform modules on the first build.
The Python/JNI library paths come from
`Driver/collector/java_linkedin/runtime.properties`.

The main window loads vacancies through the shared data API, displays details,
skills and full text, and supports filters, sorting, copyable text and editable
Fit/Interest. Companies, Applications, Collected and Config open as separate
windows. Status actions apply to the selected vacancies; Applied creates the
application records and links to them.

In the main and Collected Title columns, select text and right-click to add
only that fragment to the title blacklist; right-click without a selection to
add the whole title. The bottom Black titles button opens the active file in
Notepad. Reason code cells show descriptions on hover, and the column header
shows the full code reference. Window sizes, table sorts and the Collected
stage filter are saved in `gui-settings.json`.

Collect launches the configured Java LinkedIn collector in the background.
A collapsible panel inside the main window shows the run ID, current stage, location, search
mode, page, and accepted/limit, preview-skip, content-rejection, and failure
counts. Its top-right toolbar toggle shows the accepted/limit counter and
expands or minimizes the panel. The usual status text is at the bottom right.
The panel retains the final result or error and accumulated statistics.
Error details include the failed search or vacancy URL. Card-materialization
errors show the failing card's canonical LinkedIn URL in the same text block.
Collection runs independently of other GUI actions; only another Collect is
disabled while it is running. The run ID is sent before browser startup so an interrupted run
can still be identified. Collection does not start the analysis workflow.

### Collector connection

`backend.properties` selects `collector.mode`:

- `direct` (default): calls `LinkedInApplication.batch()` in the GUI JVM and
  receives immutable `CollectionProgress` snapshots through a Java callback.
  GUI data access and Collector share the process-wide `JpyRuntime`.
- `process`: preserves the external runnable-jar launch. The CLI encodes the
  same progress snapshots as `COLLECTOR_PROGRESS ` JSON lines on stderr and
  writes the final `CollectionReport` to stdout.

Both adapters implement `CollectorClient`; the controller and progress panel
do not depend on the transport. The configuration is read at each Collect
start. An unknown mode is reported as an error rather than falling back.
A future client for a persistent Driver process can implement this boundary;
no server protocol or general Driver facade is introduced here.
Python stdout redirection belongs only to CLI entry points, not to the direct
library calls.

## Background-process panels

New background-process progress views extend `BackgroundProcessPane`, as
`CollectorProgressPane` does, and receive the main controller's shared
`backgroundProcesses` area. The base constructor takes a title and field
names and creates the copyable fields, details area, toolbar toggle, and
collapsible panel. All toggles appear at the top right; selecting one panel
collapses the previously selected panel.

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

Apply opens the selected vacancy in the shared logged-in browser, starts
application preparation in the background, and leaves the form tab open for
review and manual submission. Apply is disabled for vacancies marked Easy Apply
by the collector. Approved answers can be added to
`Data/applier/approved_answers.md`.

The client-data API accepts a database path and primitive values, returning
structured records as JSON. JavaFX calls it through `jpy` and does not open
SQLite directly. Database and network work for these actions runs off the
JavaFX Application Thread.
