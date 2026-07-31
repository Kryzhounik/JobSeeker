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
     `python codex_proxy/run.py --run-id <run-id> --operation job_facts --input <readable-text> --output <analyzed-json>`.
   - The proxy passes only `analyzer/job_facts/extract.md` and the readable
     input to an isolated CLI agent, validates structured output, writes the
     factual JSON, and stores Codex usage metrics.
   - After success, run:
     `python db/job_registry.py ANALYZED --source <source> --job-id <job-id>`.

2. Candidate fit
   - First run the deterministic gate:
     `python analyzer/candidate_fit/filter.py --input <analyzed-json> --output <scored-json>`.
   - If it returns `passed=false`, use the scored JSON it wrote and do not run
     semantic candidate fit.
   - If it returns `passed=true`, run:
     `python codex_proxy/run.py --run-id <run-id> --operation candidate_fit --input <analyzed-json> --output <scored-json>`.
   - The proxy uses a new blind CLI agent that receives only:
     - `analyzer/candidate_fit/evaluate.md`;
     - the analyzed JSON;
     - `analyzer/config/resume.ini`.
   - Do not pass readable-text context, the job-facts agent's reasoning, an
     existing scored JSON, or an existing database score.
   - The proxy merges the structured three-field result into a copy of the
     analyzed JSON and writes the scored JSON.

3. Job interest
   - Run:
     `python analyzer/job_interest/calculate.py --input <scored-json>`.
   - This adds `job_interest` to the same scored JSON.

Do not skip or replace an operation. Do not save to SQLite here. Return the
fully scored JSON path to the top-level workflow; `db/save.py` owns persistence.
