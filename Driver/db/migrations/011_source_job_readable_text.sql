-- Store the analyzer-ready vacancy text next to its source-job lifecycle row.

CREATE TABLE IF NOT EXISTS source_job_texts (
    source_job_ref INTEGER PRIMARY KEY REFERENCES source_jobs(id) ON DELETE CASCADE,
    readable_text TEXT NOT NULL CHECK (length(trim(readable_text)) > 0)
);
