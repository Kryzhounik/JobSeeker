# Collector (Java)

Collector exposes a common Java API for vacancy collection. The active LinkedIn
source owns browser retrieval and description preparation; shared processing
owns filtering, registration and persistence. JustJoinIT implements the same
contract using HTTP, retaining native structured requirements and preparing its
HTML description without the LinkedIn cleaner. Existing API/CLI commands and
Orchestrator still start only LinkedIn; source selection is a subsequent task.

See [Collector architecture](ARCHITECTURE.md) for responsibility boundaries and
the [Driver architecture](../../ARCHITECTURE.md) for API, CLI and JPy contracts.

Optional vacancy trimming uses EasyReasoning and is configured only in
`EasyReasoning/easy-reasoning.properties` (`vacancy.trimming.enabled=false` by
default).

## Module boundaries

- `core` — `CollectorApi`, source execution, run lifecycle and final reports.
- `common` — source contract, prepared vacancies, shared models and processing.
- `linkedin` — browser sessions, search, pagination, HTML preparation and cleaner.
- `justjoin` — HTTP search, full offers, structured fields and description preparation.

`CollectorApi` accepts an optional per-operation progress callback. Settings are
loaded at the start of each call from `Driver/collector/config/linkedin.properties`
and `Data/Profile/Driver/collector/linkedin.properties`. Reusing the API picks up
file changes on the next call. `LinkedInApplication` remains a compatibility
adapter to the same API.

Callbacks run synchronously on the
calling worker thread, before browser startup and during collection, including
the final state. A UI caller marshals snapshots to its UI thread. Calls without
a listener remain available with progress ignored. `CollectionProgress` implements
the common contract: `runId`, `stage`, `action`, compact `progress` (accepted / limit), ordered immutable `fields`, and
`details`. Stage is always `Collection`; browser startup, reading results and
loading vacancies are actions. Display-ready fields include location/search,
page/cards, processed, accepted/limit, and each rejection/failure counter.
The same complete snapshot is sent directly and serialized by the process CLI.
Snapshots do not write a console protocol themselves.

The CLI encodes callbacks as `COLLECTOR_PROGRESS ` JSON lines on stderr.

`CollectionReport` describes collection only: `accepted_count` is the number
accepted by Collector and `outcomes` contains only Collector outcomes.
Subsequent workflow filtering and analysis return a separate `WorkflowResult`.

## Runtime behavior

- The logged-in `/jobs/search-results/` UI is collected. There is no guest
  prefetch.
