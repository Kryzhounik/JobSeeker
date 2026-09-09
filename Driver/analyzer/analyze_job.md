Purpose: orchestrate all analysis operations for one explicit vacancy scope.

Input:
- Source and ordered source-job IDs left after the agent relevance filter and
  whose readable text exists in SQLite. Never include a source job with
  processing status `NONRELEVANT`.

Output:
- One fully scored JSON file per input under `../Data/scored/<source>/`.
- Every output must match `contracts/scored_job.schema.json`.

Before starting, read `analyzer/config/execution.ini`. For job facts,
`parallel_agents` is the maximum number of simultaneous agents and
`vacancies_per_agent` is the maximum number handled by one agent thread.
Both values must be positive integers. Do not silently substitute defaults.

Run these phases in this exact order for the whole scope.

## 1. Job facts

Partition the ordered scope into consecutive groups of at most
`vacancies_per_agent`. Start up to `parallel_agents` groups simultaneously.
Fill all available slots before waiting when enough groups remain. When a group
finishes, start the next pending group until the scope is exhausted.

Each group has one job-facts agent target. Start it through
`agent_execution.md` with:

- operation: `job_facts`;
- instruction: `analyzer/job_facts/extract.md`;
- context: `analyzer/job_facts/language_levels.md`;
- output schema: `contracts/job_analysis.schema.json`;
- run ID from the current analysis run;
- one stable target for that group.

Continue the same target for every vacancy assigned to its group. Do not create
a new agent per vacancy. Close the target after its group is complete.

For each assigned vacancy:

1. Load its readable text with:
   `python db/readable_text.py --source <source> --job-id <job-id>`.
2. Pass that text as UTF-8 standard input to:
   `python collector/filtering/language_requirements.py`.
3. Send the readable text plus the returned deterministic language facts to
   the group's existing target. Label those facts authoritative.
4. Send the returned object directly as UTF-8 JSON on standard input to:
   `python contracts/validate_json.py --schema contracts/job_analysis.schema.json --output <analyzed-json>`.
   This command validates and writes the analyzed JSON. Do not choose another
   validation library or create a temporary transport file.
5. After successful persistence, run:
   `python db/job_registry.py ANALYZED --source <source> --job-id <job-id>`.

The job-facts agent may retain its own preceding results within its assigned
group, but the current vacancy text and deterministic facts must remain clearly
separated by source-job ID. Never copy a fact from a previous vacancy merely
because the jobs look similar.

Do not begin candidate fit until job facts have completed for the entire scope.

## 2. Deterministic candidate-fit gate

For every analyzed JSON in scope, run:

`python analyzer/candidate_fit/filter.py --input <analyzed-json> --output <scored-json>`.

- If it returns `passed=false`, keep the scored JSON and do not send that
  vacancy to semantic candidate fit.
- If it returns `passed=true`, append the analyzed JSON to the semantic-fit
  queue in original scope order.

## 3. Semantic candidate fit

If the semantic-fit queue is not empty, create exactly one blind candidate-fit
agent target for the entire analysis run. Start it once through
`agent_execution.md` with:

- operation: `candidate_fit`;
- instruction: `analyzer/candidate_fit/evaluate.md`;
- context: `analyzer/config/resume.ini`;
- output schema: `contracts/candidate_fit_result.schema.json`;
- run ID from the current analysis run;
- one stable target shared by the complete semantic-fit queue.

Send analyzed JSON files to this same target sequentially in queue order. Do
not create, close, or replace the evaluator between vacancies. It may use its
own previous evaluations to keep one scoring scale across the run.

The evaluator remains blind to readable text, job-facts reasoning,
experimental analyzer scores, existing scored JSON, and database scores. Its
allowed history consists only of its instruction, candidate profile, analyzed
JSON inputs already evaluated in this run, and its own returned fit results.

For every returned result, start the following command and send the three-field
object directly to its UTF-8 standard input as JSON:
   `python analyzer/candidate_fit/add_fit_score.py --input <analyzed-json> --output <scored-json>`.

`add_fit_score.py` validates the object against
`contracts/candidate_fit_result.schema.json` before writing the scored JSON.

Do not create a temporary fit-result file, pass JSON through command arguments,
or read or rewrite analyzed JSON through PowerShell. `add_fit_score.py` owns
the UTF-8-safe scored persistence.

Close the candidate-fit target only after the complete semantic-fit queue has
finished or a batch-stopping failure occurs.

## 4. Job interest

For every scored JSON in the original scope order, run:

`python analyzer/job_interest/calculate.py --input <scored-json>`.

This adds `job_interest` to the same scored JSON.

Do not skip or replace an operation. Do not save to SQLite here. Return the
ordered list of fully scored JSON paths to the top-level workflow;
`db/save.py` owns database persistence.
