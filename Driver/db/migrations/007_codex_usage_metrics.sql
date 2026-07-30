-- Record Codex CLI usage per invocation and aggregate it per workflow run.

CREATE TABLE IF NOT EXISTS codex_runs (
    run_id TEXT PRIMARY KEY,
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    invocation_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
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
    estimated_credits_avg REAL NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS codex_invocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT NOT NULL REFERENCES codex_runs(run_id) ON DELETE CASCADE,
    operation TEXT NOT NULL,
    target TEXT NOT NULL DEFAULT '',
    command TEXT NOT NULL,
    model TEXT NOT NULL,
    thread_id TEXT NOT NULL DEFAULT '',
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    duration_ms INTEGER NOT NULL CHECK (duration_ms >= 0),
    exit_code INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('success', 'failed')),
    input_tokens INTEGER NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    cached_input_tokens INTEGER NOT NULL DEFAULT 0 CHECK (
        cached_input_tokens >= 0
        AND cached_input_tokens <= input_tokens
    ),
    output_tokens INTEGER NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    reasoning_output_tokens INTEGER NOT NULL DEFAULT 0 CHECK (
        reasoning_output_tokens >= 0
        AND reasoning_output_tokens <= output_tokens
    ),
    input_credit_rate REAL NOT NULL CHECK (input_credit_rate >= 0),
    cached_input_credit_rate REAL NOT NULL CHECK (
        cached_input_credit_rate >= 0
    ),
    output_credit_rate REAL NOT NULL CHECK (output_credit_rate >= 0),
    weighted_tokens REAL NOT NULL DEFAULT 0 CHECK (weighted_tokens >= 0),
    estimated_credits REAL NOT NULL DEFAULT 0 CHECK (estimated_credits >= 0),
    error_message TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_codex_invocations_run_id
    ON codex_invocations(run_id);
CREATE INDEX IF NOT EXISTS idx_codex_invocations_operation
    ON codex_invocations(operation);
CREATE INDEX IF NOT EXISTS idx_codex_invocations_started_at
    ON codex_invocations(started_at);
