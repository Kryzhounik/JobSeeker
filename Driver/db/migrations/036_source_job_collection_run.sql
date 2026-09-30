ALTER TABLE source_jobs
ADD COLUMN collection_run_id TEXT REFERENCES linkedin_collection_runs(run_id);

CREATE INDEX IF NOT EXISTS idx_source_jobs_collection_run_id
ON source_jobs(collection_run_id);
