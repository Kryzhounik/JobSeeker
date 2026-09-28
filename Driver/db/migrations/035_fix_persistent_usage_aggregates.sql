-- Codex CLI usage snapshots are cumulative inside a persisted thread.
-- Preserve the raw snapshots and rebuild run aggregates from per-turn deltas.

DROP TABLE IF EXISTS temp.codex_invocation_usage_deltas;

CREATE TEMP TABLE codex_invocation_usage_deltas AS
WITH base_invocations AS (
    SELECT
        codex_invocations.*,
        CASE
            WHEN thread_id <> '' THEN thread_id
            ELSE 'invocation:' || id
        END AS usage_thread_id
    FROM codex_invocations
),
ordered_invocations AS (
    SELECT
        base_invocations.*,
        lag(input_tokens) OVER usage_thread AS previous_input_tokens,
        lag(cached_input_tokens) OVER usage_thread
            AS previous_cached_input_tokens,
        lag(output_tokens) OVER usage_thread AS previous_output_tokens,
        lag(reasoning_output_tokens) OVER usage_thread
            AS previous_reasoning_output_tokens
    FROM base_invocations
    WINDOW usage_thread AS (
        PARTITION BY run_id, usage_thread_id
        ORDER BY started_at, id
    )
),
usage_deltas AS (
    SELECT
        ordered_invocations.*,
        CASE
            WHEN previous_input_tokens IS NULL
                 OR input_tokens < previous_input_tokens
            THEN input_tokens
            ELSE input_tokens - previous_input_tokens
        END AS input_tokens_delta,
        CASE
            WHEN previous_cached_input_tokens IS NULL
                 OR cached_input_tokens < previous_cached_input_tokens
            THEN cached_input_tokens
            ELSE cached_input_tokens - previous_cached_input_tokens
        END AS cached_input_tokens_delta,
        CASE
            WHEN previous_output_tokens IS NULL
                 OR output_tokens < previous_output_tokens
            THEN output_tokens
            ELSE output_tokens - previous_output_tokens
        END AS output_tokens_delta,
        CASE
            WHEN previous_reasoning_output_tokens IS NULL
                 OR reasoning_output_tokens < previous_reasoning_output_tokens
            THEN reasoning_output_tokens
            ELSE reasoning_output_tokens - previous_reasoning_output_tokens
        END AS reasoning_output_tokens_delta
    FROM ordered_invocations
)
SELECT
    usage_deltas.*,
    (
        input_tokens_delta - cached_input_tokens_delta
        + cached_input_tokens_delta
            * cached_input_credit_rate / input_credit_rate
        + output_tokens_delta * output_credit_rate / input_credit_rate
    ) AS weighted_tokens_delta,
    (
        (input_tokens_delta - cached_input_tokens_delta) * input_credit_rate
        + cached_input_tokens_delta * cached_input_credit_rate
        + output_tokens_delta * output_credit_rate
    ) / 1000000.0 AS estimated_credits_delta
FROM usage_deltas;

UPDATE codex_runs
SET (
    started_at, finished_at,
    invocation_count, success_count, failure_count,
    input_tokens_sum, input_tokens_avg,
    cached_input_tokens_sum, cached_input_tokens_avg,
    output_tokens_sum, output_tokens_avg,
    reasoning_output_tokens_sum, reasoning_output_tokens_avg,
    weighted_tokens_sum, weighted_tokens_avg,
    estimated_credits_sum, estimated_credits_avg
) = (
    SELECT
        min(started_at), max(finished_at),
        count(*), sum(status = 'success'), sum(status = 'failed'),
        sum(input_tokens_delta), avg(input_tokens_delta),
        sum(cached_input_tokens_delta), avg(cached_input_tokens_delta),
        sum(output_tokens_delta), avg(output_tokens_delta),
        sum(reasoning_output_tokens_delta),
        avg(reasoning_output_tokens_delta),
        sum(weighted_tokens_delta), avg(weighted_tokens_delta),
        sum(estimated_credits_delta), avg(estimated_credits_delta)
    FROM codex_invocation_usage_deltas
    WHERE codex_invocation_usage_deltas.run_id = codex_runs.run_id
)
WHERE EXISTS (
    SELECT 1
    FROM codex_invocation_usage_deltas
    WHERE codex_invocation_usage_deltas.run_id = codex_runs.run_id
);

DELETE FROM codex_run_operations;

INSERT INTO codex_run_operations (
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
    sum(input_tokens_delta), avg(input_tokens_delta),
    sum(cached_input_tokens_delta), avg(cached_input_tokens_delta),
    sum(output_tokens_delta), avg(output_tokens_delta),
    sum(reasoning_output_tokens_delta), avg(reasoning_output_tokens_delta),
    sum(weighted_tokens_delta), avg(weighted_tokens_delta),
    sum(estimated_credits_delta), avg(estimated_credits_delta)
FROM codex_invocation_usage_deltas
GROUP BY run_id, operation;

DROP TABLE temp.codex_invocation_usage_deltas;
