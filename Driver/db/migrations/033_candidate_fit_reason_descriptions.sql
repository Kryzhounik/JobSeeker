-- Reapply schema.sql after adding human-readable reason descriptions.

ALTER TABLE candidate_fit_reason_codes
ADD COLUMN description TEXT NOT NULL DEFAULT '';
