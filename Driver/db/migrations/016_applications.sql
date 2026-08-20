-- Track submitted applications separately from the current vacancy status.

CREATE TABLE IF NOT EXISTS application_statuses (
    code TEXT PRIMARY KEY,
    sort_order INTEGER NOT NULL DEFAULT 0
);

INSERT OR IGNORE INTO application_statuses (code, sort_order) VALUES
    ('Applied', 10),
    ('Refused', 20),
    ('Confirmed', 30);

CREATE TABLE IF NOT EXISTS applications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,
    applied_at TEXT NOT NULL DEFAULT (date('now')),
    status TEXT NOT NULL DEFAULT 'Applied' REFERENCES application_statuses(code),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_applications_status
    ON applications(status);
CREATE INDEX IF NOT EXISTS idx_applications_applied_at
    ON applications(applied_at);
