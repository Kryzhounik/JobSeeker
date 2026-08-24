-- Add a user-managed priority flag to companies.

ALTER TABLE companies
ADD COLUMN priority INTEGER NOT NULL DEFAULT 0
    CHECK (priority IN (0, 1));

CREATE INDEX IF NOT EXISTS idx_companies_priority
    ON companies(priority);
