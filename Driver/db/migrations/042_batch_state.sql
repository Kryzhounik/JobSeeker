ALTER TABLE linkedin_collection_runs RENAME COLUMN status TO collection_status;
ALTER TABLE linkedin_collection_runs RENAME COLUMN finished_at TO collection_finished_at;
ALTER TABLE linkedin_collection_runs ADD COLUMN status TEXT NOT NULL DEFAULT 'running'
    CHECK (status IN ('running', 'failed', 'complete'));
ALTER TABLE linkedin_collection_runs ADD COLUMN stage TEXT NOT NULL DEFAULT 'Collection';
ALTER TABLE linkedin_collection_runs ADD COLUMN finished_at TEXT;

-- Old completion timestamps describe collection, not the complete workflow.
UPDATE linkedin_collection_runs
SET status = 'failed', finished_at = collection_finished_at
WHERE collection_status NOT IN ('running', 'complete');
