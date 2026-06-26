# JobSeeker Backlog

## Now

- Keep the MVP small: Codex reads vacancy text, extracts structured fields, and
  saves them into SQLite.
- Use `data/jobs.sqlite` as the local prototype database.
- Use `job_view` as the main DB Browser view.
- Use `scripts/write_job.py` to write analyzed vacancies directly into SQLite.
- Use `scripts/valuate_jobs.py` and `config/valuation.ini` for sorting score.

## Next

- Add staged analysis:
  - raw: vacancy downloaded but not analyzed.
  - tech_checked: technology requirements extracted and checked.
  - logistics_checked: remote scope, relocation, language, and location checked.
  - fully_analyzed: summary/pros/cons completed.
- Add filtering fields:
  - `analysis_stage`
  - `reject_reason`
- Add tech score rules for `valuation`, up to 99 points.
- Add `remote_scope` expert detection:
  - `worldwide` only for explicit work-anywhere/global wording.
  - country/region only for fully remote roles.
  - empty for hybrid/office unless fully remote is also explicitly allowed.
  - keep `EU` and `Europe` distinct.
- Add `relocation` expert detection:
  - `NO` when relocation is absent or not mentioned.
  - country/place list when relocation is offered.

## Later

- Split extraction into cheaper stages:
  - code/API extracts objective fields where reliable.
  - Codex analyzes expert fields and uncertain text.
- Add JustJoinIT objective-field adapter for title, company, salary, location,
  explicit skills, languages, and URLs.
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
- `valuation` is the first sorting field in `job_view`; higher is better.
