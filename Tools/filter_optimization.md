# Filter Optimization

Purpose: inspect rejected vacancies and propose safer early rules for the
LinkedIn collector preview filter.

This is a project support tool, not the main vacancy workflow. It must not
change collector, analyzer, scoring, or database data by itself.

## Target Filter

The filter being optimized here is the collector preview filter:

```text
Driver/collector/linkedin_preview_filter.py
Driver/collector/config/linkedin_preview_filter.ini
Driver/collector/config/linkedin_preview_blocked_titles.txt
```

Do not confuse it with:

```text
Driver/scoring/candidate_fit/filter.py
```

`candidate_fit_percent` and `candidate_fit_reason_code` are downstream signals
from already saved and scored jobs. Use them only to discover which LinkedIn
preview titles would have been safe to skip earlier in the collector.

## Input

Use the current SQLite database:

```text
Data/jobs.sqlite
```

For now, analyze vacancies with:

```text
candidate_fit_percent = 0
```

Later this threshold may change. For example, `candidate_fit_percent < 20`
may be treated almost the same as zero.

For every selected vacancy, inspect:

```text
title
candidate_fit_reason_code
```

The first MVP output is only a grouped title list printed back to the user.
Do not edit collector filters automatically.

Process groups in this order:

- `role_mismatch`: best candidates for new title block words, because these
  are clearly not suitable roles.
- `skill_mismatch`: possible candidates for title block words or fast tech
  rejects.
- `tech`: possible candidates for fast tech rejects, but inspect carefully.

Do not optimize from these groups by default:

- `lang`: this only says the human-language requirement failed.
- `loc`: this only says location, remote scope, relocation, permit, or similar
  availability failed.
- `undefined`: old or not-yet-classified records; inspect only if explicitly
  requested.

## Current MVP Output

Return titles grouped by reason code:

```text
role_mismatch
- <title>

skill_mismatch
- <title>

tech
- <title>
```

The goal is to let the user decide which block words are safe to add.

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

If the user explicitly approves a proposal, update the collector blocklist in:

```text
Driver/collector/config/linkedin_preview_blocked_titles.txt
```

This instruction file may be updated when the optimization process itself needs
clarification so that future agents understand the same workflow.
