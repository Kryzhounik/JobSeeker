# JobSeeker

Top-level layout:

- `GUI`: human viewer for inspecting the SQLite database.
- `Data`: local raw/readable/analyzed data and `jobs.sqlite`.
- `Driver`: collector, analyzer, scoring, DB mapper, and workflow contract.
- `Tools`: project support instructions outside the main vacancy workflow.

Main execution contract: `Driver/WORKFLOW.md`.
Database handoff through Google Drive: `Tools/google_drive_database_sync.md`.
