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

The main window currently loads the vacancy list through the shared data API
and shows the selected vacancy's fields, source link, skills and full text.
The API accepts a database path and primitive values, returning structured
records as JSON strings. JavaFX invokes `GUI.client_data` through `jpy`; it
does not open SQLite itself or depend on the collector/orchestrator.
