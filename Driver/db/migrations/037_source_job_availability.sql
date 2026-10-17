PRAGMA foreign_keys = OFF;
BEGIN IMMEDIATE;

CREATE TABLE IF NOT EXISTS availability_statuses (code TEXT PRIMARY KEY);
INSERT OR IGNORE INTO availability_statuses (code) VALUES
    ('available'), ('unavailable'), ('closed'), ('deleted');

ALTER TABLE source_jobs ADD COLUMN availability TEXT NOT NULL
    DEFAULT 'available' REFERENCES availability_statuses(code);
ALTER TABLE source_jobs ADD COLUMN unavailable_checks INTEGER NOT NULL
    DEFAULT 0 CHECK (unavailable_checks >= 0);

UPDATE source_jobs
SET availability = 'closed'
WHERE id IN (SELECT source_job_ref FROM jobs WHERE status = 'Closed');

COMMIT;
PRAGMA foreign_keys = ON;