- `AccountRemote` is seeded from the first configured country and collected
  first. Each country then gets a priority-company pass and a general pass.
  See [the purpose and current limitations of this workaround](#international-remote-search).
- Collection is intentionally single-threaded.
- `database.path` is configured in `Tools/runtime.properties`; collector output
  and browser-profile paths remain in the local `runtime.properties`.
- The managed visible Chrome process uses the persistent profile at
  `Data/browser_profiles/linkedin` and exposes CDP only on loopback. It remains
  open after collector commands. Run `login` once and sign in with the existing
  account; collection and application preparation reuse that session.
- Details-pane HTML is passed from Java to Python as a string. No pane files,
  decision JSON, scope files, or checkpoints are created.
- Java reads the Easy Apply button using the shared `com.seeker.browser.LinkedInJobPage` detector from Tools
  and passes its result to Python to store `source_job_texts.easy_apply`.
  GUI Apply uses the saved marker. Applier uses the same detector on the open
  vacancy page without reading the database. Existing vacancies are not reclassified.
- The same HTML processing step extracts publication age from vacancy metadata
  for both search and direct URLs. Vacancies older than `[age] max_days = 14` in
  `filtering/linkedin_preview_filter.ini` produce `age_filtered` before raw or
  database persistence. Missing/unrecognized dates pass; description text is not
  interpreted as a publication date. Search `datePosted` remains a separate
  LinkedIn search parameter.
- For compatibility, Python still saves canonical raw HTML, writes collector
  metadata, readable text, and lifecycle data to SQLite, applies the current
  filters, and records collection events.
- A successful command writes one final JSON result to stdout. Unhandled errors
  retain their normal Java stack trace. Analysis and scoring remain the caller's
  next stages and are never run by this module.

The normal `process-batch linkedin` workflow now enters through the Java workflow
orchestrator. The direct collector commands below remain available for collector
development, login, and testing.

`CollectorApi.collectBatch()` performs configured collection and returns a
`CollectionReport`.

`CollectorApi.collectUrl(url)` collects one vacancy through the same
preview and content filters as batch collection. The common vacancy operation
opens the URL once; batch collection applies the preview filters before loading
details from the search card. Both return `CollectionReport` with
only accepted vacancies in `scope`. A skipped URL returns an empty scope and
the reason in `message`. Existing source IDs are skipped without reanalysis.
The overload `collectUrl(runId, url)` uses the supplied collection run ID.
The full URL workflow is exposed by the orchestrator's `process-urls` command.

`CollectorApi.collectUrls(urls)` accepts an explicit list in one browser
session and collection run. It returns one report with the combined scope and
outcomes; collector skip reasons in `message` include their source IDs when
multiple URLs were supplied. `collectUrl(url)` delegates to this operation for one
URL. The orchestrator runs analysis once after collection of the whole list.
The overload `collectUrls(urls, runPrefix)` prepends the supplied label to the
generated run ID; the default overload preserves the existing ID format.

Both collection paths record individual vacancy failures and continue with the
next vacancy. Results distinguish `open_failed`, `incomplete_raw`, and
`processing_failed`; counts appear in progress and report `outcomes`, with
failure reasons in the report message and collection event log. Authentication
failure still stops collection, as does inability to persist a failure outcome.

## International remote search

`AccountRemote` was intended to collect cross-border remote vacancies, including
EMEA postings without a restriction to one country. In the classic search, the
workaround first opened Moldova + Remote (the first configured country), waited
20 seconds for LinkedIn to remember that geography, then opened Remote without
explicit location parameters. This exposed international postings that were
not tied to Moldova.

After the move to AI-powered search, this workaround appears to no longer
produce the previous coverage. It remains in the collector, but should not be
treated as a working international-remote search.

EMEA is now a selectable macroregion. EMEA + Remote includes country-specific
remote jobs across that region; it does not mean that each vacancy allows work
from anywhere in Europe or from Moldova. It therefore does not replace the
original purpose of `AccountRemote`.

Identifying country-independent remote opportunities within this broader set
may still be possible by checking each vacancy's geographic eligibility and
restrictions. That requires new selection logic, which is not implemented.

## Requirements

- JDK 21 or newer;
- Maven 3.9 or newer;
- the project's Python 3 installation;
- jpy 2.1.0 installed into that Python:

```powershell
python -m pip install -r Driver/collector/java_linkedin/requirements.txt
```

Jep 4.3.2 was tested first, but its Windows/Python 3.14 package is source-only
and this machine has no MSVC C++ toolchain. jpy 2.1.0 provides a compatible
CPython 3.14 Windows wheel, so it is the active in-process bridge.

Copy `runtime.properties.example` to the Git-ignored `runtime.properties` and
set the installed Java, raw-output and browser-profile paths there. Also copy
`Tools/runtime.properties.example` to `Tools/runtime.properties` and set the
shared Python/JPy and database paths there. They are not exposed as
command-line options. Change the local file only if an installation moves.

## Commands

Run from the project root:

```powershell
# Build after changing Java sources
mvn -f Driver/pom.xml -pl :linkedin-collector -am package

# One-time/manual login for the dedicated profile
java -jar Driver/collector/java_linkedin/linkedin-collector.jar login

# Configured batch; JSON scope is returned on stdout
java -jar Driver/collector/java_linkedin/linkedin-collector.jar collect-batch
```

The runnable JAR is thin. Runtime dependencies are copied to the repository-root
`libs/` and referenced by its manifest; keep that directory beside the modules.

## Tests

```powershell
mvn -f Driver/pom.xml -pl :linkedin-collector -am test
python -m unittest Driver.test.test_java_linkedin_bridge
```

The regular Maven tests do not start a browser, collect real vacancies, or call
Codex. Running the executable collector or orchestrator is a separate action.

## Semantic duplicate observation

Collection does not run or wait for description comparisons. After successful
collection, the orchestrator calls EasyReasoning's
`easyReasoning.deduplicateBatch(runId)` before filtering and analysis.
[EasyReasoning](../../../EasyReasoning/README.md) owns candidate selection,
operation settings, comparisons and duplicate-link persistence. Collector only
collects and saves vacancies. Each publication retains its scope entry and
analysis. Resume and standalone collection commands are unchanged.

Common JPy initialization and Python runtime loading are provided by Tools.
Shared Python/JPy paths and `database.path` are configured in
`Tools/runtime.properties`, independently of Collector. The local
`runtime.properties` retains only `java.executable`, `raw.directory` and
`profile.directory`.
