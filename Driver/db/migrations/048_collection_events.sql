ALTER TABLE linkedin_collection_events RENAME TO collection_events;
ALTER TABLE collection_events ADD COLUMN source TEXT NOT NULL DEFAULT 'linkedin';

UPDATE collection_events SET source = 'justjoin'
WHERE label = 'justjoin'
   OR lower(source_url) LIKE 'https://justjoin.it/%'
   OR lower(source_url) LIKE 'http://justjoin.it/%';

DROP INDEX IF EXISTS idx_linkedin_collection_events_job_id;
DROP INDEX IF EXISTS idx_linkedin_collection_events_status;
DROP INDEX IF EXISTS idx_linkedin_collection_events_page;
CREATE INDEX idx_collection_events_source_job_id ON collection_events(source, job_id);
CREATE INDEX idx_collection_events_status ON collection_events(status);
CREATE INDEX idx_collection_events_page ON collection_events(source, label, start);
