CREATE TABLE IF NOT EXISTS cover_letters (
    job_id INTEGER PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    letter_text TEXT NOT NULL CHECK (length(trim(letter_text)) > 0),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
