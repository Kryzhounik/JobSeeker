Task: analyze one job vacancy from a URL or pasted text and append one row to data/jobs.csv.

Fields:
- added_at: current date in YYYY-MM-DD format.
- source_url: original vacancy URL.
- title: job title.
- company: company name.
- location: country/city/timezone if available.
- remote_type: remote, hybrid, office, unknown.
- seniority: intern, junior, middle, senior, lead, unknown.
- role: backend, frontend, fullstack, devops, data, ml_ai, qa, product, other.
- stack: comma-separated technologies explicitly mentioned or strongly implied.
- salary: salary range/currency if available, otherwise unknown.
- match_score: 1-10 subjective fit score.
- status: new, interesting, maybe, reject.
- summary: one short sentence about the vacancy.
- pros: short reasons why it may fit.
- cons: short reasons why it may not fit.
- notes: anything uncertain or worth checking.

Default behavior:
- Do not invent facts that are not visible in the vacancy.
- If a field is unclear, use unknown.
- Prefer concise CSV-safe text without line breaks.
- Avoid duplicate rows with the same source_url.
