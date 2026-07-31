# JobSeeker Backlog

Purpose: small project backlog and current conventions. This is not an
execution contract; use `Driver/WORKFLOW.md` for pipeline behavior.

## Now

- Keep the MVP small: Codex reads vacancy text, extracts structured fields, and
  saves them into SQLite.
- Use `Data/jobs.sqlite` as the local prototype database.
- Use `Driver/WORKFLOW.md` as the public run contract: search, direct URL, and
  reprocess saved raw all converge on the same raw analysis process.
- Use `job_view` as the main filtered DB Browser view.
- Use `job_list` as the one-row-per-job overview, including rejected jobs.
- Use `Driver/collector/scan_justjoin.py` for JustJoinIT search and raw downloads.
- Use `Driver/collector/config/linkedin.properties` for the first LinkedIn search URL
  and one-vacancy debug limit.
- Use `Driver/analyzer/candidate_fit/filter.py` for quick candidate-fit checks.
- Use `Driver/analyzer/candidate_fit/config/filter.ini` to turn quick checks on and off.
- Use `Driver/db/job_mapper.py` for canonical JSON <-> SQLite mapping.
- Use `Driver/db/save.py` only as the CLI wrapper for writing final jobs.
- Use `Driver/analyzer/analyze_job.md` as the analysis skill/prompt.
- Put Codex-analyzed job JSON under `Data/analyzed/<source>/`.
- Run `Driver/analyzer/candidate_fit/evaluate.md` before `Driver/db/save.py`;
  it updates the same analyzed JSON with `candidate_fit_percent` and
  `candidate_fit_reason`.
- Use `Driver/analyzer/job_interest/config/interest.ini` for job-interest score rules.
- Run `python Driver/analyzer/job_interest/calculate.py --input <json-or-dir>`
  before `Driver/db/save.py`; the save script requires `job_interest` to
  already exist in JSON.
- Use `Driver/db/save.py` only to save fully scored JSON into
  SQLite.
- Use `Driver/analyzer/config/resume.ini` for candidate languages and
  available remote/relocation locations.

## Next

- Investigate dedup for near-identical LinkedIn jobs:
  - compare raw/card/analyzed data for 4441196528, 4441182950,
    4441197535, and 4441183936;
  - decide which fields can identify the same underlying vacancy safely.
- Investigate recruiter/aggregator dedup by Apply destination:
  - check Hired, micro1, Hire Feed, Quik Hire Staffing, and Crossing Hurdles;
  - compare where LinkedIn Apply redirects and whether they point to the same
    external vacancy/applicant system.
- Add `Mistaken` to the allowed job statuses for vacancies where analysis or
  collection produced a wrong result.
- Add staged analysis:
  - raw: vacancy downloaded but not analyzed.
  - tech_checked: technology requirements extracted and checked.
  - logistics_checked: remote scope, relocation, language, and location checked.
  - fully_analyzed: factual summary and notes completed.
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

- Revisit the archived Codex App Server transport experiment in
  `Driver/codex_proxy/app_server_experiment/` if CLI process overhead becomes
  a blocking problem. Before enabling it, prove nested orchestration and a
  complete pipeline run while keeping backend selection behind the proxy.
- Only add deterministic extraction later if it clearly removes cost without
  creating a growing pile of fragile wording rules.
- Add more sources after the JustJoinIT flow is comfortable.
- Add scheduling only after manual runs are useful.
- Add stale-vacancy cleanup.
- Consider a small UI only after DB Browser stops being enough.

## Rules We Agreed On

- Data stays local under `Data/` and is ignored by Git.
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
