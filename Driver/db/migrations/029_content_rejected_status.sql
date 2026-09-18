INSERT OR IGNORE INTO processing_statuses (code, sort_order)
VALUES ('CONTENT_REJECTED', 22);

UPDATE source_jobs
SET processing_status = 'CONTENT_REJECTED'
WHERE processing_status = 'CLEANED'
  AND EXISTS (
      SELECT 1
      FROM content_filter_rejections rejection
      WHERE rejection.source_job_ref = source_jobs.id
  );
