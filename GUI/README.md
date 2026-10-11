# JobSeeker GUI

This directory contains the desktop interface for reviewing the job data stored
by JobSeeker. It lets a person browse analyzed and collected vacancies, inspect
their details and source text, filter and sort results, and manually manage
vacancy statuses, companies, and applications. It also provides controls for
starting existing collection and maintenance workflows and viewing their
progress.

`JavaFX/` contains the current GUI. `Tkinter/` contains the legacy version.
This directory is the interactive front end to the project's job data and
workflows.

## Where to look

- `JavaFX/README.md` — how to build and use the current GUI.
- `AGENTS.md` — development rules and guidance for resolving unclear behavior.
- `Tkinter/README.md` — how the legacy GUI works, including its launcher, settings, and implementation-specific details.

## Shared Client Data

`client_data.py` contains the database requests used by GUI clients. It keeps
SQL and transaction handling out of the Tkinter window code, and provides a
stable boundary that both Tkinter and the JavaFX GUI can use. Functions accept
the database path and simple values; structured results are JSON strings so
JavaFX can call the same Python functions through `jpy` without passing SQLite
connection or row objects across the language boundary.

The module reuses database helpers from `Driver/db` where those already exist.
It is a GUI-facing client data layer, not a replacement Java service layer.
The Tkinter GUI imports it directly; JavaFX invokes `GUI.client_data` through
`jpy`. If the data access API later moves into Java code, the GUI clients can
be redirected there without keeping SQL in their controllers.
