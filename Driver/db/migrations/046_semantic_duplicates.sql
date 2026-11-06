ALTER TABLE source_jobs ADD COLUMN semantic_duplicate_of INTEGER REFERENCES source_jobs(id) ON DELETE SET NULL;
