Purpose: reject vacancies that are clearly irrelevant from their titles alone.

Input: one ordered JSON array of collected scope items. Each item contains
`source`, `job_id`, and `title`.

Return one JSON object matching `contracts/title_filter_result.schema.json`.
Put an ID in `nonrelevant_job_ids` only when its title is unambiguously
unrelated to the target software-engineering profile. Use only the supplied
title. Keep every ambiguous title. Return each rejected ID at most once and
never return an ID absent from the input.
