-- Remove the discontinued analyzer-owned fit experiment.

DROP VIEW IF EXISTS job_list;
DROP VIEW IF EXISTS job_view;
DROP TABLE IF EXISTS experimental_analyzer_fits;
