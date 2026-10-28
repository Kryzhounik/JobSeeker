BEGIN;
ALTER TABLE linkedin_collection_runs ADD COLUMN next_status TEXT NOT NULL DEFAULT 'running'
    CHECK (next_status IN ('running', 'pending', 'failed', 'complete'));
UPDATE linkedin_collection_runs SET next_status = status;
ALTER TABLE linkedin_collection_runs DROP COLUMN status;
ALTER TABLE linkedin_collection_runs RENAME COLUMN next_status TO status;
COMMIT;
