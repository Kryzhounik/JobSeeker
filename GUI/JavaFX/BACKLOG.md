# GUI Backlog

## Low

- **Windows release verification:** Automate pre-release verification of the
  downloadable Windows GUI build. Build the GUI JAR and C# launcher
  `SeekerJobs.exe`; run `FatJarSmoke`, `LauncherSnapshotSmoke`, and
  `PrepareRuntimeSmoke`; and verify expected release files are present.
  Reuse `build_launcher.ps1` and existing smoke tests rather than duplicating
  their logic. Keep Java-artifact checks in Maven `verify` and Windows-specific
  checks in a separate GitHub Actions job on `windows-latest`. Run it manually
  via `workflow_dispatch` and for release tags `v*`, not on every push or PR.
  Driver and GUI remain independent projects. A failed Windows job must block
  publication.
