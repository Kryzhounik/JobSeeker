-- Remove directory placeholders accidentally seen by the initial file backfill.

DELETE FROM source_jobs
WHERE source_job_id = '.gitkeep'
    AND NOT EXISTS (
        SELECT 1 FROM jobs WHERE jobs.source_job_ref = source_jobs.id
    );
