# Filter Optimization

Purpose: inspect rejected vacancies and propose safer early rules for the
LinkedIn collector preview filter.

This is a project support tool, not the main vacancy workflow. It must not
change collector, analyzer, scoring, or database data by itself.

## Target Filter

The filter being optimized here is the collector preview filter:

```text
Driver/collector/filtering/linkedin_filter.py
Driver/collector/filtering/linkedin_preview_filter.ini
Driver/collector/filtering/linkedin_preview_blocked_titles.txt
```

The preview stage checks both preview-visible fields before opening a vacancy:

```text
title -> configured title block terms
company -> companies.blacklisted in Data/jobs.sqlite
```

Company matching is an exact, case-insensitive name match. Companies are stored
once in `companies`; saved jobs reference them through `jobs.company_id`.
`companies.blacklisted` defaults to `0`, and deleting a job does not delete its
company entry or blacklist setting.

Do not confuse it with:

```text
Driver/analyzer/candidate_fit/filter.py
```

`candidate_fit_percent` and `candidate_fit_reason_code` are downstream signals
from already saved and scored jobs. Use them only to discover which LinkedIn
preview titles would have been safe to skip earlier in the collector.

## Input

Use the current SQLite database:

```text
Data/jobs.sqlite
```

For the current optimization pass, analyze every vacancy with:

```text
0 <= candidate_fit_percent <= 15
```

Do not silently narrow this range to zero-only records or to selected reason
codes.

For every selected vacancy, inspect:

```text
exact collector preview title
candidate_fit_reason_code
candidate_fit_percent
```

Resolve the title from the latest matching `linkedin_collection_events.title`
when available, then from the saved raw page heading, and only then from the
database job title. Proposals must be supported by the full title that was
actually visible to the collector.

The first MVP output is only a grouped evidence title list printed back to the
user.
Do not edit collector filters automatically.

Inspect all reason-code groups. A reason code is context for explaining a row,
not a reason to exclude it from title analysis: the first downstream failure
can hide an obviously irrelevant role.

- `role_mismatch`: best candidates for new title block words, because these
  are clearly not suitable roles.
- `skill_mismatch`: possible candidates for title block words or fast tech
  rejects.
- `tech`: possible candidates for fast tech rejects, but inspect carefully.
- `lang`: this only says the human-language requirement failed.
- `loc`: this only says location, remote scope, relocation, permit, or similar
  availability failed.
- `undefined`: old or not-yet-classified records that still require title
  inspection.
- `ok`: low-fit records that still require title inspection.

Collector title matching is order-sensitive. For every promising block phrase,
check word-order variants present in the evidence. For example,
`Administrator IT` does not block `IT Administrator`; each observed form needs
its own matching term unless the filter implementation explicitly handles both.

## Current MVP Output

Return exact downloaded vacancy titles grouped by reason code as evidence. Do
not call every title a blocklist candidate. Do not collapse titles into broad
keywords, technologies, domains, or shortened summaries.

```text
role_mismatch
- <title>

skill_mismatch
- <title>

tech
- <title>
```

The goal is to let the user decide which block words are safe to add.
At this stage the output is the evidence list, not the final blocklist.

When converting evidence into proposals, use only preview-visible phrases that
are safe block terms by themselves. If a title or phrase can plausibly describe
a valid Java/backend vacancy, keep it as evidence only.

## Future Output

Return proposals only. Do not edit collector filters automatically.

For each proposal include:

```text
candidate block term / rule
why it is safe
example rejected vacancies
which reason_code group produced it
possible false positives
```

Prefer precise terms over broad ones. For example, prefer a senior/specific
non-target role title over a generic word that can appear inside a valid Java
backend vacancy.

Proposal terms must be safe from the preview title alone. Prefer exact vacancy
title phrases, role names, or title-visible non-target domains. Do not propose
bare technology names just because the downstream reason mentions a missing
technology. For example, do not propose `Golang` from `Staff Software Engineer,
Golang`: a valid vacancy title could be `Java Developer (Golang is a plus)`,
and the preview filter would reject it incorrectly.

Do not propose broad domain words such as `Big Data`, `AI`, `Cloud`, or
`Platform` by themselves. A valid target vacancy title can contain those words
as context, for example `Java Developer (Big Data project)`. If a shortened
proposal is needed, it must still be a role-level phrase that would not reject
a valid Java/backend vacancy.

If the user explicitly approves a proposal, update the collector blocklist in:

```text
Driver/collector/filtering/linkedin_preview_blocked_titles.txt
```

## Manual Database Pass

After updating filter configuration, run every saved vacancy through the same
top-level filter used by the GUI and review the rejected jobs:

```powershell
python Tools/filter_database.py
```

The utility reads the normalized company name, `jobs.title`, and stored
`source_job_texts.readable_text`, then calls `VacancyFilter.filter(Vacancy)`.
It prints a JSON list with each rejected job's ID, title, and reason and does
not change the database. Deletion is a separate database-layer operation used
by the GUI after immediate execution or explicit confirmation.

This instruction file may be updated when the optimization process itself needs
clarification so that future agents understand the same workflow.

## Content-Stage Research

Use saved readable vacancy text to discover technologies for the second filter
stage. Vacancies with `0 <= candidate_fit_percent <= 30` are discovery
evidence. Before approving a technology, simulate it against every saved
vacancy that passes the title filter and report every affected vacancy with
`candidate_fit_percent >= 40`.

This high-fit report is an audit aid, not a veto on the proposed technology.
The fit score is currently an arithmetic aggregate and can overestimate a role
that has one unsupported mandatory technology. A vacancy may still be safe to
reject at fit 40, 50, or 60 when the saved text clearly requires that missing
technology; show it to the user for review instead of silently excluding the
technology from the proposal.

The content filter is intentionally soft. A blocked technology rejects a
vacancy only when it is bound to a complete hard-requirement template and
neither of the following appears in the matching line or an adjacent non-empty
line:

- Java as a whole word
- optional wording such as `nice to have`, `plus`, `not required`, or
  `preferred`

`JavaScript` must not count as `Java`. Java in the requirement line or an
adjacent non-empty line keeps the vacancy. Generic alternatives such as `or`,
`and/or`, `one of`, and `any of` do not keep it unless Java or explicit optional
wording is also present. When the text is ambiguous, keep the vacancy.

Every entry in `[hard_requirement_templates]` must contain `{technology}` so
that the requirement and technology are matched as one structure. Never add
standalone words such as `required`, `advanced`, `experience`, or `years` as
hard signals. Use `{years}` inside a complete template for numeric requirements
starting at two years.

Human-language requirement extraction is a separate, conservative module:

```text
Driver/collector/filtering/language_requirements.py
Driver/collector/filtering/linkedin_language_filter.ini
```

`VacancyFilter.filter_text()` calls this module and compares only confidently
extracted `language + CEFR level` requirements with
`Driver/analyzer/config/resume.ini`. Templates that contain explicit CEFR
levels are kept in `[requirement_templates]` and use both `{language}` and
`{level}`. Phrases that imply a fixed level are kept under that level in
`[implied_level_templates]` and use `{language}` only. Both lists may remain
empty; then language extraction returns no requirements and cannot reject a
vacancy. Keep extraction soft: an unrecognized or ambiguous phrase passes to
the agent pipeline.
