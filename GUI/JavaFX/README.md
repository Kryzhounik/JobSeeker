# JavaFX GUI

This is the Maven project for the JavaFX version of Seeker Jobs. It targets
Java 21, uses OpenJFX controls and FXML, and has `jpy` on its classpath for the
shared Python client-data module at `GUI/client_data.py`.

## Run on Windows

Install JDK 21 and Maven 3.9 or newer. Set `JAVA_HOME` to the JDK directory and
make sure `mvn.cmd` is on `PATH`. The Python installation configured for `jpy`
must also be available to the Java process. Then double-click `run.cmd`, or run:

```powershell
.\run.ps1
```

The project can also be launched directly with `mvn javafx:run` from this
directory. Maven downloads the JavaFX platform modules on the first build.

The main window loads vacancies through the shared data API, displays details,
skills and full text, and supports filters, sorting, copyable text and editable
Fit/Interest. Companies, Applications, Collected and Config open as separate
windows. Status actions apply to the selected vacancies; Applied creates the
application records and links to them.

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
