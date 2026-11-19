# Analyzer backlog

## Preserve broader accepted alternatives in core requirements

Priority: High.

Case: internal vacancy ID `3301`, LinkedIn ID `4490301710`, Cast AI,
Senior Software Engineer | AI Agents & Cloud Platform (Cast Pilot team).
[Stored vacancy text](../../tmp/analyzer-cases/3301-4490301710/readable.txt)
is a local regression fixture, not a workflow input; `tmp/` is not tracked in Git.

Problem: the vacancy strongly prefers production Go experience but explicitly
accepts strong systems programming skills in a comparable language. Extraction
retained that alternative in notes/raw text but emitted only `Go`, `core`,
`regular / 3`. The deterministic filter therefore rejected the candidate before
semantic evaluation could consider the accepted substitute.

The prompt keeps the actual implementation stack core, but restricts OR groups
to peers at the same abstraction level and separately instructs decomposition
of a named technology and a broader accepted capability. These rules do not
consistently preserve an admission alternative alongside a core target stack.

Solution: reconcile these instructions so an explicitly accepted substitute
can remain in the same core requirement as an OR alternative, including a
broader capability: `Go / systems programming in a comparable language`.
Do not restrict OR to peers when the employer explicitly accepts the broader
alternative, or leave the substitute only in notes. The current mixed-alternative
filter already defers such a group to semantic evaluation; no new OR engine or
blanket weakening of core-language checks is needed for this case.

Verify that this case reaches semantic evaluation without a hard Go rejection,
that the evaluator assesses the actual substitute rather than assuming any
programming language suffices, and that an unconditional core Go requirement
still fails the existing strict check when the candidate lacks its level.

## Additional candidate experience and facts

Priority: Very High.

- Allow the candidate-fit evaluator to use additional candidate facts and
  experience not included in the main commercial resume, including personal
  projects and practical work with development tools.
- Supply this evidence alongside the resume in the normal evaluation workflow.
  Existing factual material in `Data/Profile/candidate_context.txt` is a
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

