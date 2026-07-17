-- Rename the user-facing "Approved" status to "Applied" and add "Postponed".

INSERT OR IGNORE INTO job_statuses (code, sort_order) VALUES
    ('Applied', 40),
    ('Postponed', 30);

UPDATE job_statuses SET sort_order = 10 WHERE code = 'New';
UPDATE job_statuses SET sort_order = 20 WHERE code = 'Checked';
UPDATE job_statuses SET sort_order = 30 WHERE code = 'Postponed';
UPDATE job_statuses SET sort_order = 40 WHERE code = 'Applied';
UPDATE job_statuses SET sort_order = 50 WHERE code = 'Closed';

UPDATE jobs
SET status = 'Applied'
WHERE status = 'Approved';

DELETE FROM job_statuses
WHERE code = 'Approved';
