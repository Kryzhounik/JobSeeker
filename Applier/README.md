# Applier

Applier version 0.1 prepares job-application forms from the Java GUI or the console.
Both entry points pass the vacancy URL to the same Applier runner; it reads
the vacancy location from the open page and handles the browser, facts, and form filling.
The collector marks LinkedIn Easy Apply vacancies when reading their details.
GUI Apply is disabled for marked vacancies. When called directly, Applier checks
the opened page using the same Easy Apply detector as the collector and skips
these vacancies without opening a form or calling Codex. Applier does not read
the vacancy database and does not require prior collection of the URL.
It shares the logged-in browser with the independently runnable LinkedIn
collector. Applier first looks for a host-specific Playwright adapter. If no
adapter matches, it uses the existing Codex Proxy with Playwright MCP. No
adapter scripts are installed yet, so version 0.1 uses the Codex path. The
form stays open for human review, completion of missing answers, and submission.
`ManagedBrowser` starts or attaches to the shared browser using the persistent
`Data/browser_profiles/linkedin` profile and local CDP endpoint. Each Java
client closes only the tabs it owns; Applier leaves an opened form tab available
for review. Form preparation never submits or marks a vacancy Applied.

The shared browser code currently under `Applier/shared` is a refactoring
candidate for relocation to Tools; see the [project backlog](../BACKLOG.md#next).

The local resume lives at `Data/applier/CV_DokE2026M.pdf`. Its extracted UTF-8 text at
`Data/applier/resume.txt` is passed to Codex as context; the PDF path is passed
for form uploads. Replace both files together when updating the resume.
The shared candidate profile at `Driver/analyzer/config/resume.ini` supplies
available locations, relocation options, language levels, and technology levels.
Additional applicant facts, such as phone numbers, work authorization, compensation,
and notice period, go in `Data/applier/facts.md`; do not duplicate the shared profile
there. Keep decision rules in [prompts/fill_form.md](prompts/fill_form.md). Applier reads the vacancy location
from the LinkedIn page
so Codex can apply the location rule. If the extracted location is empty, Codex
first reads it on the open employer page. Add confirmed facts as testing reveals new fields;
add choice rules to `prompts/fill_form.md`. `Data/` is excluded from Git.

`REQUIREMENTS.md` defines the expected behavior. [BACKLOG.md](BACKLOG.md) records
deferred optimization ideas.

The console performs one operation per process: application preparation or
standalone cover-letter generation, using the module's CLI handler. See
[ARCHITECTURE.md](ARCHITECTURE.md). Access to a running JVM through UDS remains
deferred and is described in [Tools/ARCHITECTURE.md](../Tools/ARCHITECTURE.md).

## Configuration dependencies

Applier reuses these existing files; it has no separate copies of these settings:

- `Driver/analyzer/config/resume.ini`: shared candidate profile. The Codex path
  passes its complete contents alongside the resume text and facts in the initial
  prompt. A path or link in the facts file alone does not load another file;
  the agent uses browser tools and does not fetch profile files on demand.
- `Driver/collector/java_linkedin/runtime.properties`: `profile.directory` selects
  the persistent browser profile; `python.executable` starts the Codex Proxy.
- `Driver/collector/config/linkedin.properties`: `browserChannel` selects the
  browser when starting it (default `chrome`), and `pageTimeoutSeconds` sets
  browser action and navigation timeouts (default `60`).

Changes to these shared settings affect Applier as well as their owning modules.
The configuration dependency does not require a running collector.

Connection diagnostics use stderr with timestamped `[browser-connect]` messages.
They report browser startup, driver creation, and CDP attach timings and failures.
In the GUI, these messages go to its existing `logs/launch-*.log`.

## Build and console API

Install Playwright MCP once from the Seeker project root, using Node.js/npm:

```powershell
npm ci --prefix Driver/codex_proxy
```

Both GUI and console preparation use this installed MCP runtime through the
existing Codex Proxy.

Build with JDK 21 or newer and Maven from the Seeker project root:

```powershell
mvn -f Driver/pom.xml -pl :applier -am package
```

This creates the thin `Applier/applier.jar`. Runtime dependencies are copied
to the repository-root `libs/` and referenced by the JAR manifest.
Run from the Seeker project root, using Java 21 or newer:

```powershell
java -jar Applier/applier.jar "https://www.linkedin.com/jobs/view/1234567890/"
```

The API accepts one positional argument: an absolute HTTP(S) vacancy URL. It uses the existing
project files, browser profile, resume, facts, Python runtime, and Codex Proxy.
The console calls the same `ApplierRunner.run` as the Apply button: Easy Apply
is skipped, a matching adapter runs first, and Codex runs when there is no adapter.
The prepared form stays open for manual review and submission.

On completion, stdout contains one JSON object, using the existing result fields:

```json
{"status":"filled","note":"Preparation finished; inspect the open form.","submissionObserved":false}
```

- `status`: `filled`, `partially_filled`, or `skipped`.
- `note`: the explanation returned by Applier.
- `submissionObserved`: whether a possible submission was reported; inspect
  the page when true before taking further action.

Exit code `0` means preparation returned a result (including partially filled
or skipped). Code `1` means execution failed; the error is
written to stderr with its exception chain. Code `2` means the argument count
or URL is invalid.
The console does not change the vacancy's Applied status.

## Java API

`new ApplierRunner(projectRoot).run(vacancyUrl)` accepts the Seeker project root
as a `Path` and the vacancy URL as a `String`. It blocks until preparation
finishes, then returns `ApplierRunner.Result(status, note, submissionObserved)`;
execution errors are thrown as exceptions. The GUI runs this call in its
existing background task. The CLI calls it directly.

## Standalone cover-letter writer

From the project root, with Java 21+:

```powershell
java -jar Applier/applier.jar cover-letter 3075
java -jar Applier/applier.jar cover-letter 3075 "Data/applier/letter.txt"
```

The argument is internal `jobs.id`. Without an output path stdout contains the
letter; with a path it contains the absolute path to the UTF-8 file. The parent
directory must exist. Diagnostics use stderr. The existing vacancy-URL invocation
remains independent of the database.

Java callers use `ApplierApi.coverLetter(long)` or `coverLetter(long, Path)`;
`coverletter.CoverLetterWriter` is the standalone writer API. Full cleaned vacancy
text, title and company are loaded from SQLite by ID. Only the writer prompt,
`Data/applier/resume.txt` and `Data/applier/candidate_context.md` are supplied to
Codex Proxy; questionnaire facts, profile INI and PDF are excluded. See
[architecture](ARCHITECTURE.md) and the [CLI skill](../.agents/skills/applier-cover-letter/SKILL.md).

Verification: `python Applier/tests/test_cover_letter.py` after packaging exercises
a temporary database and a fake Proxy response; it does not invoke a model or browser.

Successful generations are appended to `cover_letters` in the configured database
with `job_id` referencing `jobs.id`, full `letter_text`, and UTC `created_at`.
Earlier letters are retained on repeat generation. Storage completes before the
API returns or writes the optional file; storage failures are reported as errors.
The idempotent migration is `Driver/db/migrations/044_cover_letters.sql`.
