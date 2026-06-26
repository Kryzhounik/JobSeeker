Task: calculate vacancy fitability/filter result.

The shared filter is used before deeper work where possible, and before
valuation for analyzed jobs. If a vacancy does not pass the filter, its
`jobs.fitability_percent` is 0 and valuation is 0.

Runtime switches live in `common/config/filter.ini`.

Current stage: languages plus remote/relocation logistics.

Language rules:
- Read known languages from `common/config/resume.ini`.
- If required English is higher than B2, fitability is 0.
- If the vacancy requires another human language that is not listed in the
  resume config, fitability is 0.
- If the vacancy requires a known language above the resume level, fitability
  is 0.
- Otherwise fitability remains 100 and the vacancy can be valued normally.
- If the vacancy has no explicit language requirements, do not reject it at
  this stage.

Later stage:
- Add resume-to-technology fit analysis as separate steps.
- Keep every new filter step explainable and independently runnable.
