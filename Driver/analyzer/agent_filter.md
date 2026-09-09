Purpose: reject vacancies that are clearly irrelevant before detailed analysis.

The current implementation receives one ordered JSON array of collected scope
items containing `source`, `job_id`, and `title`. Evaluate only the supplied
title for now.

Return one JSON object matching `contracts/agent_filter_result.schema.json`.
Put an ID in `nonrelevant_job_ids` only when its title is unambiguously
unrelated to the target software-engineering profile. Keep every ambiguous
title. Return each rejected ID at most once and never return an ID absent from
the input.
