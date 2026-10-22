-- Store per-call usage in the primary columns and retain original CLI snapshots.
BEGIN IMMEDIATE;

ALTER TABLE codex_invocations
    ADD COLUMN cumulative_input_tokens INTEGER NOT NULL DEFAULT 0
        CHECK (cumulative_input_tokens >= 0);
ALTER TABLE codex_invocations
    ADD COLUMN cumulative_cached_input_tokens INTEGER NOT NULL DEFAULT 0
        CHECK (cumulative_cached_input_tokens >= 0);
ALTER TABLE codex_invocations
    ADD COLUMN cumulative_output_tokens INTEGER NOT NULL DEFAULT 0
        CHECK (cumulative_output_tokens >= 0);
ALTER TABLE codex_invocations
    ADD COLUMN cumulative_reasoning_output_tokens INTEGER NOT NULL DEFAULT 0
        CHECK (cumulative_reasoning_output_tokens >= 0);

UPDATE codex_invocations
SET cumulative_input_tokens = input_tokens,
    cumulative_cached_input_tokens = cached_input_tokens,
    cumulative_output_tokens = output_tokens,
    cumulative_reasoning_output_tokens = reasoning_output_tokens;

DROP TABLE IF EXISTS temp.codex_invocation_usage_deltas;

CREATE TEMP TABLE codex_invocation_usage_deltas AS
WITH nonzero_snapshots AS (
    -- Failed calls without usage do not reset the next call's baseline.
    SELECT
        codex_invocations.*,
        CASE
            WHEN thread_id <> '' THEN thread_id
            ELSE 'invocation:' || id
        END AS usage_thread_id
    FROM codex_invocations
    WHERE cumulative_input_tokens > 0
       OR cumulative_cached_input_tokens > 0
       OR cumulative_output_tokens > 0
       OR cumulative_reasoning_output_tokens > 0
),
ordered_snapshots AS (
    SELECT
        id,
        lag(cumulative_input_tokens) OVER usage_thread AS previous_input_tokens,
        lag(cumulative_cached_input_tokens) OVER usage_thread
            AS previous_cached_input_tokens,
        lag(cumulative_output_tokens) OVER usage_thread AS previous_output_tokens,
        lag(cumulative_reasoning_output_tokens) OVER usage_thread
            AS previous_reasoning_output_tokens
    FROM nonzero_snapshots
    WINDOW usage_thread AS (
        PARTITION BY run_id, usage_thread_id
        ORDER BY started_at, id
    )
),
snapshot_baselines AS (
    SELECT
        codex_invocations.*,
        previous_input_tokens,
        previous_cached_input_tokens,
        previous_output_tokens,
        previous_reasoning_output_tokens,
        CASE
            WHEN previous_input_tokens IS NULL
                 OR cumulative_input_tokens < previous_input_tokens
                 OR cumulative_cached_input_tokens < previous_cached_input_tokens
                 OR cumulative_output_tokens < previous_output_tokens
                 OR cumulative_reasoning_output_tokens
                    < previous_reasoning_output_tokens
            THEN 1
            ELSE 0
        END AS usage_reset
    FROM codex_invocations
    LEFT JOIN ordered_snapshots ON ordered_snapshots.id = codex_invocations.id
),
usage_deltas AS (
    SELECT
        id,
        input_credit_rate,
        cached_input_credit_rate,
        output_credit_rate,
        CASE
            WHEN usage_reset THEN cumulative_input_tokens
            ELSE cumulative_input_tokens - previous_input_tokens
        END AS input_tokens_delta,
        CASE
            WHEN usage_reset THEN cumulative_cached_input_tokens
            ELSE cumulative_cached_input_tokens - previous_cached_input_tokens
        END AS cached_input_tokens_delta,
        CASE
            WHEN usage_reset THEN cumulative_output_tokens
            ELSE cumulative_output_tokens - previous_output_tokens
        END AS output_tokens_delta,
        CASE
            WHEN usage_reset THEN cumulative_reasoning_output_tokens
            ELSE cumulative_reasoning_output_tokens - previous_reasoning_output_tokens
        END AS reasoning_output_tokens_delta
    FROM snapshot_baselines
)
SELECT
    id,
    input_tokens_delta,
    cached_input_tokens_delta,
    output_tokens_delta,
    reasoning_output_tokens_delta,
    CASE
        WHEN input_credit_rate > 0 THEN
            input_tokens_delta - cached_input_tokens_delta
            + cached_input_tokens_delta
                * cached_input_credit_rate / input_credit_rate
            + output_tokens_delta * output_credit_rate / input_credit_rate
        ELSE 0
    END AS weighted_tokens_delta,
    (
        (input_tokens_delta - cached_input_tokens_delta) * input_credit_rate
        + cached_input_tokens_delta * cached_input_credit_rate
        + output_tokens_delta * output_credit_rate
    ) / 1000000.0 AS estimated_credits_delta
FROM usage_deltas;

UPDATE codex_invocations
SET (
    input_tokens, cached_input_tokens, output_tokens, reasoning_output_tokens,
    weighted_tokens, estimated_credits
) = (
    SELECT
        input_tokens_delta, cached_input_tokens_delta, output_tokens_delta,
        reasoning_output_tokens_delta, weighted_tokens_delta,
        estimated_credits_delta
    FROM codex_invocation_usage_deltas
    WHERE codex_invocation_usage_deltas.id = codex_invocations.id
);

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
        sum(input_tokens), avg(input_tokens),
        sum(cached_input_tokens), avg(cached_input_tokens),
        sum(output_tokens), avg(output_tokens),
        sum(reasoning_output_tokens), avg(reasoning_output_tokens),
        sum(weighted_tokens), avg(weighted_tokens),
        sum(estimated_credits), avg(estimated_credits)
    FROM codex_invocations
    WHERE codex_invocations.run_id = codex_runs.run_id
)
WHERE EXISTS (
    SELECT 1 FROM codex_invocations
    WHERE codex_invocations.run_id = codex_runs.run_id
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
    sum(input_tokens), avg(input_tokens),
    sum(cached_input_tokens), avg(cached_input_tokens),
    sum(output_tokens), avg(output_tokens),
    sum(reasoning_output_tokens), avg(reasoning_output_tokens),
    sum(weighted_tokens), avg(weighted_tokens),
    sum(estimated_credits), avg(estimated_credits)
FROM codex_invocations
GROUP BY run_id, operation;

DROP TABLE temp.codex_invocation_usage_deltas;

COMMIT;
