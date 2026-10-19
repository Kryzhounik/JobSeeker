ALTER TABLE source_job_texts ADD COLUMN easy_apply INTEGER CHECK (easy_apply IN (0, 1));
