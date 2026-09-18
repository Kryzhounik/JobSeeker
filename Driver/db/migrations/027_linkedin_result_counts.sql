ALTER TABLE linkedin_collection_pages
ADD COLUMN expected_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE linkedin_collection_pages
ADD COLUMN total_results INTEGER;
