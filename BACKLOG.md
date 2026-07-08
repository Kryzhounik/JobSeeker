# JobSeeker Backlog

Purpose: small project backlog and current conventions. This is not an
execution contract; use WORKFLOW.md for pipeline behavior.

## Now

- Keep the MVP small: Codex reads vacancy text, extracts structured fields, and
  saves them into SQLite.
- Use `data/jobs.sqlite` as the local prototype database.
- Use `WORKFLOW.md` as the public run contract: search, direct URL, and
  reprocess saved raw all converge on the same raw analysis process.
- Use `job_view` as the main filtered DB Browser view.
- Use `job_list` as the one-row-per-job overview, including rejected jobs.
- Use `collector/scan_justjoin.py` for JustJoinIT search and raw downloads.
- Use `collector/config/linkedin.properties` for the first LinkedIn search URL
  and one-vacancy debug limit.
- Use `scoring/candidate_fit/filter.py` for quick candidate-fit checks.
- Use `scoring/candidate_fit/config/filter.ini` to turn quick checks on and off.
- Use `db/job_mapper.py` for canonical JSON <-> SQLite mapping.
- Use `db/save.py` only as the CLI wrapper for writing final jobs.
- Use `analyzer/prompts/analyze_job.md` as the analysis skill/prompt.
- Put Codex-analyzed job JSON under `data/analyzed/<source>/`.
- Use `workflow/save_analyzed_job.py` to read analyzed JSON, call scoring,
  and save the result into SQLite.
- Use `scoring/job_interest/config/interest.ini` for job-interest score rules.
- Keep `analyzer/save_analyzed_job.py` and
  `analyzer/recalculate_job_interest.py` only as
  compatibility/manual entry points.
- Use `scoring/candidate_fit/config/resume.ini` for candidate languages and
  available remote/relocation locations.

## Next

- Add staged analysis:
  - raw: vacancy downloaded but not analyzed.
  - tech_checked: technology requirements extracted and checked.
  - logistics_checked: remote scope, relocation, language, and location checked.
  - fully_analyzed: summary/pros/cons completed.
- Add filtering fields:
  - `analysis_stage`
  - `reject_reason`
- Granulate `candidate_fit_percent` beyond the first language filter.
- Add tech score rules for `job_interest`, up to 99 points.
- Keep `remote_scope` in Codex analysis for now:
  - `worldwide` only for explicit work-anywhere/global wording.
  - country/region only for fully remote roles.
  - empty for hybrid/office unless fully remote is also explicitly allowed.
  - keep `EU` and `Europe` distinct.
- Keep `relocation` in Codex analysis for now:
  - `NO` when relocation is absent or not mentioned.
  - country/place list when relocation is offered.

## Later

- Only add deterministic extraction later if it clearly removes cost without
  creating a growing pile of fragile wording rules.
- Add more sources after the JustJoinIT flow is comfortable.
- Add scheduling only after manual runs are useful.
- Add stale-vacancy cleanup.
- Consider a small UI only after DB Browser stops being enough.

## Rules We Agreed On

- Data stays local under `data/` and is ignored by Git.
- Do not commit inserts or raw downloaded pages.
- Avoid building a large framework before the MVP proves useful.
- Prefer one clear main view over many temporary display views.
- Technologies use ranks:
  - 1: optional / nice to have / plus.
  - 2: junior / basic / listed required mention.
  - 3: regular / hands-on / commercial or solid experience.
  - 4: advanced / senior.
  - 5: master / expert.
- `req` means required, `opt` means optional.
- `score` is the first sorting field in `job_view`; it combines `fit` and
  `interest`.
