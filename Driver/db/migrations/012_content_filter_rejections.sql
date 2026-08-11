-- Persist structured second-stage filter rejections for later analysis.

CREATE TABLE IF NOT EXISTS content_filter_rejections (
    source_job_ref INTEGER NOT NULL REFERENCES source_jobs(id) ON DELETE CASCADE,
    rule TEXT NOT NULL CHECK (length(trim(rule)) > 0),
    matched_text TEXT NOT NULL CHECK (length(trim(matched_text)) > 0),
    matched_keyword TEXT NOT NULL CHECK (length(trim(matched_keyword)) > 0),
    matched_pattern TEXT NOT NULL CHECK (length(trim(matched_pattern)) > 0),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (source_job_ref, matched_keyword, matched_pattern)
);

CREATE INDEX IF NOT EXISTS idx_content_filter_rejections_keyword
    ON content_filter_rejections(matched_keyword);
CREATE INDEX IF NOT EXISTS idx_content_filter_rejections_pattern
    ON content_filter_rejections(matched_pattern);
