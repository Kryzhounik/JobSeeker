CREATE TABLE IF NOT EXISTS cover_letters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id),
    letter_text TEXT NOT NULL CHECK (length(trim(letter_text)) > 0),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
