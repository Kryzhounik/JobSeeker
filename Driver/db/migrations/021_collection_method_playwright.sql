-- Allow vacancies collected by the Playwright collector to be identified separately.

PRAGMA foreign_keys = OFF;
BEGIN IMMEDIATE;

CREATE TABLE source_jobs_migration_021 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL REFERENCES job_sources(code),
    source_job_id TEXT NOT NULL,
    processing_status TEXT NOT NULL REFERENCES processing_statuses(code),
    collection_method TEXT NOT NULL DEFAULT 'unknown' CHECK (
        collection_method IN ('unknown', 'script', 'browser', 'playwright')
    ),
    UNIQUE(source, source_job_id)
);

INSERT INTO source_jobs_migration_021 (
    id,
    source,
    source_job_id,
    processing_status,
    collection_method
)
SELECT
    id,
    source,
    source_job_id,
    processing_status,
    collection_method
FROM source_jobs;

DROP TABLE source_jobs;
ALTER TABLE source_jobs_migration_021 RENAME TO source_jobs;

CREATE INDEX idx_source_jobs_processing_status
    ON source_jobs(processing_status);
CREATE INDEX idx_source_jobs_collection_method
    ON source_jobs(collection_method);

COMMIT;
PRAGMA foreign_keys = ON;
