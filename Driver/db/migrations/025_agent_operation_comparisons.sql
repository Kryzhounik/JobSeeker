CREATE TABLE IF NOT EXISTS agent_operation_comparisons (
    operation_id TEXT PRIMARY KEY,
    operation_type TEXT NOT NULL CHECK (length(trim(operation_type)) > 0),
    desktop_response TEXT NOT NULL,
    cli_response TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_operation_comparisons_type
    ON agent_operation_comparisons(operation_type);
