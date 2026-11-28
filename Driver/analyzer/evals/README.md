# Analyzer evaluation runner

This manually invoked runner seeds a fresh isolated database and runs the existing
Orchestrator `job-facts` and `finish-analysis` operations. The latter includes
candidate fit, interest and database save. Calls use
the configured real Codex model and consume tokens. The title relevance filter
and browser collection are not run.

The evaluation is intended to measure extraction followed by semantic Candidate
Fit scoring with the scripted hard filters disabled: human languages, remote
location, relocation and core programming languages. Extraction of individual
location and language facts can be assessed separately using the saved database
and analyzed JSON; those cases do not need to be tested through hard rejection.
See [evaluation issues](ISSUES.md) for discrepancies found in the first run.

The runner disables those filters in the workspace's profile copy; it does not
change the original profile. The saved profile records the settings used. The
first saved run was performed with the filters enabled.

## Prerequisites and dataset

Build the current Orchestrator and its dependencies with the normal Maven
reactor (`mvn -f Driver/pom.xml package`); the runner reuses the existing JAR
and `libs/` and does not rebuild them. Rebuild whenever Java changes so the
binary corresponds to the intended source revision. The configured Python/JPy
runtime and `Tools/runtime.properties` must be usable. The current private
`Data/Profile` is copied for each run; keep it stable between comparable runs.
The proxy's `config.ini` and analyzer `execution.ini` determine model, reasoning
effort and grouping, just as in production.

The dataset is a UTF-8 SQL file applied after the normal schema and migrations.
It must create one collection run and an ordered set of `source_jobs` with
that `collection_run_id`, one source and `RELEVANT` status, with a nonempty
`source_job_texts.readable_text` for each. Insertion order determines the scope
order. IDs must not contain commas, because the existing CLI uses comma-separated
IDs. The single-source restriction follows the existing CLI; multiple sources
must not be split into separate commands, which would change session behavior.
The `datasets/linkedin-27/` dataset contains 25 vacancies. Its
`expected-scores.csv` records the external expected percentages for those
vacancies.

## Run the evaluation

From the repository root, run the analyzer and calculate its report:

```powershell
python Driver/analyzer/evals/run.py Driver/analyzer/evals/datasets/linkedin-27/dataset.sql
```

This runs the real model and then writes a combined `report.csv` with Candidate
Fit details, fact statistics and differences. `report.json` and
`facts-report.json` retain the detailed machine-readable results. Use `--k` to select the
underestimation penalty (default: 3).
The dataset must have `expected-scores.csv` and `expected-facts/` beside `dataset.sql`.

Java must be on `PATH`, or select it with `--java <path>`. Preparation resets
`tmp/analyzer-evals/workspace`, copies the deployed Python code, prompts and
profile, overrides the database and raw paths, and checks Python
module and profile resolution before running either command. Each real run
gets a new directory under `tmp/analyzer-evals/results/`, named by local date
and time, for example `2026-11-28_14-30-00`. It contains inputs, outputs,
metadata and reports. The inputs include the dataset, expected scores, profile
and settings; outputs include SQLite, generated JSON and logs.
Outputs from a failed command are retained there too. These directories contain
personal profile data and must remain private. A subsequent run resets the
workspace but leaves earlier result directories intact.

Real model calls require network access from the runner and its child Codex CLI
processes. When launching from a Codex session with restricted network access,
run the evaluation outside that session's sandbox using the normal approval
mechanism. Otherwise Codex CLI may wait while retrying failed connections.
This is separate from the Codex Proxy's own model and sandbox settings; do not
change those settings merely to grant the launcher network access.

Use `--prepare-only` to check a seed and environment without creating a result
directory or invoking Java/the model.
The caller is responsible for ensuring no other evaluation is using the shared
workspace when starting a run.

## Optional: recalculate a report

```powershell
python Driver/analyzer/evals/report.py tmp/analyzer-evals/results/<evaluation-run-id>
```

This separate command reads the saved database and reference scores without model calls,
and writes `report.csv` and `report.json` in the result directory. The report contains each vacancy's
reference, actual score, signed error and asymmetric loss, plus aggregate
asymmetric MSE, MSE and bias. Underestimates use a coefficient of 3 by default;
`--k` selects another positive coefficient. Incomplete results or mismatched
references are reported explicitly, with no aggregate metrics and exit code 1.
Compare runs using the same dataset, reference scores, profile and coefficient.

For old result folders, use `report.py` from Git revision `0032c14` to recalculate the report.

To compare saved analyzed JSON separately, including from older runs:

```powershell
python Driver/analyzer/evals/facts_report.py --expected path/to/expected-facts --analyzed path/to/analyzed --output path/to/report-directory --score-report path/to/report.json
```

Only fields in each expected JSON are compared. Language entries are compared
without regard to order and without extra fields such as `raw_value`; other
values are compared exactly. A missing analyzed file or field is reported as
incomplete. Pass `--score-report` to create a combined CSV; without it only
the facts JSON is written. No model calls are made during comparison.
