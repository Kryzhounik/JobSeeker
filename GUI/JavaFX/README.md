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
GUI's black/yellow icon, copies the JavaFX/Jackson/jpy dependencies into
`target/dependency`, checks the database connection through jpy, runs small
GUI feature and FXML checks, and saves the JDK path in `java-home.txt`. The launcher
does not need Maven at startup and does not leave a console window. Its startup
output and errors go to `launch.log` beside the executable. Rebuild after code
changes or a JDK move.

For development, set `JAVA_HOME`, make sure `mvn.cmd` is on `PATH`, then run:

```powershell
.\run.ps1
```

The project can also be launched directly with `mvn javafx:run` from this
directory. Maven downloads the JavaFX platform modules on the first build.
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

Collect launches the configured Java LinkedIn collector in the background and
shows its accepted/limit progress. Check LinkedIn checks scored New vacancies
sequentially with a five-second interval; its JSONL log is
`GUI/JavaFX/linkedin_availability_check.log`. Refilter uses the existing
`Tools.filter_database` filter implementation through `GUI.client_data` and
`jpy`. Refilter detail previews transitions and applies only checked rows.

The client-data API accepts a database path and primitive values, returning
structured records as JSON. JavaFX calls it through `jpy` and does not open
SQLite directly. Database and network work for these actions runs off the
JavaFX Application Thread.
