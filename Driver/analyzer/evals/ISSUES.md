# Analyzer evaluation issues

## Investigate extracted location and language facts

The first evaluation run (`20261127T152850Z-0301355a`) used the scripted
Candidate Fit filters. Compare its extracted facts and rejection reasons with
the vacancy descriptions and the application database, then add isolated tests
for location and human-language extraction. In particular, distinguish required
languages from preferences and verify remote and relocation facts. Expected
results must follow the actual vacancy text, not an assumption that either run
is correct. The evaluation run's analyzed JSON and database contain the extracted
facts; the application database contains the earlier results. The saved run is
under `tmp/analyzer-evals/results/` and is not tracked in Git.

The following table records the cases where Candidate Fit differed by more than
10 percentage points between the evaluation and application databases. Scores
alone do not establish an extraction error. Exxeta is a special case: its test
description was replaced with a fuller description from another publication,
while the application retained the original incomplete description.

| Vacancy | Evaluation | Application | Recorded reason difference |
| --- | ---: | ---: | --- |
| Gecko Dynamics — Senior Java Developer | 0 | 90 | Evaluation: Poland remote and no relocation; application: technical fit. |
| Miratech — Senior Java Developer | 0 | 80 | Evaluation: German required; application: technical fit. |
| Binance — Backend Java Engineer | 0 | 68 | Evaluation: Kazakh required; application: technical fit. |
| Alignerr — Software Engineer, AI Code Ranking | 0 | 72 | Evaluation: Poland remote and no relocation; application: technical fit. |
| ITSG Global — Senior Java Developer | 0 | 86 | Evaluation: German required; application: technical fit. |
| Exxeta — Java Backend Engineer | 0 | 95 | Evaluation: German required; the replacement text describes German as an advantage. |
| Cast AI — Senior Software Engineer, AI Agents & Cloud Platform | 32 | 0 | Evaluation: semantic assessment; application: missing core Go. |
| Wix — xEngineer (Backend Core) | 34 | 63 | Different assessment of backend ownership, API design and data modeling. |
| Relativity — Software Engineer | 67 | 92 | Different assessment of distributed systems and overall fit. |
| Alignerr — Senior Software Engineer, AI Infrastructure | 69 | 86 | Different assessment of AI infrastructure capability. |
| Solveva — Senior Backend Engineer, Agentic Development | 77 | 63 | Different assessment of AI agent and repository experience. |
| Innovecs — Senior Back-End Engineer (Java, AI-Native) | 65 | 76 | Different assessment of experience and AI practices. |

## Clarify remote scope when the work arrangement is unknown

The job-facts prompt specifies an empty `remote_scope` for office/hybrid work
without a fully remote option, but does not explicitly cover
`remote_type: unknown`. Clarify the distinction: an unestablished remote-work
option has no remote scope (`""`); `remote_scope: unknown` describes confirmed
fully remote work whose eligible geography cannot be established. The observed
`unknown / unknown` pair exposes this gap; do not treat it as a settled rule.

## Exxeta input and expected facts are inconsistent

For LinkedIn `4491694908`, comparing the application text with the replacement
description in `datasets/linkedin-27/dataset.sql` confirms two input problems:

- The application text repeats all nine `What awaits you` bullets under
  `What we expect from you`. The replacement instead contains substantive
  requirements, including Java/PostgreSQL, testing, infrastructure and English
  B2. The application text is therefore not a reliable technical reference.
  This comparison alone does not establish where the duplication originated.
- The application text includes a `Hybrid` header; the replacement readable
  text has no work-arrangement label. Both database records retain
  `collected_location: Tbilisi (Hybrid)` and `collected_workplace: hybrid`, but
  `job_facts/load_input.py` and `orchestrator/processing.py` pass only
  `readable_text` to the agent, not those separate collector fields. This is
  the same input path in production and evaluation.

Keep the confirmed `hybrid / ""` expectation. The evaluation input must expose
the supporting work-arrangement information while retaining the fuller
requirements; the present replacement text alone cannot support that expected
value. The empty expected language list also omits explicit English B2 in the
replacement. Optional German appearing in extracted requirements remains a
separate known extraction issue. These notes do not modify either database,
the dataset text or the Exxeta expected JSON.
