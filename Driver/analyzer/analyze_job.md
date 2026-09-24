Purpose: orchestrate post-`job_facts` analysis for one explicit vacancy scope.

Input:
- Source and ordered source-job IDs already processed by the Java-owned
  `job_facts` step. Every ID must have an analyzed JSON file. Never include a
  source job with processing status `NONRELEVANT`.

Output:
- One fully scored JSON file per input under `../Data/scored/<source>/`.
- Every output must match `contracts/scored_job.schema.json`.

Before starting, read `vacancies_per_agent` from
`analyzer/config/execution.ini` and record the current Java execution settings
once with:

`python analyzer/run_logger.py --run-id <run-id> --parallel-agents 1 --vacancies-per-agent <configured-vacancies-per-agent>`

Java currently processes groups sequentially. Support for configured
`parallel_agents` is tracked separately in the backlog.

Run these phases in this exact order for the whole scope.

For every PowerShell-to-Python stdin transfer below, read and use the fixed
transport stanza in `common/utf8_stdin.md`. Keep the payload in memory and use
the existing owning command; never embed JSON in an ordinary shell string.

## 1. Deterministic candidate-fit gate

For every analyzed JSON in scope, run:

`python analyzer/candidate_fit/filter.py --input <analyzed-json> --output <scored-json>`.

- If it returns `passed=false`, keep the scored JSON and do not send that
  vacancy to semantic candidate fit.
- If it returns `passed=true`, append the analyzed JSON to the semantic-fit
  queue in original scope order.

## 2. Semantic candidate fit

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

## 3. Job interest

For every scored JSON in the original scope order, run:

`python analyzer/job_interest/calculate.py --input <scored-json>`.

This adds `job_interest` to the same scored JSON.

Do not skip or replace an operation. Do not save to SQLite here. Return the
ordered list of fully scored JSON paths to the top-level workflow;
`db/save.py` owns database persistence.
