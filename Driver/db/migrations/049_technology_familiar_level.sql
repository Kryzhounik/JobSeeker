BEGIN;

DROP VIEW IF EXISTS job_technology_display;
DROP VIEW IF EXISTS job_language_list;
DROP VIEW IF EXISTS job_technology_list;
DROP VIEW IF EXISTS job_view;
DROP VIEW IF EXISTS job_list;

CREATE TABLE job_technologies_migration_049 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    technology_id INTEGER NOT NULL REFERENCES technologies(id) ON DELETE CASCADE,
    requirement_type TEXT NOT NULL CHECK (
        requirement_type IN ('core', 'required', 'important', 'desired', 'nice_to_have')
    ),
    level TEXT NOT NULL CHECK (
        level IN ('familiar', 'junior', 'regular', 'advanced', 'master')
    ),
    level_rank INTEGER NOT NULL CHECK (level_rank >= 1 AND level_rank <= 5),
    raw_value TEXT NOT NULL DEFAULT '',
    UNIQUE(job_id, technology_id, requirement_type)
);

INSERT INTO job_technologies_migration_049
    (id, job_id, technology_id, requirement_type, level, level_rank, raw_value)
SELECT id, job_id, technology_id, requirement_type,
    CASE level WHEN 'nice to have' THEN 'familiar' ELSE level END,
    level_rank, raw_value
FROM job_technologies;

DROP TABLE job_technologies;
ALTER TABLE job_technologies_migration_049 RENAME TO job_technologies;

CREATE INDEX idx_job_technologies_technology ON job_technologies(technology_id);
CREATE INDEX idx_job_technologies_requirement_type ON job_technologies(requirement_type);

COMMIT;
