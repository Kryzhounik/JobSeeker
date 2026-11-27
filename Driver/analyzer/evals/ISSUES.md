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
