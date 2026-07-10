Purpose: candidate-fit rules only. This file describes how to decide whether
the candidate fits the vacancy; vacancy attractiveness lives elsewhere.

Task: calculate candidate-fit/filter result.

The candidate-fit process calculates how well the vacancy fits the candidate.
It writes `jobs.candidate_fit_percent`.

Runtime switches live in `scoring/candidate_fit/config/filter.ini`.
Candidate facts live in `scoring/candidate_fit/config/resume.ini`.

Current stage: fast deterministic filtering plus mandatory agent
candidate-fit scoring for all jobs that pass the fast filter.

Before agent evaluation:
- Always run the fast deterministic filter first with
  `filter.py::filter_job_json(job_json)`.
- If the fast filter returns `candidate_fit_percent = 0`, stop the agent-stage
  evaluation and keep the rejection reason from the filter.
- Do not spend agent analysis on vacancies already rejected by simple hard
  rules, such as language level mismatch, unavailable remote/relocation
  conditions, or other configured fast-filter checks.
- Run semantic agent evaluation only for vacancies that pass the fast filter.
- A positive fast-filter result is not the final candidate-fit score.
- Never save `candidate_fit_percent = 100` with
  `candidate_fit_reason = "job filter passed"` as final scoring. That value
  only means "continue to semantic candidate-fit evaluation".

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
- If required English is higher than B2, candidate fit is 0.
- If the vacancy requires another human language that is not listed in the
  resume config, candidate fit is 0.
- If the vacancy requires a known language above the resume level, candidate fit
  is 0.
- Otherwise candidate fit remains 100.
- If the vacancy has no explicit language requirements, do not reject it at
  this stage.

Remote/relocation rules:
- Read available remote scopes from `resume.ini` section `[remote]`.
- Read acceptable relocation destinations from `resume.ini` section
  `[relocation]`.
- `filter.ini` decides whether those checks are off, loose, or location-based.

Semantic candidate-fit agent stage:
- Return the final `candidate_fit_percent` from 0 to 100.
- This is semantic matching, not a fast script filter.
- Use the vacancy's required and nice-to-have technologies together with the
  candidate profile from `config/resume.ini`.
- Required technologies have much higher weight than nice-to-have technologies.
- Nice-to-have gaps should not push a strong required-stack match below 0.7 by
  themselves.
- Do not invent experience. If a technology is not present in the candidate
  profile, treat it as unknown unless there is a clear adjacent technology.
- Use judgment for adjacent technologies. For example, NATS can partially cover
  Kafka-style messaging requirements, AWS can partially cover general cloud
  requirements even when GCP/Azure is named, and strong Spring backend
  experience can cover many Java backend framework variants.
- Explain important substitutions, gaps, and uncertainty.

Candidate-fit score meaning:
- 0: rejected by hard filter or clear mismatch.
- 25: major gaps; possible only with serious retraining.
- 50: partial fit; candidate has adjacent experience but important gaps remain.
- 75: good fit; most required technologies are covered directly or by close
  equivalents.
- 90-100: very strong fit; core stack and responsibility level match well.

Workflow requirement:
- The semantic candidate-fit agent stage is already part of the required
  workflow. If it has not been performed for a fast-filter-passed job, stop
  instead of saving the job.
- Keep every new filter step explainable and independently runnable.
