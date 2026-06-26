PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS languages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL COLLATE NOCASE UNIQUE
);

CREATE TABLE IF NOT EXISTS technologies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL COLLATE NOCASE UNIQUE
);

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
    primary_language_id INTEGER REFERENCES languages(id) ON DELETE SET NULL,
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
    language_id INTEGER NOT NULL REFERENCES languages(id) ON DELETE CASCADE,
    level TEXT,
    level_rank INTEGER,
    raw_value TEXT,
    UNIQUE(job_id, language_id)
);

CREATE TABLE IF NOT EXISTS job_technologies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    technology_id INTEGER NOT NULL REFERENCES technologies(id) ON DELETE CASCADE,
    requirement_type TEXT NOT NULL CHECK (
        requirement_type IN ('required', 'nice_to_have')
    ),
    level TEXT,
    level_rank INTEGER,
    raw_value TEXT,
    UNIQUE(job_id, technology_id, requirement_type)
);

CREATE INDEX IF NOT EXISTS idx_jobs_company ON jobs(company);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_role ON jobs(role);
CREATE INDEX IF NOT EXISTS idx_jobs_primary_language
    ON jobs(primary_language_id);
CREATE INDEX IF NOT EXISTS idx_job_languages_language
    ON job_languages(language_id);
CREATE INDEX IF NOT EXISTS idx_job_technologies_technology
    ON job_technologies(technology_id);
CREATE INDEX IF NOT EXISTS idx_job_technologies_requirement_type
    ON job_technologies(requirement_type);

DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_list;
DROP VIEW IF EXISTS job_view;

CREATE VIEW job_view AS
WITH ordered AS (
    SELECT
        row_number() OVER (
            PARTITION BY j.id
            ORDER BY
                CASE jt.requirement_type
                    WHEN 'required' THEN 1
                    WHEN 'nice_to_have' THEN 2
                    ELSE 9
                END,
                jt.level_rank DESC,
                t.name COLLATE NOCASE
        ) AS row_in_job,
        j.id AS job_id_sort,
        j.title,
        j.company,
        j.location,
        j.remote_type,
        j.seniority,
        j.role,
        pl.name AS primary_language,
        j.salary,
        j.status,
        t.name AS technology,
        CASE jt.requirement_type
            WHEN 'required' THEN 'required'
            WHEN 'nice_to_have' THEN 'optional'
            ELSE jt.requirement_type
        END AS requirement,
        jt.level,
        j.source_url,
        j.summary,
        j.added_at
    FROM job_technologies jt
    JOIN jobs j ON j.id = jt.job_id
    JOIN technologies t ON t.id = jt.technology_id
    LEFT JOIN languages pl ON pl.id = j.primary_language_id
)
SELECT
    CASE WHEN row_in_job = 1 THEN CAST(job_id_sort AS TEXT) ELSE '' END AS job_id,
    CASE WHEN row_in_job = 1 THEN coalesce(title, '') ELSE '' END AS title,
    CASE WHEN row_in_job = 1 THEN coalesce(company, '') ELSE '' END AS company,
    CASE WHEN row_in_job = 1 THEN coalesce(remote_type, '') ELSE '' END AS remote_type,
    CASE WHEN row_in_job = 1 THEN coalesce(seniority, '') ELSE '' END AS seniority,
    CASE WHEN row_in_job = 1 THEN coalesce(role, '') ELSE '' END AS role,
    CASE WHEN row_in_job = 1 THEN coalesce(primary_language, '') ELSE '' END AS primary_language,
    CASE WHEN row_in_job = 1 THEN coalesce(salary, '') ELSE '' END AS salary,
    CASE WHEN row_in_job = 1 THEN coalesce(status, '') ELSE '' END AS status,
    coalesce(technology, '') AS technology,
    coalesce(requirement, '') AS requirement,
    coalesce(level, '') AS level,
    CASE WHEN row_in_job = 1 THEN coalesce(source_url, '') ELSE '' END AS source_url,
    CASE WHEN row_in_job = 1 THEN coalesce(summary, '') ELSE '' END AS summary,
    CASE WHEN row_in_job = 1 THEN coalesce(added_at, '') ELSE '' END AS added_at
FROM ordered
ORDER BY
    job_id_sort,
    row_in_job;
