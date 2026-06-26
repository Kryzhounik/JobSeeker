PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL DEFAULT 'justjoin',
    source_url TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    company TEXT,
    location TEXT,
    remote_type TEXT,
    seniority TEXT,
    role TEXT,
    salary TEXT,
    status TEXT NOT NULL DEFAULT 'new',
    summary TEXT,
    pros TEXT,
    cons TEXT,
    notes TEXT,
    added_at TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_languages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    language TEXT NOT NULL,
    level TEXT,
    raw_value TEXT,
    UNIQUE(job_id, language)
);

CREATE TABLE IF NOT EXISTS job_technologies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    technology TEXT NOT NULL,
    level TEXT,
    is_required INTEGER NOT NULL DEFAULT 1,
    raw_value TEXT,
    UNIQUE(job_id, technology, is_required)
);

CREATE INDEX IF NOT EXISTS idx_jobs_company ON jobs(company);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_role ON jobs(role);
CREATE INDEX IF NOT EXISTS idx_job_languages_language ON job_languages(language);
CREATE INDEX IF NOT EXISTS idx_job_technologies_technology ON job_technologies(technology);

DROP VIEW IF EXISTS job_list;

CREATE VIEW job_list AS
SELECT
    j.id,
    j.title,
    j.company,
    j.location,
    j.remote_type,
    j.seniority,
    j.role,
    j.salary,
    j.status,
    (
        SELECT group_concat(
            jl.language || coalesce(': ' || nullif(jl.level, ''), ''),
            '; '
        )
        FROM job_languages jl
        WHERE jl.job_id = j.id
    ) AS languages,
    (
        SELECT group_concat(
            jt.technology || coalesce(': ' || nullif(jt.level, ''), ''),
            '; '
        )
        FROM job_technologies jt
        WHERE jt.job_id = j.id AND jt.is_required = 1
    ) AS required_technologies,
    (
        SELECT group_concat(
            jt.technology || coalesce(': ' || nullif(jt.level, ''), ''),
            '; '
        )
        FROM job_technologies jt
        WHERE jt.job_id = j.id AND jt.is_required = 0
    ) AS nice_to_have_technologies,
    j.source_url,
    j.summary,
    j.added_at
FROM jobs j;
