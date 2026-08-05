Purpose: orchestrate all analysis operations for one stored readable vacancy.

Input:
- Source and source-job ID whose readable text exists in SQLite.

Output:
- One fully scored JSON file under `../Data/scored/<source>/`.
- The output must match `contracts/scored_job.schema.json`.

Run these operations in this exact order:

Load the operation input with:
`python db/readable_text.py --source <source> --job-id <job-id>`.
Keep that text in memory; do not recreate a readable TXT file.

1. Job facts
   - Invoke the operation through `agent_execution.md` with:
     - operation: `job_facts`;
     - instruction: `analyzer/job_facts/extract.md`;
     - input: `<readable-text loaded from SQLite>`;
     - contexts: none;
     - output schema: `contracts/job_analysis.schema.json`;
     - run ID and target from the current analysis run.
   - Validate the returned object against the output schema and write it to
     `<analyzed-json>`. Result persistence belongs to this analyzer, not to the
     execution decorator or CLI proxy.
   - After success, run:
     `python db/job_registry.py ANALYZED --source <source> --job-id <job-id>`.

2. Candidate fit
   - First run the deterministic gate:
     `python analyzer/candidate_fit/filter.py --input <analyzed-json> --output <scored-json>`.
   - If it returns `passed=false`, use the scored JSON it wrote and do not run
     semantic candidate fit.
   - If it returns `passed=true`, invoke the operation through
     `agent_execution.md` with:
     - operation: `candidate_fit`;
     - instruction: `analyzer/candidate_fit/evaluate.md`;
     - input: `<analyzed-json>`;
     - context: `analyzer/config/resume.ini`;
     - output schema: `contracts/candidate_fit_result.schema.json`;
     - run ID and target from the current analysis run.
   - The operation must use a new blind agent that receives only those supplied
     instruction, input, and context files.
   - Do not pass readable-text context, the job-facts agent's reasoning, an
     existing scored JSON, or an existing database score.
   - Validate the returned three-field object, then pass its UTF-8 JSON bytes
     through standard input to:
     `python analyzer/candidate_fit/merge_result.py --input <analyzed-json> --output <scored-json>`.
   - Do not read or rewrite the analyzed JSON through PowerShell or console
     text. `merge_result.py` owns the UTF-8-safe merge and scored persistence in
     both Desktop and CLI execution modes.

3. Job interest
   - Run:
     `python analyzer/job_interest/calculate.py --input <scored-json>`.
   - This adds `job_interest` to the same scored JSON.

Do not skip or replace an operation. Do not save to SQLite here. Return the
fully scored JSON path to the top-level workflow; `db/save.py` owns persistence.
