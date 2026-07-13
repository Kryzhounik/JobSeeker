Purpose: candidate-fit rules only. This file describes how to decide whether
the candidate fits the vacancy; vacancy attractiveness lives elsewhere.

Task: calculate candidate-fit/filter result.

The candidate-fit process calculates how well the vacancy fits the candidate.
It writes `candidate_fit_percent` and `candidate_fit_reason` into the same
analyzed JSON file that came from `analyzer/analyze_job.md`.

Input/output contract:
- Input: one analyzed JSON file from `../Data/analyzed/<source>/`.
- For a directory/run scope, process every JSON file in that explicit scope.
- Output: update the same JSON file in place.
- Do not create a parallel candidate-fit output folder for the main workflow.
- Keep the same file name. For example:
  `../Data/analyzed/linkedin/4439016192.json`.
- After this stage, that same JSON is the candidate-fit-scored JSON.

Runtime switches live in `scoring/candidate_fit/config/filter.ini`.
Candidate facts live in `scoring/candidate_fit/config/resume.ini`.

Current stage: fast deterministic filtering plus mandatory agent
candidate-fit scoring for all jobs that pass the fast filter.

Before agent evaluation:
- Always run the fast deterministic filter first with
  `filter.py::filter_job_json(job_json)`.
- If the fast filter returns `candidate_fit_percent = 0`, stop the agent-stage
  evaluation, write `candidate_fit_percent = 0` and
  `candidate_fit_reason = <filter reason>` into the same JSON file.
- Do not spend agent analysis on vacancies already rejected by simple hard
  rules, such as language level mismatch, unavailable remote/relocation
  conditions, or other configured fast-filter checks.
- Run semantic agent evaluation only for vacancies that pass the fast filter.
- A positive fast-filter result is not the final candidate-fit score.
- Never save `candidate_fit_percent = 100` with
  `candidate_fit_reason = "job filter passed"` as final scoring. That value
  only means "continue to semantic candidate-fit evaluation".

Semantic candidate-fit agent stage:
- Return the final `candidate_fit_percent` from 0 to 100.
- Write both `candidate_fit_percent` and `candidate_fit_reason` into the same
  JSON file.
- This is semantic matching, not a fast script filter.
- Use the vacancy's required and nice-to-have technologies together with the
  candidate profile from `config/resume.ini`.
- Required technologies have much higher weight than nice-to-have technologies.
- Nice-to-have gaps should not push a strong required-stack match below 70 by
  themselves.
- Base the score on coverage of the vacancy's required requirements. Enumerate
  every required technology, skill, and responsibility represented in the JSON,
  estimate how well the candidate covers each one, and keep uncovered
  requirements in the denominator. Do not silently ignore requirements that do
  not match the candidate profile.
- Use approximate per-requirement coverage:
  - 1.0: direct strong match at the required level.
  - 0.75: direct match with a small level/context gap.
  - 0.5: meaningful partial match, but important depth or context is missing.
  - 0.25-0.35: weak adjacent match only.
  - 0.0: not covered.
- The base score should roughly follow the weighted average of required
  requirement coverage, converted to 0-100. Core-stack and high-rank
  requirements should weigh more than peripheral required mentions.
- If only 2 of 8 required items are directly covered, the score is around 25
  before small adjustments. If 5 of 10 required items are only half-covered, the
  score is also around 25. Weak adjacent matches should not be counted as
  half-covered.
- Nice-to-have matches may add only a small bonus after required coverage is
  assessed. They cannot compensate for missing core required requirements.
- If a required item lists true alternatives in one field, for example
  `Python/Java/Go`, use the best matching alternative for that item. If the
  vacancy separately requires several technologies, each one must be counted.
- Identify the vacancy's core technical track and primary role stack before
  scoring. Do not let secondary overlaps dominate the score.
- If a candidate strength appears only as a supporting tool inside a different
  core technical track, treat it as limited evidence. For example, experience
  with one backend ORM or framework does not make a senior Python, data
  engineering, ML/AI, mobile, frontend, DevOps/SRE, or product/program role a
  strong fit by itself.
- If the primary role stack is outside the candidate profile and requires
  substantial dedicated experience, keep the score at 50 or below even when
  some supporting tools match.
- If the vacancy requires many years of experience in a different core
  technical track, such as senior data engineering with a specific cloud data
  stack, score it around 25 unless the candidate profile directly covers that
  track.
- Do not invent experience. If a technology is not present in the candidate
  profile, treat it as unknown unless there is a clear adjacent technology.
- Use judgment for adjacent technologies. For example, NATS can partially cover
  Kafka-style messaging requirements, AWS can partially cover general cloud
  requirements even when GCP/Azure is named, and strong Spring backend
  experience can cover many Java backend framework variants.
- Explain important substitutions, gaps, and uncertainty.

Candidate-fit score meaning:
- 0: rejected by hard filter or clear mismatch.
- 25: major gaps; possible only with serious retraining.
- 50: partial fit; candidate has adjacent experience but important gaps remain.
- 75: good fit; most required technologies are covered directly or by close
  equivalents.
- 90-100: very strong fit; core stack and responsibility level match well.

Workflow requirement:
- The semantic candidate-fit agent stage is already part of the required
  workflow. If it has not been performed for a fast-filter-passed job, stop
  instead of saving the job.
- Keep every new filter step explainable and independently runnable.
