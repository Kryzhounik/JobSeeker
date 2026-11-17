ALTER TABLE source_jobs ADD COLUMN dedup_checked INTEGER NOT NULL DEFAULT 0 CHECK (dedup_checked IN (0,1));
