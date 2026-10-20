# Java LinkedIn collector

This module replaces agent-driven LinkedIn clicking with one local Java
process. Java connects to the shared logged-in browser through CDP and owns
search pagination, card selection, its own tabs, and the final in-memory run scope. It calls the existing Python
filtering, raw/readable persistence, priority-company lookup, lifecycle, and
collection logging functions through jpy.

`python_bridge.py` is a thin adapter imported by jpy. It is not a background
Python service. The underlying Python functions and their CLI entry points stay
available to agents, tests, and the GUI.

## Module boundaries

The Java code is split by responsibility:

- `Main` chooses between the two supported commands: `login` and `batch`;
- `login` owns the one-time browser authentication bootstrap;
- `collection` owns search planning, pagination, cards, limits, and run reports;
- `browser` connects to the shared browser and closes only collector-owned tabs;
- `python` owns the Java-to-Python gateway;
- `config` resolves project paths and collector settings.

`LinkedInLoginService` and `LinkedInCollectionService` contain the two use
cases without command-line request wrappers.

## Runtime behavior

- Only the classic logged-in `/jobs/search/` UI is collected. There is no guest
  prefetch.
- `AccountRemote` is seeded from the first configured country and collected
  first. Each country then gets a priority-company pass and a general pass.
- Collection is intentionally single-threaded.
- Persistence paths are configured in `runtime.properties`.
- The managed visible Chrome process uses the persistent profile at
  `Data/browser_profiles/linkedin` and exposes CDP only on loopback. It remains
  open after collector commands. Run `login` once and sign in with the existing
  account; collection and application preparation reuse that session.
- Details-pane HTML is passed from Java to Python as a string. No pane files,
  decision JSON, scope files, or checkpoints are created.
- The common HTML processing step detects the Easy Apply button and stores
  `source_job_texts.easy_apply`. GUI Apply and console Applier use this saved
  marker; neither repeats detection. Existing vacancies are not reclassified.
- For compatibility, Python still saves canonical raw HTML, writes collector
  metadata, readable text, and lifecycle data to SQLite, applies the current
  filters, and records collection events.
- A successful command writes one final JSON result to stdout. Unhandled errors
  retain their normal Java stack trace. Analysis and scoring remain the caller's
  next stages and are never run by this module.

The normal `batch linkedin` workflow now enters through the Java workflow
orchestrator. The direct collector commands below remain available for collector
development, login, and testing.

`LinkedInApplication.fromUrl(url)` collects one vacancy through the same
preview and content filters as batch collection. The URL entry loads its HTML
once and passes it to the common operation; batch collection applies the
preview filters before loading details. Both return `CollectionReport` with
only accepted vacancies in `scope`. A skipped URL returns an empty scope and
the reason in `message`. Existing source IDs are skipped without reanalysis.
The full URL workflow is exposed by the orchestrator's `check-url` command.

`LinkedInApplication.fromUrls(urls)` accepts an explicit list in one browser
session and collection run. It returns one report with the combined scope and
outcomes; collector skip reasons in `message` include their source IDs when
multiple URLs were supplied. `fromUrl(url)` delegates to this operation for one
URL. The orchestrator runs analysis once after collection of the whole list.

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
set the installed Java, Python, and jpy paths there. They are not exposed as
command-line options. Change the local file only if an installation moves.

## Commands

Run from the project root:

```powershell
# Build after changing Java sources
mvn -f Driver/pom.xml -pl :linkedin-collector -am package

# One-time/manual login for the dedicated profile
java -jar Driver/collector/java_linkedin/linkedin-collector.jar login

# Configured batch; JSON scope is returned on stdout
java -jar Driver/collector/java_linkedin/linkedin-collector.jar batch
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
