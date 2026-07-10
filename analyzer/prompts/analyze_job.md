Purpose: Codex analysis prompt for one readable vacancy. It extracts structured
job fields into JSON, then hands that JSON to the workflow save step.

Task: Codex analyzes one job vacancy from a saved readable text file.

Public analyzer call:
- Input: one saved readable vacancy text file, plus source/source_url when
  available.
- Do not run search or collection here.
- Do not reopen the vacancy in the browser unless the user explicitly asks.
- Produce one analyzed JSON file under `data/analyzed/<source>/`.
- Then pass it to `scoring/candidate_fit` to add `candidate_fit_percent`.
- Then pass it to `scoring/job_interest` to add `job_interest`.
- Save only fully scored JSON with
  `python workflow/save_analyzed_job.py --input <json> --source <source>`.
- Direct URLs from the user must first be saved as raw HTML by the collection
  side, converted to readable text, then analyzed through this same call.

Do not implement this analysis as Python string matching or regex extraction.
Collector scripts only save raw pages. `analyzer/extract_readable_text_v2.py`
removes HTML/page noise but does not extract job fields. Codex/this prompt
produces structured analysis JSON. Candidate-fit and job-interest scoring happen
after this analysis and before `workflow/save_analyzed_job.py`. Keep this
analysis layer out of Python string heuristics unless we explicitly decide
otherwise later.

Return structured JSON data for saving into SQLite. The final saved DTO contract
is `contracts/job_analysis.schema.json`; use exactly those field names and
shapes for the analysis-owned fields. Scoring stages add
`candidate_fit_percent`, `candidate_fit_reason`, and `job_interest` later. Do
not invent aliases such as `language` instead of `name`, or `requirement_type`
instead of `requirement`. Prefer visible facts from the vacancy. Use expert
judgment only for fields that explicitly require text interpretation.

Job fields:
- added_at: current date in YYYY-MM-DD format.
- source_url: original vacancy URL.
- title: job title.
- company: company name.
- location: country/city/timezone if available.
- remote_type: remote, hybrid, office, unknown.
- remote_scope: actual allowed remote geography, for example worldwide, EU,
  Europe, Poland, Germany, US time zones, EMEA, unknown.
- relocation: NO, or a concise list of relocation destination countries/places.
- seniority: intern, junior, middle, senior, lead, unknown.
- role: backend, frontend, fullstack, devops, data, ml_ai, qa, product, other.
- salary: salary range/currency if available, otherwise empty string.
- summary: one short sentence about the vacancy.
- pros: short reasons why it may fit.
- cons: short reasons why it may not fit.
- notes: anything uncertain or worth checking.

Languages:
- Extract human languages only.
- Store each language separately with its level when stated.
- Use this JSON shape for each language:
  `{"name": "English", "level": "B2", "level_rank": 4, "raw_value": "..."}`
- Pick primary_language as the language with the highest required level.
- If a language is unclear, do not confuse it with a programming language.

Technology requirements:
- Store technologies separately.
- Use this JSON shape for each technology:
  `{"name": "Java", "requirement": "required", "level": "advanced", "level_rank": 4, "raw_value": "..."}`
- `level` must be the normalized label matching `level_rank`, not raw wording.
  Put raw wording such as "3+ years" or "hands-on experience" into `raw_value`.
- requirement:
  - required: the vacancy says or strongly implies the technology is required.
  - nice_to_have: the vacancy says nice to have, will be a plus, optional, bonus.
- level_rank:
  - 1: nice to have / optional / will be a plus.
  - 2: junior, basic, beginner, or required/listed/mentioned without depth.
  - 3: regular, intermediate, hands-on experience, commercial experience,
    production experience, solid experience, strong practical experience.
  - 4: advanced, senior.
  - 5: master, expert.
- Explicit JustJoinIT levels win when visible in the vacancy header.
- If a technology is only mentioned in text, infer the level from wording.
- If wording is too vague but the technology is required, use level_rank 2.
- If wording is too vague and the technology is optional, use level_rank 1.

Remote scope rules:
- For hybrid or office vacancies, leave remote_scope empty unless the vacancy
  also explicitly allows fully remote work from some geography.
- Use worldwide when the vacancy says work anywhere, any location worldwide,
  work from any country, globally remote.
- If remote is limited to a region, write the region itself, for example EU,
  Europe, EMEA, APAC, US time zones. Keep EU and Europe separate.
- If remote is limited to a country, write the country itself, for example
  Poland, Germany, United States.
- If the vacancy is remote but does not explicitly say worldwide/global/work
  anywhere, infer the remote scope from the vacancy location when possible.
  Example: a remote vacancy located in Warsaw, Poland becomes Poland.
- Use unknown when remote/hybrid/office is known, but allowed geography is
  unclear after checking both the remote wording and the listed location, or
  when this analysis has not been done yet.

Relocation rules:
- Use NO when relocation is not offered or not mentioned.
- If relocation is offered, write the destination countries/places, for example
  Poland, Germany, Netherlands, Warsaw.
- Treat visa sponsorship, relocation package, relocation support, and paid move
  assistance as relocation signals.
- Do not mark relocation just because the office location is listed.

Default behavior:
- Keep objective extraction and expert inference separate in notes when useful.
- Do not invent company facts, salary, or benefits that are not visible.
- Use unknown for unclear factual fields.
- Keep text concise and single-line where possible.
- Do not assign candidate fit or job interest here; they are calculated later by
  `scoring/candidate_fit` and `scoring/job_interest`.
