# Collector

LinkedIn batches enter through `linkedin_collection.md`. It reads
`collectorMode` before any implementation-specific documentation.

`playwright` is the primary Java collector. `deprecated_agent_collection/`
contains the deprecated agent-driven implementation retained only as an
explicitly selected rollback path. It is invoked by `collectorMode=agent` and
must not be read in other modes.

Shared filtering, readable-text extraction, logging, and persistence remain in
this directory because both collection modes use them.
