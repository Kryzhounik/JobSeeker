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

## Runtime Packaging

The supported desktop launch uses the fat `seeker-gui-javafx.jar`. Maven Shade
bundles the GUI and its Java runtime dependencies, including Driver modules,
JavaFX, JPy, and Playwright, into this artifact. Service descriptors are merged
and dependency signatures/module descriptors are removed. The manifest has no
external `Class-Path`. Maven publishes the bundled jar to `GUI/JavaFX/` for
the existing Windows launcher; intermediate and original jars stay in `target/`.

This isolation is intentional: on Windows a running thin GUI holds shared
dependency jars open. It must not block rebuilding another Java module or
replacing jars in the repository-root `libs/`. The GUI therefore uses its own
bundled snapshot, not the backend's shared runtime files. The GUI package step
does not copy dependencies into `libs/`.

Backend Java changes become visible to the GUI after rebuilding its fat jar
and restarting the GUI. Rebuilding the GUI itself may require closing the GUI
because its own jar can be locked. Switching an already running thin GUI to
this packaging requires one restart. No hot code replacement is implied.

Configuration, database, Python source modules, and Python/JNI installation
files remain external. The fat jar is not a portable Python/JDK installation.
The optional process-mode Collector starts a separate backend jar and follows
that process's own packaging and configuration.
