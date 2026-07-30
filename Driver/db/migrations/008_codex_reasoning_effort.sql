-- Record the configured reasoning level for each metered Codex invocation.

ALTER TABLE codex_invocations
ADD COLUMN reasoning_effort TEXT NOT NULL DEFAULT 'unknown';
