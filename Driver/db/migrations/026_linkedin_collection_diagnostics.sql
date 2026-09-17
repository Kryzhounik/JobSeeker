CREATE TABLE IF NOT EXISTS linkedin_collection_runs (
    run_id TEXT PRIMARY KEY,
    started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TEXT,
    status TEXT NOT NULL DEFAULT 'running',
    stop_reason TEXT NOT NULL DEFAULT '',
    accepted_count INTEGER NOT NULL DEFAULT 0,
    config_json TEXT NOT NULL DEFAULT '{}',
    message TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS linkedin_collection_pages (
    run_id TEXT NOT NULL
        REFERENCES linkedin_collection_runs(run_id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    label TEXT NOT NULL DEFAULT '',
    search TEXT NOT NULL DEFAULT '',
    start INTEGER NOT NULL,
    requested_url TEXT NOT NULL DEFAULT '',
    actual_url TEXT NOT NULL DEFAULT '',
    layout TEXT NOT NULL DEFAULT '',
    materialized_count INTEGER NOT NULL DEFAULT 0,
    new_count INTEGER NOT NULL DEFAULT 0,
    target_new_count INTEGER NOT NULL DEFAULT 0,
    scroll_iterations INTEGER NOT NULL DEFAULT 0,
    unchanged_iterations INTEGER NOT NULL DEFAULT 0,
    card_ids_hash TEXT NOT NULL DEFAULT '',
    terminal INTEGER NOT NULL DEFAULT 0 CHECK (terminal IN (0, 1)),
    terminal_reason TEXT NOT NULL DEFAULT '',
    next_count INTEGER NOT NULL DEFAULT 0,
    next_visible INTEGER NOT NULL DEFAULT 0 CHECK (next_visible IN (0, 1)),
    next_disabled INTEGER NOT NULL DEFAULT 0 CHECK (next_disabled IN (0, 1)),
    next_aria_disabled TEXT NOT NULL DEFAULT '',
    next_label TEXT NOT NULL DEFAULT '',
    stop_reason TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_linkedin_collection_pages_location
    ON linkedin_collection_pages(run_id, label, start);
