Task: calculate vacancy valuation for sorting.

The valuation is an algorithmic sorting score. Higher is better. Do not mix it
with factual extraction. Use `analyzer/config/valuation.ini` as the source of numeric
weights.

Current formula:
- valuation = remote_score + relocation_score + tech_score.

Remote score:
- Only fully remote vacancies can receive remote_score.
- `remote_scope = worldwide` gives the configured worldwide score.
- For non-worldwide remote vacancies, match `remote_scope` against configured
  countries/regions and use the highest matched score.
- Hybrid and office vacancies receive remote_score = 0.
- Empty or unknown remote_scope receives remote_score = 0.

Relocation score:
- `relocation = NO`, empty, or unknown gives relocation_score = 0.
- If relocation is available, start with configured `base`.
- Match relocation destinations against configured countries/regions and add
  the highest matched score.
- If relocation exists but no configured destination matches, use only base.

Tech score:
- Match required and optional technologies against `[tech.keywords]`.
- Add the configured score once per matched technology keyword.
- Cap the result by `[tech] max`.
- This is intentionally rough and can be tuned later.

Notes:
- Keep EU and Europe distinct when scoring.
- If multiple countries/regions are listed, use the maximum score, not a sum.
