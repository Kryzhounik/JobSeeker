Purpose: cheaply reject vacancies whose titles clearly describe work unrelated
to a Java/JVM software-engineering career before detailed vacancy analysis.

Input

The input is one ordered JSON array of collected scope items. Every item
contains `source`, `job_id`, and `title`. Evaluate only the supplied title. Do
not infer requirements from the company, source, job ID, or facts that are not
present in the title.

Decision rule

Add a job ID to `nonrelevant_job_ids` only when the title itself makes the
vacancy clearly unsuitable for a Java software developer. This is a
high-precision rejection filter: false negatives are acceptable here, false
positives are not. Keep the vacancy whenever the title is ambiguous, generic,
software-related, engineering-adjacent, or could reasonably hide Java/JVM work.

Understand the occupation expressed by the whole title. Do not classify by an
isolated word. Modifiers, departments, technologies, and the grammatical head
of the title all matter. For example, a software developer working on a
marketing project is still a developer, while `Marketing Tech Lead` is a
marketing role. An unfamiliar acronym or product name alone is not grounds for
rejection.

Reject titles that unambiguously name a different profession or business
function, including:

- marketing, advertising, buying, communications, fundraising, HR,
  recruitment, accounting, compliance, sales, partnerships, or general
  business operations;
- customer support, administration, coordination, planning, procurement,
  logistics, travel, retail, or executive-assistant work;
- artists, producers, writers, media roles, teachers, coaches, medical roles,
  and other clearly non-engineering occupations;
- manual trades and domain engineering unrelated to software, such as
  electricians, maintenance, construction, manufacturing, process, route,
  hardware, electronics, automation, or quality engineering;
- clearly specialised technical roles whose primary occupation is security,
  cloud/infrastructure administration, technical art, or another non-software
  discipline.

Typical clear rejections include the kinds of roles represented by `Head of
Planning`, `HR Specialist`, `Compliance Specialist`, `Technical Artist`,
`Cybersecurity Associate`, `Senior Azure Engineer`, `Route Engineer`,
`Продюсер`, `Маркетолог`, `Архитектор проектов`, and `Спеціаліст з підтримки`.
These are calibration examples, not a literal blocklist: apply the semantic
rule to titles in any language and to equivalent wording.

Keep titles such as generic software engineer, backend engineer, full-stack
engineer, application developer, platform engineer, engineering lead,
technical lead, software architect, solutions engineer, data engineer, QA/SDET,
DevOps, and similarly adjacent technical work when the title alone does not
prove irrelevance. Also keep unclear titles such as `R&D Developer`, `Member of
Technical Staff`, or an unfamiliar product-specific developer title. Detailed
analysis decides those cases later.

Interpret common English, Ukrainian, Russian, and Polish occupational wording.
If a title is empty, malformed, too generic, or uncertain, keep it.

Output

Return only one JSON object matching
`contracts/agent_filter_result.schema.json`. Preserve input order among rejected
IDs. Return each rejected ID at most once. Never return an ID absent from the
input. Do not include explanations or any fields outside the schema.
