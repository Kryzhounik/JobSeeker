Task: calculate vacancy fitability as a percentage.

Fitability is a filtering score, not a sorting score. It lives in
`jobs.fitability_percent` and is intentionally hidden from `job_view`.

Current stage: language filter only.

Language rules:
- Read known languages from `analyzer/config/resume.ini`.
- If required English is higher than B2, fitability is 0.
- If the vacancy requires another human language that is not listed in the
  resume config, fitability is 0.
- If the vacancy requires a known language above the resume level, fitability
  is 0.
- Otherwise fitability remains 100.
- If the vacancy has no explicit language requirements, do not reject it at
  this stage.

Later stage:
- Add resume-to-technology fit analysis as separate steps.
- Keep every new filter step explainable and independently runnable.
