# JavaFX GUI Architecture

## Driver Lifecycle

One `DriverApi` belongs to a GUI session. It is constructed on the connection
worker, then reused for Batch, direct Collect, Apply, and GPT Eval. Each call
receives its own progress handler; the facade does not retain UI callbacks.
The direct Collector adapter reuses that same facade. Transport selection is
still read from `backend.properties` at each Collect start.

Collector settings, including the limit edited in Config, are loaded by the
backend at operation start. Saving a limit applies to the next operation;
neither recreating the facade nor restarting the GUI is necessary. An active
operation keeps its existing configuration. Python/JNI installation paths
remain startup configuration and require a restart if changed.

## Progress Rendering

Collect and Batch share `SnapshotProgressPane`, which displays the backend's
`ProgressSnapshot`: Run ID, Stage, Action, ordered dynamic fields, and Details.
Each full snapshot replaces the previous rows, including removing absent fields.
Stage labels, field labels, values, and statistics belong to the backend. The
GUI does not translate stage codes, interpret outcomes, or derive saved counts.
Completion changes only the visual state, preserving the last snapshot. A
failure retains its stage, action, and fields and adds the exception to Details.

Both Collector transports expose `Consumer<ProgressSnapshot>`. Direct mode
forwards the Java callback; process mode deserializes the CLI's same contract
into `ImmutableProgressSnapshot`, preserving field order. Neither transport
manufactures progress snapshots. Execution and lifecycle status remain separate
from the snapshot's display-ready stage/action strings.

`BackgroundProcessPane` tracks completion separately from its visual state.
`setProcessState(WAITING)` alone does not finish a running operation; callers
use `finishProcess(...)` when execution has ended. Only finished panels can be
dismissed, including a finished yellow WAITING panel. `beginProcess()` resets
completion for a manual restart.

Collect/Batch classify `UsageLimitExceededException` by Java type, never by
message text. The task has stopped: its buttons are released and its panel
finishes in WAITING, not FAILED. Details retain the backend text and add the
limit reason plus the local retry time (with time zone), or explicitly state
that the time is unknown. Run ID, stage, action, and metrics remain unchanged.
Other exceptions remain FAILED. There is no automatic retry or scheduler.

## Runtime Packaging

The supported desktop launch uses the fat `dist/seeker-gui-javafx.jar`. Maven Shade
bundles the GUI and its Java runtime dependencies, including Driver modules,
JavaFX, JPy, and Playwright, into this artifact. Service descriptors are merged
and dependency signatures/module descriptors are removed. The manifest has no
external `Class-Path`. Maven publishes the bundled jar to `GUI/JavaFX/dist/` for
the existing Windows launcher; intermediate and original jars stay in `target/`.
Publication copies the complete result to a sibling staging file and atomically
replaces the published jar. Maven clean leaves the last published build intact,
so launches during a build still have a complete version available.

This isolation is intentional: on Windows a running thin GUI holds shared
dependency jars open. It must not block rebuilding another Java module or
replacing jars in the repository-root `libs/`. The GUI therefore uses its own
bundled snapshot, not the backend's shared runtime files. The GUI package step
does not copy dependencies into `libs/`.

Each Windows-launcher invocation copies the published jar to its own directory
under `%TEMP%/SeekerJobs/` and runs that copy. Source handles permit rename/delete,
so publishing while a launch copies the previous version does not block either
operation. The working directory remains the repository root. The launcher
removes its runtime copy after Java exits; `run.ps1` uses the same isolation.
Launch logs are separate files in `GUI/JavaFX/logs/`, allowing concurrent runs.

New launches see the latest fully published build. Existing processes retain
their own build until exit, even while the GUI or backend is rebuilt. There is
no forced restart, update lock, or hot code replacement. A direct
`java -jar GUI/JavaFX/dist/seeker-gui-javafx.jar` bypasses this isolation and must not
be used for a process that should survive GUI rebuilds. Processes started by
the old launcher still need one normal close to switch to the new mechanism.

Configuration, database, Python source modules, and Python/JNI installation
files remain external. The fat jar is not a portable Python/JDK installation.
The optional process-mode Collector starts a separate backend jar and follows
that process's own packaging and configuration.
