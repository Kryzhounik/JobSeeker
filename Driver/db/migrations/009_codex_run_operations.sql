-- Aggregate Codex usage per operation inside each workflow run.

CREATE TABLE IF NOT EXISTS codex_run_operations (
    run_id TEXT NOT NULL REFERENCES codex_runs(run_id) ON DELETE CASCADE,
    operation TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    invocation_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    duration_ms_sum INTEGER NOT NULL DEFAULT 0,
    duration_ms_avg REAL NOT NULL DEFAULT 0,
    input_tokens_sum INTEGER NOT NULL DEFAULT 0,
    input_tokens_avg REAL NOT NULL DEFAULT 0,
    cached_input_tokens_sum INTEGER NOT NULL DEFAULT 0,
    cached_input_tokens_avg REAL NOT NULL DEFAULT 0,
    output_tokens_sum INTEGER NOT NULL DEFAULT 0,
    output_tokens_avg REAL NOT NULL DEFAULT 0,
    reasoning_output_tokens_sum INTEGER NOT NULL DEFAULT 0,
    reasoning_output_tokens_avg REAL NOT NULL DEFAULT 0,
    weighted_tokens_sum REAL NOT NULL DEFAULT 0,
    weighted_tokens_avg REAL NOT NULL DEFAULT 0,
    estimated_credits_sum REAL NOT NULL DEFAULT 0,
    estimated_credits_avg REAL NOT NULL DEFAULT 0,
    PRIMARY KEY (run_id, operation)
);

CREATE INDEX IF NOT EXISTS idx_codex_run_operations_operation
    ON codex_run_operations(operation);

INSERT OR REPLACE INTO codex_run_operations (
    run_id, operation, started_at, finished_at,
    invocation_count, success_count, failure_count,
    duration_ms_sum, duration_ms_avg,
    input_tokens_sum, input_tokens_avg,
    cached_input_tokens_sum, cached_input_tokens_avg,
    output_tokens_sum, output_tokens_avg,
    reasoning_output_tokens_sum, reasoning_output_tokens_avg,
    weighted_tokens_sum, weighted_tokens_avg,
    estimated_credits_sum, estimated_credits_avg
)
SELECT
    run_id, operation, min(started_at), max(finished_at),
    count(*), sum(status = 'success'), sum(status = 'failed'),
    sum(duration_ms), avg(duration_ms),
    sum(input_tokens), avg(input_tokens),
    sum(cached_input_tokens), avg(cached_input_tokens),
    sum(output_tokens), avg(output_tokens),
    sum(reasoning_output_tokens), avg(reasoning_output_tokens),
    sum(weighted_tokens), avg(weighted_tokens),
    sum(estimated_credits), avg(estimated_credits)
FROM codex_invocations
GROUP BY run_id, operation;
