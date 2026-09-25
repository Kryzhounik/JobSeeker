-- Reapply schema.sql so candidate-fit reason codes become referenced data.

CREATE TABLE IF NOT EXISTS candidate_fit_reason_codes (
    code TEXT PRIMARY KEY
);
