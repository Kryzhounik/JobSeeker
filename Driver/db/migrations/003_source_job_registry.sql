-- Move source identity and processing lifecycle out of jobs.

PRAGMA foreign_keys = OFF;

DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_list;
DROP VIEW IF EXISTS job_view;

CREATE TABLE IF NOT EXISTS job_sources (
    code TEXT PRIMARY KEY
);

INSERT OR IGNORE INTO job_sources (code) VALUES
    ('justjoin'),
    ('linkedin');

INSERT OR IGNORE INTO job_sources (code)
SELECT DISTINCT source
FROM jobs
WHERE trim(source) <> '';

CREATE TABLE IF NOT EXISTS processing_statuses (
    code TEXT PRIMARY KEY,
    sort_order INTEGER NOT NULL UNIQUE
);

INSERT OR IGNORE INTO processing_statuses (code, sort_order) VALUES
    ('RAW', 10),
    ('CLEANED', 20),
    ('ANALYZED', 30),
    ('SCORED', 40),
    ('SAVED', 50);

CREATE TABLE IF NOT EXISTS source_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL REFERENCES job_sources(code),
    source_job_id TEXT NOT NULL,
    processing_status TEXT NOT NULL REFERENCES processing_statuses(code),
    UNIQUE(source, source_job_id)
);

INSERT OR IGNORE INTO source_jobs (
    source,
    source_job_id,
    processing_status
)
SELECT
    source,
    CASE
        WHEN trim(source_job_id) <> '' THEN trim(source_job_id)
        ELSE source_url
    END,
    'SAVED'
FROM jobs;

DROP TABLE IF EXISTS jobs_migration_003;

CREATE TABLE jobs_migration_003 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_job_ref INTEGER NOT NULL UNIQUE REFERENCES source_jobs(id),
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

INSERT INTO jobs_migration_003 (
    id,
    source_job_ref,
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
    j.id,
    sj.id,
    j.source_url,
    j.status,
    j.title,
    j.company,
    j.location,
    j.remote_type,
    j.remote_scope,
    j.relocation,
    j.seniority,
    j.role,
    j.primary_language_id,
    j.salary,
    j.job_interest,
    j.candidate_fit_percent,
    j.candidate_fit_reason_code,
    j.candidate_fit_reason,
    j.summary,
    j.pros,
    j.cons,
    j.notes,
    j.added_at,
    j.created_at,
    j.updated_at
FROM jobs j
JOIN source_jobs sj
    ON sj.source = j.source
    AND sj.source_job_id = CASE
        WHEN trim(j.source_job_id) <> '' THEN trim(j.source_job_id)
        ELSE j.source_url
    END;

DROP TABLE jobs;
ALTER TABLE jobs_migration_003 RENAME TO jobs;

CREATE INDEX IF NOT EXISTS idx_source_jobs_processing_status
    ON source_jobs(processing_status);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);

PRAGMA foreign_keys = ON;
