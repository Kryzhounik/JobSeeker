# JobSeeker

Top-level layout:

- `GUI`: human viewer for inspecting the SQLite database.
- `Data`: local raw/analyzed files and `jobs.sqlite`; analyzer-ready readable
  text is stored inside SQLite.
- `Driver`: collector, analyzer, scoring, DB mapper, and Java orchestrator.
- `Tools`: project support instructions outside the main vacancy workflow.

Main entry point: `Driver/orchestrator/README.md`.
Database handoff through Google Drive: `Tools/google_drive_database_sync.md`.
