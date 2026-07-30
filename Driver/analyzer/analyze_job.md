Purpose: orchestrate all analysis operations for one readable vacancy.

Input:
- One saved readable vacancy text file.
- Source and source URL when available.

Output:
- One fully scored JSON file under `../Data/scored/<source>/`.
- The output must match `contracts/scored_job.schema.json`.

Run these operations in this exact order:

1. Job facts
   - Run:
     `python codex_proxy/run.py --run-id <run-id> --operation job_facts --target <readable-text> -- "Execute analyzer/job_facts/extract.md for <readable-text> with source <source> and source URL <source-url>. Process only that file and do not run candidate fit, job interest, or DB save."`
   - It writes factual JSON to `../Data/analyzed/<source>/`.
   - It may record its isolated experimental fit in SQLite, but that value must
     not enter analyzed or scored JSON.
   - The proxy returns the normal final Codex message and exit code. It does
     not implement job-facts logic; it only stores CLI usage metrics.

2. Candidate fit
   - Run:
     `python codex_proxy/run.py --run-id <run-id> --operation candidate_fit --target <analyzed-json> -- "Execute analyzer/candidate_fit/evaluate.md for <analyzed-json>. Process only that file and do not run job interest or DB save."`
   - Use a new blind agent that receives only:
     - `analyzer/candidate_fit/evaluate.md`;
     - the analyzed JSON;
     - `analyzer/config/resume.ini`.
   - Do not pass readable-text context, the job-facts agent's reasoning, an
     experimental fit, an existing scored JSON, or an existing database score.
   - This operation writes the scored JSON under `../Data/scored/<source>/`.
   - The proxy returns the normal final Codex message and exit code. It does
     not implement candidate-fit logic; it only stores CLI usage metrics.

3. Job interest
   - Run:
     `python analyzer/job_interest/calculate.py --input <scored-json>`.
   - This adds `job_interest` to the same scored JSON.

Do not skip or replace an operation. Do not save to SQLite here. Return the
fully scored JSON path to the top-level workflow; `db/save.py` owns persistence.
