BEGIN;

CREATE TABLE cover_letters_single (
    job_id INTEGER PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    letter_text TEXT NOT NULL CHECK (length(trim(letter_text)) > 0),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO cover_letters_single (job_id, letter_text, created_at)
SELECT job_id, letter_text, created_at
FROM cover_letters
WHERE rowid IN (SELECT max(rowid) FROM cover_letters GROUP BY job_id);

DROP TABLE cover_letters;
ALTER TABLE cover_letters_single RENAME TO cover_letters;

COMMIT;
