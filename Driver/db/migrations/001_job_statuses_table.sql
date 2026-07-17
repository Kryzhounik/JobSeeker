-- Move jobs.status from an inline CHECK enum to job_statuses FK table.
-- Apply through Driver/db/migrate.py so views are recreated from schema.sql
-- after the jobs table rebuild.

PRAGMA foreign_keys = OFF;

DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_list;
DROP VIEW IF EXISTS job_view;

CREATE TABLE IF NOT EXISTS job_statuses (
    code TEXT PRIMARY KEY,
    sort_order INTEGER NOT NULL DEFAULT 0
);

INSERT OR IGNORE INTO job_statuses (code, sort_order) VALUES
    ('New', 10),
    ('Checked', 20),
    ('Approved', 30),
    ('Closed', 40);

UPDATE jobs
SET status = 'Closed'
WHERE status = 'Close';

DROP TABLE IF EXISTS jobs_migration_001;

CREATE TABLE jobs_migration_001 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL DEFAULT 'justjoin',
    source_job_id TEXT NOT NULL DEFAULT '',
    source_url TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'New' REFERENCES job_statuses(code),
    title TEXT NOT NULL,
    company TEXT NOT NULL DEFAULT '',
    location TEXT NOT NULL DEFAULT '',
    remote_type TEXT NOT NULL DEFAULT 'unknown' CHECK (
        remote_type IN ('remote', 'hybrid', 'office', 'unknown')
    ),
    remote_scope TEXT NOT NULL DEFAULT 'unknown',
    relocation TEXT NOT NULL DEFAULT 'NO',
    seniority TEXT NOT NULL DEFAULT 'unknown' CHECK (
        seniority IN ('intern', 'junior', 'middle', 'senior', 'lead', 'unknown')
    ),
    role TEXT NOT NULL DEFAULT 'other' CHECK (
        role IN (
            'backend',
            'frontend',
            'fullstack',
            'devops',
            'data',
            'ml_ai',
            'qa',
            'product',
            'mobile',
            'automation',
            'support',
            'artist',
            'other'
        )
    ),
    primary_language_id INTEGER REFERENCES languages(id) ON DELETE SET NULL,
    salary TEXT NOT NULL DEFAULT '',
    job_interest INTEGER NOT NULL DEFAULT 0,
    candidate_fit_percent INTEGER NOT NULL DEFAULT 100 CHECK (
        candidate_fit_percent >= 0 AND candidate_fit_percent <= 100
    ),
    candidate_fit_reason_code TEXT NOT NULL DEFAULT 'undefined' CHECK (
        candidate_fit_reason_code IN (
            'undefined',
            'ok',
            'lang',
            'loc',
            'tech',
            'role_mismatch',
            'skill_mismatch'
        )
    ),
    candidate_fit_reason TEXT NOT NULL DEFAULT '',
    summary TEXT NOT NULL DEFAULT '',
    pros TEXT NOT NULL DEFAULT '',
    cons TEXT NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT '',
    added_at TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO jobs_migration_001 (
    id,
    source,
    source_job_id,
    source_url,
    status,
    title,
    company,
    location,
    remote_type,
    remote_scope,
    relocation,
    seniority,
    role,
    primary_language_id,
    salary,
    job_interest,
    candidate_fit_percent,
    candidate_fit_reason_code,
    candidate_fit_reason,
    summary,
    pros,
    cons,
    notes,
    added_at,
    created_at,
    updated_at
)
SELECT
    id,
    source,
    source_job_id,
    source_url,
    status,
    title,
    company,
    location,
    remote_type,
    remote_scope,
    relocation,
    seniority,
    role,
    primary_language_id,
    salary,
    job_interest,
    candidate_fit_percent,
    candidate_fit_reason_code,
    candidate_fit_reason,
    summary,
    pros,
    cons,
    notes,
    added_at,
    created_at,
    updated_at
FROM jobs;

DROP TABLE jobs;
ALTER TABLE jobs_migration_001 RENAME TO jobs;

CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);

PRAGMA foreign_keys = ON;
