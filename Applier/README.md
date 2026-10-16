# Applier

Applier version 0.1 prepares job-application forms from the Java GUI or the console.
Both entry points pass the vacancy URL to the same Applier runner; it reads
the stored vacancy location and handles the browser, facts, and form filling.
LinkedIn Easy Apply vacancies are skipped without calling Codex.
It shares the logged-in browser with the independently runnable LinkedIn
collector. Applier first looks for a host-specific Playwright adapter. If no
adapter matches, it uses the existing Codex Proxy with Playwright MCP. No
adapter scripts are installed yet, so version 0.1 uses the Codex path. The
form stays open for human review, completion of missing answers, and submission.
`ManagedBrowser` starts or attaches to the shared browser using the persistent
`Data/browser_profiles/linkedin` profile and local CDP endpoint. Each Java
client closes only the tabs it owns; Applier leaves an opened form tab available
for review. Form preparation never submits or marks a vacancy Applied.

The local resume lives at `Data/applier/CV_DokE2026M.pdf`. Its extracted UTF-8 text at
`Data/applier/resume.txt` is passed to Codex as context; the PDF path is passed
for form uploads. Replace both files together when updating the resume.
Applicant facts, separate from the decision rules in `fill_form.md`, go in
`Data/applier/facts.md`. The current file lists the preferred and other
available locations. Applier reads the vacancy location from `Data/jobs.sqlite`
so Codex can apply the location rule. Add confirmed facts as testing reveals new fields;
add choice rules to `fill_form.md`. `Data/` is excluded from Git.

`REQUIREMENTS.md` defines the expected behavior.

## Build and console API

Build with JDK 21 or newer and Maven from the Seeker project root:

```powershell
mvn -f Applier/pom.xml package
```

This creates `Applier/target/applier.jar` with its Java dependencies.
Run from the Seeker project root, using Java 21 or newer:

```powershell
java -jar Applier/target/applier.jar "https://www.linkedin.com/jobs/view/1234567890/"
java -jar Applier/target/applier.jar --help
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
or skipped), or help was shown. Code `1` means execution failed; the error is
written to stderr with its exception chain. Code `2` means the argument count
or URL is invalid.
The console does not change the vacancy's Applied status.

## Java API

`new ApplierRunner(projectRoot).run(vacancyUrl)` accepts the Seeker project root
as a `Path` and the vacancy URL as a `String`. It blocks until preparation
finishes, then returns `ApplierRunner.Result(status, note, submissionObserved)`;
execution errors are thrown as exceptions. The GUI runs this call in its
existing background task. The CLI calls it directly.
