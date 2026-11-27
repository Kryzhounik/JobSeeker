# Analyzer evaluation runner

This manually invoked runner seeds a fresh isolated database and runs the existing
Orchestrator `job-facts` and `finish-analysis` operations. The latter includes
the deterministic gate, candidate fit, interest and database save. Calls use
the configured real Codex model and consume tokens. The title relevance filter
and browser collection are not run. This runner does not define reference
results, quality metrics or comparison rules.

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
`external-scores.csv` records the external expected percentages for those
vacancies. Comparison rules and quality metrics are not yet defined.

## Invocation

From the repository root, prepare and validate without model calls:

```powershell
python Driver/analyzer/evals/run.py path/to/dataset.sql --prepare-only
```

When a real evaluation is intended, omit `--prepare-only`:

```powershell
python Driver/analyzer/evals/run.py path/to/dataset.sql
```

Java must be on `PATH`, or select it with `--java <path>`. Preparation resets
`tmp/analyzer-evals/workspace`, copies the deployed Python code, prompts,
libraries and profile, overrides the database and raw paths, and checks Python
module and profile resolution before running either command. Each real run
gets a new directory under `tmp/analyzer-evals/results/`. It contains the seed,
`external-scores.csv` if present beside the seed, the profile snapshot, analyzer
inputs including shared prompts, metadata and file hashes, command logs, and
the resulting `Data/` including SQLite, generated JSON and token accounting.
Outputs from a failed command are retained there too. These directories contain
personal profile data and must remain private. A subsequent run resets the
workspace but leaves earlier result directories intact.

`--prepare-only` does not create a result directory or invoke Java/the model;
it is useful for checking a seed and environment, not for evaluating quality.
The caller is responsible for ensuring no other evaluation is using the shared
workspace when starting a run.
