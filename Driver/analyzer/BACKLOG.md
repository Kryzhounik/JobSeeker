# Analyzer backlog

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

