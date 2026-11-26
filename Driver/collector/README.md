# Collector

LinkedIn batches enter through `linkedin_collection.md`. It reads
`collectorMode` before any implementation-specific documentation.

`playwright` is the primary Java collector.

## Legacy Collector

The deprecated agent-driven collector is intentionally retained
as a rollback option in case LinkedIn UI changes break the
primary Playwright-based collector.

Its presence is a deliberate architectural decision, not
unresolved technical debt.

It is invoked by `collectorMode=agent` and must not be read in other modes.

The Java module exposes `CollectorApi`, shared processing and a source contract.
See its [architecture](java_linkedin/ARCHITECTURE.md) for the boundaries between
core, common operations and site-specific retrieval.

Shared filtering, readable-text extraction, logging, and persistence remain in
this directory because both collection modes use them.
