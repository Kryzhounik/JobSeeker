Purpose: extract structured vacancy facts from one readable vacancy.

Task: perform only the job-facts operation for one saved readable vacancy.

Job-facts call:
- Input: one saved readable vacancy text file, plus source/source_url when
  available.
- Do not run search or collection here.
- Do not reopen the vacancy in the browser unless the user explicitly asks.
- Return one analyzed JSON object matching
  `contracts/job_analysis.schema.json`.
- The analyzed JSON contains vacancy facts only. Never put candidate fit or
  job interest into it.
- Do not write files or update the database. The analyzer orchestrator owns
  output persistence and lifecycle updates after this operation succeeds.
- Direct URLs from the user must first be saved as raw HTML by the collection
  side, converted to readable text, then analyzed through this same call.

Do not implement this analysis as Python string matching or regex extraction.
The source collector/adapter produces the saved readable vacancy text before
this prompt runs. Codex/this prompt produces structured analysis JSON. Keep
this facts layer out of Python string heuristics unless we explicitly decide
otherwise later.

Return structured factual JSON for later evaluation. The analysis DTO contract is
`contracts/job_analysis.schema.json`; use exactly those field names and shapes.
Do not invent aliases such as `language` instead of `name`, or
`requirement_type` instead of `requirement`. Prefer visible facts from the
vacancy. Use expert judgment only for fields that explicitly require text
interpretation.

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
- Do not mark every visible technology as `core` or `required`.
  - Use `core` only for the role-defining stack, platform, domain, or capability:
    the thing the vacancy is fundamentally about.
  - Use `required` for explicit must-have requirements that are not themselves
    the defining core of the role.
  - Use `important` for supporting skills, tools, platforms, or practices that
    materially affect daily work but are not stated or implied as strict gates.
  - Decide importance from the requirement's role in this vacancy, not from the
    technology name. A technology is not automatically core/required just
    because it is named; it is core/required only when the job would stop being
    the same job, or the candidate would be rejected, without it.
  - Generic workflow, collaboration, delivery, or tooling mentions are usually
    supporting requirements unless the vacancy explicitly makes ownership or
    deep expertise in that area a must-have.
  - Do not turn broad personality traits, generic engineering virtues, or
    process slogans into separate technology rows. They are not stack
    requirements. If the vacancy makes them materially relevant, keep them in
    `notes` or combine them into one supporting low-importance capability.
  - Do not create many separate rows from a list of generic practices. Multiple
    supporting practices must not outnumber or obscure the role-defining stack
    and explicit must-have requirements.
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
- If one requirement lists true alternatives where any one option satisfies the
  requirement, keep it as one technology item instead of splitting it into
  several required rows. Use a slash-separated name such as
  `Python / Scala / SQL`, keep the original sentence in `raw_value`, and let
  candidate-fit evaluation choose the best matching alternative. Split into
  separate rows only when the vacancy requires each item independently.
- Use `/` only for true OR alternatives. Do not use slash for AND requirements,
  bundled skill groups, capability names, examples, or concepts that must be
  covered together.
- Split AND requirements into separate technology rows when each part is a
  distinct skill or technology that should stay in the candidate-fit
  denominator. Use one capability row only when the text describes one
  indivisible capability rather than independently required parts.
- Do not expand example tool lists into many required rows when the real
  requirement is a capability. For example, `CI/CD pipelines using AWS
  CodePipeline, CodeBuild, and CodeDeploy` should usually be one required
  `AWS CI/CD pipelines` item, unless the vacancy clearly requires each service
  independently.

Remote scope rules:
- Determine `remote_scope` from the full vacancy text. The LinkedIn header and
  listed location are useful evidence, but they are not authoritative by
  themselves.
- Determine `remote_type` from whether physical presence is mandatory, not from
  the header label. If office attendance is entirely optional, use `remote`;
  use `hybrid` only when office presence is required or regularly expected.
  When the header conflicts with the full vacancy text, prefer the full text.
- `remote_scope` means the geography from which a fully remote worker may be
  located.
- For hybrid or office vacancies, leave `remote_scope` empty unless the vacancy
  explicitly allows fully remote work.
- If exact remote geography is explicitly stated as worldwide, global, any
  country, work anywhere, or equivalent wording, use `worldwide`.
- If exact remote geography is explicitly limited to a named region, timezone
  range, or country, preserve that scope exactly. Examples: EMEA, Europe, EU,
  APAC, US time zones, Poland, Germany. Do not expand regions into countries
  and do not treat EU, Europe, and EMEA as interchangeable.
- If the header says remote and the full text gives no better evidence about
  allowed remote geography, infer `remote_scope` from the listed location when
  possible.
- Do not infer `remote_scope` from the header/listed location when the full
  vacancy text gives credible evidence that allowed remote geography may differ
  from that location but does not define the exact scope.
- If remote work is allowed but the exact allowed geography cannot be determined
  from the full text, use `unknown` and explain the ambiguity in `notes`.

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
- Do not assign candidate fit or job interest here.
