# Analyzer backlog

## Additional candidate experience and facts

Priority: Very High.

- Allow the candidate-fit evaluator to use additional candidate facts and
  experience not included in the main commercial resume, including personal
  projects and practical work with development tools.
- Supply this evidence alongside the resume in the normal evaluation workflow.
  Existing factual material in `Data/Profile/Applier/candidate_context.md` is a
  starting point; cover-letter writing instructions are not evaluation input.
- Preserve the distinction between commercial experience, personal projects,
  experiments, and plans. Credit supported experience without presenting it as
  commercial work or inventing duration, scope, or completed capabilities.
- Verify that relevant supplemental evidence affects requirement coverage and
  that unsupported claims remain unsupported.

## Dependency-aware candidate-fit aggregation

- Replace the flat weighted average of extracted rows with an evaluation of the
  candidate's ability to perform the role's actual work.
- Identify independent requirements, true alternatives, jointly necessary
  components, prerequisites, consequences, and overlapping requirements before
  calculating the final score.
- Treat explicit required requirements as genuinely required. Partial coverage
  of one jointly necessary component must not compensate for another missing
  component.
- Do not count derived or overlapping requirements as separate positive votes.
  A missing prerequisite must also reduce coverage of the responsibilities that
  depend on it; those responsibilities cannot compensate for that missing
  prerequisite.
- Make the final score follow coverage of the role's central capability rather
  than the percentage of vacancy bullets with any adjacent evidence.

