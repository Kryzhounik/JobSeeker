-- Add five technology-importance levels, remove pros/cons, and isolate the
-- analyzer's experimental candidate-fit score.

PRAGMA foreign_keys = OFF;

DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_list;
DROP VIEW IF EXISTS job_view;

DROP TABLE IF EXISTS jobs_migration_006;

CREATE TABLE jobs_migration_006 (
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
    notes TEXT NOT NULL DEFAULT '',
    added_at TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO jobs_migration_006 (
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
    notes,
    added_at,
    created_at,
    updated_at
)
SELECT
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
    notes,
    added_at,
    created_at,
    updated_at
FROM jobs;

DROP TABLE jobs;
ALTER TABLE jobs_migration_006 RENAME TO jobs;

DROP TABLE IF EXISTS job_technologies_migration_006;

CREATE TABLE job_technologies_migration_006 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    technology_id INTEGER NOT NULL REFERENCES technologies(id) ON DELETE CASCADE,
    requirement_type TEXT NOT NULL CHECK (
        requirement_type IN (
            'core',
            'required',
            'important',
            'desired',
            'nice_to_have'
        )
    ),
    level TEXT NOT NULL CHECK (
        level IN ('nice to have', 'junior', 'regular', 'advanced', 'master')
    ),
    level_rank INTEGER NOT NULL CHECK (
        level_rank >= 1 AND level_rank <= 5
    ),
    raw_value TEXT NOT NULL DEFAULT '',
    UNIQUE(job_id, technology_id, requirement_type)
);

INSERT INTO job_technologies_migration_006 (
    id,
    job_id,
    technology_id,
    requirement_type,
    level,
    level_rank,
    raw_value
)
SELECT
    id,
    job_id,
    technology_id,
    requirement_type,
    level,
    level_rank,
    raw_value
FROM job_technologies;

DROP TABLE job_technologies;
ALTER TABLE job_technologies_migration_006 RENAME TO job_technologies;

CREATE TABLE IF NOT EXISTS experimental_analyzer_fits (
    source_job_ref INTEGER PRIMARY KEY REFERENCES source_jobs(id) ON DELETE CASCADE,
    analyzer_fit_percent INTEGER NOT NULL CHECK (
        analyzer_fit_percent >= 0 AND analyzer_fit_percent <= 100
    )
);

CREATE INDEX IF NOT EXISTS idx_jobs_company ON jobs(company);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_job_interest ON jobs(job_interest);
CREATE INDEX IF NOT EXISTS idx_jobs_candidate_fit ON jobs(candidate_fit_percent);
CREATE INDEX IF NOT EXISTS idx_jobs_candidate_fit_reason_code
    ON jobs(candidate_fit_reason_code);
CREATE INDEX IF NOT EXISTS idx_jobs_role ON jobs(role);
CREATE INDEX IF NOT EXISTS idx_jobs_remote_scope ON jobs(remote_scope);
CREATE INDEX IF NOT EXISTS idx_jobs_relocation ON jobs(relocation);
CREATE INDEX IF NOT EXISTS idx_jobs_primary_language ON jobs(primary_language_id);
CREATE INDEX IF NOT EXISTS idx_job_technologies_technology
    ON job_technologies(technology_id);
CREATE INDEX IF NOT EXISTS idx_job_technologies_requirement_type
    ON job_technologies(requirement_type);

PRAGMA foreign_keys = ON;
