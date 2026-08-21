-- Record which collector first persisted a source vacancy.

ALTER TABLE source_jobs
ADD COLUMN collection_method TEXT NOT NULL DEFAULT 'unknown' CHECK (
    collection_method IN ('unknown', 'script', 'browser')
);

CREATE INDEX IF NOT EXISTS idx_source_jobs_collection_method
    ON source_jobs(collection_method);
