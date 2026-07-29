Purpose: orchestrate all analysis operations for one readable vacancy.

Input:
- One saved readable vacancy text file.
- Source and source URL when available.

Output:
- One fully scored JSON file under `../Data/scored/<source>/`.
- The output must match `contracts/scored_job.schema.json`.

Run these operations in this exact order:

1. Job facts
   - Execute `analyzer/job_facts/extract.md` on the readable vacancy.
   - It writes factual JSON to `../Data/analyzed/<source>/`.
   - It may record its isolated experimental fit in SQLite, but that value must
     not enter analyzed or scored JSON.

2. Candidate fit
   - Execute `analyzer/candidate_fit/evaluate.md` on the analyzed JSON.
   - Use a new blind agent that receives only:
     - `analyzer/candidate_fit/evaluate.md`;
     - the analyzed JSON;
     - `analyzer/config/resume.ini`.
   - Do not pass readable-text context, the job-facts agent's reasoning, an
     experimental fit, an existing scored JSON, or an existing database score.
   - This operation writes the scored JSON under `../Data/scored/<source>/`.

3. Job interest
   - Run:
     `python analyzer/job_interest/calculate.py --input <scored-json>`.
   - This adds `job_interest` to the same scored JSON.

Do not skip or replace an operation. Do not save to SQLite here. Return the
fully scored JSON path to the top-level workflow; `db/save.py` owns persistence.
