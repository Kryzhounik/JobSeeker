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

DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
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
    pl.name AS primary_language,
    j.salary,
    j.status,
    (
        SELECT group_concat(
            l.name || coalesce(': ' || nullif(jl.level, ''), ''),
            '; '
        )
        FROM job_languages jl
        JOIN languages l ON l.id = jl.language_id
        WHERE jl.job_id = j.id
    ) AS languages,
    j.source_url,
    j.summary,
    j.added_at
FROM jobs j
LEFT JOIN languages pl ON pl.id = j.primary_language_id;

CREATE VIEW job_language_list AS
SELECT
    j.id AS job_id,
    j.title,
    j.company,
    l.name AS language,
    jl.level,
    jl.level_rank,
    CASE
        WHEN j.primary_language_id = l.id THEN 1
        ELSE 0
    END AS is_primary,
    j.source_url
FROM job_languages jl
JOIN jobs j ON j.id = jl.job_id
JOIN languages l ON l.id = jl.language_id
ORDER BY
    j.id,
    is_primary DESC,
    coalesce(jl.level_rank, 0) DESC,
    l.name COLLATE NOCASE;

CREATE VIEW job_technology_list AS
SELECT
    j.id AS job_id,
    j.title,
    j.company,
    j.remote_type,
    j.seniority,
    j.role,
    t.name AS technology,
    jt.requirement_type,
    CASE jt.requirement_type
        WHEN 'required' THEN 1
        WHEN 'nice_to_have' THEN 2
        ELSE 9
    END AS requirement_priority,
    jt.level,
    jt.level_rank,
    jt.raw_value,
    j.source_url
FROM job_technologies jt
JOIN jobs j ON j.id = jt.job_id
JOIN technologies t ON t.id = jt.technology_id
ORDER BY
    j.id,
    requirement_priority,
    coalesce(jt.level_rank, 0) DESC,
    t.name COLLATE NOCASE;

CREATE VIEW job_technology_display AS
WITH ordered AS (
    SELECT
        row_number() OVER (
            PARTITION BY job_id
            ORDER BY
                requirement_priority,
                coalesce(level_rank, 0) DESC,
                technology COLLATE NOCASE
        ) AS row_in_job,
        *
    FROM job_technology_list
)
SELECT
    CASE WHEN row_in_job = 1 THEN job_id ELSE NULL END AS job_id,
    CASE WHEN row_in_job = 1 THEN title ELSE '' END AS title,
    CASE WHEN row_in_job = 1 THEN company ELSE '' END AS company,
    CASE WHEN row_in_job = 1 THEN remote_type ELSE '' END AS remote_type,
    CASE WHEN row_in_job = 1 THEN seniority ELSE '' END AS seniority,
    CASE WHEN row_in_job = 1 THEN role ELSE '' END AS role,
    technology,
    requirement_type,
    requirement_priority,
    level,
    level_rank,
    raw_value,
    CASE WHEN row_in_job = 1 THEN source_url ELSE '' END AS source_url
FROM ordered
ORDER BY
    coalesce(job_id, 0),
    row_in_job;
