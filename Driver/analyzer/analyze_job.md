Purpose: Codex analysis prompt for one readable vacancy. It extracts structured
job facts into JSON and independently records an experimental analyzer fit.

Task: Codex analyzes one job vacancy from a saved readable text file.

Public analyzer call:
- Input: one saved readable vacancy text file, plus source/source_url when
  available.
- Do not run search or collection here.
- Do not reopen the vacancy in the browser unless the user explicitly asks.
- Produce one analyzed JSON file under `../Data/analyzed/<source>/`.
- The analyzed JSON contains vacancy facts only. Never put the experimental
  analyzer fit, official candidate fit, or job interest into it.
- After the analyzed JSON is written successfully, record the completed stage:
  `python db/job_registry.py ANALYZED --source <source> --job-id <json-file-stem>`.
  If analysis fails, leave the registry at `CLEANED`.
- While the complete readable vacancy is still in context, read
  `scoring/candidate_fit/config/resume.ini` and independently estimate how well
  the candidate fits the vacancy from 0 to 100. This is an experimental
  analyzer-owned score, not the official candidate-fit result. Store it only
  with:
  `python db/experimental_analyzer_fit.py --source <source> --url <source_url> --fit <0-100>`.
  Do not expose this score to the later candidate-fit evaluator.
- Then pass that analyzed JSON file to `scoring/candidate_fit/evaluate.md`;
  candidate-fit writes a scored JSON file under `../Data/scored/<source>/`
  with the same file name.
- Then run `python scoring/job_interest/calculate.py --input <scored-json>` to
  add `job_interest` to the scored JSON.
- Save only fully scored JSON with
  `python db/save.py --input <scored-json> --source <source>`.
- Direct URLs from the user must first be saved as raw HTML by the collection
  side, converted to readable text, then analyzed through this same call.

Do not implement this analysis as Python string matching or regex extraction.
Collector scripts only save raw pages. `analyzer/extract_readable_text_v2.py`
removes HTML/page noise but does not extract job fields. Codex/this prompt
produces structured analysis JSON. Candidate-fit and job-interest scoring happen
after this analysis and before `db/save.py`. Keep this
analysis layer out of Python string heuristics unless we explicitly decide
otherwise later.

Return structured JSON data for later scoring. The analysis DTO contract is
`contracts/job_analysis.schema.json`; use exactly those field names and shapes.
Scoring stages create a scored DTO matching `contracts/scored_job.schema.json`
by adding `candidate_fit_percent`, `candidate_fit_reason_code`,
`candidate_fit_reason`, and `job_interest` later.
`job_interest` is added to the scored JSON before DB save by
`scoring/job_interest/calculate.py --input <scored-json>`. Do not invent
aliases such as `language` instead of `name`, or `requirement_type` instead of
`requirement`. Prefer visible facts from the vacancy. Use expert judgment only
for fields that explicitly require text interpretation.

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
  `{"name": "Java", "requirement": "core", "level": "advanced", "level_rank": 4, "raw_value": "..."}`
- `level` must be the normalized label matching `level_rank`, not raw wording.
  Put raw wording such as "3+ years" or "hands-on experience" into `raw_value`.
- requirement:
  - 1, core: role-defining technology or domain. Missing it fundamentally changes
    the role. A junior-level core technology is still core. For example, C++ is
    core for a Junior C++ Developer vacancy.
  - 2, required: explicit must-have that is not itself the defining core of the
    role.
  - 3, important: strongly emphasized and materially important for doing the job,
    but not a strict or role-defining gate.
  - 4, desired: preferred and useful, but the vacancy remains realistic without
    it.
  - 5, nice_to_have: explicitly optional, bonus, plus, or nice to have.
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
- Do not assign official candidate fit or job interest here. The analyzer's
  experimental fit is stored separately and must never enter analyzed JSON.
