DELETE FROM jobs
WHERE source_job_ref IN (
    SELECT id
    FROM source_jobs
    WHERE processing_status = 'PREVIEW_REJECTED'
);

DELETE FROM source_jobs
WHERE processing_status = 'PREVIEW_REJECTED';

DELETE FROM processing_statuses
WHERE code = 'PREVIEW_REJECTED';
