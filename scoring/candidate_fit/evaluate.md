Task: calculate vacancy fitability/filter result.

The candidate-fit process calculates how well the vacancy fits the candidate.
It writes `jobs.fitability_percent`.

Runtime switches live in `scoring/candidate_fit/config/filter.ini`.
Candidate facts live in `scoring/candidate_fit/config/resume.ini`.

Current stage: languages plus remote/relocation logistics.

Remote/relocation filter modes:
- `off`: ignore the field.
- `on`: require any remote or any relocation.
- `location`: require remote_scope or relocation destination to match the
  configured allowed list.

For full remote jobs, use `remote_scope` as the source of truth. The listed
office/job location does not matter if the job is truly remote. For
office/hybrid jobs in unavailable locations, pass them only when relocation is
offered and the relocation destination matches the configured list.

Language rules:
- Read known languages from `scoring/candidate_fit/config/resume.ini`.
- If required English is higher than B2, fitability is 0.
- If the vacancy requires another human language that is not listed in the
  resume config, fitability is 0.
- If the vacancy requires a known language above the resume level, fitability
  is 0.
- Otherwise fitability remains 100.
- If the vacancy has no explicit language requirements, do not reject it at
  this stage.

Remote/relocation rules:
- Read available remote scopes from `resume.ini` section `[remote]`.
- Read acceptable relocation destinations from `resume.ini` section
  `[relocation]`.
- `filter.ini` decides whether those checks are off, loose, or location-based.

Later stage:
- Add resume-to-technology fit analysis as separate steps.
- Keep every new filter step explainable and independently runnable.
