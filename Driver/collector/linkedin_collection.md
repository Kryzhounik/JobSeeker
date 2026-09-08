---
name: linkedin-collection
description: Select the configured LinkedIn collector and return its run scope.
---

# LinkedIn Collection

Purpose: the single LinkedIn batch-collection entry point.

1. Read only `collectorMode` from `collector/config/linkedin.properties` before
   opening any implementation-specific instruction.
2. If `collectorMode=playwright`, do not read, inspect, search, or summarize
   anything under `collector/deprecated_agent_collection/`. Read only
   `java.executable` from `collector/java_linkedin/runtime.properties`, then
   from the project root run:

   ```text
   <java.executable> -jar Driver/collector/java_linkedin/target/linkedin-collector.jar batch
   ```

   Start this command exactly once as a foreground process and wait for that
   same process to exit. While it runs, do not inspect the browser, database,
   files, logs, metrics, or intermediate stderr, and do not send progress
   reports. If a terminal wait interval ends while the process is still
   running, wait on the same process handle again for five minutes; do not
   restart it or perform diagnostics. Resume the workflow only after process
   exit and receipt of its final JSON.

   Consume the command's JSON result directly from stdout. Continue only when
   `status` is `complete`; use its `run_id` and ordered `scope` as the collection
   result. On any other status, stop and report it. Never fall back to the agent
   implementation automatically.
3. If `collectorMode=agent`, only then read and execute
   `collector/deprecated_agent_collection/agent_scan.md`. Use the `run_id` and
   ordered `scope` returned by that instruction.
4. For any other value, stop before collection and report the invalid mode.

Both implementations own collection only. Analysis starts afterward from the
returned explicit scope.
