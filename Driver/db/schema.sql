-- JobSeeker SQLite schema.
-- Keep storage/view definitions here; do not put scoring or analysis logic in SQL.
-- JSON <-> table mapping belongs in db/job_mapper.py.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS languages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL COLLATE NOCASE UNIQUE
);

CREATE TABLE IF NOT EXISTS technologies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL COLLATE NOCASE UNIQUE
);

CREATE TABLE IF NOT EXISTS job_statuses (
    code TEXT PRIMARY KEY,
    sort_order INTEGER NOT NULL DEFAULT 0
);

INSERT OR IGNORE INTO job_statuses (code, sort_order) VALUES
    ('New', 10),
    ('Checked', 20),
    ('Postponed', 30),
    ('Applied', 40),
    ('Closed', 50);

CREATE TABLE IF NOT EXISTS job_sources (
    code TEXT PRIMARY KEY
);

INSERT OR IGNORE INTO job_sources (code) VALUES
    ('justjoin'),
    ('linkedin');

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

CREATE TABLE IF NOT EXISTS jobs (
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

CREATE TABLE IF NOT EXISTS job_languages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    language_id INTEGER NOT NULL REFERENCES languages(id) ON DELETE CASCADE,
    level TEXT NOT NULL DEFAULT '',
    level_rank INTEGER NOT NULL CHECK (
        level_rank >= 1 AND level_rank <= 6
    ),
    raw_value TEXT NOT NULL DEFAULT '',
    UNIQUE(job_id, language_id)
);

