-- Normalize job companies and make company-level preview blocking possible.

PRAGMA foreign_keys = OFF;

DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_list;
DROP VIEW IF EXISTS job_view;

CREATE TABLE IF NOT EXISTS companies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(trim(name)) > 0),
    blacklisted INTEGER NOT NULL DEFAULT 0 CHECK (blacklisted IN (0, 1))
);

INSERT OR IGNORE INTO companies (name)
SELECT DISTINCT trim(company)
FROM jobs
WHERE length(trim(company)) > 0;

DROP TABLE IF EXISTS jobs_migration_013;

CREATE TABLE jobs_migration_013 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_job_ref INTEGER NOT NULL UNIQUE REFERENCES source_jobs(id),
    source_url TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'New' REFERENCES job_statuses(code),
    title TEXT NOT NULL,
    company_id INTEGER REFERENCES companies(id) ON DELETE SET NULL,
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
    notes TEXT NOT NULL DEFAULT '',
    added_at TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO jobs_migration_013 (
    id,
    source_job_ref,
    source_url,
    status,
    title,
    company_id,
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
    notes,
    added_at,
    created_at,
    updated_at
)
SELECT
    j.id,
    j.source_job_ref,
    j.source_url,
    j.status,
    j.title,
    (
        SELECT c.id
        FROM companies c
        WHERE c.name = trim(j.company) COLLATE NOCASE
        LIMIT 1
    ),
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
    j.notes,
    j.added_at,
    j.created_at,
    j.updated_at
FROM jobs j;

DROP TABLE jobs;
ALTER TABLE jobs_migration_013 RENAME TO jobs;

PRAGMA foreign_keys = ON;
