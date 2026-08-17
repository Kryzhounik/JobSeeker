-- Record which preview filter rejected a vacancy.

CREATE TABLE IF NOT EXISTS preview_filter_rejections (
    title TEXT NOT NULL,
    blocked_term TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    last_source_url TEXT,
    last_company TEXT,
    last_seen_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (title, blocked_term)
);

ALTER TABLE preview_filter_rejections
ADD COLUMN rule TEXT NOT NULL DEFAULT 'title_blocked';
