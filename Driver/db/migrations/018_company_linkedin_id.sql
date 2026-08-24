-- Store an optional LinkedIn company identifier.

ALTER TABLE companies
ADD COLUMN linkedin_id TEXT CHECK (
    linkedin_id IS NULL OR length(trim(linkedin_id)) > 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_companies_linkedin_id
    ON companies(linkedin_id)
    WHERE linkedin_id IS NOT NULL;
