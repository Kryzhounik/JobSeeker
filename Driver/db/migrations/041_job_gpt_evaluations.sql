CREATE TABLE IF NOT EXISTS job_gpt_evaluations (
    job_id INTEGER PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    answer TEXT NOT NULL CHECK (length(trim(answer)) > 0),
    evaluated_at TEXT NOT NULL
);
