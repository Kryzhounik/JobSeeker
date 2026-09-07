# Java LinkedIn collector

This module replaces agent-driven LinkedIn clicking with one local Java
process. Java owns the logged-in Playwright browser session, search pagination,
card selection, and the final in-memory run scope. It calls the existing Python
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
- `browser` owns only the shared Playwright persistent-context lifecycle;
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
- The browser profile lives at `Data/browser_profiles/linkedin` by default.
  This is only a separate local Chrome data directory; it needs neither a new
  LinkedIn account nor any purchase. Run `login` once and sign in with the
  existing account.
- Details-pane HTML is passed from Java to Python as a string. No pane files,
  decision JSON, scope files, or checkpoints are created.
- For compatibility, Python still saves canonical raw HTML under
  `Data/raw/linkedin/pages`, writes readable text and lifecycle data to SQLite,
  applies the current filters, and records collection events.
- A successful command writes one final JSON result to stdout. Unhandled errors
  retain their normal Java stack trace. Analysis and scoring remain the caller's
  next stages and are never run by this module.

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

The installed Python/jpy paths are stored once in `runtime.properties`. They
are not exposed as command-line options. Change that file only if the local
Python installation moves.

## Commands

Run from the project root:

```powershell
# Build after changing Java sources
mvn -f Driver/collector/java_linkedin/pom.xml package

# One-time/manual login for the dedicated profile
java -jar Driver/collector/java_linkedin/target/linkedin-collector.jar login

# Configured batch; JSON scope is returned on stdout
java -jar Driver/collector/java_linkedin/target/linkedin-collector.jar batch
```

## Tests

```powershell
mvn -f Driver/collector/java_linkedin/pom.xml test
python -m unittest Driver.test.test_java_linkedin_bridge
```
