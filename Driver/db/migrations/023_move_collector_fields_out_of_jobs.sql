-- Keep collector-owned URL, title, and company only with collected source data.

PRAGMA foreign_keys = OFF;

DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_list;
DROP VIEW IF EXISTS job_view;

UPDATE source_job_texts
SET
    source_url = CASE
        WHEN length(trim(source_url)) = 0 THEN coalesce((
            SELECT jobs.source_url
            FROM jobs
            WHERE jobs.source_job_ref = source_job_texts.source_job_ref
        ), '')
        ELSE source_url
    END,
    title = CASE
        WHEN length(trim(title)) = 0 THEN coalesce((
            SELECT jobs.title
            FROM jobs
            WHERE jobs.source_job_ref = source_job_texts.source_job_ref
        ), '')
        ELSE title
    END,
    company_id = coalesce(company_id, (
        SELECT jobs.company_id
        FROM jobs
        WHERE jobs.source_job_ref = source_job_texts.source_job_ref
    ))
WHERE EXISTS (
    SELECT 1
    FROM jobs
    WHERE jobs.source_job_ref = source_job_texts.source_job_ref
);

DROP TABLE IF EXISTS jobs_migration_023;

CREATE TABLE jobs_migration_023 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_job_ref INTEGER NOT NULL UNIQUE REFERENCES source_jobs(id),
    status TEXT NOT NULL DEFAULT 'New' REFERENCES job_statuses(code),
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

INSERT INTO jobs_migration_023 (
    id,
    source_job_ref,
    status,
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
    id,
    source_job_ref,
    status,
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
FROM jobs;

DROP TABLE jobs;
ALTER TABLE jobs_migration_023 RENAME TO jobs;

PRAGMA foreign_keys = ON;