CREATE TABLE IF NOT EXISTS job_technologies (
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

CREATE TABLE IF NOT EXISTS linkedin_collection_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    label TEXT NOT NULL DEFAULT '',
    start INTEGER,
    card_index INTEGER,
    job_id TEXT NOT NULL DEFAULT '',
    source_url TEXT NOT NULL DEFAULT '',
    title TEXT NOT NULL DEFAULT '',
    company TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS codex_runs (
    run_id TEXT PRIMARY KEY,
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    invocation_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    input_tokens_sum INTEGER NOT NULL DEFAULT 0,
    input_tokens_avg REAL NOT NULL DEFAULT 0,
    cached_input_tokens_sum INTEGER NOT NULL DEFAULT 0,
    cached_input_tokens_avg REAL NOT NULL DEFAULT 0,
    output_tokens_sum INTEGER NOT NULL DEFAULT 0,
    output_tokens_avg REAL NOT NULL DEFAULT 0,
    reasoning_output_tokens_sum INTEGER NOT NULL DEFAULT 0,
    reasoning_output_tokens_avg REAL NOT NULL DEFAULT 0,
    weighted_tokens_sum REAL NOT NULL DEFAULT 0,
    weighted_tokens_avg REAL NOT NULL DEFAULT 0,
    estimated_credits_sum REAL NOT NULL DEFAULT 0,
    estimated_credits_avg REAL NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS codex_invocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT NOT NULL REFERENCES codex_runs(run_id) ON DELETE CASCADE,
    operation TEXT NOT NULL,
    target TEXT NOT NULL DEFAULT '',
    command TEXT NOT NULL,
    model TEXT NOT NULL,
    reasoning_effort TEXT NOT NULL DEFAULT 'unknown',
    thread_id TEXT NOT NULL DEFAULT '',
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    duration_ms INTEGER NOT NULL CHECK (duration_ms >= 0),
    exit_code INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('success', 'failed')),
    input_tokens INTEGER NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    cached_input_tokens INTEGER NOT NULL DEFAULT 0 CHECK (
        cached_input_tokens >= 0
        AND cached_input_tokens <= input_tokens
    ),
    output_tokens INTEGER NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    reasoning_output_tokens INTEGER NOT NULL DEFAULT 0 CHECK (
        reasoning_output_tokens >= 0
        AND reasoning_output_tokens <= output_tokens
    ),
    input_credit_rate REAL NOT NULL CHECK (input_credit_rate >= 0),
    cached_input_credit_rate REAL NOT NULL CHECK (
        cached_input_credit_rate >= 0
    ),
    output_credit_rate REAL NOT NULL CHECK (output_credit_rate >= 0),
    weighted_tokens REAL NOT NULL DEFAULT 0 CHECK (weighted_tokens >= 0),
    estimated_credits REAL NOT NULL DEFAULT 0 CHECK (estimated_credits >= 0),
    error_message TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS codex_run_operations (
    run_id TEXT NOT NULL REFERENCES codex_runs(run_id) ON DELETE CASCADE,
    operation TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    invocation_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    duration_ms_sum INTEGER NOT NULL DEFAULT 0,
    duration_ms_avg REAL NOT NULL DEFAULT 0,
    input_tokens_sum INTEGER NOT NULL DEFAULT 0,
    input_tokens_avg REAL NOT NULL DEFAULT 0,
    cached_input_tokens_sum INTEGER NOT NULL DEFAULT 0,
    cached_input_tokens_avg REAL NOT NULL DEFAULT 0,
    output_tokens_sum INTEGER NOT NULL DEFAULT 0,
    output_tokens_avg REAL NOT NULL DEFAULT 0,
    reasoning_output_tokens_sum INTEGER NOT NULL DEFAULT 0,
    reasoning_output_tokens_avg REAL NOT NULL DEFAULT 0,
    weighted_tokens_sum REAL NOT NULL DEFAULT 0,
    weighted_tokens_avg REAL NOT NULL DEFAULT 0,
    estimated_credits_sum REAL NOT NULL DEFAULT 0,
    estimated_credits_avg REAL NOT NULL DEFAULT 0,
    PRIMARY KEY (run_id, operation)
);

CREATE INDEX IF NOT EXISTS idx_jobs_company ON jobs(company);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_source_jobs_processing_status
    ON source_jobs(processing_status);
CREATE INDEX IF NOT EXISTS idx_jobs_job_interest ON jobs(job_interest);
CREATE INDEX IF NOT EXISTS idx_jobs_candidate_fit ON jobs(candidate_fit_percent);
CREATE INDEX IF NOT EXISTS idx_jobs_candidate_fit_reason_code
    ON jobs(candidate_fit_reason_code);
CREATE INDEX IF NOT EXISTS idx_jobs_role ON jobs(role);
CREATE INDEX IF NOT EXISTS idx_jobs_remote_scope ON jobs(remote_scope);
CREATE INDEX IF NOT EXISTS idx_jobs_relocation ON jobs(relocation);
CREATE INDEX IF NOT EXISTS idx_jobs_primary_language
    ON jobs(primary_language_id);
CREATE INDEX IF NOT EXISTS idx_job_languages_language
    ON job_languages(language_id);
CREATE INDEX IF NOT EXISTS idx_job_technologies_technology
    ON job_technologies(technology_id);
CREATE INDEX IF NOT EXISTS idx_job_technologies_requirement_type
    ON job_technologies(requirement_type);
CREATE INDEX IF NOT EXISTS idx_linkedin_collection_events_job_id
    ON linkedin_collection_events(job_id);
CREATE INDEX IF NOT EXISTS idx_linkedin_collection_events_status
    ON linkedin_collection_events(status);
CREATE INDEX IF NOT EXISTS idx_linkedin_collection_events_page
    ON linkedin_collection_events(label, start);
CREATE INDEX IF NOT EXISTS idx_codex_invocations_run_id
    ON codex_invocations(run_id);
CREATE INDEX IF NOT EXISTS idx_codex_invocations_operation
    ON codex_invocations(operation);
CREATE INDEX IF NOT EXISTS idx_codex_invocations_started_at
    ON codex_invocations(started_at);
CREATE INDEX IF NOT EXISTS idx_codex_run_operations_operation
    ON codex_run_operations(operation);

DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_list;
DROP VIEW IF EXISTS job_view;

CREATE VIEW job_list AS
SELECT
    CAST(CAST(ROUND(j.job_interest * j.candidate_fit_percent * j.candidate_fit_percent / 10000.0) AS INTEGER) AS TEXT) AS score,
    CAST(j.candidate_fit_percent AS TEXT) AS fit,
    CAST(j.job_interest AS TEXT) AS interest,
    coalesce(j.remote_scope, '') AS remote_scope,
    coalesce(j.status, 'New') AS status,
    coalesce(j.relocation, '') AS relocation,
    coalesce(j.remote_type, '') AS remote_type,
    coalesce(j.location, '') AS location,
    (
        SELECT l.name || coalesce(': ' || nullif(jl.level, ''), '')
        FROM job_languages jl
        JOIN languages l ON l.id = jl.language_id
        WHERE jl.job_id = j.id
            AND jl.language_id = j.primary_language_id
        LIMIT 1
    ) AS primary_language,
    coalesce((
        SELECT group_concat(language_value, '; ')
        FROM (
            SELECT l.name || coalesce(': ' || nullif(jl.level, ''), '')
                AS language_value
            FROM job_languages jl
            JOIN languages l ON l.id = jl.language_id
            WHERE jl.job_id = j.id
            ORDER BY
                jl.level_rank DESC,
                l.name COLLATE NOCASE
        )
    ), '') AS languages,
    coalesce((
        SELECT group_concat(technology_value, '; ')
        FROM (
            SELECT
                t.name
                || ' ('
                || CASE jt.requirement_type
                    WHEN 'core' THEN 'core'
                    WHEN 'required' THEN 'req'
                    WHEN 'important' THEN 'imp'
                    WHEN 'desired' THEN 'des'
                    WHEN 'nice_to_have' THEN 'opt'
                    ELSE jt.requirement_type
                END
                || coalesce(
                    ', ' || nullif(
                        CASE
                            WHEN jt.requirement_type = 'nice_to_have'
                                THEN 'nice to have'
                            WHEN lower(coalesce(jt.level, '')) IN (
                                '',
                                'listed',
                                'mentioned',
                                'required',
                                'required/listed'
                            ) THEN
                                CASE jt.level_rank
                                    WHEN 1 THEN 'nice to have'
                                    WHEN 2 THEN 'junior'
                                    WHEN 3 THEN 'regular'
                                    WHEN 4 THEN 'advanced'
                                    WHEN 5 THEN 'master'
                                    ELSE ''
                                END
                            ELSE
                                CASE jt.level_rank
                                    WHEN 1 THEN 'nice to have'
                                    WHEN 2 THEN 'junior'
                                    WHEN 3 THEN 'regular'
                                    WHEN 4 THEN 'advanced'
                                    WHEN 5 THEN 'master'
                                    ELSE ''
                                END
                        END,
                        ''
                    ),
                    ''
                )
                || ')' AS technology_value
            FROM job_technologies jt
            JOIN technologies t ON t.id = jt.technology_id
            WHERE jt.job_id = j.id
            ORDER BY
                CASE jt.requirement_type
                    WHEN 'core' THEN 1
                    WHEN 'required' THEN 2
                    WHEN 'important' THEN 3
                    WHEN 'desired' THEN 4
                    WHEN 'nice_to_have' THEN 5
                    ELSE 9
                END,
                jt.level_rank DESC,
                t.name COLLATE NOCASE
        )
    ), '') AS technologies,
    coalesce(nullif(j.salary, 'unknown'), '') AS salary,
    coalesce(j.seniority, '') AS seniority,
    coalesce(j.role, '') AS role,
    coalesce(j.title, '') AS title,
    coalesce(j.source_url, '') AS source_url,
    coalesce(j.company, '') AS company,
    coalesce(j.added_at, '') AS added_at,
    coalesce(j.summary, '') AS summary
FROM jobs j
ORDER BY
    CAST(ROUND(j.job_interest * j.candidate_fit_percent * j.candidate_fit_percent / 10000.0) AS INTEGER) DESC,
    j.job_interest DESC,
    j.id;

CREATE VIEW job_view AS
WITH ordered AS (
    SELECT
        row_number() OVER (
            PARTITION BY j.id
            ORDER BY
                CASE jt.requirement_type
                    WHEN 'core' THEN 1
                    WHEN 'required' THEN 2
                    WHEN 'important' THEN 3
                    WHEN 'desired' THEN 4
                    WHEN 'nice_to_have' THEN 5
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
        j.remote_scope,
        j.status,
        j.relocation,
        CAST(ROUND(j.job_interest * j.candidate_fit_percent * j.candidate_fit_percent / 10000.0) AS INTEGER)
            AS score_sort,
        j.job_interest AS interest_sort,
        j.candidate_fit_percent AS fit_sort,
        j.seniority,
        j.role,
        (
            SELECT l.name || coalesce(': ' || nullif(jl.level, ''), '')
            FROM job_languages jl
            JOIN languages l ON l.id = jl.language_id
            WHERE jl.job_id = j.id
                AND jl.language_id = j.primary_language_id
            LIMIT 1
        ) AS primary_language,
        (
            SELECT group_concat(language_value, '; ')
            FROM (
                SELECT l.name || coalesce(': ' || nullif(jl.level, ''), '')
                    AS language_value
                FROM job_languages jl
                JOIN languages l ON l.id = jl.language_id
                WHERE jl.job_id = j.id
                ORDER BY
                    jl.level_rank DESC,
                    l.name COLLATE NOCASE
            )
        ) AS languages,
        j.salary,
        t.name AS technology,
        CASE jt.requirement_type
            WHEN 'core' THEN 'core'
            WHEN 'required' THEN 'req'
            WHEN 'important' THEN 'imp'
            WHEN 'desired' THEN 'des'
            WHEN 'nice_to_have' THEN 'opt'
            ELSE jt.requirement_type
        END AS req,
        CASE
            WHEN jt.requirement_type = 'nice_to_have' THEN 'nice to have'
            WHEN lower(coalesce(jt.level, '')) IN (
                '',
                'listed',
                'mentioned',
                'required',
                'required/listed'
            ) THEN
                CASE jt.level_rank
                    WHEN 1 THEN 'nice to have'
                    WHEN 2 THEN 'junior'
                    WHEN 3 THEN 'regular'
                    WHEN 4 THEN 'advanced'
                    WHEN 5 THEN 'master'
                    ELSE ''
                END
            ELSE
                CASE jt.level_rank
                    WHEN 1 THEN 'nice to have'
                    WHEN 2 THEN 'junior'
                    WHEN 3 THEN 'regular'
                    WHEN 4 THEN 'advanced'
                    WHEN 5 THEN 'master'
                    ELSE ''
                END
        END AS level,
        j.source_url,
        j.summary,
        j.added_at
    FROM job_technologies jt
    JOIN jobs j ON j.id = jt.job_id
    JOIN technologies t ON t.id = jt.technology_id
    LEFT JOIN languages pl ON pl.id = j.primary_language_id
)
SELECT
    CASE WHEN row_in_job = 1 THEN CAST(score_sort AS TEXT) ELSE '' END AS score,
    CASE WHEN row_in_job = 1 THEN CAST(fit_sort AS TEXT) ELSE '' END AS fit,
    CASE WHEN row_in_job = 1 THEN CAST(interest_sort AS TEXT) ELSE '' END AS interest,
    CASE WHEN row_in_job = 1 THEN coalesce(remote_scope, '') ELSE '' END AS remote_scope,
    CASE WHEN row_in_job = 1 THEN coalesce(status, 'New') ELSE '' END AS status,
    CASE WHEN row_in_job = 1 THEN coalesce(relocation, '') ELSE '' END AS relocation,
    CASE WHEN row_in_job = 1 THEN coalesce(remote_type, '') ELSE '' END AS remote_type,
    CASE WHEN row_in_job = 1 THEN coalesce(location, '') ELSE '' END AS location,
    CASE WHEN row_in_job = 1 THEN coalesce(primary_language, '') ELSE '' END AS primary_language,
    CASE WHEN row_in_job = 1 THEN coalesce(languages, '') ELSE '' END AS languages,
    technology,
    coalesce(req, '') AS req,
    coalesce(level, '') AS level,
    CASE WHEN row_in_job = 1 THEN coalesce(nullif(salary, 'unknown'), '') ELSE '' END AS salary,
    CASE WHEN row_in_job = 1 THEN coalesce(seniority, '') ELSE '' END AS seniority,
    CASE WHEN row_in_job = 1 THEN coalesce(role, '') ELSE '' END AS role,
    CASE WHEN row_in_job = 1 THEN coalesce(title, '') ELSE '' END AS title,
    CASE WHEN row_in_job = 1 THEN coalesce(source_url, '') ELSE '' END AS source_url,
    CASE WHEN row_in_job = 1 THEN coalesce(company, '') ELSE '' END AS company,
    CASE WHEN row_in_job = 1 THEN coalesce(added_at, '') ELSE '' END AS added_at,
    CASE WHEN row_in_job = 1 THEN coalesce(summary, '') ELSE '' END AS summary
FROM ordered
WHERE interest_sort > 0
    AND fit_sort > 0
ORDER BY
    score_sort DESC,
    interest_sort DESC,
    job_id_sort,
    row_in_job;
