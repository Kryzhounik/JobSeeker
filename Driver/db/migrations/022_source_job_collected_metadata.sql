-- Persist collector-visible vacancy fields beside analyzer-ready text.

ALTER TABLE source_job_texts
ADD COLUMN source_url TEXT NOT NULL DEFAULT '';

ALTER TABLE source_job_texts
ADD COLUMN title TEXT NOT NULL DEFAULT '';

ALTER TABLE source_job_texts
ADD COLUMN company_id INTEGER REFERENCES companies(id) ON DELETE SET NULL;

ALTER TABLE source_job_texts
ADD COLUMN collected_location TEXT NOT NULL DEFAULT '';

ALTER TABLE source_job_texts
ADD COLUMN collected_workplace TEXT NOT NULL DEFAULT 'unknown' CHECK (
    collected_workplace IN ('remote', 'hybrid', 'office', 'unknown')
);

ALTER TABLE source_job_texts
ADD COLUMN collected_salary TEXT NOT NULL DEFAULT '';
