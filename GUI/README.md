# JobSeeker GUI

This directory contains the desktop interface for reviewing the job data stored
by JobSeeker. It lets a person browse analyzed and collected vacancies, inspect
their details and source text, filter and sort results, and manually manage
vacancy statuses, companies, and applications. It also provides controls for
starting existing collection and maintenance workflows and viewing their
progress.

`Tkinter/` contains the current GUI. The migration plan calls for a JavaFX
replacement while keeping the Tkinter version available. This directory is
the interactive front end to the project's job data and workflows.

## Where to look

- `PLAN.md` — the steps for the JavaFX migration.
- `REQUIREMENTS.md` — the detailed user-visible behavior required of the JavaFX GUI.
- `AGENTS.md` — development rules and guidance for resolving unclear behavior.
- `Tkinter/README.md` — how the current GUI works, including its launcher, settings, and implementation-specific details.
